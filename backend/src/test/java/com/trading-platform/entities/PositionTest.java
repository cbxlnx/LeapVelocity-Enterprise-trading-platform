package com.leapvelocity.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Position")
class PositionTest {

    @Test
    @DisplayName("apply increases quantity and recalculates average cost")
    void applyIncreasesQuantityAndRecalculatesAverageCost() {
        Position position = new Position(1L, "AAPL", new BigDecimal("10"), new BigDecimal("100.00"));

        position.apply(new BigDecimal("5"), new BigDecimal("130.00"));

        assertEquals(new BigDecimal("15"), position.getQuantity());
        assertEquals(new BigDecimal("110.00"), position.getAverageCost());
    }

    @Test
    @DisplayName("market value multiplies quantity by current price")
    void marketValueMultipliesQuantityByCurrentPrice() {
        Position position = new Position(1L, "AAPL", new BigDecimal("10"), new BigDecimal("100.00"));

        assertEquals(new BigDecimal("1500.00"), position.marketValue(new BigDecimal("150.00")));
    }

    @Test
    @DisplayName("apply and market value reject null values")
    void methodsRejectNullValues() {
        Position position = new Position(1L, "AAPL", new BigDecimal("10"), new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class, () -> position.apply(null, new BigDecimal("100.00")));
        assertThrows(IllegalArgumentException.class, () -> position.apply(BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class, () -> position.marketValue(null));
    }
}
