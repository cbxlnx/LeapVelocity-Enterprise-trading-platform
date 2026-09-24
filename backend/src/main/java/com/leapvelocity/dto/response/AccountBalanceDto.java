package com.leapvelocity.dto.response;

import java.math.BigDecimal;

public record AccountBalanceDto(
        Long accountId,
        BigDecimal cashBalance
) {
}
