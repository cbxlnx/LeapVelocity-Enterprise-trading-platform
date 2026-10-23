from datetime import date

from app.services.stress import custom_shock, historical_scenarios


def test_custom_shock_reconciles_signed_deltas_and_leaves_cash_unchanged():
    result = custom_shock(
        [{"symbol": "AAPL", "sector": "Technology", "marketValue": 1000.0}],
        1500.0,
        500.0,
        -10.0,
        {"Technology": -5.0},
    )

    assert result["lossUsd"] == 150.0
    assert result["postShockEquityUsd"] == 1350.0
    assert result["waterfall"]["endingEquityUsd"] == 1350.0
    assert result["waterfall"]["cashReserveUsd"] == 500.0


def test_historical_stress_reports_missing_symbol_history():
    scenarios, waterfalls, issues = historical_scenarios(
        [{"symbol": "AAPL", "marketValue": 1000.0}],
        {"AAPL": {date(2008, 9, 2): 100.0, date(2008, 11, 30): 80.0}},
        1500.0,
        500.0,
    )

    assert len(scenarios) == 2
    assert waterfalls[0]["endingEquityUsd"] == 1300.0
    assert any("2020" in issue for issue in issues)
