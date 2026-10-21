"""Reject inconsistent transport data before it can look like a valid assessment."""

import json

import pytest
from pydantic import ValidationError

from analysis.risk_service.calculator import calculate
from analysis.risk_service.schemas import CalculationRequest, CalculationResponse
from analysis.tests.risk_fixture import EXAMPLE, request, snapshot


@pytest.mark.parametrize("name", ["core", "beta", "historicalVar"])
def test_availability_counts_must_match_actual_inputs(name):
    data = snapshot(EXAMPLE)
    data[name]["observations"] -= 1
    with pytest.raises(ValidationError, match="Availability counts"):
        CalculationRequest.model_validate(data)


@pytest.mark.parametrize("path,value", [
    (("metrics", "beta", "value"), None),
    (("metrics", "beta", "value"), "NaN"),
    (("metrics", "beta", "reasons"), ["MISSING_BENCHMARK"]),
    (("metrics", "annualisedVolatility", "value"), "-1"),
    (("metrics", "maximumDrawdown", "value"), "101"),
    (("metrics", "beta", "unit"), "PERCENT"),
    (("valueAtRisk", 0, "lossAmount"), None),
    (("valueAtRisk", 0, "lossAmount"), "-1"),
    (("valueAtRisk", 0, "status"), "UNAVAILABLE"),
    (("valueAtRisk", 0, "method"), "MONTE_CARLO"),
    (("valueAtRisk", 0, "observations"), 1),
])
def test_response_rejects_inconsistent_values_statuses_and_units(path, value):
    data = json.loads(calculate(request(EXAMPLE)).model_dump_json(by_alias=True))
    target = data
    for part in path[:-1]:
        target = target[part]
    target[path[-1]] = value
    with pytest.raises(ValidationError):
        CalculationResponse.model_validate(data)


def test_empty_and_unavailable_response_round_trip():
    for values in ([], ["0"] * 59):
        response = calculate(request(values))
        assert CalculationResponse.model_validate_json(response.model_dump_json(by_alias=True)) == response
