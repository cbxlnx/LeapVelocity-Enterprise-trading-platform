package com.leapvelocity.dto.request;

import com.leapvelocity.entities.enums.AccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateAccountRequestDto(
        @NotBlank String accountId,
        @NotBlank String holderName,
        @NotNull @PositiveOrZero BigDecimal cashBalance,
        @NotNull AccountStatus status
) {
}
