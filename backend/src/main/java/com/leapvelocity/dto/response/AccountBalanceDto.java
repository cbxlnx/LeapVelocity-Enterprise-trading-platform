package com.leapvelocity.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Account cash balance summary")
public record AccountBalanceDto(
        @Schema(description = "The account ID", example = "1")
        Long accountId,
        
        @Schema(description = "Current available cash balance for trading", example = "10000.00")
        BigDecimal cashBalance
) {
}
