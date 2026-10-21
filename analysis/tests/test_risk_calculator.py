"""Independent worked examples ported from the established Java calculation tests."""

import itertools
import json
import math
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal, localcontext
from pathlib import Path

import pytest
import numpy as np
from pydantic import ValidationError

from analysis.risk_service.calculator import calculate, minimum_var, normal_quantile, rank, simulated_loss, windows
from analysis.risk_service.numeric import PRECISION, exact_add
from analysis.risk_service.schemas import CalculationRequest
from analysis.tests.risk_fixture import EXAMPLE, request, snapshot


class FixedRng:
    """Inject independently specified standard-normal draws, never production RNG logic."""
    def __init__(self, gaussian):
        self.gaussian = gaussian

    def normal(self, *, loc, scale, size):
        return loc + scale * np.array([self.gaussian() for _ in range(size)])


def ready(value, percent, amount):
    assert value.status == "READY"
    assert value.lossPercent == Decimal(percent)
    assert value.lossAmount == Decimal(amount)
    assert value.lossPercent.as_tuple().exponent == -8
    assert value.lossAmount.as_tuple().exponent == -2
    assert value.reasons == []


@pytest.mark.parametrize("confidence,percent,amount", [
    ("0.90", "1.48786188", "1487.86"), ("0.95", "1.92382435", "1923.82"),
    ("0.99", "2.74161745", "2741.62"),
])
def test_independent_normal_examples(confidence, percent, amount):
    result = calculate(request(EXAMPLE, confidence=confidence))
    ready(result.valueAtRisk[1], percent, amount)
    assert result.valueAtRisk[1].observations == 501


@pytest.mark.parametrize("confidence,expected", [
    (".90", 1.2815515655446004), (".95", 1.6448536269514722),
    (".975", 1.959963984540054), (".99", 2.3263478740408408),
])
def test_independent_normal_inverse(confidence, expected):
    assert abs(normal_quantile(Decimal(confidence)) - expected) < 3e-14


def test_numpy_pcg64_seeded_monte_carlo_example():
    ready(calculate(request(EXAMPLE)).valueAtRisk[2], "1.91601270", "1916.01")


def test_different_numpy_seeds_change_only_the_sampled_method():
    first = calculate(request(EXAMPLE), rng=np.random.Generator(np.random.PCG64(20261020)))
    second = calculate(request(EXAMPLE), rng=np.random.Generator(np.random.PCG64(20261021)))
    assert first.metrics == second.metrics
    assert first.valueAtRisk[:2] == second.valueAtRisk[:2]
    assert first.valueAtRisk[2].lossPercent != second.valueAtRisk[2].lossPercent


@pytest.mark.parametrize("seed", [1, 2, 3])
def test_simulation_agrees_with_independent_normal_model_sampling_error(seed):
    from statistics import NormalDist
    confidence = .95
    model = NormalDist()
    z = model.inv_cdf(confidence)
    expected = .012 * z - .0005
    standard_error = .012 * math.sqrt(confidence * (1 - confidence) / 100000) / model.pdf(z)
    result = calculate(request(EXAMPLE), rng=np.random.Generator(np.random.PCG64(seed))).valueAtRisk[2]
    assert abs(float(result.lossPercent) / 100 - expected) <= 6 * standard_error


def test_actual_draws_and_nonzero_two_session_fit():
    draws = itertools.cycle([-2, -1, 0, 1, 2])
    result = calculate(request(EXAMPLE), rng=FixedRng(lambda: next(draws)), simulations=1000)
    ready(result.valueAtRisk[2], "2.35", "2350")
    result = calculate(request(EXAMPLE, confidence=".99", horizon=2), rng=FixedRng(lambda: next(draws)), simulations=1000)
    ready(result.valueAtRisk[1], ".03835049", "38.35")
    ready(result.valueAtRisk[2], ".02061790", "20.62")


@pytest.mark.parametrize("confidence,expected", [(".90", 1.80), (".949999999999999", 1.90),
                                                   (".95", 1.90), (".950000000000001", 1.91)])
def test_exact_decimal_nearest_rank(confidence, expected):
    values = [str(Decimal(-i) / 10000) for i in range(1, 201)]
    result = calculate(request(values, confidence=confidence, invested="1000")).valueAtRisk[0]
    assert result.lossPercent == Decimal(str(expected))


