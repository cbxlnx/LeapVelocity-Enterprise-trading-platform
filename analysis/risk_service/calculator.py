"""Risk statistics using NumPy/SciPy/Pandas over a validated Java snapshot."""

from decimal import Decimal, DecimalException, localcontext
from fractions import Fraction
from functools import cache
from math import ceil

import numpy as np
import pandas as pd
from numpy.lib.stride_tricks import sliding_window_view
from scipy.stats import norm

from .numeric import HUNDRED, ONE, ZERO, PRECISION, exact_add, exact_mul, rounded
from .schemas import CalculationRequest, CalculationResponse

SIMULATIONS = 100_000
SEED = 20261020
ARITHMETIC_ERRORS = (DecimalException, OverflowError, FloatingPointError)


class InvalidInput(Exception):
    def __init__(self, code, message):
        self.code, self.message = code, message


def invalid():
    return InvalidInput("INVALID_POSITION", "A long-only portfolio return must be a finite decimal no less than -1.")


def numeric():
    return InvalidInput("NUMERICAL_ERROR", "Calculation inputs or outcomes exceed the supported float64 numeric range or resolution.")


def finite(value):
    result = float(value)
    if not np.isfinite(result) or (result == 0 and value != 0):
        raise numeric()
    return result


def minimum_var(confidence):
    # Business policy uses the exact supplied confidence, including sub-float64 epsilons.
    return max(100, ceil(5 / (1 - Fraction(confidence))))


def rank(confidence, count):
    return ceil(Fraction(confidence) * count) - 1


def as_array(values):
    result = np.asarray(values, dtype=np.float64)
    if not np.all(np.isfinite(result)) or any(value != 0 and number == 0 for value, number in zip(values, result)):
        raise numeric()
    # Do not mistake precision-lost observations for a genuinely constant series.
    if len(values) > 1 and np.all(result == result[0]) and any(value != values[0] for value in values):
        raise numeric()
    return result


def validate_returns(values):
    if any(value is None or value < -1 for value in values):
        raise invalid()


def validate_intervals(points):
    for index, point in enumerate(points):
        if point.from_ >= point.to or (index and points[index - 1].to != point.from_):
            raise InvalidInput("INCOMPLETE_HISTORY", "Consecutive validated return intervals are required; gaps cannot be bridged.")


def daily_volatility(points):
    validate_intervals(points)
    values = [point.value for point in points]
    validate_returns(values)
    _, deviation = moments(as_array(values))
    return deviation * np.sqrt(252) * 100


def moments(values):
    if np.all(values == values[0]):
        return float(values[0]), 0.0
    mean, deviation = float(np.mean(values)), float(np.std(values, ddof=1))
    if deviation == 0:  # Nonconstant observations whose variance underflowed.
        raise numeric()
    finite(mean)
    finite(deviation)
    return mean, deviation


def drawdown(wealth, points):
    validate_intervals(points)
    validate_returns([point.value for point in points])
    if len(wealth) != len(points) + 1 or wealth[0].value is None or wealth[0].value <= 0:
        raise InvalidInput("INVALID_POSITION", "A positive initial wealth observation and one more price than returns are required.")
    for index, point in enumerate(wealth):
        if point.value is None or point.value < 0:
            raise InvalidInput("INVALID_POSITION", "Wealth must be non-negative.")
        if index:
            interval, previous = points[index - 1], wealth[index - 1]
            if previous.date != interval.from_ or point.date != interval.to:
                raise InvalidInput("INCOMPLETE_HISTORY", "Wealth dates must match the portfolio return intervals.")
            expected = previous.value * exact_add(ONE, interval.value)
            tolerance = max(abs(expected), abs(point.value)) * Decimal("1e-30")
            if abs(point.value - expected) > tolerance:
                raise InvalidInput("INVALID_POSITION", "Wealth must compound the supplied portfolio returns.")
    # Include the initial observation so a loss on the first interval counts.
    # Validation above rejects missing values; Pandas must never skip or fill them.
    levels = pd.Series(as_array([point.value for point in wealth]), index=[point.date for point in wealth])
    declines = 1 - levels / levels.cummax(skipna=False)
    return float(declines.max(skipna=False) * 100)


def beta(pairs, points):
    validate_intervals(points)
    validate_intervals(pairs)
    intervals = {(point.from_, point.to): point.value for point in points}
    for pair in pairs:
        key = (pair.from_, pair.to)
        if key not in intervals:
            raise InvalidInput("INCOMPLETE_HISTORY", "Beta requires matching portfolio and benchmark return intervals.")
        if pair.portfolio != intervals[key]:
            raise InvalidInput("INVALID_POSITION", "Paired portfolio returns must match the portfolio series.")
    portfolio, benchmark = [p.portfolio for p in pairs], [p.benchmark for p in pairs]
    validate_returns(portfolio)
    validate_returns(benchmark)
    p, b = as_array(portfolio), as_array(benchmark)
    if np.all(b == b[0]):
        raise InvalidInput("ZERO_BENCHMARK_VARIANCE", "Beta is unavailable because benchmark return variance is zero.")
    if np.all(p == p[0]):
        return 0.0
    covariance = np.cov(p, b, ddof=1)
    if covariance[1, 1] <= 0:
        raise numeric()
    return float(covariance[0, 1] / covariance[1, 1])


