package com.leapvelocity.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Message written to the {@code orders} topic: a new order placement.
 * Fields map directly from Order entity.
 */
public record OrderEvent(
    UUID orderId,           // Order.id
    Long accountId,         // Order.accountId
    String symbol,          // Order.symbol
    String side,            // Order.side.toString() (BUY or SELL)
    BigDecimal quantity,    // Order.quantity
    BigDecimal price,       // Order.price (limit price)
    Instant createdOn       // Order.createdOn (convert from LocalDateTime)
) { }