def test_minimum_windows_keeps_confidence_epsilon_beyond_decimal128():
    confidence = ".950000000000000000000000000000000000000000001"
    assert minimum_var(Decimal(confidence)) == 101
    assert calculate(request(["0"] * 100, confidence=confidence)).valueAtRisk[0].requiredObservations == 101


@pytest.mark.parametrize("confidence", [".90", ".95", ".99", ".9501"])
def test_monte_carlo_nearest_rank(confidence):
    draws = iter([-i / 1000 for i in range(1, 1001)])
    assert simulated_loss(0, 1, Decimal(confidence), 1000, FixedRng(lambda: next(draws))) == (rank(Decimal(confidence), 1000) + 1) / 1000


def test_compounded_overlapping_windows_and_no_square_root_scaling():
    result = calculate(request(["-.01", ".01"] * 250 + ["-.01"], confidence=".99", horizon=2),
                       rng=FixedRng(lambda: pytest.fail("Degenerate distribution must not draw")))
    for value in result.valueAtRisk:
        ready(value, ".01", "10")
        assert value.observations == 500


def test_vector_compounding_handles_total_loss_and_preserves_small_returns():
    points = request(["-1", ".5", "-.2", "0"]).portfolio.returns
    np.testing.assert_allclose(windows(points, 2), [-1, .2, -.2], atol=1e-15)
    points = request(["1e-20", "1e-20"]).portfolio.returns
    np.testing.assert_allclose(windows(points, 2), [2e-20], rtol=1e-15, atol=0)


@pytest.mark.parametrize("value", ["-0.9999999999999999999999999999999999999999", "1e-1000", "1e1000"])
def test_float64_unrepresentable_returns_are_explicitly_unavailable(value):
    data = snapshot(["0"] * 110)
    data["portfolio"]["returns"][0]["value"] = value
    result = calculate(CalculationRequest.model_validate(data))
    assert all(value.reasons == ["NUMERICAL_ERROR"] for value in result.valueAtRisk)


@pytest.mark.parametrize("horizon,percent,amount", [(1, "1", "1000"), (2, "1.99", "1990"), (10, "9.56179250", "9561.79")])
def test_constant_loss_horizons(horizon, percent, amount):
    for value in calculate(request(["-.01"] * 110, horizon=horizon)).valueAtRisk:
        ready(value, percent, amount)


@pytest.mark.parametrize("mean", ["0", ".001"])
def test_real_zero_is_available(mean):
    result = calculate(request([mean] * 110))
    for value in result.valueAtRisk:
        ready(value, "0", "0")
    assert result.metrics.annualisedVolatility.value == 0


@pytest.mark.parametrize("daily,invested,percent,amount", [
    ("-.0000000000494", "1000000000000", "0", "49.40"),
    ("-.01234567895", "1000000000", "1.23456790", "12345678.95"),
    ("-.000005", "1000", ".0005", ".01"),
])
def test_amount_uses_unrounded_loss_and_half_up(daily, invested, percent, amount):
    for value in calculate(request([daily] * 110, invested=invested)).valueAtRisk:
        ready(value, percent, amount)


def test_core_independent_sixty_observation_example():
    result = calculate(request([".01", "-.02", ".03", "-.04"] * 15,
                               benchmark=[".02", ".01", "-.01", "-.02"] * 15)).metrics
    assert result.annualisedVolatility.value == Decimal("43.10413091")
    assert result.maximumDrawdown.value == Decimal("28.96801781")
    assert result.beta.value == Decimal(".5")


def test_negative_beta_with_nonzero_mean():
    result = calculate(request(["-.013", "-.043"] * 30, benchmark=[".01", ".03"] * 30)).metrics
    assert result.beta.value == Decimal("-1.5")
    assert result.annualisedVolatility.value == Decimal("24.01270850")


def test_variance_lost_in_float64_is_unavailable_instead_of_a_false_zero():
    with localcontext() as ctx:
        ctx.prec = 80
        epsilon = Decimal("1e-40")
        b = [Decimal(".01") + epsilon, Decimal(".01") - epsilon] * 30
        p = [Decimal(".001") - epsilon * Decimal("1.5"), Decimal(".001") + epsilon * Decimal("1.5")] * 30
    result = calculate(request(p, benchmark=b))
    assert result.metrics.beta.value is None
    assert result.metrics.beta.reasons == ["NUMERICAL_ERROR"]
    assert result.metrics.annualisedVolatility.reasons == ["NUMERICAL_ERROR"]


