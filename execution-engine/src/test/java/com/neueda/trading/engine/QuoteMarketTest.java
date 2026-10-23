package com.neueda.trading.engine;

import com.neueda.trading.engine.exceptions.TemporaryFailureException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteMarketTest {

    private final MutableClock clock = new MutableClock(Instant.EPOCH);
    private final QuoteMarket market = new QuoteMarket(Duration.ofSeconds(15), "SIM", clock);

    @Test
    void testBuy_AskAtOrBelowLimit_FillsAtAsk() throws Exception {
        market.update(quote("ACME", "25.00", "25.20"));

        ExecutionEvent fill = market.execute(order(Side.BUY, "25.50"));

        assertEquals(new BigDecimal("25.20"), fill.price());
        assertEquals(new BigDecimal("25.50"), fill.limitPrice());
        assertEquals("SIM", fill.venue());
    }

    @Test
    void testBuy_AskAboveLimit_TemporaryFailure() {
        market.update(quote("ACME", "25.00", "25.20"));

        TemporaryFailureException exception = assertThrows(TemporaryFailureException.class,
                () -> market.execute(order(Side.BUY, "25.10")));

        assertTrue(exception.getMessage().contains("No marketable BUY price for ACME at limit 25.10"));
    }

    @Test
    void testSell_BidAtOrAboveLimit_FillsAtBid() throws Exception {
        market.update(quote("ACME", "25.30", "25.40"));

        ExecutionEvent fill = market.execute(order(Side.SELL, "25.20"));

        assertEquals(new BigDecimal("25.30"), fill.price());
    }

    @Test
    void testSell_BidBelowLimit_TemporaryFailure() {
        market.update(quote("ACME", "25.30", "25.40"));

        TemporaryFailureException exception = assertThrows(TemporaryFailureException.class,
                () -> market.execute(order(Side.SELL, "25.50")));

        assertTrue(exception.getMessage().contains("No marketable SELL price for ACME at limit 25.50"));
    }

    @Test
    void testNoQuote_TemporaryFailure() {
        TemporaryFailureException exception = assertThrows(TemporaryFailureException.class,
                () -> market.execute(order(Side.BUY, "25.50")));

        assertTrue(exception.getMessage().contains("No quote yet for ACME"));
    }

    @Test
    void testStaleQuote_TemporaryFailure() {
        market.update(quote("ACME", "25.00", "25.20"));
        clock.advance(Duration.ofSeconds(16));

        TemporaryFailureException exception = assertThrows(TemporaryFailureException.class,
                () -> market.execute(order(Side.BUY, "25.50")));

        assertTrue(exception.getMessage().contains("is older than"));
    }

    @Test
    void testQuoteAtStalenessLimit_StillFills() throws Exception {
        market.update(quote("ACME", "25.00", "25.20"));
        clock.advance(Duration.ofSeconds(15));

        ExecutionEvent fill = market.execute(order(Side.BUY, "25.50"));

        assertEquals(new BigDecimal("25.20"), fill.price());
    }

    @Test
    void testCrossedQuote_IgnoredAndPreviousQuoteKept() throws Exception {
        market.update(quote("ACME", "25.00", "25.20"));
        market.update(quote("ACME", "26.00", "25.90"));

        ExecutionEvent fill = market.execute(order(Side.BUY, "25.50"));

        assertEquals(new BigDecimal("25.20"), fill.price());
    }

    private static MarketDataEvent quote(String symbol, String bid, String ask) {
        return new MarketDataEvent(symbol, new BigDecimal(ask), new BigDecimal(bid), new BigDecimal(ask),
                "USD", Instant.EPOCH, "test");
    }

    private static OrderEvent order(Side side, String limit) {
        return new OrderEvent(UUID.randomUUID(), 1L, "ACME", side, new BigDecimal("10"),
                new BigDecimal(limit), Instant.EPOCH);
    }

    /** A clock the test can move forward to age a quote. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