def windows(points, horizon):
    values = [point.value for point in points]
    validate_returns(values)
    validate_intervals(points)
    returns = as_array(values)
    if any(number == -1 and value != -1 for value, number in zip(values, returns)):
        raise numeric()  # Rounding must not turn a near-total loss into a total loss.
    if horizon == 1:
        return returns
    view = sliding_window_view(returns, horizon)
    total_loss = np.any(view == -1, axis=1)
    # log1p/expm1 compound small returns without losing them in (1 + r) - 1.
    compounded = np.expm1(np.sum(np.log1p(np.where(view == -1, 0, view)), axis=1))
    return np.where(total_loss, -1.0, compounded)


def normal_quantile(confidence):
    return float(norm.ppf(float(confidence)))


def nearest_loss(losses, confidence):
    # The API accepts exact decimal confidences, including epsilons smaller than
    # float64 can represent. Select that rank's CDF bin midpoint, so NumPy's
    # inverted CDF gives ceil(confidence * n) without rounding a boundary across
    # ranks. The default linear quantile would change the agreed definition.
    index = rank(confidence, len(losses))
    probability = (index + 0.5) / len(losses)
    return max(0.0, float(np.quantile(losses, probability, method="inverted_cdf")))


def simulation_losses(mean, deviation, simulations, rng):
    if not np.isfinite(mean) or not np.isfinite(deviation) or deviation < 0:
        raise numeric()
    try:
        with np.errstate(over="raise", invalid="raise"):
            outcomes = np.asarray(rng.normal(loc=mean, scale=deviation, size=simulations), dtype=np.float64)
        if outcomes.shape != (simulations,) or not np.all(np.isfinite(outcomes)):
            raise numeric()
        return -outcomes
    except (FloatingPointError, OverflowError, ValueError) as error:
        raise numeric() from error


def simulated_loss(mean, deviation, confidence, simulations, rng):
    return nearest_loss(simulation_losses(mean, deviation, simulations, rng), confidence)


def expected_shortfall(losses, confidence):
    """Integral of the empirical inverse CDF over (confidence, 1), divided by tail mass.

    Fractional boundary weight keeps exactly (1-c)*N outcomes of mass. Ties do
    not inflate the tail, and constant/gain-only distributions remain defined.
    """
    values = np.sort(np.asarray(losses, dtype=np.float64))
    if values.ndim != 1 or not values.size or not np.all(np.isfinite(values)):
        raise numeric()
    if values[0] == values[-1]:
        return max(0.0, finite(values[0]))
    cutoff = Fraction(confidence) * len(values)
    index = cutoff.numerator // cutoff.denominator
    if values[index] == values[-1]:
        return max(0.0, finite(values[index]))
    weights = np.ones(len(values) - index)
    weights[0] = float(index + 1 - cutoff)
    return max(0.0, finite(np.average(values[index:], weights=weights)))


def summarize_simulation(losses, confidence, invested, horizon, var_amount):
    amounts = losses * finite(invested)
    if not np.all(np.isfinite(amounts)):
        raise numeric()
    if np.all(amounts == amounts[0]):
        edges, counts = [amounts[0], amounts[0]], [len(amounts)]
    else:
        counts, edges = np.histogram(amounts, bins=40)
        if not np.all(np.isfinite(edges)) or np.any(np.diff(edges) <= 0):
            raise numeric()
    loss = Decimal(str(expected_shortfall(losses, confidence)))
    amount, percent = exact_mul(loss, invested), exact_mul(loss, HUNDRED)
    finite(amount)
    finite(percent)
    return dict(status="READY", reasons=[], simulations=len(losses), confidenceLevel=confidence,
                holdingPeriodDays=horizon, varAmount=var_amount,
                expectedShortfallAmount=rounded(amount, 2), expectedShortfallPercent=rounded(percent, 8),
                bins=[dict(lowerBound=Decimal(str(edges[index])), upperBound=Decimal(str(edges[index + 1])), count=int(count))
                      for index, count in enumerate(counts)])


def calculate(request: CalculationRequest, *, rng=None, simulations=SIMULATIONS):
    # Each request owns its generator, decimal context and floating-point error state.
    generator = np.random.Generator(np.random.PCG64(SEED)) if rng is None else rng
    with localcontext(PRECISION), np.errstate(over="raise", invalid="raise", divide="raise"):
        return _calculate(request, generator, simulations)


