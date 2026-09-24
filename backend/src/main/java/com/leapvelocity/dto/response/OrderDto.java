package com.leapvelocity.dto.response;

import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderDto(
        UUID id,
        Long accountId,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price,
        OrderStatus status,
        String idempotencyKey,
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
