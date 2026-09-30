package com.leapvelocity.messaging;

import com.leapvelocity.entities.enums.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Message on the {@code trade-events} topic: an execution engine fill report.
 */
public record ExecutionEvent(
        UUID executionId,
        UUID orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal limitPrice,
        String venue,
        Instant executedOn
) {
}
