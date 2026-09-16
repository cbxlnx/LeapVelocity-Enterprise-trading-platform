package com.leapvelocity.entities.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OrderSide Enum Tests")
class OrderSideTest {

    @Test
    @DisplayName("should have BUY side")
    void testBuySideExists() {
        assertNotNull(OrderSide.BUY);
        assertEquals("BUY", OrderSide.BUY.name());
    }

    @Test
    @DisplayName("should have SELL side")
    void testSellSideExists() {
        assertNotNull(OrderSide.SELL);
        assertEquals("SELL", OrderSide.SELL.name());
    }

    @Test
    @DisplayName("should have 2 enum values")
    void testEnumValuesCount() {
        OrderSide[] values = OrderSide.values();
        assertEquals(2, values.length);
    }

    @Test
    @DisplayName("should return correct value for valueOf BUY")
    void testValueOfBuy() {
        OrderSide side = OrderSide.valueOf("BUY");
        assertEquals(OrderSide.BUY, side);
    }

    @Test
    @DisplayName("should return correct value for valueOf SELL")
    void testValueOfSell() {
        OrderSide side = OrderSide.valueOf("SELL");
        assertEquals(OrderSide.SELL, side);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for invalid valueOf")
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () -> OrderSide.valueOf("HOLD"));
    }

    @Test
    @DisplayName("should have correct ordinal values")
    void testOrdinalValues() {
        assertEquals(0, OrderSide.BUY.ordinal());
        assertEquals(1, OrderSide.SELL.ordinal());
    }

    @Test
    @DisplayName("should have consistent values array order")
    void testValuesArrayOrder() {
        OrderSide[] values = OrderSide.values();
        assertEquals(OrderSide.BUY, values[0]);
        assertEquals(OrderSide.SELL, values[1]);
    }

    @Test
    @DisplayName("should be able to use ordinal for comparison")
    void testOrdinalComparison() {
        assertTrue(OrderSide.BUY.ordinal() < OrderSide.SELL.ordinal());
    }
}
