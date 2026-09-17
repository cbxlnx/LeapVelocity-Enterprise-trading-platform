package com.leapvelocity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInstrumentRequestDto(
        @NotBlank String symbol,
        @NotBlank String name,
        @NotBlank String assetClass,
        @NotBlank String currency,
        @NotNull Boolean tradable
) {
}