@pytest.mark.parametrize("constant", ["0", ".01", ".01000000000000000000000000000000000000000000001"])
def test_zero_benchmark_variance_only_disables_beta(constant):
    result = calculate(request(["-.01", ".01"] * 60, benchmark=[constant] * 120))
    assert result.metrics.beta.value is None
    assert result.metrics.beta.reasons == ["ZERO_BENCHMARK_VARIANCE"]
    assert result.valueAtRisk[0].status == "READY"
    assert result.metrics.annualisedVolatility.status == "READY"


@pytest.mark.parametrize("values,expected", [
    (["-.01"] * 60, "45.28433576"), ([".25", "-.20", ".50", "-.10"] + ["0"] * 56, "20"),
    (["-.20"] + ["0"] * 59, "20"), (["0"] * 59 + ["-1"], "100"),
])
def test_drawdown_uses_initial_peak_and_retains_worst_loss(values, expected):
    assert calculate(request(values)).metrics.maximumDrawdown.value == Decimal(expected)


@pytest.mark.parametrize("count,confidence,horizon,windows,required", [
    (99, ".95", 1, 99, 100), (499, ".99", 1, 499, 500), (100, ".95", 2, 99, 100),
    (108, ".95", 10, 99, 100), (5, ".95", 10, 0, 100),
])
def test_insufficient_history(count, confidence, horizon, windows, required):
    for value in calculate(request(["0"] * count, confidence=confidence, horizon=horizon)).valueAtRisk:
        assert value.status == "UNAVAILABLE" and value.lossAmount is None
        assert value.reasons == ["INSUFFICIENT_HISTORY"]
        assert (value.observations, value.requiredObservations) == (windows, required)
        assert (value.from_ is None) == (windows == 0)


def test_empty_snapshot_has_null_values_dates_and_empty_status():
    result = calculate(request([], confidence=".99", horizon=10, invested="0"))
    for value in result.valueAtRisk:
        assert value.status == "EMPTY" and value.lossPercent is None
        assert value.from_ is None and value.to is None and value.requiredObservations == 500
    assert result.metrics.beta.status == "EMPTY"


@pytest.mark.parametrize("reason", ["MISSING_PRICE", "INVALID_PRICE", "STALE_PRICE", "INCOMPLETE_HISTORY", "TRADING_CALENDAR_UNAVAILABLE"])
def test_preparation_reasons_preserved_without_calculation(reason):
    data = snapshot(EXAMPLE)
    for name in ["core", "beta", "historicalVar"]:
        data[name].update(status="UNAVAILABLE", reasons=[reason])
    result = calculate(CalculationRequest.model_validate(data), rng=FixedRng(lambda: pytest.fail("Blocked")))
    assert all(value.reasons == [reason] for value in result.valueAtRisk)
    assert result.metrics.annualisedVolatility.reasons == [reason]


def test_missing_benchmark_preserves_portfolio_results():
    data = snapshot(EXAMPLE, benchmark=[])
    data["beta"].update(status="UNAVAILABLE", reasons=["MISSING_BENCHMARK"])
    result = calculate(CalculationRequest.model_validate(data))
    assert result.metrics.beta.reasons == ["MISSING_BENCHMARK"]
    assert all(value.status == "READY" for value in result.valueAtRisk)


@pytest.mark.parametrize("value", [None, "-1.01"])
def test_invalid_returns_are_unavailable(value):
    data = snapshot(["0"] * 110)
    data["portfolio"]["returns"][10]["value"] = value
    result = calculate(CalculationRequest.model_validate(data))
    assert all(v.reasons == ["INVALID_POSITION"] for v in result.valueAtRisk)


def test_intervals_cannot_bridge_gaps():
    data = snapshot(["0"] * 110)
    data["portfolio"]["returns"][10]["from"] = "2024-01-01"
    result = calculate(CalculationRequest.model_validate(data))
    assert all(v.reasons == ["INCOMPLETE_HISTORY"] for v in result.valueAtRisk)


