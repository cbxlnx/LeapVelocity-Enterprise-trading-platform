from __future__ import annotations

from datetime import date
from typing import Any


def _scenario_return(prices: dict[date, float], start: date, end: date) -> float | None:
    start_values = [value for trade_date, value in prices.items() if trade_date >= start and trade_date <= end]
    if len(start_values) < 2:
        return None
    first = next(value for trade_date, value in sorted(prices.items()) if trade_date >= start and trade_date <= end)
    last = next(value for trade_date, value in reversed(sorted(prices.items())) if trade_date >= start and trade_date <= end)
    return last / first - 1.0 if first > 0 else None


def _waterfall(title: str, period: str, starting_equity: float, cash: float, deltas: list[dict[str, Any]]) -> dict[str, Any]:
    ending = starting_equity + sum(item["deltaUsd"] for item in deltas)
    return {
        "title": title,
        "period": period,
        "startingEquityUsd": round(starting_equity, 2),
        "deltas": deltas,
        "endingEquityUsd": round(ending, 2),
        "cashReserveUsd": round(cash, 2),
    }


def historical_scenarios(
    holdings: list[dict[str, Any]],
    prices: dict[str, dict[date, float]],
    equity: float,
    cash: float,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[str]]:
    definitions = (
        ("stress-2008", "2008 financial crisis", "2008-09-01", "2008-12-01", "Replay of the September-November 2008 event window."),
        ("stress-2020", "March 2020 selloff", "2020-02-19", "2020-03-23", "Replay of the February-March 2020 market shock."),
    )
    scenarios: list[dict[str, Any]] = []
    waterfalls: list[dict[str, Any]] = []
    issues: list[str] = []
    for scenario_id, title, start_text, end_text, description in definitions:
        start = date.fromisoformat(start_text)
        end = date.fromisoformat(end_text)
        deltas: list[dict[str, Any]] = []
        missing: list[str] = []
        for holding in holdings:
            symbol = holding["symbol"]
            market_value = holding.get("marketValue")
            change = _scenario_return(prices.get(symbol, {}), start, end)
            if market_value is None or change is None:
                missing.append(symbol)
                continue
            deltas.append({"label": symbol, "deltaUsd": round(market_value * change, 2)})
        if missing:
            issues.append(f"{title}: missing event-window history for {', '.join(sorted(set(missing)))}")
        covered_loss = -sum(item["deltaUsd"] for item in deltas)
        loss_percent = covered_loss / equity * 100.0 if equity > 0 else None
        period = f"{start_text} to {end_text}"
        waterfall = _waterfall(title, period, equity, cash, deltas)
        scenarios.append({
            "id": scenario_id,
            "title": title,
            "period": period,
            "description": description,
            "kind": "historical",
            "lossUsd": round(covered_loss, 2),
            "lossPercent": round(loss_percent, 4) if loss_percent is not None else None,
            "waterfall": waterfall,
        })
        waterfalls.append(waterfall)
    return scenarios, waterfalls, issues


def custom_shock(
    holdings: list[dict[str, Any]],
    equity: float,
    cash: float,
    market_shock: float,
    sector_shocks: dict[str, float],
) -> dict[str, Any]:
    deltas = []
    for holding in holdings:
        market_value = holding.get("marketValue")
        if market_value is None:
            continue
        shock = market_shock + sector_shocks.get(holding.get("sector"), 0.0)
        deltas.append({"label": holding["symbol"], "deltaUsd": round(market_value * shock / 100.0, 2)})
    net_delta = sum(item["deltaUsd"] for item in deltas)
    loss = -net_delta
    return {
        "lossUsd": round(loss, 2),
        "impactPercent": round(loss / equity * 100.0, 4) if equity else 0.0,
        "postShockEquityUsd": round(equity + net_delta, 2),
        "waterfall": _waterfall("Custom shock", "Current positions", equity, cash, deltas),
    }
