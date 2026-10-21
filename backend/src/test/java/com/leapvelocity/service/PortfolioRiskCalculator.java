package com.leapvelocity.service;

import com.leapvelocity.dto.response.PortfolioRiskDto.Assumptions;
import com.leapvelocity.dto.response.PortfolioRiskDto.CoreMetrics;
import com.leapvelocity.dto.response.PortfolioRiskDto.Issue;
import com.leapvelocity.dto.response.PortfolioRiskDto.IssueCode;
import com.leapvelocity.dto.response.PortfolioRiskDto.ScalarMetric;
import com.leapvelocity.dto.response.PortfolioRiskDto.Severity;
import com.leapvelocity.dto.response.PortfolioRiskDto.ResultStatus;
import com.leapvelocity.dto.response.PortfolioRiskDto.Unit;
import com.leapvelocity.dto.response.PortfolioRiskDto.VarMethod;
import com.leapvelocity.dto.response.PortfolioRiskDto.VarResult;
import com.leapvelocity.service.PortfolioReturnPreparation.Availability;
import com.leapvelocity.service.PortfolioReturnPreparation.PreparedInputs;
import com.leapvelocity.service.PortfolioReturnPreparation.ReturnPoint;
import com.leapvelocity.service.PortfolioReturnPreparation.WealthPoint;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.leapvelocity.dto.response.PortfolioRiskDto.IssueCode.*;
import static com.leapvelocity.dto.response.PortfolioRiskDto.ResultStatus.*;

/** Frozen pre-migration test oracle. Never packaged in production. Stateless historical VaR and core risk calculations over validated portfolio returns. */
public final class PortfolioRiskCalculator {
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final Assumptions ASSUMPTIONS = Assumptions.mvp();
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public record Result(CoreMetrics metrics, VarResult historicalVar, List<Issue> issues) {
        public Result { issues = List.copyOf(issues); }
    }

    public Result calculate(PreparedInputs inputs) {
        Objects.requireNonNull(inputs, "Validated risk inputs are required");
        List<Issue> issues = new ArrayList<>(inputs.issues());
        var returns = inputs.portfolio().returns();
        LocalDate from = returns.isEmpty() ? null : returns.get(0).from();
        LocalDate to = returns.isEmpty() ? null : returns.get(returns.size() - 1).to();
        var volatility = metric(inputs.core(), returns.size(), Unit.PERCENT, from, to,
                "metrics.annualisedVolatility", issues, () -> annualisedVolatility(returns));
        var drawdown = metric(inputs.core(), returns.size(), Unit.PERCENT, from, to,
                "metrics.maximumDrawdown", issues, () -> maximumDrawdown(inputs.portfolio().wealth(), returns.size()));
        var pairs = inputs.pairedReturns();
        LocalDate betaFrom = pairs.isEmpty() ? null : pairs.get(0).from();
        LocalDate betaTo = pairs.isEmpty() ? null : pairs.get(pairs.size() - 1).to();
        var beta = metric(inputs.beta(), pairs.size(), Unit.DIMENSIONLESS, betaFrom, betaTo,
                "metrics.beta", issues, () -> beta(inputs));
        var historical = historicalVar(inputs, issues);
        return new Result(new CoreMetrics(volatility, drawdown, beta), historical, issues.stream().distinct().toList());
    }

