"""Distribution, ES and wire consistency checks using controlled losses."""

import json
from decimal import Decimal
import numpy as np
import pytest
from pydantic import ValidationError

from analysis.risk_service.calculator import SEED, SIMULATIONS, calculate, expected_shortfall, summarize_simulation
from analysis.risk_service.schemas import CalculationResponse
from analysis.tests.risk_fixture import EXAMPLE, request


@pytest.mark.parametrize("losses,confidence,expected", [
    ([0, 1, 2, 3, 4, 5, 6, 7, 8, 9], ".90", 9),
    ([0, 0, 0, 0, 0, 0, 0, 0, 10, 20], ".90", 20),
    ([0] * 95 + [10] * 4 + [20], ".95", 12),
    ([0] * 95 + [10] * 4 + [20], ".952", (3.8 * 10 + 20) / 4.8),
    ([3] * 10, ".95", 3), ([-3] * 10, ".95", 0),
    ([2], ".99", 2), ([0] * 99 + [100], ".99", 100),
])
def test_empirical_tail_integral_handles_ties_fractional_mass_and_small_samples(losses, confidence, expected):
    assert expected_shortfall(losses, Decimal(confidence)) == pytest.approx(expected)


@pytest.mark.parametrize("losses", [[], [1, np.nan], [np.inf], [-np.inf]])
def test_invalid_loss_distributions_are_rejected(losses):
    from analysis.risk_service.calculator import InvalidInput
    with pytest.raises(InvalidInput):
        expected_shortfall(losses, Decimal(".95"))


def test_histogram_es_and_var_share_the_exact_seeded_draws():
    # The EXAMPLE fixture has mean .0005 and sample standard deviation .012.
    losses = -np.random.Generator(np.random.PCG64(SEED)).normal(.0005, .012, SIMULATIONS)
    expected_counts, expected_edges = np.histogram(losses * 100000, bins=40)
    actual = calculate(request(EXAMPLE)).monteCarlo
    assert actual.status == "READY" and actual.simulations == SIMULATIONS
    assert actual.varAmount == Decimal("1916.01")  # Existing seeded VaR is unchanged.
    assert [item.count for item in actual.bins] == expected_counts.tolist()
    np.testing.assert_allclose([float(item.lowerBound) for item in actual.bins], expected_edges[:-1], atol=1e-10)
    tail = np.sort(losses)[95000:].mean()
    assert float(actual.expectedShortfallAmount) == pytest.approx(tail * 100000, abs=.005)
    assert float(actual.expectedShortfallPercent) == pytest.approx(tail * 100, abs=5e-9)
    assert actual.expectedShortfallAmount >= actual.varAmount
    assert sum(item.count for item in actual.bins) == SIMULATIONS
    assert len(calculate(request(EXAMPLE)).model_dump_json()) < 20000


@pytest.mark.parametrize("daily", ["-.01234567895", "0", ".01"])
def test_constant_distributions_keep_one_bin_and_exact_var_es_rounding(daily):
    result = calculate(request([daily] * 110, invested="1000000000"))
    value = result.monteCarlo
    assert value.expectedShortfallAmount == value.varAmount == result.valueAtRisk[2].lossAmount
    assert value.expectedShortfallPercent == result.valueAtRisk[2].lossPercent
    assert len(value.bins) == 1 and value.bins[0].count == SIMULATIONS


def test_tied_tail_retains_half_cent_rounding_without_averaging_drift():
    losses = np.array([0] * 95000 + [.01234567895] * 5000)
    result = summarize_simulation(losses, Decimal(".95"), Decimal("1000000000"), 1, Decimal("0.00"))
    assert result["expectedShortfallAmount"] == Decimal("12345678.95")
    assert result["expectedShortfallPercent"] == Decimal("1.23456790")


def test_failed_simulation_returns_no_histogram_or_tail_estimate():
    class InvalidRng:
        def normal(self, *, loc, scale, size):
            return np.full(size, np.inf)

    response = calculate(request(EXAMPLE), rng=InvalidRng())
    assert response.valueAtRisk[1].status == "READY"
    assert response.monteCarlo.status == "UNAVAILABLE"
    assert response.monteCarlo.reasons == ["NUMERICAL_ERROR"]
    assert response.monteCarlo.bins == [] and response.monteCarlo.expectedShortfallAmount is None


@pytest.mark.parametrize("values,status", [([], "EMPTY"), (["0"] * 99, "UNAVAILABLE")])
def test_blocked_assessments_have_no_distribution_or_es(values, status):
    result = calculate(request(values))
    assert result.monteCarlo.status == status
    assert result.monteCarlo.bins == [] and result.monteCarlo.expectedShortfallAmount is None
    assert result.monteCarlo.reasons == result.valueAtRisk[2].reasons


@pytest.mark.parametrize("path,value", [
    (("bins", 0, "count"), -1), (("bins", 0, "count"), 0),
    (("bins", 0, "upperBound"), "-1e30"), (("expectedShortfallAmount",), "0"),
    (("expectedShortfallPercent",), "0"), (("varAmount",), "1"), (("unit",), "PERCENT"),
])
def test_distribution_response_rejects_corrupt_bins_and_inconsistent_tail_results(path, value):
    result = json.loads(calculate(request(EXAMPLE)).model_dump_json(by_alias=True))
    target = result["monteCarlo"]
    for part in path[:-1]:
        target = target[part]
    target[path[-1]] = value
    with pytest.raises(ValidationError):
        CalculationResponse.model_validate(result)
