package com.leapvelocity.messaging;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Message on the {@code market-data} topic: a quote from the market-data publisher.
 * Mirrors the JSON contract in {@code docs/event-flow.md}.
 */
public record MarketDataEvent(
        String symbol,
        BigDecimal lastPrice,
        BigDecimal bid,
        BigDecimal ask,
        String currency,
        Instant asOf,
                String source,
                BigDecimal change,
                BigDecimal changePercent,
                BigDecimal dayHigh,
                BigDecimal dayLow,
                Long volume
) {
        public MarketDataEvent(String symbol, BigDecimal lastPrice, BigDecimal bid, BigDecimal ask,
                                                   String currency, Instant asOf, String source) {
                this(symbol, lastPrice, bid, ask, currency, asOf, source, null, null, null, null, null);
        }
}
