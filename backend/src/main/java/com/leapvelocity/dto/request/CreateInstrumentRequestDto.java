package com.leapvelocity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to add a new tradable instrument (stock, bond, etc.)")
public record CreateInstrumentRequestDto(
        @Schema(description = "Trading symbol/ticker of the instrument", example = "AAPL")
        @NotBlank String symbol,
        
        @Schema(description = "Full name of the instrument", example = "Apple Inc.")
        @NotBlank String name,
        
        @Schema(description = "Asset class (e.g., EQUITY, BOND, COMMODITY)", example = "EQUITY")
        @NotBlank String assetClass,
        
        @Schema(description = "Currency in which instrument is quoted", example = "USD")
        @NotBlank String currency,
        
        @Schema(description = "Whether the instrument is available for trading", example = "true")
        @NotNull Boolean tradable
) {
}
