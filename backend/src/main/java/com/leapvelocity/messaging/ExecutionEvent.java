package com.leapvelocity.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Message written to the {@code trade-events} topic: order execution (fill or rejection).
 * Fields match Order entity where applicable; venue is assigned by execution engine.
 */
public record ExecutionEvent(
    UUID executionId,           // unique execution identifier
    UUID orderId,               // Order.id
    Long accountId,             // Order.accountId 
    String symbol,              // Order.symbol
    String side,                // Order.side.toString() (BUY or SELL)
    BigDecimal quantity,        // Order.quantity 
    BigDecimal price,           // execution price 
    BigDecimal limitPrice,      // Order.price (requested limit)
    String venue,               // execution venue ("SIMULATED_MARKET", "NYSE", etc.)
    Instant executedOn          // execution timestamp (convert from LocalDateTime)
) { }