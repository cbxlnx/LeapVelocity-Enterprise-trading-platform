package com.leapvelocity.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Percentage points: -10 means a hypothetical 10% holding price decline. */
public record PriceShockRequestDto(
        @NotNull @DecimalMin("-50") @DecimalMax("50") BigDecimal priceChangePercent) { }
