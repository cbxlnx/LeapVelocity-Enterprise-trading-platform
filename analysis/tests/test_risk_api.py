import sys
import subprocess

import pytest
from fastapi.testclient import TestClient

from analysis.risk_service.app import app
from analysis.tests.risk_fixture import EXAMPLE, snapshot


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv("RISK_SERVICE_TOKEN", "test-internal-token")
    with TestClient(app) as connection:
        yield connection


def test_health_and_snapshot_only_imports(client):
    assert client.get("/health").json() == {"status": "ok", "schemaVersion": "1.0"}
    subprocess.run([sys.executable, "-c", "import analysis.risk_service.app; import sys; "
                    "assert not {'analysis.scripts.import_data', 'yfinance', 'psycopg'} & sys.modules.keys()"], check=True)


def test_missing_token_fails_startup(monkeypatch):
    monkeypatch.delenv("RISK_SERVICE_TOKEN", raising=False)
    with pytest.raises(RuntimeError, match="RISK_SERVICE_TOKEN"):
        with TestClient(app):
            pass


@pytest.mark.parametrize("token", [None, "wrong"])
def test_authentication(client, token):
    headers = {"X-Risk-Service-Token": token} if token else {}
    assert client.post("/internal/v1/risk/calculate", json=snapshot(EXAMPLE), headers=headers).status_code == 401


def test_successful_versioned_decimal_contract(client):
    response = client.post("/internal/v1/risk/calculate", json=snapshot(EXAMPLE), headers={"X-Risk-Service-Token": "test-internal-token"})
    assert response.status_code == 200
    data = response.json()
    assert data["requestId"] == "synthetic" and data["schemaVersion"] == "1.0"
    assert data["valueAtRisk"][2]["lossAmount"] == "1916.01"
    assert isinstance(data["metrics"]["annualisedVolatility"]["value"], str)


@pytest.mark.parametrize("field,value", [("schemaVersion", "2.0"), ("holdingsValue", "NaN"), ("unknown", True)])
def test_malformed_snapshot_uses_non_echoing_422(client, field, value):
    data = snapshot(EXAMPLE)
    data[field] = value
    response = client.post("/internal/v1/risk/calculate", json=data, headers={"X-Risk-Service-Token": "test-internal-token"})
    assert response.status_code == 422
    assert response.json() == {"detail": "Invalid v1 calculation snapshot"}


def test_missing_prices_are_a_successful_unavailable_assessment(client):
    data = snapshot([])
    for name in ("core", "beta", "historicalVar"):
        data[name].update(status="UNAVAILABLE", reasons=["MISSING_PRICE"])
    response = client.post("/internal/v1/risk/calculate", json=data,
                           headers={"X-Risk-Service-Token": "test-internal-token"})
    assert response.status_code == 200
    for value in response.json()["valueAtRisk"]:
        assert value["status"] == "UNAVAILABLE" and value["lossAmount"] is None
        assert value["reasons"] == ["MISSING_PRICE"]


def test_mismatched_counts_are_a_non_echoing_validation_error(client):
    data = snapshot(EXAMPLE)
    data["beta"]["observations"] = 1
    response = client.post("/internal/v1/risk/calculate", json=data,
                           headers={"X-Risk-Service-Token": "test-internal-token"})
    assert response.status_code == 422
    assert response.json() == {"detail": "Invalid v1 calculation snapshot"}


def test_arithmetic_failure_is_reported_per_metric(client, monkeypatch):
    from decimal import Overflow
    from analysis.risk_service import calculator

    def overflow(_points):
        raise Overflow("synthetic arithmetic failure")

    monkeypatch.setattr(calculator, "daily_volatility", overflow)
    response = client.post("/internal/v1/risk/calculate", json=snapshot(EXAMPLE),
                           headers={"X-Risk-Service-Token": "test-internal-token"})
    assert response.status_code == 200
    assert response.json()["metrics"]["annualisedVolatility"]["reasons"] == ["NUMERICAL_ERROR"]
    assert response.json()["valueAtRisk"][0]["status"] == "READY"
    assert "synthetic arithmetic failure" not in response.text
