package com.neueda.trading.engine;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Message read from the {@code market-data} topic. Mirrors the publisher's JSON
 * contract documented in {@code docs/event-flow.md}. Every field is declared
 * because the engine's object mapper rejects unknown properties.
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