    private VarResult historicalVar(PreparedInputs inputs, List<Issue> issues) {
        var parameters = inputs.parameters();
        int horizon = parameters.holdingPeriodDays();
        if (horizon < 1 || horizon > 10) throw new IllegalArgumentException("holdingPeriodDays must be between 1 and 10");
        int required = PortfolioReturnPreparation.minimumVarObservations(parameters.confidenceLevel());
        var eligibility = inputs.historicalVar();
        var returns = inputs.portfolio().returns();
        int observations = Math.max(0, returns.size() - horizon + 1);
        LocalDate from = observations == 0 ? null : returns.get(0).from();
        LocalDate to = observations == 0 ? null : returns.get(returns.size() - 1).to();
        if (eligibility.status() != READY) {
            return new VarResult(VarMethod.HISTORICAL, eligibility.status(), true, null, null, observations, required,
                    from, to, eligibility.reasons());
        }
        try {
            if (observations < required) throw new InvalidMetricInput(INSUFFICIENT_HISTORY,
                    "Requires " + required + " complete horizon windows; available " + observations + ".");
            var holdingsValue = inputs.valuation().holdingsValue();
            if (holdingsValue == null || holdingsValue.signum() <= 0) throw invalid("A positive invested holdings value is required for VaR.");
            for (int i = 0; i < returns.size(); i++) {
                var point = returns.get(i);
                validateReturn(point.value());
                if (point.from() == null || point.to() == null || !point.from().isBefore(point.to())
                        || (i > 0 && !returns.get(i - 1).to().equals(point.from()))) {
                    throw new InvalidMetricInput(INCOMPLETE_HISTORY, "VaR requires consecutive validated return intervals; gaps cannot be bridged.");
                }
            }
            List<BigDecimal> losses = new ArrayList<>(observations);
            for (int start = 0; start < observations; start++) {
                BigDecimal growth = BigDecimal.ONE;
                for (int offset = 0; offset < horizon; offset++) {
                    growth = growth.multiply(BigDecimal.ONE.add(returns.get(start + offset).value()), PRECISION);
                }
                losses.add(BigDecimal.ONE.subtract(growth));
            }
            losses.sort(BigDecimal::compareTo);
            // Exact decimal rank arithmetic matters at integer quantile boundaries; no interpolation or time scaling.
            int rank = parameters.confidenceLevel().multiply(BigDecimal.valueOf(observations))
                    .setScale(0, RoundingMode.CEILING).intValueExact();
            var lossFraction = losses.get(rank - 1).max(BigDecimal.ZERO);
            return new VarResult(VarMethod.HISTORICAL, READY, true, lossFraction.multiply(HUNDRED).setScale(8, RoundingMode.HALF_UP),
                    lossFraction.multiply(holdingsValue).setScale(2, RoundingMode.HALF_UP), observations, required, from, to, List.of());
        } catch (InvalidMetricInput exception) {
            issues.add(new Issue(exception.code, Severity.WARNING, "valueAtRisk.HISTORICAL", null, to, exception.getMessage()));
            return new VarResult(VarMethod.HISTORICAL, UNAVAILABLE, true, null, null, observations, required, from, to, List.of(exception.code));
        }
    }

    private ScalarMetric metric(Availability eligibility, int observations, Unit unit, LocalDate from, LocalDate to,
                                 String scope, List<Issue> issues, Supplier<BigDecimal> calculation) {
        int required = Math.max(ASSUMPTIONS.minimumCoreReturns(), eligibility.requiredObservations());
        if (eligibility.status() != READY) {
            return new ScalarMetric(eligibility.status(), null, unit, observations, required, from, to, eligibility.reasons());
        }
        if (observations < required) {
            return unavailable(INSUFFICIENT_HISTORY, observations, required, unit, from, to, scope, issues,
                    "Requires " + required + " observations; available " + observations + ".");
        }
        try {
            BigDecimal value = calculation.get().setScale(8, RoundingMode.HALF_UP);
            return new ScalarMetric(READY, value, unit, observations, required, from, to, List.of());
        } catch (InvalidMetricInput exception) {
            return unavailable(exception.code, observations, required, unit, from, to, scope, issues, exception.getMessage());
        }
    }

    private ScalarMetric unavailable(IssueCode reason, int observations, int required, Unit unit, LocalDate from,
                                      LocalDate to, String scope, List<Issue> issues, String message) {
        issues.add(new Issue(reason, Severity.WARNING, scope, null, to, message));
        return new ScalarMetric(UNAVAILABLE, null, unit, observations, required, from, to, List.of(reason));
    }

