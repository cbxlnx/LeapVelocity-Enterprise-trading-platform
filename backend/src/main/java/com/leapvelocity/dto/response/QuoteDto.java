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
        String source
) {

    public static QuoteDto from(MarketDataEvent quote) {
        return new QuoteDto(
                quote.symbol(),
                quote.lastPrice(),
                quote.bid(),
                quote.ask(),
                quote.currency(),
                quote.asOf(),
                quote.source()
        );
    }
}
