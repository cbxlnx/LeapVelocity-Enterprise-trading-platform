package com.leapvelocity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreatePositionRequestDto(
        @NotNull Long accountId,
        @NotBlank String symbol,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @PositiveOrZero BigDecimal averageCost
) {
}
