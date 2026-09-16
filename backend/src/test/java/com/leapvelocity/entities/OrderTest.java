package com.leapvelocity.entities;

import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Order Entity")
class OrderTest {

    @Test
    @DisplayName("constructor creates a new order")
    void constructorCreatesNewOrder() {
        Order order = new Order(
                1L,
                "AAPL",
                OrderSide.BUY,
                new BigDecimal("10"),
                new BigDecimal("150.00"),
                "idem-001"
        );

        assertEquals(1L, order.getAccountId());
        assertEquals("AAPL", order.getSymbol());
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals(new BigDecimal("10"), order.getQuantity());
        assertEquals(new BigDecimal("150.00"), order.getPrice());
        assertEquals(OrderStatus.NEW, order.getStatus());
        assertEquals("idem-001", order.getIdempotencyKey());
        assertNotNull(order.getCreatedOn());
    }

    @Test
    @DisplayName("status can be updated")
    void statusCanBeUpdated() {
        Order order = new Order(1L, "AAPL", OrderSide.BUY, BigDecimal.ONE, BigDecimal.TEN, "idem-001");

        order.setStatus(OrderStatus.FILLED);

        assertEquals(OrderStatus.FILLED, order.getStatus());
    }
}
