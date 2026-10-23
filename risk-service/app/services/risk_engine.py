from __future__ import annotations

import asyncio
from datetime import date, datetime, time, timedelta, timezone
from typing import Any, Callable

import numpy as np
import pandas as pd

from app.clients.trade_api_client import TradeApiClient, TradeApiError
from app.core.config import Settings
from app.db.postgres import read_only_connection
from app.repositories.historical_prices_repo import HistoricalPricesRepository
from app.schemas.risk_models import CustomShockRequest
from app.services.metrics import (
    calculate_drawdown,
    calculate_instrument_beta,
    calculate_risk_contributions,
    calculate_var_metrics,
)
from app.services.stress import custom_shock, historical_scenarios


class RiskEngine:
    def __init__(
        self,
        settings: Settings,
        trade_client_factory: Callable[..., TradeApiClient] = TradeApiClient,
        connection_factory: Callable[..., Any] = read_only_connection,
    ):
        self.settings = settings
        self.trade_client_factory = trade_client_factory
        self.connection_factory = connection_factory

    async def _load_snapshot(self, account_id: int, token: str) -> tuple[dict[str, Any], list[dict[str, Any]], list[str]]:
        async with self.trade_client_factory(self.settings, token) as client:
            account, positions, instruments = await asyncio.gather(
                client.get_account(account_id),
                client.get_positions(account_id),
                client.get_instruments(),
            )
            holdings_positions = [item for item in positions if float(item.get("quantity", 0) or 0) != 0]
            symbols = sorted({str(item["symbol"]).upper() for item in holdings_positions})
            quotes_payload = await client.get_quotes(symbols) if symbols else {"quotes": [], "missingSymbols": []}
        instrument_map = {str(item.get("symbol", "")).upper(): item for item in instruments}
        quote_map = {str(item.get("symbol", "")).upper(): item for item in quotes_payload.get("quotes", [])}
        issues: list[str] = []
        holdings: list[dict[str, Any]] = []
        for position in holdings_positions:
            symbol = str(position.get("symbol", "")).upper()
            instrument = instrument_map.get(symbol, {})
            quote = quote_map.get(symbol)
            quantity = float(position.get("quantity", 0) or 0)
            price = float(quote.get("lastPrice")) if quote and quote.get("lastPrice") is not None else None
            market_value = quantity * price if price is not None and price > 0 else None
            if market_value is None:
                issues.append(f"Missing or invalid latest quote for {symbol}")
            if not instrument:
                issues.append(f"Missing instrument metadata for {symbol}")
            sector = instrument.get("sector")
            if not sector:
                issues.append(f"Missing sector metadata for {symbol}")
            holdings.append({
                "symbol": symbol,
                "company": instrument.get("name") or symbol,
                "sector": sector,
                "marketValue": market_value,
            })
        cash = float(account.get("cashBalance", 0) or 0)
        known_invested = sum(item["marketValue"] for item in holdings if item["marketValue"] is not None)
        has_missing_value = any(item["marketValue"] is None for item in holdings)
        equity = None if has_missing_value else cash + known_invested
        snapshot = {
            "account": account,
            "holdings": holdings,
            "cash": cash,
            "invested": known_invested,
            "equity": equity,
            "complete": not issues,
        }
        return snapshot, issues, symbols

    def _history(self, symbols: list[str], start: date, end: date) -> dict[str, dict[date, float]]:
        with self.connection_factory(self.settings) as connection:
            rows = HistoricalPricesRepository(connection).get_adjusted_close(symbols, start, end)
        prices: dict[str, dict[date, float]] = {}
        for row in rows:
            symbol = str(row["symbol"]).upper()
            adjusted = row["adjusted_close"]
            if adjusted is not None:
                prices.setdefault(symbol, {})[row["price_date"]] = float(adjusted)
        return prices

    def _portfolio_frame(self, prices: dict[str, dict[date, float]], symbols: list[str]) -> pd.DataFrame:
        series = {symbol: pd.Series(values, dtype=float) for symbol, values in prices.items() if symbol in symbols}
        if not series:
            return pd.DataFrame()
        frame = pd.DataFrame(series)
        frame.index = pd.to_datetime(frame.index)
        return frame.sort_index()

    def _base_status(self, status: str, issues: list[str], observations: int | None = None) -> dict[str, Any]:
        return {"status": status, "issues": issues, "observations": observations, "requiredObservations": self.settings.minimum_observations}

    async def build_dashboard(
        self,
        account_id: int,
        token: str,
        lookback_days: int,
        confidence: float,
        benchmark: str,
        mc_paths: int,
    ) -> dict[str, Any]:
        snapshot, snapshot_issues, symbols = await self._load_snapshot(account_id, token)
        today = date.today()
        historical_symbols = symbols + ([] if benchmark in symbols else [benchmark])
        prices = self._history(sorted(set(historical_symbols)), date(2006, 1, 1), today + timedelta(days=1)) if historical_symbols else {}
        holdings = snapshot["holdings"]
        equity = snapshot["equity"]
        invested = snapshot["invested"]
        account = snapshot["account"]
        section_status: dict[str, dict[str, Any]] = {
            "currentSnapshot": self._base_status("PARTIAL" if snapshot_issues else "READY", snapshot_issues),
            "historicalRisk": self._base_status("UNAVAILABLE", []),
            "stress": self._base_status("UNAVAILABLE", []),
        }
        risk_issues = list(snapshot_issues)
        for holding in holdings:
            market_value = holding.get("marketValue")
            holding["allocationPercent"] = round(market_value / invested * 100.0, 4) if market_value is not None and invested > 0 else None
        sector_exposure = self._sector_exposure(holdings, invested)
        scenarios, waterfalls, stress_issues = historical_scenarios(holdings, prices, equity or (snapshot["cash"] + invested), snapshot["cash"])
        section_status["stress"] = self._base_status("PARTIAL" if stress_issues else "READY", stress_issues)
        risk_issues.extend(stress_issues)

        risk = self._calculate_risk(holdings, prices, equity, lookback_days, confidence, benchmark, mc_paths)
        section_status["historicalRisk"] = risk["status"]
        risk_issues.extend(risk["status"]["issues"])
        self._apply_holding_metrics(holdings, risk)
        observed_dates = risk.get("dates", [])
        all_covered = [item["symbol"] for item in holdings if item["symbol"] in prices]
        missing_symbols = [item["symbol"] for item in holdings if item["symbol"] not in prices]
        portfolio = {
            "accountId": str(account.get("id", account_id)),
            "accountLabel": account.get("accountId") or account.get("holderName") or str(account_id),
            "asOf": datetime.now(timezone.utc).isoformat(),
            "historicalFrom": observed_dates[0] if observed_dates else str(today),
            "historicalTo": observed_dates[-1] if observed_dates else str(today),
            "cashReserveUsd": round(snapshot["cash"], 2),
            "investedEquityUsd": round(invested, 2),
            "portfolioValueUsd": round(equity, 2) if equity is not None else None,
            "oneDayVar": risk["one_day"],
            "varByHorizon": {
                horizon: {
                    "historicalUsd": values.get("historical"),
                    "parametricUsd": values.get("parametric"),
                    "monteCarloUsd": values.get("monteCarlo"),
                }
                for horizon, values in risk.get("by_horizon", {}).items()
            },
            "expectedShortfallUsd": risk.get("expected_shortfall"),
            "volatilityPercent": risk.get("volatility_percent"),
            "beta": risk.get("beta"),
            "maximumDrawdownPercent": risk.get("maximum_drawdown_percent"),
            "maximumDrawdownDate": risk.get("maximum_drawdown_date"),
            "dailyReturnCount": risk.get("daily_return_count", 0),
            "monteCarloPathCount": mc_paths,
            "holdings": holdings,
            "distribution": risk.get("distribution", []),
            "drawdownSeries": risk.get("drawdown_series", []),
            "insights": self._insights(holdings, snapshot["cash"], equity, risk),
            "sectorExposure": sector_exposure,
            "scenarios": scenarios,
            "historicalWaterfalls": waterfalls,
            "sectionStatus": section_status,
            "coverage": {
                "coveredSymbols": all_covered,
                "missingSymbols": sorted(set(missing_symbols)),
                "observedFrom": observed_dates[0] if observed_dates else None,
                "observedTo": observed_dates[-1] if observed_dates else None,
            },
            "issues": risk_issues,
        }
        return {"portfolios": [portfolio]}

    async def build_custom_shock(self, account_id: int, token: str, request: CustomShockRequest) -> dict[str, Any]:
        snapshot, issues, _ = await self._load_snapshot(account_id, token)
        if snapshot["equity"] is None:
            raise TradeApiError("Custom shock requires a complete current quote set")
        if issues and any("quote" in issue.lower() for issue in issues):
            raise TradeApiError("Custom shock requires valid latest quotes for every holding")
        return custom_shock(snapshot["holdings"], snapshot["equity"], snapshot["cash"], request.market, request.sector_shocks())

    def _calculate_risk(
        self,
        holdings: list[dict[str, Any]],
        prices: dict[str, dict[date, float]],
        equity: float | None,
        lookback_days: int,
        confidence: float,
        benchmark: str,
        mc_paths: int,
    ) -> dict[str, Any]:
        if not holdings and equity is not None:
            return {
                "status": self._base_status("READY", ["All-cash portfolio: risk metrics are zero by construction."], 0),
                "one_day": {"historicalUsd": 0.0, "parametricUsd": 0.0, "monteCarloUsd": 0.0},
                "by_horizon": {
                    str(horizon): {"historical": 0.0, "parametric": 0.0, "monteCarlo": 0.0}
                    for horizon in (1, 10, 30)
                },
                "expected_shortfall": 0.0,
                "volatility_percent": 0.0,
                "beta": 0.0,
                "maximum_drawdown_percent": 0.0,
                "maximum_drawdown_date": None,
                "daily_return_count": 0,
                "distribution": [{"returnUsd": 0.0, "frequency": 1}],
                "drawdown_series": [],
                "dates": [],
                "holding_metrics": {},
            }
        issues: list[str] = []
        symbols = [item["symbol"] for item in holdings]
        missing = [symbol for symbol in symbols + [benchmark] if symbol not in prices]
        if equity is None:
            issues.append("Portfolio equity is unavailable because one or more latest quotes are missing")
        if missing:
            issues.append(f"Missing historical adjusted-close data for {', '.join(sorted(set(missing)))}")
        if equity is None or missing:
            return {"status": self._base_status("UNAVAILABLE", issues), "one_day": {"historicalUsd": None, "parametricUsd": None, "monteCarloUsd": None}, "dates": [], "holding_metrics": {}}
        frame = self._portfolio_frame(prices, symbols + [benchmark]).dropna(subset=symbols + [benchmark]).tail(lookback_days + 1)
        if len(frame) < self.settings.minimum_observations + 1:
            issues.append(f"Only {max(0, len(frame) - 1)} aligned daily returns are available; {self.settings.minimum_observations} are required")
            return {"status": self._base_status("UNAVAILABLE", issues, max(0, len(frame) - 1)), "one_day": {"historicalUsd": None, "parametricUsd": None, "monteCarloUsd": None}, "dates": [], "holding_metrics": {}}
        returns = frame.pct_change().dropna()
        weights = np.array([item["marketValue"] / equity for item in holdings], dtype=float)
        asset_returns = returns[symbols].to_numpy(dtype=float)
        benchmark_returns = returns[benchmark].to_numpy(dtype=float)
        portfolio_returns = asset_returns @ weights
        var = calculate_var_metrics(portfolio_returns, asset_returns, weights, equity, confidence, (1, 10, 30), mc_paths)
        drawdown_series, max_drawdown, max_date = calculate_drawdown(portfolio_returns, [item.strftime("%Y-%m-%d") for item in returns.index])
        covariance_volatility = float(np.std(portfolio_returns, ddof=1) * np.sqrt(252) * 100.0)
        portfolio_beta = float(np.cov(portfolio_returns, benchmark_returns, ddof=1)[0, 1] / np.var(benchmark_returns, ddof=1)) if np.var(benchmark_returns, ddof=1) > 0 else None
        contributions = calculate_risk_contributions(asset_returns, weights)
        betas = calculate_instrument_beta(asset_returns, benchmark_returns)
        holding_metrics = {}
        for index, symbol in enumerate(symbols):
            sigma = float(np.std(asset_returns[:, index], ddof=1))
            standalone = float(norm_ppf(confidence) * sigma * holdings[index]["marketValue"]) if holdings[index].get("marketValue") is not None else None
            holding_metrics[symbol] = {
                "beta": float(betas[index]) if np.isfinite(betas[index]) else None,
                "annualizedVolatilityPercent": sigma * np.sqrt(252) * 100.0,
                "standaloneVarUsd": standalone,
                "volatilityContributionPercent": float(contributions[index]),
            }
        return {
            "status": self._base_status("READY", issues, len(returns)),
            "one_day": var["one_day"],
            "by_horizon": var["by_horizon"],
            "expected_shortfall": var["expectedShortfallUsd"],
            "volatility_percent": covariance_volatility,
            "beta": portfolio_beta,
            "maximum_drawdown_percent": max_drawdown,
            "maximum_drawdown_date": max_date,
            "daily_return_count": len(returns),
            "distribution": var["distribution"],
            "drawdown_series": drawdown_series,
            "dates": [item.strftime("%Y-%m-%d") for item in returns.index],
            "holding_metrics": holding_metrics,
        }

    def _apply_holding_metrics(self, holdings: list[dict[str, Any]], risk: dict[str, Any]) -> None:
        for holding in holdings:
            holding.update(risk.get("holding_metrics", {}).get(holding["symbol"], {}))

    def _sector_exposure(self, holdings: list[dict[str, Any]], invested: float) -> list[dict[str, Any]]:
        grouped: dict[str, dict[str, Any]] = {}
        for holding in holdings:
            if holding.get("marketValue") is None or not holding.get("sector"):
                continue
            item = grouped.setdefault(holding["sector"], {"marketValue": 0.0, "symbols": []})
            item["marketValue"] += holding["marketValue"]
            item["symbols"].append(holding["symbol"])
        return [
            {"sector": sector, "marketValue": round(value["marketValue"], 2), "allocationPercent": round(value["marketValue"] / invested * 100.0, 4) if invested else 0.0, "symbols": value["symbols"]}
            for sector, value in sorted(grouped.items())
        ]

    def _insights(self, holdings: list[dict[str, Any]], cash: float, equity: float | None, risk: dict[str, Any]) -> list[dict[str, str]]:
        insights = []
        if equity and equity > 0:
            insights.append({"label": "Cash reserve", "value": f"{cash / equity * 100:.1f}%", "detail": "Cash reduces account-level exposure."})
        known = [item for item in holdings if item.get("allocationPercent") is not None]
        if known:
            top = max(known, key=lambda item: item["allocationPercent"])
            insights.append({"label": "Top holding", "value": f"{top['symbol']} · {top['allocationPercent']:.1f}%", "detail": "Largest invested-equity allocation."})
        if risk.get("status", {}).get("status") != "READY":
            insights.append({"label": "Data coverage", "value": risk.get("status", {}).get("status", "UNAVAILABLE"), "detail": "Some analytics are unavailable until enough aligned history is present."})
        return insights


def norm_ppf(confidence: float) -> float:
    from scipy.stats import norm

    return float(norm.ppf(confidence))
