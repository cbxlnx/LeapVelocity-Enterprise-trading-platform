"""Synthetic snapshot builders; deliberately no price imports or database access."""

from datetime import date, timedelta
from decimal import Decimal, localcontext

from analysis.risk_service.calculator import minimum_var
from analysis.risk_service.numeric import ONE, PRECISION, exact_add
from analysis.risk_service.schemas import CalculationRequest


def snapshot(values, benchmark=None, confidence="0.95", horizon=1, invested="100000"):
    values = [Decimal(str(value)) for value in values]
    benchmark = [Decimal(str(value)) for value in benchmark] if benchmark is not None else values
    start = date(2024, 1, 2)
    returns, wealth, pairs = [], [], []
    level = ONE
    if values:
        wealth.append(dict(date=str(start), value="1"))
    with localcontext(PRECISION):
        for index, value in enumerate(values):
            first, last = start + timedelta(days=index), start + timedelta(days=index + 1)
            returns.append({"from": str(first), "to": str(last), "value": str(value)})
            level *= exact_add(ONE, value)
            wealth.append(dict(date=str(last), value=str(level)))
            if index < len(benchmark):
                pairs.append({"from": str(first), "to": str(last), "portfolio": str(value), "benchmark": str(benchmark[index])})

    def availability(count, required):
        status = "EMPTY" if not values else "READY" if count >= required else "UNAVAILABLE"
        return dict(status=status, observations=count, requiredObservations=required,
                    reasons=[] if status == "READY" else ["EMPTY_PORTFOLIO" if not values else "INSUFFICIENT_HISTORY"])

    return dict(schemaVersion="1.0", requestId="synthetic", holdingsValue=invested,
                parameters=dict(confidenceLevel=confidence, holdingPeriodDays=horizon),
                portfolio=dict(returns=returns, wealth=wealth), pairedReturns=pairs,
                core=availability(len(values), 60), beta=availability(len(pairs), 60),
                historicalVar=availability(max(0, len(values) - horizon + 1), minimum_var(Decimal(confidence))))


def request(values, **options):
    return CalculationRequest.model_validate(snapshot(values, **options))


EXAMPLE = ["-0.0115", "0.0125"] * 250 + ["0.0005"]
