package com.leapvelocity.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Null valuation fields explicitly mean unavailable, not zero. */
public record PortfolioSummaryDto(
        Long accountId, String currency, LocalDate asOf, ValuationStatus valuationStatus,
        BigDecimal holdingsValue, BigDecimal cashBalance, BigDecimal totalEquity,
        BigDecimal costBasis, BigDecimal unrealizedPnL,
        List<PortfolioHoldingDto> holdings, List<String> issues
) {
    public enum ValuationStatus { READY, EMPTY, UNAVAILABLE }
}
