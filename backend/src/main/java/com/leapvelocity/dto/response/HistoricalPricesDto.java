package com.leapvelocity.dto.response;

import java.time.LocalDate;
import java.util.List;

/** Requested dates are inclusive; prices can be empty when no bars exist. */
public record HistoricalPricesDto(
        String symbol,
        String currency,
        LocalDate from,
        LocalDate to,
        List<HistoricalPricePointDto> prices
) {
}
