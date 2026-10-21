package com.leapvelocity.service;

import com.leapvelocity.dto.response.PortfolioSummaryDto.ValuationStatus;
import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.AccountStatus;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.HistoricalPriceRepository;
import com.leapvelocity.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PortfolioAnalyticsServiceTest {
    private AccountRepository accounts;
    private PositionRepository positions;
    private HistoricalPriceRepository prices;
    private PortfolioAnalyticsService service;
    private final LocalDate day = LocalDate.of(2025, 10, 1);

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        positions = mock(PositionRepository.class);
        prices = mock(HistoricalPriceRepository.class);
        service = new PortfolioAnalyticsService(new PortfolioSnapshotService(accounts, positions, prices,
                mock(com.leapvelocity.repository.InstrumentRepository.class)), mock(RiskCalculationClient.class));
        when(accounts.findById(1L)).thenReturn(Optional.of(new Account("ACC-001", "Demo", new BigDecimal("5.00"), AccountStatus.ACTIVE)));
        when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of(holding("AAPL", "2", "10.00", "USD")));
        when(prices.findLatestCommonDate(anyList(), any(LocalDate.class))).thenReturn(day);
        when(prices.findClosesOnDate(anyList(), eq(day))).thenReturn(Map.of("AAPL", new BigDecimal("12.000000")));
    }

    private Position holding(String symbol, String quantity, String averageCost, String currency) {
        var position = new Position(1L, symbol, new BigDecimal(quantity), new BigDecimal(averageCost));
        position.setInstrument(new Instrument(symbol, symbol, "EQUITY", currency, true));
        return position;
    }

    private void money(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    @Test
    void valuesHoldingsAndKeepsCashOutsideAllocation() {
        var result = service.getSummary(1L);
        assertEquals(ValuationStatus.READY, result.valuationStatus());
        assertEquals(day, result.asOf());
        money("24", result.holdingsValue());
        money("20", result.costBasis());
        money("4", result.unrealizedPnL());
        money("29", result.totalEquity());
        money("1", result.holdings().get(0).weight());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void sumsGainsAndLossesAndUsesInvestedValueForWeights() {
        when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of(
                holding("AAPL", "2", "10", "USD"), holding("MSFT", "3", "20", "USD")));
        when(prices.findClosesOnDate(anyList(), eq(day))).thenReturn(Map.of("AAPL", new BigDecimal("12"), "MSFT", new BigDecimal("18")));
        var result = service.getSummary(1L);
        money("78", result.holdingsValue());
        money("80", result.costBasis());
        money("-2", result.unrealizedPnL());
        money("83", result.totalEquity());
        money("0.307692307692", result.holdings().get(0).weight());
        money("1", result.holdings().stream().map(row -> row.weight()).reduce(BigDecimal.ZERO, BigDecimal::add));
        verify(prices).findClosesOnDate(List.of("AAPL", "MSFT"), day);
    }

    @Test
    void preservesFractionalQuantityAndPricePrecision() {
        when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of(holding("AAPL", "0.1234", "10.10", "USD")));
        when(prices.findClosesOnDate(anyList(), eq(day))).thenReturn(Map.of("AAPL", new BigDecimal("12.123456")));
        var result = service.getSummary(1L);
        money("1.4960344704", result.holdingsValue());
        money("0.2496944704", result.unrealizedPnL());
    }

    @Test
    void emptyOrZeroQuantityHoldingsReturnCashWithoutRequiringPrices() {
        when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of(holding("AAPL", "0", "10", "USD")));
        var result = service.getSummary(1L);
        assertEquals(ValuationStatus.EMPTY, result.valuationStatus());
        assertNull(result.asOf());
        money("0", result.holdingsValue());
        money("5", result.totalEquity());
        assertTrue(result.holdings().isEmpty());
        verifyNoInteractions(prices);
    }

    @Test
    void missingHistoryIsExplicitAndDoesNotProducePartialOrZeroValuation() {
        when(prices.findLatestCommonDate(anyList(), any(LocalDate.class))).thenReturn(null);
        when(prices.findSymbolsWithHistoryBefore(anyList(), any(LocalDate.class))).thenReturn(Set.of());
        var result = service.getSummary(1L);
        assertEquals(ValuationStatus.UNAVAILABLE, result.valuationStatus());
        assertTrue(result.issues().get(0).contains("AAPL"));
        assertNull(result.holdingsValue());
        assertNull(result.totalEquity());
        assertNull(result.unrealizedPnL());
        assertNull(result.holdings().get(0).price());
        assertNull(result.holdings().get(0).weight());
        money("5", result.cashBalance());
        money("20", result.costBasis());
    }

    @Test
    void disjointHistoriesReportNoCommonDate() {
        when(prices.findLatestCommonDate(anyList(), any(LocalDate.class))).thenReturn(null);
        when(prices.findSymbolsWithHistoryBefore(anyList(), any(LocalDate.class))).thenReturn(Set.of("AAPL"));
        assertTrue(service.getSummary(1L).issues().get(0).startsWith("No common completed trading date"));
    }

    @Test
    void missingMultipleSymbolsKeepsTheExistingSummaryMessage() {
        when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of(
                holding("AAPL", "2", "10", "USD"), holding("MSFT", "3", "20", "USD")));
        when(prices.findLatestCommonDate(anyList(), any(LocalDate.class))).thenReturn(null);
        when(prices.findSymbolsWithHistoryBefore(anyList(), any(LocalDate.class))).thenReturn(Set.of());
        assertEquals(List.of("Missing completed price history for: AAPL, MSFT"), service.getSummary(1L).issues());
    }

    @Test
    void absentPriceOnValuationDateDoesNotBecomeZero() {
        when(prices.findClosesOnDate(anyList(), eq(day))).thenReturn(Map.of());
        var result = service.getSummary(1L);
        assertEquals(ValuationStatus.UNAVAILABLE, result.valuationStatus());
        assertTrue(result.issues().get(0).contains(day.toString()));
        assertNull(result.holdingsValue());
    }

    @Test
    void unsupportedShortAndNonUsdPositionsRemainVisibleWithoutValuation() {
        for (Position invalid : List.of(holding("AAPL", "-1", "10", "USD"), holding("AAPL", "2", "10", "EUR"))) {
            when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of(invalid));
            var result = service.getSummary(1L);
            assertEquals(ValuationStatus.UNAVAILABLE, result.valuationStatus());
            assertEquals(1, result.holdings().size());
            assertNull(result.totalEquity());
            assertFalse(result.issues().isEmpty());
        }
        verifyNoInteractions(prices);
    }

    @Test
    void missingAccountDoesNotReadHoldings() {
        when(accounts.findById(1L)).thenReturn(Optional.empty());
        assertThrows(AccountNotFoundException.class, () -> service.getSummary(1L));
        verifyNoInteractions(positions, prices);
    }
}
