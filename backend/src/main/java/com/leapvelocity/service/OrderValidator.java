package com.leapvelocity.service;

import com.leapvelocity.entities.Order;

import java.math.BigDecimal;

public class OrderValidator {

    public void validate(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order is required");
        }
        if (order.getAccountId() == null) {
            throw new IllegalArgumentException("Order account id is required");
        }
        if (order.getSide() == null) {
            throw new IllegalArgumentException("Order side is required");
        }
        if (order.getSymbol() == null || order.getSymbol().isBlank()) {
            throw new IllegalArgumentException("Order symbol is required");
        }
        if (order.getIdempotencyKey() == null || order.getIdempotencyKey().isBlank()) {
            throw new IllegalArgumentException("Order idempotency key is required");
        }
        if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Order quantity must be greater than zero");
        }
        if (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Order price must be greater than zero");
        }

        order.setSymbol(order.getSymbol().trim());
    }
}
