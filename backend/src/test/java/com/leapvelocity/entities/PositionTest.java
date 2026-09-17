package com.leapvelocity.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.leapvelocity.exceptions.InsufficientHoldingsException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Position Entity")
class PositionTest {

    @Test
    @DisplayName("increase for buy increases quantity and recalculates average cost")
    void increaseForBuyIncreasesQuantityAndRecalculatesAverageCost() {
        Position position = new Position(1L, "AAPL", new BigDecimal("10"), new BigDecimal("100.00"));

        position.increaseForBuy(new BigDecimal("5"), new BigDecimal("130.00"));

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
    @DisplayName("increase for buy and market value reject null values")
    void methodsRejectNullValues() {
        Position position = new Position(1L, "AAPL", new BigDecimal("10"), new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class, () -> position.increaseForBuy(null, new BigDecimal("100.00")));
        assertThrows(IllegalArgumentException.class, () -> position.increaseForBuy(BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class, () -> position.marketValue(null));
    }

    @Test
    @DisplayName("increase for buy rejects non-positive values")
    void increaseForBuyRejectsNonPositiveValues() {
        Position position = new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));

        assertThrows(IllegalArgumentException.class,
            () -> position.increaseForBuy(BigDecimal.ZERO, new BigDecimal("160.00")));
        assertThrows(IllegalArgumentException.class,
            () -> position.increaseForBuy(new BigDecimal("-30"), new BigDecimal("160.00")));
        assertThrows(IllegalArgumentException.class,
            () -> position.increaseForBuy(new BigDecimal("30"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> position.increaseForBuy(new BigDecimal("30"), new BigDecimal("-160.00")));
    }
}
