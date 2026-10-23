from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class SectionStatus(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    status: Literal["READY", "PARTIAL", "EMPTY", "UNAVAILABLE"]
    issues: list[str] = Field(default_factory=list)
    observations: int | None = None
    required_observations: int | None = Field(default=None, alias="requiredObservations")


class Coverage(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    covered_symbols: list[str] = Field(default_factory=list, alias="coveredSymbols")
    missing_symbols: list[str] = Field(default_factory=list, alias="missingSymbols")
    observed_from: str | None = Field(default=None, alias="observedFrom")
    observed_to: str | None = Field(default=None, alias="observedTo")


class HoldingRisk(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    symbol: str
    company: str
    sector: str | None = None
    market_value: float | None = Field(default=None, alias="marketValue")
    allocation_percent: float | None = Field(default=None, alias="allocationPercent")
    beta: float | None = None
    standalone_var_usd: float | None = Field(default=None, alias="standaloneVarUsd")
    annualized_volatility_percent: float | None = Field(default=None, alias="annualizedVolatilityPercent")
    volatility_contribution_percent: float | None = Field(default=None, alias="volatilityContributionPercent")


class VaRModels(BaseModel):
    historical_usd: float | None = Field(default=None, alias="historicalUsd")
    parametric_usd: float | None = Field(default=None, alias="parametricUsd")
    monte_carlo_usd: float | None = Field(default=None, alias="monteCarloUsd")


class DrawdownPoint(BaseModel):
    date: str
    percent: float


class DistributionBin(BaseModel):
    return_usd: float = Field(alias="returnUsd")
    frequency: int


class RiskInsight(BaseModel):
    label: str
    value: str
    detail: str


class SectorExposure(BaseModel):
    sector: str
    market_value: float = Field(alias="marketValue")
    allocation_percent: float = Field(alias="allocationPercent")
    symbols: list[str]


class WaterfallDelta(BaseModel):
    label: str
    delta_usd: float = Field(alias="deltaUsd")


class ScenarioWaterfall(BaseModel):
    title: str
    period: str
    starting_equity_usd: float = Field(alias="startingEquityUsd")
    deltas: list[WaterfallDelta]
    ending_equity_usd: float = Field(alias="endingEquityUsd")
    cash_reserve_usd: float = Field(alias="cashReserveUsd")


class StressScenario(BaseModel):
    id: str
    title: str
    period: str
    description: str
    kind: Literal["historical", "custom"]
    loss_usd: float | None = Field(default=None, alias="lossUsd")
    loss_percent: float | None = Field(default=None, alias="lossPercent")
    waterfall: ScenarioWaterfall | None = None


class RiskPortfolio(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    account_id: str = Field(alias="accountId")
    account_label: str = Field(alias="accountLabel")
    as_of: str = Field(alias="asOf")
    historical_from: str = Field(alias="historicalFrom")
    historical_to: str = Field(alias="historicalTo")
    cash_reserve_usd: float = Field(alias="cashReserveUsd")
    invested_equity_usd: float = Field(alias="investedEquityUsd")
    portfolio_value_usd: float | None = Field(default=None, alias="portfolioValueUsd")
    one_day_var: VaRModels = Field(alias="oneDayVar")
    var_by_horizon: dict[str, VaRModels] = Field(default_factory=dict, alias="varByHorizon")
    expected_shortfall_usd: float | None = Field(default=None, alias="expectedShortfallUsd")
    volatility_percent: float | None = Field(default=None, alias="volatilityPercent")
    beta: float | None = None
    maximum_drawdown_percent: float | None = Field(default=None, alias="maximumDrawdownPercent")
    maximum_drawdown_date: str | None = Field(default=None, alias="maximumDrawdownDate")
    daily_return_count: int = Field(alias="dailyReturnCount")
    monte_carlo_path_count: int = Field(alias="monteCarloPathCount")
    holdings: list[HoldingRisk]
    distribution: list[DistributionBin]
    drawdown_series: list[DrawdownPoint] = Field(alias="drawdownSeries")
    insights: list[RiskInsight]
    sector_exposure: list[SectorExposure] = Field(alias="sectorExposure")
    scenarios: list[StressScenario]
    historical_waterfalls: list[ScenarioWaterfall] = Field(alias="historicalWaterfalls")
    section_status: dict[str, SectionStatus] = Field(default_factory=dict, alias="sectionStatus")
    coverage: Coverage = Field(default_factory=Coverage)
    issues: list[str] = Field(default_factory=list)


class RiskDashboardData(BaseModel):
    portfolios: list[RiskPortfolio]


class CustomShockRequest(BaseModel):
    market: float = Field(ge=-100, le=100)
    technology: float = Field(default=0, ge=-100, le=100, alias="Technology")
    financials: float = Field(default=0, ge=-100, le=100, alias="Financials")
    energy: float = Field(default=0, ge=-100, le=100, alias="Energy")
    healthcare: float = Field(default=0, ge=-100, le=100, alias="Healthcare")

    def sector_shocks(self) -> dict[str, float]:
        return {
            "Technology": self.technology,
            "Financials": self.financials,
            "Energy": self.energy,
            "Healthcare": self.healthcare,
        }


class CustomShockResponse(BaseModel):
    loss_usd: float = Field(alias="lossUsd")
    impact_percent: float = Field(alias="impactPercent")
    post_shock_equity_usd: float = Field(alias="postShockEquityUsd")
    waterfall: ScenarioWaterfall
