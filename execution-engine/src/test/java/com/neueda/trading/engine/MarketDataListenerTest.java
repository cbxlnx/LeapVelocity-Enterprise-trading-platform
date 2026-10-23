package com.neueda.trading.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketDataListenerTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final QuoteMarket market = new QuoteMarket(Duration.ofSeconds(15), "SIM",
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    private final MarketDataListener listener = new MarketDataListener(market, mapper);

    @Test
    void testOnQuote_ValidMessage_UpdatesMarket() throws Exception {
        listener.onQuote("""
                {"symbol":"ACME","lastPrice":25.10,"bid":25.00,"ask":25.20,"currency":"USD",
                 "asOf":"2026-10-14T14:30:00Z","source":"yfinance"}
                """);

        ExecutionEvent fill = market.execute(new OrderEvent(UUID.randomUUID(), 1L, "ACME", Side.BUY,
                new BigDecimal("10"), new BigDecimal("25.50"), Instant.EPOCH));

        assertEquals(new BigDecimal("25.20"), fill.price());
    }

    @Test
    void testOnQuote_UnreadableMessage_IsSkipped() {
        assertDoesNotThrow(() -> listener.onQuote("not json"));
    }
}
