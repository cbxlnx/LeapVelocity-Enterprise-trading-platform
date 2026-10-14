package com.leapvelocity.service;

import com.leapvelocity.messaging.MarketDataEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MarketDataService")
class MarketDataServiceTest {

    private final MarketDataService service = new MarketDataService();

    @Test
    @DisplayName("latestQuote is empty before any quote arrives")
    void latestQuoteIsEmptyBeforeAnyQuoteArrives() {
        assertFalse(service.latestQuote("AAPL").isPresent());
    }

    @Test
    @DisplayName("latestQuote returns the most recent update for a symbol")
    void latestQuoteReturnsMostRecentUpdateForSymbol() {
        service.update(quote("AAPL", "187.25"));
        service.update(quote("AAPL", "188.00"));

        assertEquals(new BigDecimal("188.00"), service.latestQuote("AAPL").orElseThrow().lastPrice());
    }

    @Test
    @DisplayName("latestQuote ignores case and surrounding whitespace")
    void latestQuoteIgnoresCaseAndWhitespace() {
        service.update(quote("AAPL", "187.25"));

        assertTrue(service.latestQuote(" aapl ").isPresent());
    }

    @Test
    @DisplayName("latestQuote keeps symbols separate")
    void latestQuoteKeepsSymbolsSeparate() {
        service.update(quote("AAPL", "187.25"));
        service.update(quote("MSFT", "380.50"));

        assertEquals(new BigDecimal("380.50"), service.latestQuote("MSFT").orElseThrow().lastPrice());
    }

    @Test
    @DisplayName("update ignores null events and events without a symbol")
    void updateIgnoresNullEventsAndMissingSymbols() {
        service.update(null);
        service.update(new MarketDataEvent(null, new BigDecimal("1.00"), null, null, "USD", Instant.EPOCH, "yfinance"));

        assertFalse(service.latestQuote("AAPL").isPresent());
    }

    private MarketDataEvent quote(String symbol, String lastPrice) {
        return new MarketDataEvent(symbol, new BigDecimal(lastPrice), null, null, "USD", Instant.EPOCH, "yfinance");
    }
}
