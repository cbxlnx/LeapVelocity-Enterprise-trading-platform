package com.leapvelocity.dto.response;

import java.math.BigDecimal;

/** Weight is a fraction of invested holdings, excluding cash. */
public record PortfolioHoldingDto(
        String symbol, String currency, BigDecimal quantity, BigDecimal averageCost,
        BigDecimal price, BigDecimal marketValue, BigDecimal costBasis,
        BigDecimal unrealizedPnL, BigDecimal weight
) {
}
