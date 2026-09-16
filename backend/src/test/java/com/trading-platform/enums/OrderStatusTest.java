package com.leapvelocity.entities.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OrderStatus Enum Tests")
class OrderStatusTest {

    @Test
    @DisplayName("should have NEW status")
    void testNewStatusExists() {
        assertNotNull(OrderStatus.NEW);
        assertEquals("NEW", OrderStatus.NEW.name());
    }

    @Test
    @DisplayName("should have FILLED status")
    void testFilledStatusExists() {
        assertNotNull(OrderStatus.FILLED);
        assertEquals("FILLED", OrderStatus.FILLED.name());
    }

    @Test
    @DisplayName("should have REJECTED status")
    void testRejectedStatusExists() {
        assertNotNull(OrderStatus.REJECTED);
        assertEquals("REJECTED", OrderStatus.REJECTED.name());
    }

    @Test
    @DisplayName("should have CANCELLED status")
    void testCancelledStatusExists() {
        assertNotNull(OrderStatus.CANCELLED);
        assertEquals("CANCELLED", OrderStatus.CANCELLED.name());
    }

    @Test
    @DisplayName("should have 4 enum values")
    void testEnumValuesCount() {
        OrderStatus[] values = OrderStatus.values();
        assertEquals(4, values.length);
    }

    @Test
    @DisplayName("should return correct value for valueOf NEW")
    void testValueOfNew() {
        OrderStatus status = OrderStatus.valueOf("NEW");
        assertEquals(OrderStatus.NEW, status);
    }

    @Test
    @DisplayName("should return correct value for valueOf FILLED")
    void testValueOfFilled() {
        OrderStatus status = OrderStatus.valueOf("FILLED");
        assertEquals(OrderStatus.FILLED, status);
    }

    @Test
    @DisplayName("should return correct value for valueOf REJECTED")
    void testValueOfRejected() {
        OrderStatus status = OrderStatus.valueOf("REJECTED");
        assertEquals(OrderStatus.REJECTED, status);
    }

    @Test
    @DisplayName("should return correct value for valueOf CANCELLED")
    void testValueOfCancelled() {
        OrderStatus status = OrderStatus.valueOf("CANCELLED");
        assertEquals(OrderStatus.CANCELLED, status);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for invalid valueOf")
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () -> OrderStatus.valueOf("PENDING"));
    }

    @Test
    @DisplayName("should have correct ordinal values")
    void testOrdinalValues() {
        assertEquals(0, OrderStatus.NEW.ordinal());
        assertEquals(1, OrderStatus.FILLED.ordinal());
        assertEquals(2, OrderStatus.REJECTED.ordinal());
        assertEquals(3, OrderStatus.CANCELLED.ordinal());
    }

    @Test
    @DisplayName("should have consistent values array order")
    void testValuesArrayOrder() {
        OrderStatus[] values = OrderStatus.values();
        assertEquals(OrderStatus.NEW, values[0]);
        assertEquals(OrderStatus.FILLED, values[1]);
        assertEquals(OrderStatus.REJECTED, values[2]);
        assertEquals(OrderStatus.CANCELLED, values[3]);
    }

    @Test
    @DisplayName("should correctly identify terminal statuses")
    void testTerminalStatusIdentification() {
        assertTrue(isTerminalStatus(OrderStatus.FILLED));
        assertTrue(isTerminalStatus(OrderStatus.REJECTED));
        assertTrue(isTerminalStatus(OrderStatus.CANCELLED));
        assertFalse(isTerminalStatus(OrderStatus.NEW));
    }

    private boolean isTerminalStatus(OrderStatus status) {
        return status == OrderStatus.FILLED || 
               status == OrderStatus.REJECTED || 
               status == OrderStatus.CANCELLED;
    }
}
