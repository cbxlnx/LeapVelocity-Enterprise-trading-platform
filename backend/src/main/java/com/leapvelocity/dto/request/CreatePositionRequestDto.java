package com.leapvelocity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(description = "Request to add an initial position (holdings) for an account")
public record CreatePositionRequestDto(
        @Schema(description = "Account ID to associate with this position", example = "1")
        @NotNull Long accountId,
        
        @Schema(description = "Trading symbol of the instrument", example = "AAPL")
        @NotBlank String symbol,
        
        @Schema(description = "Number of shares held (must be positive)", example = "50")
        @NotNull @Positive BigDecimal quantity,
        
        @Schema(description = "Average cost per share (non-negative, for P&L calculation)", example = "145.30")
        @NotNull @PositiveOrZero BigDecimal averageCost
) {
}
