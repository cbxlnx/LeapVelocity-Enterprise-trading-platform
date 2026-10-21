"""Independent financial examples for the library adapters and all API horizons."""

import math
from decimal import Decimal
from fractions import Fraction
from statistics import NormalDist, mean, stdev

import numpy as np
import pytest

from analysis.risk_service.calculator import calculate, nearest_loss
from analysis.tests.risk_fixture import request, snapshot
from analysis.risk_service.schemas import CalculationRequest


@pytest.mark.parametrize("count", [100, 101, 200, 999, 1000, 2000, 100000])
@pytest.mark.parametrize("confidence", [
    ".90", ".95", ".99",
    ".949999999999999999999999999999999999999999999",
    ".950000000000000000000000000000000000000000001",
])
def test_quantile_keeps_exact_nearest_rank_at_decimal_boundaries(count, confidence):
    # Distinct, unsorted losses make an off-by-one selection observable.
    losses = np.arange(count, dtype=np.float64)[::-1] / count
    expected_index = math.ceil(Fraction(confidence) * count) - 1
    assert nearest_loss(losses, Decimal(confidence)) == sorted(losses)[expected_index]


@pytest.mark.parametrize("horizon", range(1, 11))
@pytest.mark.parametrize("confidence", [".90", ".95", ".99"])
def test_historical_and_normal_models_for_every_supported_horizon(horizon, confidence):
    daily = [-.02, .01, -.01, .03, .005] * 102
    # Separate stdlib implementation is the financial reference, not service helpers.
    compounded = [math.prod(1 + value for value in daily[index:index + horizon]) - 1
                  for index in range(len(daily) - horizon + 1)]
    losses = sorted(-value for value in compounded)
    historical = max(0, losses[math.ceil(Fraction(confidence) * len(losses)) - 1])
    normal = max(0, stdev(compounded) * NormalDist().inv_cdf(float(confidence)) - mean(compounded))
    actual = calculate(request(daily, horizon=horizon, confidence=confidence))
    for result, expected in zip(actual.valueAtRisk[:2], [historical, normal]):
        assert result.status == "READY"
        assert result.observations == len(compounded)
        assert float(result.lossPercent) == pytest.approx(expected * 100, abs=5e-9)
        assert float(result.lossAmount) == pytest.approx(expected * 100000, abs=.005)


def test_dated_drawdown_keeps_session_gaps_without_filling_prices():
    from datetime import date, timedelta

    data = snapshot([".25", "-.2", ".5", "-.1"] + ["0"] * 56)
    # Friday to Monday is one validated return interval, not three daily fills.
    dates, day = [], date(2024, 1, 5)
    while len(dates) < 61:
        if day.weekday() < 5:
            dates.append(day.isoformat())
        day += timedelta(days=1)
    for index, point in enumerate(data["portfolio"]["wealth"]):
        point["date"] = dates[index]
    for index, point in enumerate(data["pairedReturns"]):
        point.update({"from": dates[index], "to": dates[index + 1]})
    for index, point in enumerate(data["portfolio"]["returns"]):
        point.update({"from": dates[index], "to": dates[index + 1]})
    metric = calculate(CalculationRequest.model_validate(data)).metrics.maximumDrawdown
    assert metric.status == "READY"
    assert metric.value == Decimal("20.00000000")
    assert str(metric.from_) == dates[0] and str(metric.to) == dates[-1]
