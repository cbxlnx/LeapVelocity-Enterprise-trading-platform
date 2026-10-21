package com.leapvelocity.service;

import com.leapvelocity.dto.response.PortfolioRiskDto.*;
import com.leapvelocity.service.PortfolioReturnPreparation.PreparedInputs;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.DoubleSupplier;

import static com.leapvelocity.dto.response.PortfolioRiskDto.IssueCode.*;
import static com.leapvelocity.dto.response.PortfolioRiskDto.ResultStatus.*;

/** Frozen pre-migration test oracle. Never packaged in production. Fit a normal distribution to observed compounded horizon returns; cash is excluded. */
public final class NormalModelVarCalculator {
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final int simulations;
    private final long seed;

    public NormalModelVarCalculator() {
        this(Assumptions.mvp().monteCarloSimulations(), Assumptions.mvp().monteCarloSeed());
    }

    NormalModelVarCalculator(int simulations, long seed) {
        if (simulations < 1_000 || simulations > 1_000_000) {
            throw new IllegalArgumentException("Monte Carlo simulations must be between 1000 and 1000000");
        }
        this.simulations = simulations;
        this.seed = seed;
    }

    public record Result(List<VarResult> values, List<Issue> issues) {
        public Result { values = List.copyOf(values); issues = List.copyOf(issues); }
    }

    public Result calculate(PreparedInputs inputs) {
        // Each request owns its generator: refreshes and concurrent calls are reproducible.
        return calculate(inputs, new Random(seed)::nextGaussian);
    }

    Result calculate(PreparedInputs inputs, DoubleSupplier gaussian) {
        Objects.requireNonNull(inputs, "Validated risk inputs are required");
        Objects.requireNonNull(gaussian);
        var options = inputs.parameters();
        int required = PortfolioReturnPreparation.minimumVarObservations(options.confidenceLevel());
        int horizon = options.holdingPeriodDays();
        if (horizon < 1 || horizon > 10) throw new IllegalArgumentException("holdingPeriodDays must be between 1 and 10");
        var returns = inputs.portfolio().returns();
        int observations = Math.max(0, returns.size() - horizon + 1);
        LocalDate from = observations == 0 ? null : returns.get(0).from();
        LocalDate to = observations == 0 ? null : returns.get(returns.size() - 1).to();
        List<Issue> issues = new ArrayList<>();
        var eligibility = inputs.historicalVar();
        if (eligibility.status() != READY) {
            return new Result(List.of(blocked(VarMethod.PARAMETRIC, eligibility.status(), observations, required, from, to, eligibility.reasons()),
                    blocked(VarMethod.MONTE_CARLO, eligibility.status(), observations, required, from, to, eligibility.reasons())), List.of());
        }
        Moments moments;
        try {
            if (observations < required) throw new InvalidInput(INSUFFICIENT_HISTORY,
                    "Requires " + required + " complete horizon windows; available " + observations + ".");
            if (inputs.valuation().holdingsValue() == null || inputs.valuation().holdingsValue().signum() <= 0) {
                throw new InvalidInput(INVALID_POSITION, "A positive invested holdings value is required for VaR.");
            }
            finite(inputs.valuation().holdingsValue());
            for (int i = 0; i < returns.size(); i++) {
                var point = returns.get(i);
                if (point.value() == null || point.value().compareTo(BigDecimal.ONE.negate()) < 0) {
                    throw new InvalidInput(INVALID_POSITION, "Portfolio returns must be finite decimals no less than -1.");
                }
                finite(point.value());
                if (point.from() == null || point.to() == null || !point.from().isBefore(point.to())
                        || (i > 0 && !returns.get(i - 1).to().equals(point.from()))) {
                    throw new InvalidInput(INCOMPLETE_HISTORY, "Normal-model VaR requires consecutive validated return intervals.");
                }
            }
            List<BigDecimal> windows = new ArrayList<>(observations);
            for (int start = 0; start < observations; start++) {
                BigDecimal growth = BigDecimal.ONE;
                for (int offset = 0; offset < horizon; offset++) {
                    growth = growth.multiply(BigDecimal.ONE.add(returns.get(start + offset).value()), PRECISION);
                }
                BigDecimal value = growth.subtract(BigDecimal.ONE);
                finite(value);
                windows.add(value);
            }
            moments = moments(windows);
        } catch (InvalidInput exception) {
            return new Result(List.of(failed(VarMethod.PARAMETRIC, observations, required, from, to, exception, issues),
                    failed(VarMethod.MONTE_CARLO, observations, required, from, to, exception, issues)), issues);
        }
        List<VarResult> results = new ArrayList<>();
        for (var method : List.of(VarMethod.PARAMETRIC, VarMethod.MONTE_CARLO)) {
            try {
                BigDecimal loss;
                if (method == VarMethod.PARAMETRIC) {
                    // Positive z(confidence) equals -z(lower tail). No annualised volatility or time scaling.
                    loss = moments.deviation().multiply(BigDecimal.valueOf(normalQuantile(options.confidenceLevel())), PRECISION)
                            .subtract(moments.mean());
                } else if (moments.deviation().signum() == 0) {
                    loss = moments.mean().negate(); // Degenerate normal: every simulated outcome is identical.
                } else {
                    loss = BigDecimal.valueOf(simulatedLoss(finite(moments.mean()), finite(moments.deviation()),
                            options.confidenceLevel(), simulations, gaussian));
                }
                loss = loss.max(BigDecimal.ZERO);
                BigDecimal amount = loss.multiply(inputs.valuation().holdingsValue());
                BigDecimal percent = loss.multiply(HUNDRED);
                finite(amount); finite(percent);
                results.add(new VarResult(method, READY, true, percent.setScale(8, RoundingMode.HALF_UP),
                        amount.setScale(2, RoundingMode.HALF_UP), observations, required, from, to, List.of()));
            } catch (InvalidInput exception) {
                results.add(failed(method, observations, required, from, to, exception, issues));
            }
        }
        return new Result(results, issues);
    }

