package com.leapvelocity.service;

import com.leapvelocity.dto.request.RiskRequestDto;
import com.leapvelocity.dto.response.PortfolioRiskDto.Assumptions;
import com.leapvelocity.dto.response.PortfolioRiskDto.CoreAssessment;
import com.leapvelocity.dto.response.PortfolioRiskDto.Unit;
import com.leapvelocity.dto.response.PortfolioRiskDto.RiskStatus;
import com.leapvelocity.dto.response.PortfolioRiskDto.VarMethod;
import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.AccountStatus;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.HistoricalPriceRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.repository.HistoricalPriceRepository.AdjustedPrice;
import com.leapvelocity.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.leapvelocity.dto.response.PortfolioRiskDto.IssueCode.*;
import static com.leapvelocity.dto.response.PortfolioRiskDto.ResultStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PortfolioRiskInputsServiceTest {
    private AccountRepository accounts;
    private PositionRepository positions;
    private HistoricalPriceRepository prices;
    private InstrumentRepository instruments;
    private PortfolioAnalyticsService service;
    private final LocalDate end = LocalDate.parse("2026-10-16");
    private final LocalDate today = LocalDate.parse("2026-10-19");
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        positions = mock(PositionRepository.class);
        prices = mock(HistoricalPriceRepository.class);
        instruments = mock(InstrumentRepository.class);
        when(instruments.findBySymbol("SPY")).thenReturn(Optional.of(new Instrument("SPY", "Benchmark", "FUND", "USD", true)));
        service = new PortfolioAnalyticsService(new PortfolioSnapshotService(accounts, positions, prices, instruments), PythonRiskServer.client(), clock);
        when(accounts.findById(1L)).thenReturn(Optional.of(new Account("EXAMPLE", "Test", new BigDecimal("500"), AccountStatus.ACTIVE)));
        when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of(position("AAPL", "1", "10", "USD", "EQUITY")));
        when(prices.findLatestCommonDate(List.of("AAPL"), today)).thenReturn(end);
        when(prices.findClosesOnDate(List.of("AAPL"), end)).thenReturn(Map.of("AAPL", new BigDecimal("100")));
        when(prices.findAdjustedPrices(anyList(), any(LocalDate.class), eq(end))).thenReturn(history("AAPL"));
    }

    @Test
    void reusesASingleValuationSnapshotAndTheExistingAdjustedPriceQuery() {
        var result = service.prepareRiskInputs(1L, null);
        assertEquals(RiskRequestDto.defaults(), result.parameters());
        assertEquals(today, result.calculationDate());
        assertEquals(READY, result.core().status());
        assertEquals(READY, result.beta().status());
        assertEquals(0, new BigDecimal("100").compareTo(result.valuation().holdingsValue()));
        verify(accounts).findById(1L);
        verify(positions).findByAccountIdOrderBySymbolAsc(1L);
        verify(prices).findLatestCommonDate(List.of("AAPL"), today);
        verify(prices).findAdjustedPrices(List.of("AAPL", "SPY"), end.minusYears(1), end);
        assertEquals(result, service.prepareRiskInputs(1L, null));
    }

    @Test
    void zeroOrEmptyHoldingsDoNotFetchHistoryOrBenchmark() {
        when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of(position("AAPL", "0", "10", "USD", "EQUITY")));
        var result = service.prepareRiskInputs(1L, RiskRequestDto.defaults());
        assertEquals(EMPTY, result.valuationStatus());
        assertTrue(result.portfolio().returns().isEmpty());
        verifyNoInteractions(prices);
    }

    @Test
    void staleCurrentValuationDoesNotTriggerAHistoricalQueryOrInventMissingHistory() {
        var staleDate = end.minusDays(30);
        when(prices.findLatestCommonDate(anyList(), eq(today))).thenReturn(staleDate);
        when(prices.findClosesOnDate(anyList(), eq(staleDate))).thenReturn(Map.of("AAPL", BigDecimal.TEN));
        var result = service.prepareRiskInputs(1L, RiskRequestDto.defaults());
        assertEquals(UNAVAILABLE, result.valuationStatus());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == STALE_PRICE));
        assertFalse(result.issues().stream().anyMatch(issue -> issue.code() == MISSING_PRICE));
        verify(prices, never()).findAdjustedPrices(anyList(), any(), any());
    }

    @Test
    void missingCurrentPricesHaveStructuredSymbolIssues() {
        when(prices.findLatestCommonDate(anyList(), eq(today))).thenReturn(null);
        when(prices.findSymbolsWithHistoryBefore(anyList(), eq(today))).thenReturn(Set.of());
        var result = service.prepareRiskInputs(1L, RiskRequestDto.defaults());
        assertEquals(UNAVAILABLE, result.valuationStatus());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == MISSING_PRICE && "AAPL".equals(issue.symbol())));
        verify(prices, never()).findAdjustedPrices(anyList(), any(), any());
    }

    @Test
    void noCommonValuationDateIsDistinctFromAMissingSymbol() {
        when(prices.findLatestCommonDate(anyList(), eq(today))).thenReturn(null);
        when(prices.findSymbolsWithHistoryBefore(anyList(), eq(today))).thenReturn(Set.of("AAPL"));
        var result = service.prepareRiskInputs(1L, RiskRequestDto.defaults());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == NO_COMMON_DATES));
        assertFalse(result.issues().stream().anyMatch(issue -> issue.code() == MISSING_PRICE));
    }

    @Test
    void unsupportedPositionsAndMetadataDoNotReachMarketHistory() {
        for (var holding : List.of(position("AAPL", "-1", "10", "USD", "EQUITY"),
                position("AAPL", "1", "10", "EUR", "EQUITY"), position("AAPL", "1", "10", "USD", "CASH"))) {
            when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of(holding));
            var result = service.prepareRiskInputs(1L, RiskRequestDto.defaults());
            assertEquals(UNAVAILABLE, result.valuationStatus());
            assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == UNSUPPORTED_POSITION || issue.code() == UNSUPPORTED_CURRENCY));
        }
        verifyNoInteractions(prices);
    }

    @Test
    void absentInstrumentMetadataIsNotReportedAsAPriceFailure() {
        var holding = position("AAPL", "1", "10", "USD", "EQUITY");
        holding.setInstrument(null);
        when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of(holding));
        var result = service.prepareRiskInputs(1L, RiskRequestDto.defaults());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == MISSING_INSTRUMENT_METADATA));
        verifyNoInteractions(prices);
    }

    @Test
    void invalidCloseAndNegativeCashAreUnavailableWithoutArithmeticFailures() {
        when(prices.findClosesOnDate(anyList(), eq(end))).thenReturn(Map.of("AAPL", BigDecimal.ZERO));
        var invalid = service.prepareRiskInputs(1L, RiskRequestDto.defaults());
        assertTrue(invalid.issues().stream().anyMatch(issue -> issue.code() == INVALID_PRICE));
        when(accounts.findById(1L)).thenReturn(Optional.of(new Account("EXAMPLE", "Test", BigDecimal.ONE.negate(), AccountStatus.ACTIVE)));
        var negativeCash = service.prepareRiskInputs(1L, RiskRequestDto.defaults());
        assertTrue(negativeCash.issues().stream().anyMatch(issue -> issue.code() == INVALID_POSITION));
        verify(prices, never()).findAdjustedPrices(anyList(), any(), any());
    }

    @Test
    void calculationDateUsesNewYorkRatherThanHostOrUtcDate() {
        var afterUtcMidnight = Clock.fixed(Instant.parse("2026-10-19T01:00:00Z"), ZoneOffset.UTC);
        service = new PortfolioAnalyticsService(new PortfolioSnapshotService(accounts, positions, prices, instruments), PythonRiskServer.client(), afterUtcMidnight);
        var newYorkDate = LocalDate.parse("2026-10-18");
        when(prices.findLatestCommonDate(anyList(), eq(newYorkDate))).thenReturn(end);
        var result = service.prepareRiskInputs(1L, RiskRequestDto.defaults());
        assertEquals(newYorkDate, result.calculationDate());
        verify(prices).findLatestCommonDate(List.of("AAPL"), newYorkDate);
    }

    @Test
    void benchmarkAlreadyHeldIsQueriedOnceAndUsesTheSamePriceReturns() {
        when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of(position("SPY", "1", "10", "USD", "FUND")));
        when(prices.findLatestCommonDate(List.of("SPY"), today)).thenReturn(end);
        when(prices.findClosesOnDate(List.of("SPY"), end)).thenReturn(Map.of("SPY", new BigDecimal("100")));
        when(prices.findAdjustedPrices(List.of("SPY"), end.minusYears(1), end)).thenReturn(history("SPY"));
        var result = service.prepareRiskInputs(1L, RiskRequestDto.defaults());
        verify(prices).findAdjustedPrices(List.of("SPY"), end.minusYears(1), end);
        assertEquals(READY, result.beta().status());
        result.pairedReturns().forEach(pair -> assertEquals(0, pair.portfolio().compareTo(pair.benchmark())));
    }

    @Test
    void missingAccountDoesNotReadPositionsOrPrices() {
        when(accounts.findById(1L)).thenReturn(Optional.empty());
        assertThrows(AccountNotFoundException.class, () -> service.prepareRiskInputs(1L, RiskRequestDto.defaults()));
        verifyNoInteractions(positions, prices);
    }

    @Test
    void coreAssessmentUsesOneSnapshotAndEchoesItsPoliciesDatesAndBenchmark() {
        var options = new RiskRequestDto(null, "3M", null, null, "QQQ", null);
        when(instruments.findBySymbol("QQQ")).thenReturn(Optional.of(new Instrument("QQQ", "Benchmark", "FUND", "USD", true)));
        when(prices.findAdjustedPrices(anyList(), any(LocalDate.class), eq(end))).thenReturn(history("AAPL").stream()
                .map(row -> row.symbol().equals("SPY") ? new AdjustedPrice("QQQ", row.date(), row.adjustedClose()) : row).toList());
        var result = service.calculateCoreRisk(1L, options);
        assertEquals("1.0", result.schemaVersion());
        assertEquals(clock.instant(), result.calculatedAt());
        assertEquals(today, result.calculationDate());
        assertEquals(UsEquityTradingCalendar.VERSION, result.calendarVersion());
        assertEquals(options, result.parameters());
        assertEquals(Assumptions.mvp(), result.assumptions());
        assertEquals(new BigDecimal("500"), result.valuation().cashBalance());
        var volatility = result.metrics().annualisedVolatility();
        assertEquals(READY, volatility.status());
        assertEquals(Unit.PERCENT, volatility.unit());
        assertEquals(end.minusMonths(3), volatility.from());
        assertEquals(end, volatility.to());
        assertEquals(result.history().returnObservations(), volatility.observations());
        assertEquals(60, volatility.requiredObservations());
        assertEquals(READY, result.metrics().maximumDrawdown().status());
        assertEquals(READY, result.metrics().beta().status());
        assertEquals(Unit.DIMENSIONLESS, result.metrics().beta().unit());
        assertEquals(volatility.from(), result.metrics().beta().from());
        verify(accounts).findById(1L);
        verify(positions).findByAccountIdOrderBySymbolAsc(1L);
        verify(prices).findLatestCommonDate(List.of("AAPL"), today);
        verify(prices).findAdjustedPrices(List.of("AAPL", "QQQ"), end.minusMonths(3), end);
    }

    @Test
    void flatHoldingsRemainReadyWhenZeroBenchmarkVarianceMakesBetaUnavailableAndSerializeExplicitNulls() {
        when(prices.findAdjustedPrices(anyList(), any(), any())).thenReturn(history("AAPL").stream()
                .map(row -> new AdjustedPrice(row.symbol(), row.date(), BigDecimal.TEN)).toList());
        var result = service.calculateCoreRisk(1L, null);
        assertEquals(READY, result.metrics().annualisedVolatility().status());
        assertEquals(0, result.metrics().annualisedVolatility().value().signum());
        assertEquals(READY, result.metrics().maximumDrawdown().status());
        assertEquals(0, result.metrics().maximumDrawdown().value().signum());
        assertEquals(UNAVAILABLE, result.metrics().beta().status());
        assertEquals(List.of(ZERO_BENCHMARK_VARIANCE), result.metrics().beta().reasons());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == ZERO_BENCHMARK_VARIANCE
                && issue.scope().equals("metrics.beta") && end.equals(issue.date())));
        var json = JsonMapper.builder().build();
        var serialized = json.writeValueAsString(result);
        var tree = json.readTree(serialized);
        assertTrue(tree.at("/metrics/beta/value").isNull());
        assertEquals("DIMENSIONLESS", tree.at("/metrics/beta/unit").asString());
        assertEquals("PERCENT", tree.at("/metrics/annualisedVolatility/unit").asString());
        assertEquals(result, json.readValue(serialized, CoreAssessment.class));
    }

    @Test
    void emptyCoreAssessmentPreservesCashAndHasNoRiskValuesOrDates() {
        when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of());
        var result = service.calculateCoreRisk(1L, null);
        assertEquals(new BigDecimal("500"), result.valuation().cashBalance());
        for (var metric : List.of(result.metrics().annualisedVolatility(), result.metrics().maximumDrawdown(), result.metrics().beta())) {
            assertEquals(EMPTY, metric.status());
            assertNull(metric.value());
            assertNull(metric.from());
            assertNull(metric.to());
            assertEquals(0, metric.observations());
            assertEquals(List.of(EMPTY_PORTFOLIO), metric.reasons());
        }
        verifyNoInteractions(prices);
    }

    @Test
    void shortCompleteWindowsReportCountsAndDatesWithoutInventingMetrics() {
        var options = new RiskRequestDto(null, "1M", null, null, null, null);
        var result = service.calculateCoreRisk(1L, options);
        for (var metric : List.of(result.metrics().annualisedVolatility(), result.metrics().maximumDrawdown(), result.metrics().beta())) {
            assertEquals(UNAVAILABLE, metric.status());
            assertNull(metric.value());
            assertEquals(end.minusMonths(1), metric.from());
            assertEquals(end, metric.to());
            assertEquals(result.history().returnObservations(), metric.observations());
            assertEquals(60, metric.requiredObservations());
            assertEquals(List.of(INSUFFICIENT_HISTORY), metric.reasons());
        }
    }

    @Test
    void absentBenchmarkLeavesCalculatedPortfolioMetricsAvailable() {
        when(prices.findAdjustedPrices(anyList(), any(), any())).thenReturn(history("AAPL").stream()
                .filter(row -> row.symbol().equals("AAPL")).toList());
        var result = service.calculateCoreRisk(1L, null);
        assertEquals(READY, result.metrics().annualisedVolatility().status());
        assertEquals(READY, result.metrics().maximumDrawdown().status());
        assertEquals(UNAVAILABLE, result.metrics().beta().status());
        assertNull(result.metrics().beta().value());
        assertEquals(List.of(MISSING_BENCHMARK), result.metrics().beta().reasons());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == MISSING_BENCHMARK));
    }

    @Test
    void staleValuationPropagatesItsReasonWithoutRequestingHistory() {
        var old = end.minusDays(30);
        when(prices.findLatestCommonDate(anyList(), eq(today))).thenReturn(old);
        when(prices.findClosesOnDate(anyList(), eq(old))).thenReturn(Map.of("AAPL", BigDecimal.TEN));
        var result = service.calculateCoreRisk(1L, null);
        for (var metric : List.of(result.metrics().annualisedVolatility(), result.metrics().maximumDrawdown(), result.metrics().beta())) {
            assertEquals(UNAVAILABLE, metric.status());
            assertNull(metric.value());
            assertTrue(metric.reasons().contains(STALE_PRICE));
        }
        verify(prices, never()).findAdjustedPrices(anyList(), any(), any());
    }

    @Test
    void timestampAndCalculationDateUseTheSameClockReadAcrossNewYorkMidnight() {
        var changingClock = mock(Clock.class);
        var justBeforeMidnight = Instant.parse("2026-10-19T03:59:59.999Z");
        when(changingClock.instant()).thenReturn(justBeforeMidnight, Instant.parse("2026-10-19T04:00:00Z"));
        service = new PortfolioAnalyticsService(new PortfolioSnapshotService(accounts, positions, prices, instruments), PythonRiskServer.client(), changingClock);
        var newYorkDate = LocalDate.parse("2026-10-18");
        when(prices.findLatestCommonDate(anyList(), eq(newYorkDate))).thenReturn(end);
        var result = service.calculateCoreRisk(1L, null);
        assertEquals(justBeforeMidnight, result.calculatedAt());
        assertEquals(newYorkDate, result.calculationDate());
        verify(changingClock).instant();
        verifyNoMoreInteractions(changingClock);
        verify(prices).findLatestCommonDate(List.of("AAPL"), newYorkDate);
    }

    @Test
    void benchmarkSelectionRequiresKnownUsdNonCashMetadataEvenWhenNoHistoryExists() {
        when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of());
        for (var symbol : List.of("UNKNOWN", "EURO", "CASHUSD")) {
            if (!symbol.equals("UNKNOWN")) when(instruments.findBySymbol(symbol)).thenReturn(Optional.of(
                    new Instrument(symbol, "Invalid benchmark", symbol.equals("CASHUSD") ? "CASH" : "EQUITY",
                            symbol.equals("EURO") ? "EUR" : "USD", false)));
            var options = new RiskRequestDto(null, null, null, null, symbol, null);
            assertThrows(IllegalArgumentException.class, () -> service.calculateCoreRisk(1L, options));
        }
        verifyNoInteractions(prices);
    }

    @Test
    void authorizedMissingCoreAccountReturns404BeforeBenchmarkValidation() {
        when(accounts.findById(1L)).thenReturn(Optional.empty());
        assertThrows(AccountNotFoundException.class, () -> service.calculateCoreRisk(1L, null));
        verifyNoInteractions(instruments, positions, prices);
    }

    @Test
    void fullResponseCalculatesAllThreeVarMethodsFromOneSnapshot() {
        var result = service.getRisk(1L, null);
        assertEquals(RiskStatus.READY, result.riskStatus());
        assertEquals(READY, result.metrics().annualisedVolatility().status());
        assertEquals(READY, result.metrics().maximumDrawdown().status());
        assertEquals(READY, result.metrics().beta().status());
        assertEquals(List.of(VarMethod.HISTORICAL, VarMethod.PARAMETRIC, VarMethod.MONTE_CARLO),
                result.valueAtRisk().stream().map(value -> value.method()).toList());
        var historical = result.valueAtRisk().get(0);
        assertEquals(READY, historical.status());
        assertEquals(0, historical.lossPercent().signum());
        assertEquals(0, historical.lossAmount().signum());
        assertEquals(251, historical.observations());
        assertEquals(100, historical.requiredObservations());
        assertTrue(historical.estimate());
        assertFalse(result.issues().stream().anyMatch(issue -> issue.code() == METHOD_NOT_IMPLEMENTED));
        result.valueAtRisk().stream().skip(1).forEach(value -> {
            assertEquals(READY, value.status());
            assertTrue(value.estimate());
            assertNotNull(value.lossPercent());
            assertNotNull(value.lossAmount());
            assertTrue(value.lossAmount().signum() >= 0);
            assertEquals(251, value.observations());
            assertEquals(100, value.requiredObservations());
            assertEquals(historical.from(), value.from());
            assertEquals(historical.to(), value.to());
            assertTrue(value.reasons().isEmpty());
        });
        verify(accounts).findById(1L);
        verify(positions).findByAccountIdOrderBySymbolAsc(1L);
        verify(instruments).findBySymbol("SPY");
        var json = JsonMapper.builder().build();
        assertEquals(result, json.readValue(json.writeValueAsString(result), com.leapvelocity.dto.response.PortfolioRiskDto.class));
        assertEquals(result, service.getRisk(1L, null));
    }

    @Test
    void fullEmptyResponseHasEmptyRequiredResultsWithoutFabricatingZeroRisk() {
        when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of());
        var result = service.getRisk(1L, null);
        assertEquals(RiskStatus.EMPTY, result.riskStatus());
        assertEquals(EMPTY, result.valueAtRisk().get(0).status());
        assertEquals(EMPTY, result.valueAtRisk().get(1).status());
        assertEquals(EMPTY, result.valueAtRisk().get(2).status());
        assertNull(result.valueAtRisk().get(0).lossAmount());
        result.valueAtRisk().forEach(value -> {
            assertNull(value.lossAmount()); assertNull(value.lossPercent());
            assertEquals(List.of(EMPTY_PORTFOLIO), value.reasons());
        });
        verifyNoInteractions(prices);
    }

    @Test
    void fullResponseWithNoMarketHistoryIsUnavailableRatherThanAnException() {
        when(prices.findLatestCommonDate(anyList(), eq(today))).thenReturn(null);
        when(prices.findSymbolsWithHistoryBefore(anyList(), eq(today))).thenReturn(Set.of());
        var result = service.getRisk(1L, null);
        assertEquals(RiskStatus.UNAVAILABLE, result.riskStatus());
        assertEquals(UNAVAILABLE, result.metrics().annualisedVolatility().status());
        assertNull(result.metrics().annualisedVolatility().value());
        assertTrue(result.metrics().annualisedVolatility().reasons().contains(MISSING_PRICE));
        assertEquals(UNAVAILABLE, result.valueAtRisk().get(0).status());
        assertTrue(result.valueAtRisk().get(0).reasons().contains(MISSING_PRICE));
        result.valueAtRisk().forEach(value -> {
            assertEquals(UNAVAILABLE, value.status()); assertNull(value.lossAmount());
            assertTrue(value.reasons().contains(MISSING_PRICE));
        });
        verify(prices, never()).findAdjustedPrices(anyList(), any(), any());
    }

    private Position position(String symbol, String quantity, String averageCost, String currency, String assetClass) {
        var result = new Position(1L, symbol, new BigDecimal(quantity), new BigDecimal(averageCost));
        result.setInstrument(new Instrument(symbol, symbol, assetClass, currency, true));
        return result;
    }

    private List<AdjustedPrice> history(String holding) {
        List<AdjustedPrice> result = new ArrayList<>();
        var days = UsEquityTradingCalendar.sessions(end.minusYears(1), end).orElseThrow();
        for (int i = 0; i < days.size(); i++) {
            result.add(new AdjustedPrice(holding, days.get(i), new BigDecimal(100 + i)));
            if (!holding.equals("SPY")) result.add(new AdjustedPrice("SPY", days.get(i), new BigDecimal(500 + i)));
        }
        return result;
    }
}
