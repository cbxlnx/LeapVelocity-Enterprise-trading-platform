"""Internal v1 contract. Every decimal crosses JSON as a string, never a float."""

from datetime import date
from decimal import Decimal, InvalidOperation
from typing import Annotated, Literal
import re

from pydantic import BaseModel, BeforeValidator, ConfigDict, Field, WithJsonSchema, model_validator

DECIMAL_PATTERN = r"^[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[Ee][+-]?[0-9]+)?$"


def decimal_string(value):
    if not isinstance(value, str) or len(value) > 4096 or not re.fullmatch(DECIMAL_PATTERN, value):
        raise ValueError("Decimals must be strings of at most 4096 characters")
    try:
        number = Decimal(value)
    except InvalidOperation as exc:
        raise ValueError("Invalid decimal") from exc
    if not number.is_finite() or abs(number.adjusted()) > 10000:
        raise ValueError("Decimal must be finite with exponent within +/-10000")
    return number


DecimalString = Annotated[Decimal, BeforeValidator(decimal_string, json_schema_input_type=str),
                          WithJsonSchema({"type": "string", "maxLength": 4096, "pattern": DECIMAL_PATTERN}, mode="validation")]
Status = Literal["READY", "EMPTY", "UNAVAILABLE"]
IssueCode = Literal[
    "EMPTY_PORTFOLIO", "UNSUPPORTED_POSITION", "UNSUPPORTED_CURRENCY", "INVALID_POSITION",
    "MISSING_INSTRUMENT_METADATA", "MISSING_PRICE", "INVALID_PRICE", "NO_COMMON_DATES",
    "STALE_PRICE", "INCOMPLETE_HISTORY", "INSUFFICIENT_HISTORY", "MISSING_BENCHMARK",
    "ZERO_BENCHMARK_VARIANCE", "TRADING_CALENDAR_UNAVAILABLE", "METHOD_NOT_IMPLEMENTED", "NUMERICAL_ERROR",
]


class Model(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)


class Parameters(Model):
    confidenceLevel: Annotated[DecimalString, Field(ge=Decimal("0.90"), le=Decimal("0.99"))]
    holdingPeriodDays: Annotated[int, Field(strict=True, ge=1, le=10)]


class ReturnPoint(Model):
    from_: date = Field(alias="from")
    to: date
    value: DecimalString | None


class WealthPoint(Model):
    date: date
    value: DecimalString | None


class PairedReturn(Model):
    from_: date = Field(alias="from")
    to: date
    portfolio: DecimalString | None
    benchmark: DecimalString | None


class Availability(Model):
    status: Status
    observations: Annotated[int, Field(strict=True, ge=0, le=2000)]
    requiredObservations: Annotated[int, Field(strict=True, ge=1, le=2000)]
    reasons: list[IssueCode]

    @model_validator(mode="after")
    def coherent(self):
        if (self.status == "READY") != (not self.reasons):
            raise ValueError("READY requires no reasons; blocked results require reasons")
        return self


class Series(Model):
    returns: Annotated[list[ReturnPoint], Field(max_length=2000)]
    wealth: Annotated[list[WealthPoint], Field(max_length=2001)]


class CalculationRequest(Model):
    schemaVersion: Literal["1.0"]
    requestId: Annotated[str, Field(min_length=1, max_length=64)]
    holdingsValue: DecimalString | None
    parameters: Parameters
    portfolio: Series
    pairedReturns: Annotated[list[PairedReturn], Field(max_length=2000)]
    core: Availability
    beta: Availability
    historicalVar: Availability

    @model_validator(mode="after")
    def observation_counts(self):
        counts = (len(self.portfolio.returns), len(self.pairedReturns),
                  max(0, len(self.portfolio.returns) - self.parameters.holdingPeriodDays + 1))
        for availability, count in zip((self.core, self.beta, self.historicalVar), counts):
            if availability.observations != count:
                raise ValueError("Availability counts must match the supplied series")
        return self


class ScalarMetric(Model):
    status: Status
    value: Decimal | None
    unit: Literal["PERCENT", "DIMENSIONLESS"]
    observations: Annotated[int, Field(strict=True, ge=0, le=2000)]
    requiredObservations: Annotated[int, Field(strict=True, ge=60, le=2000)]
    from_: date | None = Field(alias="from")
    to: date | None
    reasons: list[IssueCode]

    @model_validator(mode="after")
    def coherent(self):
        ready = self.status == "READY"
        if ready != (self.value is not None) or ready != (not self.reasons):
            raise ValueError("Only READY metrics have values and no reasons")
        if ready and (self.observations < self.requiredObservations or self.from_ is None or self.to is None):
            raise ValueError("READY metrics require sufficient dated observations")
        if self.value is not None and self.unit == "PERCENT" and self.value < 0:
            raise ValueError("Percentage metrics must be non-negative")
        return self


class VarResult(Model):
    method: Literal["HISTORICAL", "PARAMETRIC", "MONTE_CARLO"]
    status: Status
    estimate: Literal[True] = True
    lossPercent: Decimal | None
    lossAmount: Decimal | None
    observations: Annotated[int, Field(strict=True, ge=0, le=2000)]
    requiredObservations: Annotated[int, Field(strict=True, ge=100, le=2000)]
    from_: date | None = Field(alias="from")
    to: date | None
    reasons: list[IssueCode]

    @model_validator(mode="after")
    def coherent(self):
        ready = self.status == "READY"
        if ready != (self.lossPercent is not None) or ready != (self.lossAmount is not None) or ready != (not self.reasons):
            raise ValueError("Only READY VaR results have both losses and no reasons")
        if ready and (self.observations < self.requiredObservations or self.from_ is None or self.to is None):
            raise ValueError("READY VaR requires sufficient dated observations")
        if ready and (self.lossPercent < 0 or self.lossAmount < 0):
            raise ValueError("VaR losses must be non-negative")
        return self


class CoreMetrics(Model):
    annualisedVolatility: ScalarMetric
    maximumDrawdown: ScalarMetric
    beta: ScalarMetric


class Issue(Model):
    code: IssueCode
    severity: Literal["WARNING"] = "WARNING"
    scope: str
    symbol: None = None
    date: date | None
    message: str


class CalculationResponse(Model):
    schemaVersion: Literal["1.0"] = "1.0"
    requestId: str
    metrics: CoreMetrics
    valueAtRisk: list[VarResult]
    issues: list[Issue]

    @model_validator(mode="after")
    def methods_and_units(self):
        if [value.method for value in self.valueAtRisk] != ["HISTORICAL", "PARAMETRIC", "MONTE_CARLO"]:
            raise ValueError("Exactly one result per VaR method is required, in contract order")
        if (self.metrics.annualisedVolatility.unit != "PERCENT" or self.metrics.maximumDrawdown.unit != "PERCENT"
                or self.metrics.beta.unit != "DIMENSIONLESS"):
            raise ValueError("Metric units must match the v1 contract")
        drawdown = self.metrics.maximumDrawdown.value
        if drawdown is not None and drawdown > 100:
            raise ValueError("Long-only maximum drawdown cannot exceed 100 percent")
        return self
