package com.leapvelocity.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Message written to the {@code orders} topic: a new order placement.
 */
public record OrderEvent(
        UUID orderId,
        Long accountId,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        Instant createdOn
) {
}
