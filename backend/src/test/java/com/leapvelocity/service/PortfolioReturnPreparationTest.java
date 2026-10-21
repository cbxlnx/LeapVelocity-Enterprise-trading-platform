package com.leapvelocity.service;

import com.leapvelocity.dto.request.RiskRequestDto;
import com.leapvelocity.dto.response.PortfolioHoldingDto;
import com.leapvelocity.dto.response.PortfolioRiskDto.IssueCode;
import com.leapvelocity.dto.response.PortfolioSummaryDto;
import com.leapvelocity.dto.response.PortfolioSummaryDto.ValuationStatus;
import com.leapvelocity.repository.HistoricalPriceRepository.AdjustedPrice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.leapvelocity.dto.response.PortfolioRiskDto.IssueCode.*;
import static com.leapvelocity.dto.response.PortfolioRiskDto.ResultStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class PortfolioReturnPreparationTest {
    private final LocalDate end = LocalDate.parse("2026-10-16");
    private final LocalDate calculationDate = LocalDate.parse("2026-10-19");
    private final RiskRequestDto defaults = RiskRequestDto.defaults();

    @Test
    void preparesAlignedUnroundedReturnsWithDatesAndIndependentEligibility() {
        var rows = history(defaults);
        var result = prepare(rows, defaults);
        int prices = days(defaults).size();
        assertEquals(READY, result.valuationStatus());
        assertEquals(READY, result.portfolio().status());
        assertEquals(READY, result.core().status());
        assertEquals(READY, result.beta().status());
        assertEquals(READY, result.historicalVar().status());
        assertEquals(prices, result.history().priceObservations());
        assertEquals(prices - 1, result.history().returnObservations());
        assertEquals(prices - 1, result.history().benchmarkReturnObservations());
        assertEquals(end.minusYears(1), result.history().requestedFrom());
        assertEquals(end, result.history().requestedTo());
        assertEquals(days(defaults).get(0), result.history().from());
        assertEquals(end, result.history().to());
        number("1", result.portfolio().wealth().get(0).value());
        number("0.01", result.portfolio().returns().get(0).value());
        assertEquals(days(defaults).get(0), result.portfolio().returns().get(0).from());
        assertEquals(days(defaults).get(1), result.portfolio().returns().get(0).to());
        assertEquals(UsEquityTradingCalendar.VERSION, result.calendarVersion());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void sharesBuyAndHoldMathRatherThanRebalancingOrUsingSerializedWeights() {
        var options = options("1M", "0.95", 1);
        var rows = history(options);
        var dates = days(options);
        replace(rows, "AAPL", dates.get(0), "100");
        replace(rows, "AAPL", dates.get(1), "200");
        replace(rows, "AAPL", dates.get(2), "100");
        replace(rows, "MSFT", dates.get(0), "200");
        replace(rows, "MSFT", dates.get(1), "200");
        replace(rows, "MSFT", dates.get(2), "400");
        var result = prepare(rows, options);
        number("1.6", result.portfolio().wealth().get(1).value());
        number("1.4", result.portfolio().wealth().get(2).value());
        number("0.6", result.portfolio().returns().get(0).value());
        number("-0.125", result.portfolio().returns().get(1).value());
        assertEquals(UNAVAILABLE, result.core().status()); // Short complete history remains available as inputs.
        assertTrue(result.core().reasons().contains(INSUFFICIENT_HISTORY));
    }

    @Test
    void doesNotRoundInputsToTheChartResponseScale() {
        var options = options("1M", "0.95", 1);
        var rows = history(options);
        replace(rows, "AAPL", days(options).get(0), "7");
        replace(rows, "AAPL", days(options).get(1), "8");
        var result = prepare(rows, options);
        var value = result.portfolio().returns().get(0).value();
        assertTrue(value.scale() > 8);
        assertTrue(value.compareTo(new BigDecimal("0.08971428")) > 0);
        assertTrue(value.compareTo(new BigDecimal("0.08971429")) < 0);
    }

    @Test
    void fixedSnapshotsAreRepeatableIndependentOfRowOrderAndOutputsAreImmutable() {
        var rows = history(defaults);
        var copy = List.copyOf(rows);
        var expected = prepare(rows, defaults);
        assertEquals(copy, rows);
        Collections.shuffle(rows, new Random(12));
        assertEquals(expected, prepare(rows, defaults));
        assertEquals(expected, prepare(rows, defaults));
        assertThrows(UnsupportedOperationException.class, () -> expected.portfolio().wealth().clear());
        assertThrows(UnsupportedOperationException.class, () -> expected.pairedReturns().clear());
        assertThrows(UnsupportedOperationException.class, () -> expected.portfolio().returns().clear());
        assertThrows(UnsupportedOperationException.class, () -> expected.issues().clear());
    }

    @Test
    void emptyAndZeroQuantityPortfoliosNeedNoPricesOrCalendar() {
        var empty = new PortfolioSummaryDto(1L, "USD", null, ValuationStatus.EMPTY,
                BigDecimal.ZERO, new BigDecimal("500"), new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of());
        var result = PortfolioReturnPreparation.prepare(empty, defaults, calculationDate, List.of(), List.of());
        assertEquals(EMPTY, result.valuationStatus());
        assertEquals(EMPTY, result.portfolio().status());
        assertEquals(EMPTY, result.historicalVar().status());
        assertNull(result.history().from());
        assertNull(result.history().requestedFrom());
        assertEquals(List.of(EMPTY_PORTFOLIO), result.core().reasons());
    }

    @Test
    void missingHoldingsAreNotDroppedOrRenormalized() {
        var rows = history(defaults);
        rows.removeIf(row -> row.symbol().equals("MSFT"));
        var result = prepare(rows, defaults);
        assertTrue(has(result, MISSING_PRICE, "history", "MSFT"));
        assertEquals(UNAVAILABLE, result.portfolio().status());
        assertTrue(result.portfolio().returns().isEmpty());
        assertEquals(UNAVAILABLE, result.core().status());
        assertEquals(READY, result.valuationStatus());
    }

    @Test
    void disjointHistoriesReportNoCommonDates() {
        var rows = history(defaults);
        var split = days(defaults).get(days(defaults).size() / 2);
        rows.removeIf(row -> row.symbol().equals("AAPL") && !row.date().isBefore(split)
                || row.symbol().equals("MSFT") && row.date().isBefore(split));
        var result = prepare(rows, defaults);
        assertTrue(has(result, NO_COMMON_DATES, "history", null));
        assertEquals(0, result.history().priceObservations());
        assertTrue(result.portfolio().wealth().isEmpty());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1"})
    void invalidAdjustedPricesAreExplicit(String price) {
        var rows = history(defaults);
        replace(rows, "AAPL", days(defaults).get(10), price);
        var result = prepare(rows, defaults);
        assertTrue(has(result, INVALID_PRICE, "history", "AAPL"));
        assertEquals(UNAVAILABLE, result.portfolio().status());
        assertTrue(result.portfolio().returns().isEmpty());
    }

    @Test
    void aSessionMissingFromEverySymbolStillCannotBeBridged() {
        var rows = history(defaults);
        LocalDate missing = days(defaults).get(50);
        rows.removeIf(row -> row.date().equals(missing));
        var result = prepare(rows, defaults);
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == INCOMPLETE_HISTORY && missing.equals(issue.date())));
        assertEquals(UNAVAILABLE, result.core().status());
        assertEquals(UNAVAILABLE, result.historicalVar().status());
        assertEquals(0, result.history().returnObservations());
        assertEquals(days(defaults).size() - 1, result.history().priceObservations());
    }

    @Test
    void absentOrGappedBenchmarkNeverChangesThePortfolioPath() {
        var complete = prepare(history(defaults), defaults);
        for (int missingIndex : List.of(-1, 0, 20)) {
            boolean absent = missingIndex < 0;
            var rows = history(defaults);
            rows.removeIf(row -> row.symbol().equals("SPY") && (absent || row.date().equals(days(defaults).get(missingIndex))));
            var result = prepare(rows, defaults);
            assertEquals(complete.portfolio(), result.portfolio());
            assertEquals(complete.history().from(), result.history().from());
            assertEquals(READY, result.core().status());
            assertEquals(READY, result.historicalVar().status());
            assertEquals(UNAVAILABLE, result.beta().status());
            assertEquals(0, result.history().benchmarkReturnObservations());
            assertTrue(has(result, absent ? MISSING_BENCHMARK : INCOMPLETE_HISTORY, "metrics.beta", "SPY"));
        }
    }

    @Test
    void invalidOrStaleBenchmarkIsIsolatedFromHoldings() {
        var rows = history(defaults);
        replace(rows, "SPY", days(defaults).get(10), "0");
        var invalid = prepare(rows, defaults);
        assertTrue(has(invalid, INVALID_PRICE, "metrics.beta", "SPY"));
        assertEquals(READY, invalid.core().status());
        assertEquals(UNAVAILABLE, invalid.beta().status());
        rows = history(defaults);
        rows.removeIf(row -> row.symbol().equals("SPY") && row.date().isAfter(end.minusDays(8)));
        var stale = prepare(rows, defaults);
        assertTrue(has(stale, STALE_PRICE, "metrics.beta", "SPY"));
        assertEquals(READY, stale.portfolio().status());
        assertEquals(UNAVAILABLE, stale.beta().status());
    }

    @Test
    void staleValuationFailsClosedAndTheAgeLimitIsInclusive() {
        var atLimit = PortfolioReturnPreparation.prepare(summary(end), defaults, end.plusDays(7), history(defaults), List.of());
        assertEquals(READY, atLimit.valuationStatus());
        var stale = PortfolioReturnPreparation.prepare(summary(end), defaults, end.plusDays(8), history(defaults), List.of());
        assertEquals(UNAVAILABLE, stale.valuationStatus());
        assertTrue(has(stale, STALE_PRICE, "valuation", null));
        assertTrue(stale.portfolio().returns().isEmpty());
    }

    @Test
    void staleHoldingHistoryIsReportedInAdditionToItsMissingSessions() {
        var rows = history(defaults);
        rows.removeIf(row -> row.symbol().equals("AAPL") && row.date().isAfter(end.minusDays(8)));
        var result = prepare(rows, defaults);
        assertTrue(has(result, STALE_PRICE, "history", "AAPL"));
        assertTrue(has(result, INCOMPLETE_HISTORY, "history", "AAPL"));
        assertTrue(result.portfolio().returns().isEmpty());
    }

    @Test
    void futureSameDayOrNonSessionValuationIsInvalid() {
        for (LocalDate day : List.of(calculationDate, calculationDate.plusDays(1), LocalDate.parse("2026-10-17"))) {
            var result = PortfolioReturnPreparation.prepare(summary(day), defaults, calculationDate, List.of(), List.of());
            assertEquals(UNAVAILABLE, result.valuationStatus());
            assertTrue(has(result, INVALID_PRICE, "valuation", null));
        }
    }

    @Test
    void unsupportedCalendarRangeIsExplicitRatherThanInventingSessions() {
        var oldEnd = LocalDate.parse("2023-10-16");
        var result = PortfolioReturnPreparation.prepare(summary(oldEnd), defaults, oldEnd.plusDays(1),
                List.of(bar("AAPL", oldEnd, "100"), bar("MSFT", oldEnd, "200")), List.of());
        assertEquals(READY, result.valuationStatus());
        assertEquals(UNAVAILABLE, result.core().status());
        assertTrue(has(result, TRADING_CALENDAR_UNAVAILABLE, "history", null));
        assertTrue(result.portfolio().returns().isEmpty());
    }

    @Test
    void shortCompleteWindowRetainsInputsButReportsMetricSpecificInsufficiency() {
        var options = options("1M", "0.99", 10);
        var result = prepare(history(options), options);
        assertEquals(READY, result.portfolio().status());
        assertEquals(UNAVAILABLE, result.core().status());
        assertEquals(60, result.core().requiredObservations());
        assertEquals(List.of(INSUFFICIENT_HISTORY), result.core().reasons());
        assertEquals(500, result.historicalVar().requiredObservations());
        assertEquals(Math.max(0, result.portfolio().returns().size() - 10 + 1), result.historicalVar().observations());
        assertTrue(has(result, INSUFFICIENT_HISTORY, "valueAtRisk.HISTORICAL", null));
        assertEquals(100, PortfolioReturnPreparation.minimumVarObservations(new BigDecimal("0.90")));
        assertEquals(100, PortfolioReturnPreparation.minimumVarObservations(new BigDecimal("0.95")));
        assertEquals(200, PortfolioReturnPreparation.minimumVarObservations(new BigDecimal("0.975")));
        assertEquals(500, PortfolioReturnPreparation.minimumVarObservations(new BigDecimal("0.99")));
    }

    @Test
    void coreInputsCanBeEligibleWhileHistoricalVarStillHasInsufficientWindows() {
        var options = options("3M", "0.95", 1);
        var result = prepare(history(options), options);
        assertEquals(READY, result.core().status());
        assertEquals(READY, result.beta().status());
        assertEquals(UNAVAILABLE, result.historicalVar().status());
        assertEquals(List.of(INSUFFICIENT_HISTORY), result.historicalVar().reasons());
        assertTrue(result.historicalVar().observations() < result.historicalVar().requiredObservations());
        assertFalse(result.portfolio().returns().isEmpty());
    }

    @Test
    void duplicateOrNonSessionBarsDoNotProduceOrderDependentReturns() {
        var rows = history(defaults);
        rows.add(bar("AAPL", days(defaults).get(5), "900"));
        var first = prepare(rows, defaults);
        Collections.reverse(rows);
        assertEquals(first, prepare(rows, defaults));
        assertTrue(has(first, INVALID_PRICE, "history", "AAPL"));
        assertTrue(first.portfolio().returns().isEmpty());
        rows = history(defaults);
        rows.add(bar("AAPL", LocalDate.parse("2026-10-10"), "100"));
        assertTrue(has(prepare(rows, defaults), INVALID_PRICE, "history", "AAPL"));
    }

    @Test
    void malformedPriceObservationsAreQualityIssues() {
        var rows = history(defaults);
        rows.add(new AdjustedPrice("AAPL", null, BigDecimal.ONE));
        var result = prepare(rows, defaults);
        assertTrue(has(result, INVALID_PRICE, "history", null));
        assertTrue(result.portfolio().returns().isEmpty());
    }

    @Test
    void invalidDenominatorDoesNotThrowAnArithmeticException() {
        var valid = summary(end);
        var invalid = new PortfolioSummaryDto(1L, "USD", end, ValuationStatus.READY, BigDecimal.ZERO,
                valid.cashBalance(), valid.totalEquity(), valid.costBasis(), valid.unrealizedPnL(), valid.holdings(), List.of());
        var result = PortfolioReturnPreparation.prepare(invalid, defaults, calculationDate, history(defaults), List.of());
        assertEquals(UNAVAILABLE, result.valuationStatus());
        assertTrue(has(result, INVALID_POSITION, "valuation", null));
    }

    @Test
    void periodBoundsClampLeapDaysAndRejectUnsupportedPeriods() {
        assertEquals(LocalDate.parse("2023-02-28"), PortfolioReturnPreparation.lookbackStart(LocalDate.parse("2024-02-29"), "1Y"));
        assertThrows(IllegalArgumentException.class, () -> PortfolioReturnPreparation.lookbackStart(end, "ALL"));
    }

    private PortfolioReturnPreparation.PreparedInputs prepare(List<AdjustedPrice> rows, RiskRequestDto options) {
        return PortfolioReturnPreparation.prepare(summary(end), options, calculationDate, rows, List.of());
    }

    private PortfolioSummaryDto summary(LocalDate day) {
        return new PortfolioSummaryDto(1L, "USD", day, ValuationStatus.READY,
                new BigDecimal("100"), new BigDecimal("500"), new BigDecimal("600"), new BigDecimal("20"), new BigDecimal("80"),
                List.of(holding("AAPL", "60"), holding("MSFT", "40")), List.of());
    }

    private PortfolioHoldingDto holding(String symbol, String value) {
        var amount = new BigDecimal(value);
        return new PortfolioHoldingDto(symbol, "USD", BigDecimal.ONE, BigDecimal.TEN, amount, amount, BigDecimal.TEN,
                amount.subtract(BigDecimal.TEN), new BigDecimal("0.333333333333"));
    }

    private RiskRequestDto options(String period, String confidence, int holdingPeriod) {
        return new RiskRequestDto(null, period, new BigDecimal(confidence), holdingPeriod, null, null);
    }

    private List<LocalDate> days(RiskRequestDto options) {
        return UsEquityTradingCalendar.sessions(PortfolioReturnPreparation.lookbackStart(end, options.period()), end).orElseThrow();
    }

    private List<AdjustedPrice> history(RiskRequestDto options) {
        List<AdjustedPrice> rows = new ArrayList<>();
        var dates = days(options);
        for (int i = 0; i < dates.size(); i++) {
            rows.add(bar("AAPL", dates.get(i), String.valueOf(100 + i)));
            rows.add(bar("MSFT", dates.get(i), String.valueOf(200 + 2 * i)));
            rows.add(bar("SPY", dates.get(i), String.valueOf(500 + i)));
        }
        return rows;
    }

    private void replace(List<AdjustedPrice> rows, String symbol, LocalDate date, String value) {
        rows.removeIf(row -> row.symbol().equals(symbol) && row.date().equals(date));
        rows.add(bar(symbol, date, value));
    }

    private AdjustedPrice bar(String symbol, LocalDate day, String value) {
        return new AdjustedPrice(symbol, day, value == null ? null : new BigDecimal(value));
    }

    private boolean has(PortfolioReturnPreparation.PreparedInputs result, IssueCode code, String scope, String symbol) {
        return result.issues().stream().anyMatch(issue -> issue.code() == code && issue.scope().equals(scope)
                && java.util.Objects.equals(symbol, issue.symbol()));
    }

    private void number(String expected, BigDecimal value) {
        assertNotNull(value);
        assertEquals(0, new BigDecimal(expected).compareTo(value));
    }
}
