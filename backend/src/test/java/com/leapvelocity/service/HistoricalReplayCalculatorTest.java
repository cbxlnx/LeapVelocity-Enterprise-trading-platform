package com.leapvelocity.service;

import com.leapvelocity.dto.response.*;
import com.leapvelocity.dto.response.PortfolioRiskDto.*;
import com.leapvelocity.repository.HistoricalPriceRepository.ReplayPrice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic algorithm fixtures only; live provider observations are verified separately. */
class HistoricalReplayCalculatorTest {
    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final Instant LOADED = Instant.parse("2026-10-21T10:00:00Z");
    private static final HistoricalReplayCatalog.Event EVENT = HistoricalReplayCatalog.event("CRISIS_2008");
    private static BigDecimal d(String number) { return new BigDecimal(number); }
    private PortfolioHoldingDto holding(String symbol) {
        return new PortfolioHoldingDto(symbol, "USD", BigDecimal.ONE, d("100"), d("201.50"), d("201.50"), d("100"), d("101.50"), BigDecimal.ONE);
    }
    private List<ReplayPrice> prices(String symbol, HistoricalReplayCatalog.Event event) {
        return List.of(new ReplayPrice(symbol, event.from(), d("100"), "yfinance", LOADED),
                new ReplayPrice(symbol, event.to(), d("95"), "yfinance", LOADED));
    }
    private HistoricalReplayCalculator.HoldingInput input(String symbol) {
        var mapping = HistoricalReplayCatalog.mapping(symbol);
        return new HistoricalReplayCalculator.HoldingInput(holding(symbol), mapping == null ? "Unknown" : mapping.instrumentNames().get(0),
                mapping == null ? "EQUITY" : mapping.assetClass(), prices(symbol, EVENT));
    }
    private HistoricalReplayCalculator.Inputs inputs(List<HistoricalReplayCalculator.HoldingInput> rows, List<Issue> issues) {
        var holdings = rows.stream().map(HistoricalReplayCalculator.HoldingInput::holding).toList();
        var invested = holdings.stream().map(PortfolioHoldingDto::marketValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        var valuation = new PortfolioSummaryDto(1L, "USD", LocalDate.parse("2026-10-20"),
                rows.isEmpty() ? PortfolioSummaryDto.ValuationStatus.EMPTY : PortfolioSummaryDto.ValuationStatus.READY,
                invested, d("100"), invested.add(d("100")), BigDecimal.ZERO, BigDecimal.ZERO, holdings, List.of());
        return new HistoricalReplayCalculator.Inputs(new PortfolioSnapshotService.ShockSnapshot(valuation, issues), rows);
    }
    private PortfolioReplayDto calculate(List<HistoricalReplayCalculator.HoldingInput> rows) {
        return HistoricalReplayCalculator.calculate(inputs(rows, List.of()), EVENT, NOW, LocalDate.parse("2026-10-21"));
    }

    @Test void explicitCalendarWindowsDoNotNeedTheDailyRiskCalendar() {
        assertEquals(LocalDate.parse("2007-12-31"), EVENT.from());
        assertEquals(LocalDate.parse("2008-12-31"), EVENT.to());
        var march = HistoricalReplayCatalog.event("MARCH_2020");
        assertEquals(LocalDate.parse("2020-02-28"), march.from());
        assertEquals(LocalDate.parse("2020-03-31"), march.to());
        assertTrue(UsEquityTradingCalendar.sessions(EVENT.from(), EVENT.to()).isEmpty());
        assertEquals(ResultStatus.READY, calculate(List.of(input("AAPL"))).status());
        assertThrows(IllegalArgumentException.class, () -> HistoricalReplayCatalog.event("arbitrary-date-window"));
    }

    @Test void roundsEachHoldingBeforeAggregationAndKeepsCash() {
        var result = calculate(List.of(input("AAPL"), input("MSFT")));
        assertEquals(ResultStatus.READY, result.status());
        assertEquals(d("-20.16"), result.totalPnL());
        assertEquals(d("-10.08"), result.holdings().get(0).pnl());
        assertEquals(d("482.84"), result.postShockEquity());
        assertEquals(d("100"), result.valuation().cashBalance());
        assertEquals(d("191.42"), result.holdings().get(0).postShockValue());
        assertEquals(2, result.validatedHoldings());
    }

    @Test void usesCumulativeAdjustedEndpointsRatherThanDailyOrRawPriceShocks() {
        var row = input("AAPL");
        var neutral = new HistoricalReplayCalculator.HoldingInput(row.holding(), row.instrumentName(), row.assetClass(),
                List.of(new ReplayPrice("AAPL", EVENT.from(), d("50"), "yfinance", LOADED),
                        new ReplayPrice("AAPL", EVENT.to(), d("50"), "yfinance", LOADED)));
        var result = calculate(List.of(neutral));
        assertEquals(d("0.00"), result.totalPnL());
        assertEquals(HistoricalReplayCatalog.METHOD, result.methodology());
        assertEquals("adjusted_close", result.priceField());
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "duplicate", "zero", "negative", "wrong-symbol", "unknown-source", "no-timestamp", "mixed-batch"})
    void incompleteOrUnvalidatedHistoryNeverProducesAPortfolioEstimate(String corruption) {
        var row = input("AAPL");
        var prices = new ArrayList<>(row.prices());
        var last = prices.get(1);
        switch (corruption) {
            case "missing" -> prices.remove(1);
            case "duplicate" -> prices.add(last);
            case "zero" -> prices.set(1, new ReplayPrice("AAPL", last.date(), BigDecimal.ZERO, "yfinance", LOADED));
            case "negative" -> prices.set(1, new ReplayPrice("AAPL", last.date(), d("-1"), "yfinance", LOADED));
            case "wrong-symbol" -> prices.set(1, new ReplayPrice("MSFT", last.date(), last.adjustedClose(), "yfinance", LOADED));
            case "unknown-source" -> prices.set(1, new ReplayPrice("AAPL", last.date(), last.adjustedClose(), "unverified", LOADED));
            case "no-timestamp" -> prices.set(1, new ReplayPrice("AAPL", last.date(), last.adjustedClose(), "yfinance", null));
            case "mixed-batch" -> prices.set(1, new ReplayPrice("AAPL", last.date(), last.adjustedClose(), "yfinance", LOADED.plusSeconds(1)));
        }
        var result = calculate(List.of(new HistoricalReplayCalculator.HoldingInput(row.holding(), row.instrumentName(), row.assetClass(), prices)));
        assertEquals(ResultStatus.UNAVAILABLE, result.status());
        assertNull(result.totalPnL()); assertNull(result.postShockEquity());
        assertFalse(result.reasons().isEmpty());
    }

