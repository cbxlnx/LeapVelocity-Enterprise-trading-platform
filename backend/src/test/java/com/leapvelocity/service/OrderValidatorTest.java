package com.leapvelocity.service;

import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.enums.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("OrderValidator")
class OrderValidatorTest {

    private final OrderValidator validator = new OrderValidator();

    @Test
    @DisplayName("accepts a valid order and trims the symbol")
    void acceptsValidOrderAndTrimsSymbol() {
        Order order = new Order(1L, " AAPL ", OrderSide.BUY, new BigDecimal("10"), new BigDecimal("150.00"), "order-1");

        assertDoesNotThrow(() -> validator.validate(order));

        assertEquals("AAPL", order.getSymbol());
    }

    @Test
    @DisplayName("rejects a null order")
    void rejectsNullOrder() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
    }

    @Test
    @DisplayName("rejects missing required fields")
    void rejectsMissingRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                new Order(null, "AAPL", OrderSide.BUY, new BigDecimal("10"), new BigDecimal("150.00"), "order-1")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                new Order(1L, "AAPL", null, new BigDecimal("10"), new BigDecimal("150.00"), "order-1")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                new Order(1L, " ", OrderSide.BUY, new BigDecimal("10"), new BigDecimal("150.00"), "order-1")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("10"), new BigDecimal("150.00"), " ")));
    }

    @Test
    @DisplayName("rejects non-positive quantity and price")
    void rejectsNonPositiveQuantityAndPrice() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                new Order(1L, "AAPL", OrderSide.BUY, BigDecimal.ZERO, new BigDecimal("150.00"), "order-1")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("-10"), new BigDecimal("150.00"), "order-1")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("10"), BigDecimal.ZERO, "order-1")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("10"), new BigDecimal("-150.00"), "order-1")));
    }
}
