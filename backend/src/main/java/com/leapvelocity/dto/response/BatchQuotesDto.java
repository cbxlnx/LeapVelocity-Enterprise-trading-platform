package com.leapvelocity.dto.response;

import java.time.Instant;
import java.util.List;

public record BatchQuotesDto(
        List<QuoteDto> quotes,
        List<String> missingSymbols,
        Instant serverTimestamp
) {
}
