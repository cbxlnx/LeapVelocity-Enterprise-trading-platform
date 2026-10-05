package com.leapvelocity.dto.request;

import com.leapvelocity.entities.enums.OrderSide;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "Request to place a new trading order (buy or sell)")
public record PlaceOrderRequestDto(
        @Schema(description = "Account ID placing the order", example = "1")
        @NotNull Long accountId,
        
        @Schema(description = "Trading symbol (ticker) of the instrument", example = "AAPL")
        @NotBlank String symbol,
        
        @Schema(description = "Order direction: BUY or SELL", example = "BUY")
        @NotNull OrderSide side,
        
        @Schema(description = "Number of shares to trade (must be positive)", example = "100")
        @NotNull @Positive BigDecimal quantity,
        
        @Schema(description = "Limit price per share (must be positive)", example = "150.50")
        @NotNull @Positive BigDecimal price,
        
        @Schema(description = "Unique identifier for idempotency (prevents duplicate orders)", example = "order-uuid-12345")
        @NotBlank String idempotencyKey
) {
}
