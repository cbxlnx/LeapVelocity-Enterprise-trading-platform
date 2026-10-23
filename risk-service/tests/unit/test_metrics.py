import numpy as np

from app.services.metrics import calculate_drawdown, calculate_risk_contributions, calculate_var_metrics


def test_var_metrics_produce_all_requested_horizons_and_real_distribution():
    portfolio = np.array([-0.02, 0.01, -0.01, 0.03, -0.04, 0.02, -0.005, 0.01, -0.015, 0.0])
    assets = np.column_stack((portfolio, portfolio * 0.5))
    result = calculate_var_metrics(portfolio, assets, np.array([1.0, 0.0]), 10000, 0.95, (1, 10, 30), 500)

    assert set(result["by_horizon"]) == {"1", "10", "30"}
    assert result["one_day"]["historicalUsd"] is not None
    assert sum(item["frequency"] for item in result["distribution"]) == 500
    assert result["by_horizon"]["30"]["historical"] is None


def test_risk_contributions_are_covariance_based_and_sum_to_total():
    assets = np.array([[0.01, 0.0], [0.0, 0.01], [-0.01, 0.0], [0.0, -0.01]])
    contributions = calculate_risk_contributions(assets, np.array([0.75, 0.25]))

    assert np.isclose(contributions.sum(), 100.0)
    assert not np.isclose(contributions[0], 75.0)


def test_drawdown_returns_dated_series_and_peak_to_trough():
    series, maximum, day = calculate_drawdown(np.array([0.1, -0.2, 0.05]), ["2020-01-01", "2020-01-02", "2020-01-03"])

    assert len(series) == 3
    assert maximum > 10
    assert day == "2020-01-02"