@pytest.mark.parametrize("index,field,value", [(10, "from", "2024-01-01"), (0, "to", "2024-01-02")])
def test_invalid_intervals_also_block_core_metrics(index, field, value):
    data = snapshot(["0"] * 110)
    data["portfolio"]["returns"][index][field] = value
    result = calculate(CalculationRequest.model_validate(data))
    for metric in (result.metrics.annualisedVolatility, result.metrics.maximumDrawdown, result.metrics.beta):
        assert metric.status == "UNAVAILABLE"
        assert metric.reasons == ["INCOMPLETE_HISTORY"]


@pytest.mark.parametrize("field,value,reason", [
    ("from", "2024-01-01", "INCOMPLETE_HISTORY"),
    ("to", "2024-01-05", "INCOMPLETE_HISTORY"),
    ("portfolio", ".25", "INVALID_POSITION"),
    ("benchmark", None, "INVALID_POSITION"),
    ("benchmark", "-1.1", "INVALID_POSITION"),
])
def test_beta_rejects_unaligned_or_invalid_pairs_without_blocking_portfolio(field, value, reason):
    data = snapshot(EXAMPLE)
    data["pairedReturns"][0][field] = value
    result = calculate(CalculationRequest.model_validate(data))
    assert result.metrics.beta.reasons == [reason]
    assert result.metrics.annualisedVolatility.status == "READY"
    assert all(value.status == "READY" for value in result.valueAtRisk)


@pytest.mark.parametrize("field,value,reason", [
    ("date", "2024-01-01", "INCOMPLETE_HISTORY"),
    ("value", ".5", "INVALID_POSITION"),
])
def test_drawdown_rejects_wealth_that_does_not_match_returns(field, value, reason):
    data = snapshot(["0"] * 110)
    data["portfolio"]["wealth"][10][field] = value
    result = calculate(CalculationRequest.model_validate(data))
    assert result.metrics.maximumDrawdown.reasons == [reason]
    assert result.metrics.annualisedVolatility.value == 0


def test_volatility_and_beta_against_independent_standard_library_statistics():
    from statistics import covariance, stdev, variance
    portfolio = [.01, -.02, .03, -.04] * 15
    benchmark = [.02, .01, -.01, -.02] * 15
    metrics = calculate(request(portfolio, benchmark=benchmark)).metrics
    assert float(metrics.annualisedVolatility.value) == pytest.approx(stdev(portfolio) * math.sqrt(252) * 100, abs=5e-9)
    assert float(metrics.beta.value) == pytest.approx(covariance(portfolio, benchmark) / variance(benchmark), abs=5e-9)


def test_normal_horizon_fit_against_independent_standard_library_statistics():
    from statistics import NormalDist, mean, stdev
    values = [-.02, .01, -.01, .03, .005] * 30
    horizon = 3
    horizons = [math.prod(1 + value for value in values[index:index + horizon]) - 1
                for index in range(len(values) - horizon + 1)]
    expected = max(0, stdev(horizons) * NormalDist().inv_cdf(.95) - mean(horizons))
    result = calculate(request(values, horizon=horizon)).valueAtRisk[1]
    assert result.observations == 148
    assert float(result.lossPercent) == pytest.approx(expected * 100, abs=5e-9)
    assert float(result.lossAmount) == pytest.approx(expected * 100000, abs=.005)


@pytest.mark.parametrize("value", ["NaN", "Infinity", "-Infinity", 0.01, "1e10001", "1_000", " 1 "])
def test_decimal_transport_rejects_nonfinite_and_binary_floats(value):
    data = snapshot(["0"] * 110)
    data["portfolio"]["returns"][0]["value"] = value
    with pytest.raises(ValidationError):
        CalculationRequest.model_validate(data)


@pytest.mark.parametrize("confidence,horizon", [(".8999", 1), (".9901", 1), (".95", 0), (".95", 11), (".95", True)])
def test_query_boundaries(confidence, horizon):
    data = snapshot(["0"] * 110)
    data["parameters"].update(confidenceLevel=confidence, holdingPeriodDays=horizon)
    with pytest.raises(ValidationError):
        CalculationRequest.model_validate(data)


@pytest.mark.parametrize("bad", [math.nan, math.inf, -math.inf])
def test_invalid_draw_disables_only_monte_carlo(bad):
    result = calculate(request(EXAMPLE), rng=FixedRng(lambda: bad))
    assert result.valueAtRisk[1].status == "READY"
    assert result.valueAtRisk[2].reasons == ["NUMERICAL_ERROR"]
    assert "NaN" not in result.model_dump_json()


