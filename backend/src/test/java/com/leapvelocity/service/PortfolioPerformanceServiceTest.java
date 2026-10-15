package com.leapvelocity.service;

import com.leapvelocity.dto.response.PortfolioHoldingDto;
import com.leapvelocity.dto.response.PortfolioSummaryDto;
import com.leapvelocity.dto.response.PortfolioSummaryDto.ValuationStatus;
import com.leapvelocity.dto.response.PortfolioPerformanceDto.SimulationStatus;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.repository.HistoricalPriceRepository;
import com.leapvelocity.repository.HistoricalPriceRepository.AdjustedPrice;
import com.leapvelocity.repository.InstrumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PortfolioPerformanceServiceTest {
    private PortfolioAnalyticsService analytics;
    private HistoricalPriceRepository prices;
    private InstrumentRepository instruments;
    private PortfolioPerformanceService service;
    private final LocalDate start = LocalDate.parse("2026-10-09");
    private final LocalDate end = LocalDate.parse("2026-10-13");

    @BeforeEach
    void setUp() {
        analytics = mock(PortfolioAnalyticsService.class);
        prices = mock(HistoricalPriceRepository.class);
        instruments = mock(InstrumentRepository.class);
        service = new PortfolioPerformanceService(analytics, prices, instruments);
        when(analytics.getSummary(1L)).thenReturn(summary(List.of(holding("AAPL", "60"), holding("MSFT", "40")), "100"));
        when(instruments.findBySymbol("SPY")).thenReturn(Optional.of(new Instrument("SPY", "Benchmark", "FUND", "USD", true)));
        when(prices.findAdjustedPrices(anyList(), nullable(LocalDate.class), eq(end))).thenReturn(List.of(
                bar("SPY", end, "525"), bar("AAPL", start, "100"), bar("MSFT", end, "180"),
                bar("SPY", start, "500"), bar("AAPL", end, "110"), bar("MSFT", start, "200")));
    }

    private PortfolioHoldingDto holding(String symbol, String value) {
        BigDecimal amount = new BigDecimal(value);
        return new PortfolioHoldingDto(symbol, "USD", BigDecimal.ONE, amount, amount, amount, amount,
                BigDecimal.ZERO, new BigDecimal("0.333333333333")); // Deliberately unsuitable rounded weight.
    }

    private PortfolioSummaryDto summary(List<PortfolioHoldingDto> holdings, String total) {
        BigDecimal value = new BigDecimal(total);
        return new PortfolioSummaryDto(1L, "USD", end, ValuationStatus.READY, value, new BigDecimal("999999"),
                value.add(new BigDecimal("999999")), value, BigDecimal.ZERO, holdings, List.of());
    }

    private AdjustedPrice bar(String symbol, LocalDate date, String value) {
        return new AdjustedPrice(symbol, date, new BigDecimal(value));
    }

    private void number(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    @Test
    void calculatesSixtyFortyExampleWithoutCashOrRoundedSummaryWeights() {
        var result = service.getPerformance(1L, "1Y");
        assertEquals(SimulationStatus.READY, result.simulationStatus());
        assertEquals(start, result.from());
        assertEquals(end, result.to());
        number("100", result.points().get(0).portfolioIndex());
        number("100", result.points().get(0).benchmarkIndex());
        number("102", result.points().get(1).portfolioIndex());
        number("105", result.points().get(1).benchmarkIndex());
        number("2", result.portfolioReturnPercent());
        number("5", result.benchmarkReturnPercent());
        number("0.6", result.initialWeights().get(0).weight());
    }

    @Test
    void doesNotRebalanceWeightsEachDay() {
        LocalDate middle = LocalDate.parse("2026-10-12");
        when(prices.findAdjustedPrices(anyList(), nullable(LocalDate.class), eq(end))).thenReturn(List.of(
                bar("AAPL", start, "100"), bar("MSFT", start, "200"), bar("SPY", start, "500"),
                bar("AAPL", middle, "200"), bar("MSFT", middle, "200"), bar("SPY", middle, "500"),
                bar("AAPL", end, "100"), bar("MSFT", end, "400"), bar("SPY", end, "500")));
        var result = service.getPerformance(1L, "1Y");
        number("160", result.points().get(1).portfolioIndex());
        number("140", result.points().get(2).portfolioIndex());
        number("40", result.portfolioReturnPercent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1M", "3M", "6M", "1Y", "2Y"})
    void appliesPeriodBoundsRelativeToCurrentValuationDate(String period) {
        var result = service.getPerformance(1L, period);
        LocalDate expected = switch (period) {
            case "1M" -> end.minusMonths(1);
            case "3M" -> end.minusMonths(3);
            case "6M" -> end.minusMonths(6);
            case "1Y" -> end.minusYears(1);
            case "2Y" -> end.minusYears(2);
            default -> throw new IllegalArgumentException(period);
        };
        assertEquals(expected, result.requestedFrom());
        assertEquals(end, result.weightingDate());
        verify(prices).findAdjustedPrices(List.of("AAPL", "MSFT", "SPY"), expected, end);
    }

    @Test
    void twoYearsUsesBoundedHistoryEvenWhenOlderPricesAreStored() {
        LocalDate twoYearStart = LocalDate.parse("2024-10-13");
        LocalDate firstCommonDate = LocalDate.parse("2024-10-15");
        List<AdjustedPrice> recent = List.of(
                bar("AAPL", firstCommonDate, "100"), bar("MSFT", firstCommonDate, "200"), bar("SPY", firstCommonDate, "500"),
                bar("AAPL", end, "110"), bar("MSFT", end, "180"), bar("SPY", end, "525"));
        when(prices.findAdjustedPrices(anyList(), eq(twoYearStart), eq(end))).thenReturn(recent);

        var twoYears = service.getPerformance(1L, " 2y ");
        assertEquals("2Y", twoYears.period());
        assertEquals(twoYearStart, twoYears.requestedFrom());
        assertEquals(firstCommonDate, twoYears.from());
        assertEquals(2, twoYears.points().size());
        number("100", twoYears.points().get(0).portfolioIndex());
        number("100", twoYears.points().get(0).benchmarkIndex());
        verify(prices).findAdjustedPrices(List.of("AAPL", "MSFT", "SPY"), twoYearStart, end);
    }

    @Test
    void alignsOnCommonDatesWithoutZeroOrForwardFilling() {
        var rows = new ArrayList<>(prices.findAdjustedPrices(List.of(), null, end));
        LocalDate missingForMsft = LocalDate.parse("2026-10-12");
        rows.add(bar("AAPL", missingForMsft, "500"));
        rows.add(bar("SPY", missingForMsft, "900"));
        when(prices.findAdjustedPrices(anyList(), nullable(LocalDate.class), eq(end))).thenReturn(rows);
        var result = service.getPerformance(1L, "1Y");
        assertEquals(List.of(start, end), result.points().stream().map(point -> point.date()).toList());
        number("102", result.points().get(1).portfolioIndex());
    }

    @Test
    void handlesSpyAsAHoldingWithoutDuplicateBenchmarkInput() {
        when(analytics.getSummary(1L)).thenReturn(summary(List.of(holding("SPY", "100")), "100"));
        var result = service.getPerformance(1L, "1Y");
        verify(prices).findAdjustedPrices(List.of("SPY"), end.minusYears(1), end);
        result.points().forEach(point -> assertEquals(point.portfolioIndex(), point.benchmarkIndex()));
    }

    @Test
    void missingBenchmarkAndInsufficientHistoryReturnNoInventedReturn() {
        for (var rows : List.of(List.of(bar("AAPL", start, "100"), bar("MSFT", start, "200")),
                List.of(bar("AAPL", start, "100"), bar("MSFT", start, "200"), bar("SPY", start, "500")))) {
            when(prices.findAdjustedPrices(anyList(), nullable(LocalDate.class), eq(end))).thenReturn(rows);
            var result = service.getPerformance(1L, "1Y");
            assertEquals(SimulationStatus.UNAVAILABLE, result.simulationStatus());
            assertTrue(result.points().isEmpty());
            assertNull(result.portfolioReturnPercent());
            assertFalse(result.issues().isEmpty());
        }
    }

    @Test
    void invalidAdjustedPriceIsExplicit() {
        when(prices.findAdjustedPrices(anyList(), nullable(LocalDate.class), eq(end)))
                .thenReturn(List.of(bar("AAPL", start, "0")));
        var result = service.getPerformance(1L, "1Y");
        assertEquals(SimulationStatus.UNAVAILABLE, result.simulationStatus());
        assertTrue(result.issues().get(0).contains("AAPL"));
    }

    @Test
    void cashOnlyAccountIsEmptyAndDoesNotFetchBenchmark() {
        when(analytics.getSummary(1L)).thenReturn(new PortfolioSummaryDto(1L, "USD", null, ValuationStatus.EMPTY,
                BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of()));
        var result = service.getPerformance(1L, "1Y");
        assertEquals(SimulationStatus.EMPTY, result.simulationStatus());
        assertNull(result.portfolioReturnPercent());
        assertTrue(result.points().isEmpty());
        verifyNoInteractions(prices, instruments);
    }

    @Test
    void invalidPeriodFailsBeforeReadingAccount() {
        assertThrows(IllegalArgumentException.class, () -> service.getPerformance(1L, "ALL"));
        assertThrows(IllegalArgumentException.class, () -> service.getPerformance(1L, "5Y"));
        verifyNoInteractions(analytics, prices, instruments);
    }

    @Test
    void unavailableCurrentValuationPreventsHistoricalSimulation() {
        when(analytics.getSummary(1L)).thenReturn(new PortfolioSummaryDto(1L, "USD", null, ValuationStatus.UNAVAILABLE,
                null, BigDecimal.TEN, null, null, null, List.of(), List.of("Missing prices for AAPL")));
        var result = service.getPerformance(1L, "1Y");
        assertEquals(List.of("Missing prices for AAPL"), result.issues());
        assertEquals(SimulationStatus.UNAVAILABLE, result.simulationStatus());
        verifyNoInteractions(prices, instruments);
    }
}