    @Test void tickerEqualityDoesNotProveIdentityAndUnmappedHoldingsAreNotOmitted() {
        var row = input("AAPL");
        var badName = new HistoricalReplayCalculator.HoldingInput(row.holding(), "Unrelated issuer reusing AAPL", "EQUITY", row.prices());
        var result = calculate(List.of(input("MSFT"), badName));
        assertEquals(ResultStatus.UNAVAILABLE, result.status());
        assertEquals(2, result.expectedHoldings()); assertEquals(1, result.validatedHoldings());
        assertEquals(2, result.holdings().size()); assertNull(result.totalPnL());
        assertTrue(result.reasons().get(0).contains("CURRENT_METADATA_MISMATCH"));
        assertTrue(calculate(List.of(input("NEW_TICKER"))).reasons().get(0).contains("UNRESOLVED_INSTRUMENT_MAPPING"));
        var value = row.holding();
        var euro = new PortfolioHoldingDto(value.symbol(), "EUR", value.quantity(), value.averageCost(), value.price(),
                value.marketValue(), value.costBasis(), value.unrealizedPnL(), value.weight());
        var currencyMismatch = calculate(List.of(new HistoricalReplayCalculator.HoldingInput(euro, row.instrumentName(), "EQUITY", row.prices())));
        assertEquals(ResultStatus.UNAVAILABLE, currencyMismatch.status());
        assertNull(currencyMismatch.totalPnL(), "No currency conversion or implicit USD substitution is allowed");
    }

    @Test void postEventIpoAndReviewedTickerChangesAreExplicit() {
        assertTrue(calculate(List.of(input("TSLA"))).reasons().get(0).contains("HISTORICAL_INSTRUMENT_NOT_LISTED"));
        var result = calculate(List.of(input("GOOGL"), input("QQQ"), input("XOM")));
        assertEquals(ResultStatus.READY, result.status());
        assertEquals("GOOG (Class A)", result.holdings().get(0).historicalTicker());
        assertEquals("GOOGL", result.holdings().get(0).providerSymbol());
        assertEquals("QQQQ", result.holdings().get(1).historicalTicker());
        assertTrue(result.holdings().get(2).identityNotes().contains("2026-07-01"));
        assertFalse(result.holdings().get(0).identitySources().isEmpty());
        var march = HistoricalReplayCatalog.event("MARCH_2020");
        var tesla = input("TSLA");
        var rows = List.of(new HistoricalReplayCalculator.HoldingInput(tesla.holding(), tesla.instrumentName(), tesla.assetClass(), prices("TSLA", march)));
        assertEquals(ResultStatus.READY, HistoricalReplayCalculator.calculate(inputs(rows, List.of()), march, NOW, LocalDate.parse("2026-10-21")).status());
    }

    @Test void staleValuationEmptyAccountsAndMissingHoldingInputsRemainUnavailable() {
        var rows = List.of(input("AAPL"));
        var stale = new Issue(IssueCode.STALE_PRICE, Severity.ERROR, "valuation", "AAPL", null, "Valuation too old");
        assertEquals(ResultStatus.UNAVAILABLE, HistoricalReplayCalculator.calculate(inputs(rows, List.of(stale)), EVENT, NOW, LocalDate.parse("2026-10-21")).status());
        assertEquals(ResultStatus.EMPTY, calculate(List.of()).status());
        assertNull(calculate(List.of()).totalPnL());
        var omitted = new HistoricalReplayCalculator.Inputs(inputs(rows, List.of()).snapshot(), List.of());
        assertEquals(ResultStatus.UNAVAILABLE, HistoricalReplayCalculator.calculate(omitted, EVENT, NOW, LocalDate.parse("2026-10-21")).status());
    }
}
