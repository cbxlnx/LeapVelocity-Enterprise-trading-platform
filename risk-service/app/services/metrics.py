from __future__ import annotations

from math import sqrt
from typing import Any

import numpy as np
from scipy.stats import norm


def horizon_returns(daily_returns: np.ndarray, horizon: int) -> np.ndarray:
    values = np.asarray(daily_returns, dtype=float)
    if horizon < 1 or len(values) < horizon:
        return np.array([], dtype=float)
    if horizon == 1:
        return values.copy()
    return np.array([np.prod(1.0 + values[index:index + horizon]) - 1.0 for index in range(len(values) - horizon + 1)])


def _tail_mean(losses: np.ndarray, threshold: float) -> float | None:
    tail = losses[losses >= threshold]
    return float(np.mean(tail)) if len(tail) else None


def _loss_metrics(returns: np.ndarray, equity: float, confidence: float) -> tuple[float | None, float | None]:
    if len(returns) == 0:
        return None, None
    losses = -returns * equity
    var = float(np.quantile(losses, confidence))
    return var, _tail_mean(losses, var)


def _simulation_outcomes(
    asset_returns: np.ndarray,
    weights: np.ndarray,
    horizon: int,
    paths: int,
    seed: int,
) -> np.ndarray:
    if len(asset_returns) < 2 or len(weights) == 0 or paths < 1:
        return np.array([], dtype=float)
    mean = np.mean(asset_returns, axis=0)
    covariance = np.atleast_2d(np.cov(asset_returns, rowvar=False, ddof=1))
    covariance = (covariance + covariance.T) / 2.0
    covariance += np.eye(covariance.shape[0]) * 1e-12
    rng = np.random.default_rng(seed)
    simulated = rng.multivariate_normal(mean, covariance, size=(paths, horizon), check_valid="ignore")
    portfolio_returns = np.sum(simulated * weights, axis=2)
    return np.prod(1.0 + portfolio_returns, axis=1) - 1.0


def distribution_bins(outcomes: np.ndarray, equity: float, bin_count: int | None = None) -> list[dict[str, Any]]:
    if len(outcomes) == 0:
        return []
    values = outcomes * equity
    count = bin_count or max(10, min(40, int(sqrt(len(values)))))
    frequencies, edges = np.histogram(values, bins=count)
    return [
        {"returnUsd": round(float((edges[index] + edges[index + 1]) / 2.0), 2), "frequency": int(frequencies[index])}
        for index in range(len(frequencies))
    ]


def calculate_var_metrics(
    portfolio_returns: np.ndarray,
    asset_returns: np.ndarray,
    weights: np.ndarray,
    equity: float,
    confidence: float,
    horizons: tuple[int, ...],
    mc_paths: int,
) -> dict[str, Any]:
    if len(portfolio_returns) == 0 or equity <= 0:
        return {"by_horizon": {}, "one_day": {"historicalUsd": None, "parametricUsd": None, "monteCarloUsd": None}, "expectedShortfallUsd": None, "distribution": []}

    daily_std = float(np.std(portfolio_returns, ddof=1)) if len(portfolio_returns) > 1 else 0.0
    results: dict[str, dict[str, float | None]] = {}
    one_day_outcomes = np.array([], dtype=float)
    for horizon in horizons:
        historical, historical_es = _loss_metrics(horizon_returns(portfolio_returns, horizon), equity, confidence)
        parametric = float(norm.ppf(confidence) * daily_std * sqrt(horizon) * equity)
        outcomes = _simulation_outcomes(asset_returns, weights, horizon, mc_paths, seed=1000 + horizon)
        monte_carlo, monte_carlo_es = _loss_metrics(outcomes, equity, confidence)
        results[str(horizon)] = {
            "historical": historical,
            "parametric": parametric,
            "monteCarlo": monte_carlo,
            "historicalExpectedShortfall": historical_es,
            "parametricExpectedShortfall": float(parametric),
            "monteCarloExpectedShortfall": monte_carlo_es,
        }
        if horizon == 1:
            one_day_outcomes = outcomes

    one_day = results.get("1", {})
    return {
        "by_horizon": results,
        "one_day": {
            "historicalUsd": one_day.get("historical"),
            "parametricUsd": one_day.get("parametric"),
            "monteCarloUsd": one_day.get("monteCarlo"),
        },
        "expectedShortfallUsd": one_day.get("monteCarloExpectedShortfall"),
        "distribution": distribution_bins(one_day_outcomes, equity),
    }


def calculate_risk_contributions(asset_returns: np.ndarray, weights: np.ndarray) -> np.ndarray:
    if len(asset_returns) < 2 or len(weights) == 0:
        return np.zeros(len(weights), dtype=float)
    covariance = np.atleast_2d(np.cov(asset_returns, rowvar=False, ddof=1))
    portfolio_variance = float(weights @ covariance @ weights)
    if portfolio_variance <= 0:
        return np.zeros(len(weights), dtype=float)
    portfolio_volatility = sqrt(portfolio_variance)
    marginal = covariance @ weights / portfolio_volatility
    return (weights * marginal / portfolio_volatility) * 100.0


def calculate_instrument_beta(asset_returns: np.ndarray, benchmark_returns: np.ndarray) -> np.ndarray:
    if len(benchmark_returns) < 2 or asset_returns.size == 0:
        return np.full(asset_returns.shape[1] if asset_returns.ndim == 2 else 0, np.nan)
    benchmark_variance = float(np.var(benchmark_returns, ddof=1))
    if benchmark_variance <= 0:
        return np.full(asset_returns.shape[1], np.nan)
    centered_benchmark = benchmark_returns - np.mean(benchmark_returns)
    centered_assets = asset_returns - np.mean(asset_returns, axis=0)
    return np.mean(centered_assets * centered_benchmark[:, None], axis=0) / benchmark_variance


def calculate_drawdown(portfolio_returns: np.ndarray, dates: list[str]) -> tuple[list[dict[str, Any]], float | None, str | None]:
    if len(portfolio_returns) == 0:
        return [], None, None
    index = np.cumprod(1.0 + portfolio_returns)
    drawdown = (index / np.maximum.accumulate(index) - 1.0) * 100.0
    series = [{"date": dates[position], "percent": round(float(value), 4)} for position, value in enumerate(drawdown)]
    trough = int(np.argmin(drawdown))
    return series, round(float(abs(drawdown[trough])), 4), dates[trough]
