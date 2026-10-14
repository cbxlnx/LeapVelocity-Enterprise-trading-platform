package com.leapvelocity.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A stored daily observation; close and adjustedClose have distinct uses. */
public record HistoricalPricePointDto(
        LocalDate date,
        BigDecimal close,
        BigDecimal adjustedClose,
        long volume,
        String source
) {
}
