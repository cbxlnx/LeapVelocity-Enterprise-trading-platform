package com.leapvelocity.dto.request;

import com.leapvelocity.entities.enums.OrderSide;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PlaceOrderRequestDto(
        @NotNull Long accountId,
        @NotBlank String symbol,
        @NotNull OrderSide side,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @Positive BigDecimal price,
        @NotBlank String idempotencyKey
) {
}