    private BigDecimal annualisedVolatility(List<ReturnPoint> returns) {
        var values = returns.stream().map(ReturnPoint::value).toList();
        values.forEach(this::validateReturn);
        BigDecimal squaredDeviations = BigDecimal.ZERO;
        for (var deviation : centered(values)) {
            squaredDeviations = squaredDeviations.add(deviation.multiply(deviation, PRECISION));
        }
        BigDecimal sampleVariance = squaredDeviations.divide(BigDecimal.valueOf(values.size() - 1L), PRECISION);
        return sampleVariance.multiply(BigDecimal.valueOf(ASSUMPTIONS.annualisationFactor()), PRECISION)
                .sqrt(PRECISION).multiply(HUNDRED, PRECISION);
    }

    private BigDecimal maximumDrawdown(List<WealthPoint> wealth, int returns) {
        if (wealth.size() != returns + 1 || wealth.get(0).value() == null || wealth.get(0).value().signum() <= 0) {
            throw invalid("A positive initial wealth observation and one more price than returns are required.");
        }
        BigDecimal peak = wealth.get(0).value();
        BigDecimal maximum = BigDecimal.ZERO;
        for (var point : wealth) {
            if (point.value() == null || point.value().signum() < 0) throw invalid("Wealth must be non-negative.");
            peak = peak.max(point.value());
            BigDecimal drawdown = BigDecimal.ONE.subtract(point.value().divide(peak, PRECISION), PRECISION);
            maximum = maximum.max(drawdown);
        }
        return maximum.multiply(HUNDRED, PRECISION);
    }

    private BigDecimal beta(PreparedInputs inputs) {
        var pairs = inputs.pairedReturns();
        var portfolioValues = pairs.stream().map(PortfolioReturnPreparation.PairedReturn::portfolio).toList();
        var benchmarkValues = pairs.stream().map(PortfolioReturnPreparation.PairedReturn::benchmark).toList();
        portfolioValues.forEach(this::validateReturn);
        benchmarkValues.forEach(this::validateReturn);
        var portfolioCentered = centered(portfolioValues);
        var benchmarkCentered = centered(benchmarkValues);
        BigDecimal crossDeviations = BigDecimal.ZERO, benchmarkDeviations = BigDecimal.ZERO;
        for (int i = 0; i < pairs.size(); i++) {
            var p = portfolioCentered.get(i);
            var b = benchmarkCentered.get(i);
            crossDeviations = crossDeviations.add(p.multiply(b, PRECISION));
            benchmarkDeviations = benchmarkDeviations.add(b.multiply(b, PRECISION));
        }
        if (benchmarkDeviations.signum() == 0) {
            throw new InvalidMetricInput(ZERO_BENCHMARK_VARIANCE, "Beta is unavailable because benchmark return variance is zero.");
        }
        // The shared sample denominator n-1 cancels in covariance / benchmark variance.
        return crossDeviations.divide(benchmarkDeviations, PRECISION);
    }

    private List<BigDecimal> centered(List<BigDecimal> values) {
        // Subtract an anchor exactly before averaging: preserve tiny differences and exact constant-series zeros.
        BigDecimal anchor = values.get(0);
        var shifts = values.stream().map(value -> value.subtract(anchor)).toList();
        var meanShift = shifts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), PRECISION);
        return shifts.stream().map(shift -> shift.subtract(meanShift, PRECISION)).toList();
    }

    private void validateReturn(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ONE.negate()) < 0) {
            throw invalid("A long-only portfolio return must be a finite decimal no less than -1.");
        }
    }

    private InvalidMetricInput invalid(String message) { return new InvalidMetricInput(INVALID_POSITION, message); }

    private static final class InvalidMetricInput extends RuntimeException {
        private final IssueCode code;
        private InvalidMetricInput(IssueCode code, String message) { super(message); this.code = code; }
    }
}
