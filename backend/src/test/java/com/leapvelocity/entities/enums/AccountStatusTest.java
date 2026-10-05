package com.leapvelocity.entities.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AccountStatus Enum Tests")
class AccountStatusTest {

    @Test
    @DisplayName("should have ACTIVE status")
    void testActiveStatusExists() {
        assertNotNull(AccountStatus.ACTIVE);
        assertEquals("ACTIVE", AccountStatus.ACTIVE.name());
    }

    @Test
    @DisplayName("should have SUSPENDED status")
    void testSuspendedStatusExists() {
        assertNotNull(AccountStatus.SUSPENDED);
        assertEquals("SUSPENDED", AccountStatus.SUSPENDED.name());
    }

    @Test
    @DisplayName("should have CLOSED status")
    void testClosedStatusExists() {
        assertNotNull(AccountStatus.CLOSED);
        assertEquals("CLOSED", AccountStatus.CLOSED.name());
    }

    @Test
    @DisplayName("should have 3 enum values")
    void testEnumValuesCount() {
        AccountStatus[] values = AccountStatus.values();
        assertEquals(3, values.length);
    }

    @Test
    @DisplayName("should return correct value for valueOf ACTIVE")
    void testValueOfActive() {
        AccountStatus status = AccountStatus.valueOf("ACTIVE");
        assertEquals(AccountStatus.ACTIVE, status);
    }

    @Test
    @DisplayName("should return correct value for valueOf SUSPENDED")
    void testValueOfSuspended() {
        AccountStatus status = AccountStatus.valueOf("SUSPENDED");
        assertEquals(AccountStatus.SUSPENDED, status);
    }

    @Test
    @DisplayName("should return correct value for valueOf CLOSED")
    void testValueOfClosed() {
        AccountStatus status = AccountStatus.valueOf("CLOSED");
        assertEquals(AccountStatus.CLOSED, status);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for invalid valueOf")
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () -> AccountStatus.valueOf("INVALID"));
    }

    @Test
    @DisplayName("should have correct ordinal values")
    void testOrdinalValues() {
        assertEquals(0, AccountStatus.ACTIVE.ordinal());
        assertEquals(1, AccountStatus.SUSPENDED.ordinal());
        assertEquals(2, AccountStatus.CLOSED.ordinal());
    }

    @Test
    @DisplayName("should have consistent values array order")
    void testValuesArrayOrder() {
        AccountStatus[] values = AccountStatus.values();
        assertEquals(AccountStatus.ACTIVE, values[0]);
        assertEquals(AccountStatus.SUSPENDED, values[1]);
        assertEquals(AccountStatus.CLOSED, values[2]);
    }
}
