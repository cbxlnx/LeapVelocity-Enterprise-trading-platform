package com.leapvelocity.dto.response;

import com.leapvelocity.messaging.MarketDataEvent;

import java.math.BigDecimal;
import java.time.Instant;

public record QuoteDto(
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

    public static QuoteDto from(MarketDataEvent quote) {
        return new QuoteDto(
                quote.symbol(),
                quote.lastPrice(),
                quote.bid(),
                quote.ask(),
                quote.currency(),
                quote.asOf(),
                quote.source(),
                quote.change(),
                quote.changePercent(),
                quote.dayHigh(),
                quote.dayLow(),
                quote.volume()
        );
    }
}
