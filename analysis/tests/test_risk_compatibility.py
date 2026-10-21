"""Wire compatibility and library RNG reproducibility; no Java random-stream dependency."""

import json
from pathlib import Path

import numpy as np

from analysis.risk_service.calculator import SEED, calculate
from analysis.tests.risk_fixture import EXAMPLE, request
from analysis.risk_service.schemas import CalculationRequest, CalculationResponse

ROOT = Path(__file__).resolve().parents[2]


def test_published_versioned_schemas_match_wire_models():
    for model, name, mode in [(CalculationRequest, "request", "validation"), (CalculationResponse, "response", "serialization")]:
        schema = json.loads((ROOT / f"contracts/risk-calculation-v1-{name}.schema.json").read_text())
        assert schema == model.model_json_schema(by_alias=True, mode=mode)



def test_numpy_pcg64_request_rng_matches_a_direct_library_reference():
    # Direct library reference checks the distribution and seed, independently of service helpers.
    rng = np.random.Generator(np.random.PCG64(SEED))
    outcomes = rng.normal(loc=.0005, scale=.012, size=100000)
    loss = sorted(-outcomes)[94999]
    assert abs(loss * 100 - 1.91601270) < 5e-9
    first = calculate(request(EXAMPLE))
    assert abs(float(first.valueAtRisk[2].lossPercent) - loss * 100) < 5e-9
    assert calculate(request(EXAMPLE)) == first