    private record Moments(BigDecimal mean, BigDecimal deviation) { }

    private static Moments moments(List<BigDecimal> values) {
        BigDecimal anchor = values.get(0);
        var shifts = values.stream().map(value -> value.subtract(anchor)).toList();
        BigDecimal meanShift = shifts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), PRECISION);
        BigDecimal squared = BigDecimal.ZERO;
        for (var shift : shifts) {
            var centered = shift.subtract(meanShift, PRECISION);
            squared = squared.add(centered.multiply(centered, PRECISION));
        }
        BigDecimal deviation = squared.divide(BigDecimal.valueOf(values.size() - 1L), PRECISION).sqrt(PRECISION);
        BigDecimal mean = anchor.add(meanShift);
        finite(mean); finite(deviation);
        return new Moments(mean, deviation);
    }

    /** Actual sampled horizon outcomes, followed by the same nearest-rank loss convention as Historical VaR. */
    static double simulatedLoss(double mean, double deviation, BigDecimal confidence, int simulations, DoubleSupplier gaussian) {
        if (!Double.isFinite(mean) || !Double.isFinite(deviation) || deviation < 0) throw numeric();
        if (simulations < 1_000 || simulations > 1_000_000) throw new IllegalArgumentException("Invalid simulation count");
        PortfolioReturnPreparation.minimumVarObservations(confidence);
        double[] losses = new double[simulations];
        for (int i = 0; i < simulations; i++) {
            double draw = gaussian.getAsDouble();
            if (!Double.isFinite(draw)) throw numeric();
            double outcome = mean + deviation * draw;
            if (!Double.isFinite(outcome)) throw numeric();
            losses[i] = -outcome;
        }
        Arrays.sort(losses);
        int rank = confidence.multiply(BigDecimal.valueOf(simulations)).setScale(0, RoundingMode.CEILING).intValueExact();
        return Math.max(0, losses[rank - 1]);
    }

    /** Invert Phi on the supported confidence range using bisection and NIST DLMF 7.6.2. */
    static double normalQuantile(BigDecimal confidence) {
        PortfolioReturnPreparation.minimumVarObservations(confidence);
        double target = confidence.doubleValue();
        double low = 0, high = 4;
        for (int i = 0; i < 60; i++) {
            double mid = (low + high) / 2;
            double term = mid, sum = mid;
            for (int n = 1; n < 100; n++) {
                term *= mid * mid / (2 * n + 1);
                double next = sum + term;
                if (next == sum) break;
                sum = next;
            }
            double cdf = 0.5 + StrictMath.exp(-mid * mid / 2) * sum / StrictMath.sqrt(2 * StrictMath.PI);
            if (cdf < target) low = mid; else high = mid;
        }
        return (low + high) / 2;
    }

    private static double finite(BigDecimal value) {
        double number = value.doubleValue();
        if (!Double.isFinite(number) || (number == 0 && value.signum() != 0)) throw numeric();
        return number;
    }

    private static InvalidInput numeric() {
        return new InvalidInput(NUMERICAL_ERROR, "Normal-model VaR inputs or outcomes exceed the supported numeric range.");
    }

    private static VarResult failed(VarMethod method, int observations, int required, LocalDate from, LocalDate to,
                                    InvalidInput exception, List<Issue> issues) {
        issues.add(new Issue(exception.code, Severity.WARNING, "valueAtRisk." + method, null, to, exception.getMessage()));
        return blocked(method, UNAVAILABLE, observations, required, from, to, List.of(exception.code));
    }

    private static VarResult blocked(VarMethod method, ResultStatus status, int observations, int required,
                                     LocalDate from, LocalDate to, List<IssueCode> reasons) {
        return new VarResult(method, status, true, null, null, observations, required, from, to, reasons);
    }

    private static final class InvalidInput extends RuntimeException {
        private final IssueCode code;
        private InvalidInput(IssueCode code, String message) { super(message); this.code = code; }
    }
}
