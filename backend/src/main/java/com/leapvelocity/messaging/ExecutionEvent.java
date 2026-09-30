package com.leapvelocity.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Message written to the {@code trade-events} topic: an order execution event.
 */
public record ExecutionEvent(
        UUID executionId,
        UUID orderId,
        Long accountId,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal limitPrice,
        String venue,
        Instant executedOn
) {
}
