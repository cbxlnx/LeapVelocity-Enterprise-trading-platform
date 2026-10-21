package com.leapvelocity.service;

import com.leapvelocity.dto.response.PortfolioRiskDto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/** Model comparisons with frozen Java formulas. NumPy uses float64 and its own seeded stream. */
class PythonRiskParityTest {
    @ParameterizedTest
    @CsvSource({".90,1", ".95,1", ".99,1", ".950000000000001,1", ".90,2", ".95,2", ".99,2",
            ".90,3", ".95,3", ".99,3", ".90,10", ".95,10", ".99,10"})
    void deterministicModelsMatchAndSimulationAgreesWithinSamplingError(String confidence, int horizon) {
        compare(RiskParityFixtures.example(confidence, horizon));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-.01", ".001", "-.0000000000494"})
    void constantSeriesZerosAndZeroBenchmarkVarianceMatch(String value) {
        compare(RiskParityFixtures.fixture(Collections.nCopies(110, new BigDecimal(value)), ".95", 1));
    }

    @ParameterizedTest
    @CsvSource({"0,.99,10", "59,.95,1", "99,.95,1", "100,.95,10", "499,.99,1"})
    void emptyAndShortHistoriesKeepExactAvailability(int count, String confidence, int horizon) {
        compare(RiskParityFixtures.fixture(Collections.nCopies(count, BigDecimal.ZERO), confidence, horizon));
    }

    private void compare(PortfolioReturnPreparation.PreparedInputs input) {
        var core = new PortfolioRiskCalculator().calculate(input);
        var normal = new NormalModelVarCalculator().calculate(input);
        var actual = PythonRiskServer.client().calculate(input);
        scalar(core.metrics().annualisedVolatility(), actual.metrics().annualisedVolatility());
        scalar(core.metrics().maximumDrawdown(), actual.metrics().maximumDrawdown());
        scalar(core.metrics().beta(), actual.metrics().beta());
        var historical = actual.valueAtRisk().get(0);
        var parametric = actual.valueAtRisk().get(1);
        var simulated = actual.valueAtRisk().get(2);
        varContext(core.historicalVar(), historical);
        varContext(normal.values().get(0), parametric);
        varContext(normal.values().get(1), simulated);
        deterministic(core.historicalVar(), historical);
        deterministic(normal.values().get(0), parametric);
        if (simulated.status() == ResultStatus.READY) {
            // Six asymptotic standard errors of the normal quantile, plus final rounding.
            // The model reference is analytic; equality to Java's different RNG is no longer intended.
            double error = quantileError(input);
            assertEquals(parametric.lossPercent().doubleValue(), simulated.lossPercent().doubleValue(), error * 100 + 1e-8);
            assertEquals(parametric.lossAmount().doubleValue(), simulated.lossAmount().doubleValue(),
                    error * input.valuation().holdingsValue().doubleValue() + .010000001);
        }
        var issues = new java.util.ArrayList<>(core.issues());
        issues.addAll(normal.issues());
        assertEquals(issues, actual.issues());
    }

    private static void scalar(ScalarMetric expected, ScalarMetric actual) {
        assertEquals(new ScalarMetric(expected.status(), actual.value(), expected.unit(), expected.observations(),
                expected.requiredObservations(), expected.from(), expected.to(), expected.reasons()), actual);
        if (expected.value() == null) assertNull(actual.value());
        else assertEquals(expected.value().doubleValue(), actual.value().doubleValue(), 1.000001e-8);
    }

    private static void varContext(VarResult expected, VarResult actual) {
        assertEquals(new VarResult(expected.method(), expected.status(), expected.estimate(), actual.lossPercent(), actual.lossAmount(),
                expected.observations(), expected.requiredObservations(), expected.from(), expected.to(), expected.reasons()), actual);
        if (expected.status() != ResultStatus.READY) {
            assertNull(actual.lossPercent());
            assertNull(actual.lossAmount());
        }
    }

    private static void deterministic(VarResult expected, VarResult actual) {
        if (expected.status() != ResultStatus.READY) return;
        assertEquals(expected.lossPercent().doubleValue(), actual.lossPercent().doubleValue(), 1.000001e-8);
        assertEquals(expected.lossAmount().doubleValue(), actual.lossAmount().doubleValue(), .010000001);
    }

    private static double quantileError(PortfolioReturnPreparation.PreparedInputs input) {
        var returns = input.portfolio().returns();
        int horizon = input.parameters().holdingPeriodDays();
        double[] windows = new double[returns.size() - horizon + 1];
        for (int start = 0; start < windows.length; start++) {
            double growth = 1;
            for (int offset = 0; offset < horizon; offset++) growth *= 1 + returns.get(start + offset).value().doubleValue();
            windows[start] = growth - 1;
        }
        double mean = Arrays.stream(windows).average().orElseThrow();
        double deviation = Math.sqrt(Arrays.stream(windows).map(v -> (v - mean) * (v - mean)).sum() / (windows.length - 1));
        double confidence = input.parameters().confidenceLevel().doubleValue();
        double z = NormalModelVarCalculator.normalQuantile(input.parameters().confidenceLevel());
        double density = Math.exp(-z * z / 2) / Math.sqrt(2 * Math.PI);
        return 6 * deviation * Math.sqrt(confidence * (1 - confidence) / 100000) / density;
    }

    @Test
    void numpySeededExampleIsRepeatableThroughHttp() {
        var input = RiskParityFixtures.example(".95", 1);
        var first = PythonRiskServer.client().calculate(input);
        var second = PythonRiskServer.client().calculate(input);
        assertEquals(first.metrics(), second.metrics());
        assertEquals(first.valueAtRisk(), second.valueAtRisk());
        assertEquals(new BigDecimal("1.91601270"), first.valueAtRisk().get(2).lossPercent());
        assertEquals(new BigDecimal("1916.01"), first.valueAtRisk().get(2).lossAmount());
    }
}