def test_wrong_simulation_batch_shape_disables_only_monte_carlo():
    class WrongShape:
        def normal(self, *, loc, scale, size):
            return np.zeros((size, 1))

    result = calculate(request(EXAMPLE), rng=WrongShape())
    assert result.valueAtRisk[1].status == "READY"
    assert result.valueAtRisk[2].reasons == ["NUMERICAL_ERROR"]


@pytest.mark.parametrize("invested", ["1e1000", "1e-1000"])
def test_normal_float_overflow_or_underflow_is_explicit(invested):
    result = calculate(request(EXAMPLE, invested=invested))
    assert all(v.reasons == ["NUMERICAL_ERROR"] for v in result.valueAtRisk[1:])


@pytest.mark.parametrize("index,value", [(0, "0"), (10, "-.1"), (10, None)])
def test_invalid_wealth_isolated_from_other_metrics(index, value):
    data = snapshot(["0"] * 110)
    data["portfolio"]["wealth"][index]["value"] = value
    result = calculate(CalculationRequest.model_validate(data))
    assert result.metrics.maximumDrawdown.reasons == ["INVALID_POSITION"]
    assert result.metrics.annualisedVolatility.status == "READY"
    assert all(v.status == "READY" for v in result.valueAtRisk)


def test_normal_outcome_and_usd_overflow_are_unavailable():
    from analysis.risk_service.calculator import InvalidInput
    with pytest.raises(InvalidInput) as error:
        simulated_loss(1e308, 1e308, Decimal(".95"), 1000, FixedRng(lambda: 2))
    assert error.value.code == "NUMERICAL_ERROR"
    result = calculate(request(["-.99", "2"] * 55, invested="1e308"))
    assert all(value.reasons == ["NUMERICAL_ERROR"] for value in result.valueAtRisk[1:])
    assert result.valueAtRisk[0].status == "READY"


def test_zero_investment_cannot_become_zero_var():
    assert all(value.reasons == ["INVALID_POSITION"] for value in calculate(request(["0"] * 110, invested="0")).valueAtRisk)


def test_concurrent_request_rng_and_decimal_context_isolation():
    first, second = request(EXAMPLE), request(EXAMPLE, confidence=".99", horizon=2)
    expected = [calculate(first), calculate(second)]
    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(calculate, [first, second] * 4))
    assert results == expected * 4


def test_shared_mvp_history_matches_existing_golden():
    root = Path(__file__).resolve().parents[2] / "backend/src/test/resources/risk"
    import csv
    rows = list(csv.DictReader((root / "mvp-history.csv").open()))
    expected = json.loads((root / "mvp-expected.json").read_text())
    # Independent fixture reconstruction of today's 60%/40% allocation, using
    # the established DECIMAL128 operation boundaries of Java preparation.
    with localcontext(PRECISION):
        levels, benchmark = [], []
        columns = list(rows[0])
        for row in rows:
            grown = exact_add(Decimal(600) * (Decimal(row[columns[1]]) / Decimal(rows[0][columns[1]])),
                              Decimal(400) * (Decimal(row[columns[2]]) / Decimal(rows[0][columns[2]])))
            levels.append(grown / Decimal(1000))
            benchmark.append(Decimal(row[columns[3]]) / Decimal(rows[0][columns[3]]))
        values = [exact_add(levels[i] / levels[i-1], Decimal(-1)) for i in range(1, len(levels))]
        b_returns = [exact_add(benchmark[i] / benchmark[i-1], Decimal(-1)) for i in range(1, len(benchmark))]
    data = snapshot(values, benchmark=b_returns, invested="1000")
    data["portfolio"]["wealth"] = [dict(date=str(p["date"]), value=str(v)) for p, v in zip(data["portfolio"]["wealth"], levels)]
    result = calculate(CalculationRequest.model_validate(data))
    assert result.metrics.annualisedVolatility.value == Decimal(expected["volatility"])
    assert result.metrics.maximumDrawdown.value == Decimal(expected["drawdown"])
    assert result.metrics.beta.value == Decimal(expected["beta"])
    ready(result.valueAtRisk[0], expected["varPercent"], expected["varAmount"])
    ready(result.valueAtRisk[1], "9.41585204", "94.16")
