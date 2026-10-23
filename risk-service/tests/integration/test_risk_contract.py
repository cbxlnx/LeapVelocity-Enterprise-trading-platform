import asyncio
from datetime import date, timedelta

from app.core.config import Settings
from app.schemas.risk_models import RiskDashboardData
from app.services.risk_engine import RiskEngine


class FakeTradeClient:
    def __init__(self, settings: Settings, token: str):
        self.token = token

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return None

    async def get_account(self, account_id: int):
        return {"id": account_id, "accountId": "ACC-007", "cashBalance": 1000}

    async def get_positions(self, account_id: int):
        return [{"symbol": "AAPL", "quantity": 10}]

    async def get_instruments(self):
        return [{"symbol": "AAPL", "name": "Apple Inc.", "sector": "Technology"}]

    async def get_quotes(self, symbols: list[str]):
        return {"quotes": [{"symbol": "AAPL", "lastPrice": 100}], "missingSymbols": []}


class FakeResult:
    def __init__(self, rows):
        self.rows = rows

    def fetchall(self):
        return self.rows


class FakeConnection:
    def __init__(self, rows):
        self.rows = rows

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return None

    def execute(self, query, params):
        return FakeResult(self.rows)


def make_price_rows():
    rows = []
    start = date(2025, 1, 1)
    for index in range(45):
        price_date = start + timedelta(days=index)
        rows.extend([
            {"symbol": "AAPL", "price_date": price_date, "adjusted_close": 100 + index * 0.2},
            {"symbol": "SPY", "price_date": price_date, "adjusted_close": 400 + index * 0.1},
        ])
    return rows


def test_engine_result_matches_dashboard_contract_with_mocked_sources():
    settings = Settings(RISK_MINIMUM_OBSERVATIONS=30, RISK_DEFAULT_MC_PATHS=100)
    rows = make_price_rows()
    engine = RiskEngine(
        settings,
        trade_client_factory=FakeTradeClient,
        connection_factory=lambda _: FakeConnection(rows),
    )

    payload = asyncio.run(engine.build_dashboard(7, "token", 252, 0.95, "SPY", 100))
    dashboard = RiskDashboardData.model_validate(payload)
    portfolio = dashboard.portfolios[0]

    assert portfolio.account_id == "7"
    assert portfolio.coverage.covered_symbols == ["AAPL"]
    assert portfolio.coverage.missing_symbols == []
    assert portfolio.holdings[0].allocation_percent is not None
    assert portfolio.one_day_var.monte_carlo_usd is not None
    assert portfolio.section_status["historicalRisk"].status == "READY"
