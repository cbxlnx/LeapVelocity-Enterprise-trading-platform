package com.leapvelocity.messaging;

import com.leapvelocity.entities.enums.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Message on the {@code orders} topic: an accepted order the execution engine should work.
 */
public record OrderEvent(
        UUID orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price,
        Instant createdOn
) {
}