def _calculate(request, rng, simulations):
    issues = []
    points, pairs = request.portfolio.returns, request.pairedReturns
    count = len(points)
    first, last = (points[0].from_, points[-1].to) if points else (None, None)

    def issue(error, scope, end):
        issues.append(dict(code=error.code, scope=scope, date=end, message=error.message))

    def scalar(eligibility, observations, unit, start, end, name, function):
        result = dict(status=eligibility.status, value=None, unit=unit, observations=observations,
                      requiredObservations=max(60, eligibility.requiredObservations),
                      **{"from": start, "to": end}, reasons=eligibility.reasons)
        if eligibility.status != "READY":
            return result
        try:
            if observations < result["requiredObservations"]:
                raise InvalidInput("INSUFFICIENT_HISTORY", f"Requires {result['requiredObservations']} observations; available {observations}.")
            result["value"] = rounded(Decimal(str(finite(function()))), 8)
        except ARITHMETIC_ERRORS:
            error = numeric()
            result.update(status="UNAVAILABLE", reasons=[error.code])
            issue(error, "metrics." + name, end)
        except InvalidInput as error:
            result.update(status="UNAVAILABLE", reasons=[error.code])
            issue(error, "metrics." + name, end)
        return result

    metrics = dict(
        annualisedVolatility=scalar(request.core, count, "PERCENT", first, last, "annualisedVolatility",
                                    lambda: daily_volatility(points)),
        maximumDrawdown=scalar(request.core, count, "PERCENT", first, last, "maximumDrawdown",
                              lambda: drawdown(request.portfolio.wealth, points)),
        beta=scalar(request.beta, len(pairs), "DIMENSIONLESS", pairs[0].from_ if pairs else None,
                    pairs[-1].to if pairs else None, "beta", lambda: beta(pairs, points)),
    )
    confidence, horizon = request.parameters.confidenceLevel, request.parameters.holdingPeriodDays
    observations = max(0, count - horizon + 1)
    required = minimum_var(confidence)
    start, end = (first, last) if observations else (None, None)
    eligibility = request.historicalVar

    def blocked(method):
        return dict(method=method, status=eligibility.status, estimate=True, lossPercent=None, lossAmount=None,
                    observations=observations, requiredObservations=required, **{"from": start, "to": end},
                    reasons=eligibility.reasons)

    def failed(result, error):
        result.update(status="UNAVAILABLE", reasons=[error.code])
        issue(error, "valueAtRisk." + result["method"], end)

    def prerequisites():
        if observations < required:
            raise InvalidInput("INSUFFICIENT_HISTORY", f"Requires {required} complete horizon windows; available {observations}.")
        if request.holdingsValue is None or request.holdingsValue <= 0:
            raise InvalidInput("INVALID_POSITION", "A positive invested holdings value is required for VaR.")

    @cache
    def horizon_returns():
        # One validated NumPy array per request, shared by all three VaR models.
        return windows(points, horizon)

    def success(result, loss, normal=False):
        loss = max(ZERO, Decimal(str(finite(loss))))
        amount, percent = exact_mul(loss, request.holdingsValue), exact_mul(loss, HUNDRED)
        if normal:
            finite(amount)
            finite(percent)
        result.update(lossPercent=rounded(percent, 8), lossAmount=rounded(amount, 2))

    historical, parametric, monte_carlo = [blocked(method) for method in ("HISTORICAL", "PARAMETRIC", "MONTE_CARLO")]
    losses = None
    if eligibility.status == "READY":
        try:
            prerequisites()
            success(historical, nearest_loss(-horizon_returns(), confidence))
        except (InvalidInput, *ARITHMETIC_ERRORS) as error:
            failed(historical, error if isinstance(error, InvalidInput) else numeric())
        try:
            prerequisites()
            finite(request.holdingsValue)
            mean, deviation = moments(horizon_returns())
        except (InvalidInput, *ARITHMETIC_ERRORS) as error:
            error = error if isinstance(error, InvalidInput) else numeric()
            failed(parametric, error)
            failed(monte_carlo, error)
        else:
            for result in (parametric, monte_carlo):
                try:
                    if result is parametric:
                        loss = deviation * normal_quantile(confidence) - mean
                    elif deviation == 0:
                        loss = -mean
                        losses = np.full(simulations, -mean)
                    else:
                        losses = simulation_losses(mean, deviation, simulations, rng)
                        loss = nearest_loss(losses, confidence)
                    success(result, loss, normal=True)
                except (InvalidInput, *ARITHMETIC_ERRORS) as error:
                    failed(result, error if isinstance(error, InvalidInput) else numeric())
    distribution = dict(status=monte_carlo["status"], reasons=monte_carlo["reasons"], simulations=simulations,
                        confidenceLevel=confidence, holdingPeriodDays=horizon, bins=[], varAmount=None,
                        expectedShortfallAmount=None, expectedShortfallPercent=None)
    if monte_carlo["status"] == "READY":
        try:
            distribution = summarize_simulation(losses, confidence, request.holdingsValue, horizon, monte_carlo["lossAmount"])
        except (InvalidInput, ValueError, *ARITHMETIC_ERRORS):
            distribution.update(status="UNAVAILABLE", reasons=["NUMERICAL_ERROR"])
            issue(numeric(), "monteCarlo", end)
    return CalculationResponse(requestId=request.requestId, metrics=metrics,
                               valueAtRisk=[historical, parametric, monte_carlo], issues=issues, monteCarlo=distribution)
