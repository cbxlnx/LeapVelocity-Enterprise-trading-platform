package com.leapvelocity.dto.response;

import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Trading order details with execution status and pricing")
public record OrderDto(
        @Schema(description = "Unique order identifier as UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,
        
        @Schema(description = "Account ID that placed this order", example = "1")
        Long accountId,
        
        @Schema(description = "Trading symbol of the instrument", example = "AAPL")
        String symbol,
        
        @Schema(description = "Order side: BUY or SELL", example = "BUY")
        OrderSide side,
        
        @Schema(description = "Number of shares in the order", example = "100")
        BigDecimal quantity,
        
        @Schema(description = "Limit price per share", example = "150.50")
        BigDecimal price,
        
        @Schema(description = "Current order status: NEW, FILLED, REJECTED, or CANCELLED", example = "FILLED")
        OrderStatus status,
        
        @Schema(description = "Unique key for idempotency to prevent duplicate orders", example = "order-uuid-12345")
        String idempotencyKey,
        
        @Schema(description = "Timestamp when order was created", example = "2026-09-24T14:30:00")
        LocalDateTime createdOn
) {

    public static OrderDto from(Order order) {
        return new OrderDto(
                order.getId(),
                order.getAccountId(),
                order.getSymbol(),
                order.getSide(),
                order.getQuantity(),
                order.getPrice(),
                order.getStatus(),
                order.getIdempotencyKey(),
                order.getCreatedOn()
        );
    }
}
