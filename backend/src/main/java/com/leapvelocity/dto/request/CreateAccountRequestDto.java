package com.leapvelocity.dto.request;

import com.leapvelocity.entities.enums.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(description = "Request to create a new trading account")
public record CreateAccountRequestDto(
        @Schema(description = "Unique account identifier", example = "ACC-001")
        @NotBlank String accountId,
        
        @Schema(description = "Full name of the account holder", example = "John Smith")
        @NotBlank String holderName,
        
        @Schema(description = "Initial cash balance in the account (non-negative)", example = "10000.00")
        @NotNull @PositiveOrZero BigDecimal cashBalance,
        
        @Schema(description = "Initial status of the account (ACTIVE, SUSPENDED, CLOSED)", example = "ACTIVE")
        @NotNull AccountStatus status
) {
}
