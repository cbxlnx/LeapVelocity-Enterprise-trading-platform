package com.leapvelocity.service;

import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.enums.OrderSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CHARACTERIZATION TEST: Sprint 6 OrderValidator Rules
 * 
 * The test captures:
 * - Required fields: accountId, side, symbol, idempotencyKey, quantity, price
 * - Validation rules: Non-blank strings, positive numbers
 * - Normalization: Symbol whitespace trimming
 *
 * Seed data context:
 * - Account: ACC-001 (Alice Johnson)
 * - Instrument: GLBEQ1 (Global Equity Index Fund)
 * - Typical order: BUY 100 shares @ $12.50
 */
@DisplayName("Characterization: Sprint 6 OrderValidator Rules")
class OrderValidatorCharacterizationTest {

    private OrderValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OrderValidator();
    }

    @Test
    @DisplayName("should accept valid order with all required fields present")
    void characterizeValidOrderAccepted() {
        // GIVEN: Valid order matching seed data (ACC-001, GLBEQ1, BUY)
        Order validOrder = new Order(
                1L,                              // accountId
                "GLBEQ1",                        // symbol (from seeds)
                OrderSide.BUY,                   // side
                new BigDecimal("100"),           // quantity
                new BigDecimal("12.50"),         // price (from seeds)
                "CHAR-VALIDATOR-001"             // idempotencyKey
        );

        // WHEN/THEN: Should pass validation without exception
        assertDoesNotThrow(
                () -> validator.validate(validOrder),
                "Valid order with all required fields should pass validation"
        );
    }

    @Test
    @DisplayName("should reject order with null accountId")
    void characterizeRejectNullAccountId() {
        // GIVEN: Order with null accountId
        Order order = new Order(
                null,                            // accountId is NULL
                "GLBEQ1",
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("12.50"),
                "CHAR-VALIDATOR-002"
        );

        // WHEN/THEN: Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(order),
                "Order with null accountId should fail validation"
        );
        assertTrue(exception.getMessage().contains("account id"),
                "Error message should mention account id");
    }

    @Test
    @DisplayName("should reject order with null side")
    void characterizeRejectNullSide() {
        // GIVEN: Order with null side (BUY/SELL)
        Order order = new Order(
                1L,
                "GLBEQ1",
                null,                            // side is NULL
                new BigDecimal("100"),
                new BigDecimal("12.50"),
                "CHAR-VALIDATOR-003"
        );

        // WHEN/THEN: Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(order),
                "Order with null side should fail validation"
        );
        assertTrue(exception.getMessage().contains("side"),
                "Error message should mention order side");
    }

    @Test
    @DisplayName("should reject order with null symbol")
    void characterizeRejectNullSymbol() {
        // GIVEN: Order with null symbol
        Order order = new Order(
                1L,
                null,                            // symbol is NULL
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("12.50"),
                "CHAR-VALIDATOR-004"
        );

        // WHEN/THEN: Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(order),
                "Order with null symbol should fail validation"
        );
        assertTrue(exception.getMessage().contains("symbol"),
                "Error message should mention symbol");
    }

    @Test
    @DisplayName("should reject order with blank symbol")
    void characterizeRejectBlankSymbol() {
        // GIVEN: Order with blank symbol (only whitespace)
        Order order = new Order(
                1L,
                "   ",                           // symbol is BLANK
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("12.50"),
                "CHAR-VALIDATOR-005"
        );

        // WHEN/THEN: Should throw IllegalArgumentException
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(order),
                "Order with blank symbol should fail validation"
        );
    }

    @Test
    @DisplayName("should reject order with empty symbol")
    void characterizeRejectEmptySymbol() {
        // GIVEN: Order with empty string symbol
        Order order = new Order(
                1L,
                "",                              // symbol is EMPTY
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("12.50"),
                "CHAR-VALIDATOR-006"
        );

        // WHEN/THEN: Should throw IllegalArgumentException
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(order),
                "Order with empty symbol should fail validation"
        );
    }

    @Test
    @DisplayName("should reject order with null idempotencyKey")
    void characterizeRejectNullIdempotencyKey() {
        // GIVEN: Order with null idempotencyKey
        Order order = new Order(
                1L,
                "GLBEQ1",
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("12.50"),
                null                             // idempotencyKey is NULL
        );

        // WHEN/THEN: Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(order),
                "Order with null idempotencyKey should fail validation"
        );
        assertTrue(exception.getMessage().contains("idempotency"),
                "Error message should mention idempotency key");
    }

    @Test
    @DisplayName("should reject order with blank idempotencyKey")
    void characterizeRejectBlankIdempotencyKey() {
        // GIVEN: Order with blank idempotencyKey
        Order order = new Order(
                1L,
                "GLBEQ1",
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("12.50"),
                "   "                            // idempotencyKey is BLANK
        );

        // WHEN/THEN: Should throw IllegalArgumentException
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(order),
                "Order with blank idempotencyKey should fail validation"
        );
    }

    @Test
    @DisplayName("should reject order with null quantity")
    void characterizeRejectNullQuantity() {
        // GIVEN: Order with null quantity
        Order order = new Order(
                1L,
                "GLBEQ1",
                OrderSide.BUY,
                null,                            // quantity is NULL
                new BigDecimal("12.50"),
                "CHAR-VALIDATOR-007"
        );

        // WHEN/THEN: Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(order),
                "Order with null quantity should fail validation"
        );
        assertTrue(exception.getMessage().contains("quantity"),
                "Error message should mention quantity");
    }

    @Test
    @DisplayName("should reject order with zero quantity")
    void characterizeRejectZeroQuantity() {
        // GIVEN: Order with zero quantity
        Order order = new Order(
                1L,
                "GLBEQ1",
                OrderSide.BUY,
                new BigDecimal("0"),             // quantity is ZERO
                new BigDecimal("12.50"),
                "CHAR-VALIDATOR-008"
        );

        // WHEN/THEN: Should throw IllegalArgumentException
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(order),
                "Order with zero quantity should fail validation"
        );
    }

    @Test
    @DisplayName("should reject order with negative quantity")
    void characterizeRejectNegativeQuantity() {
        // GIVEN: Order with negative quantity
        Order order = new Order(
                1L,
                "GLBEQ1",
                OrderSide.BUY,
                new BigDecimal("-100"),          // quantity is NEGATIVE
                new BigDecimal("12.50"),
                "CHAR-VALIDATOR-009"
        );

        // WHEN/THEN: Should throw IllegalArgumentException
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(order),
                "Order with negative quantity should fail validation"
        );
    }

    @Test
    @DisplayName("should accept order with fractional quantity")
    void characterizeAcceptFractionalQuantity() {
        // GIVEN: Order with fractional quantity (valid for fractional shares)
        Order order = new Order(
                1L,
                "GLBEQ1",
                OrderSide.BUY,
                new BigDecimal("0.5"),           // quantity is FRACTIONAL
                new BigDecimal("12.50"),
                "CHAR-VALIDATOR-010"
        );

        // WHEN/THEN: Should pass validation (positive > 0)
        assertDoesNotThrow(
                () -> validator.validate(order),
                "Order with fractional quantity > 0 should pass validation"
        );
    }

    @Test
    @DisplayName("should reject order with null price")
    void characterizeRejectNullPrice() {
        // GIVEN: Order with null price
        Order order = new Order(
                1L,
                "GLBEQ1",
                OrderSide.BUY,
                new BigDecimal("100"),
                null,                            // price is NULL
                "CHAR-VALIDATOR-011"
        );

        // WHEN/THEN: Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(order),
                "Order with null price should fail validation"
        );
        assertTrue(exception.getMessage().contains("price"),
                "Error message should mention price");
    }

    @Test
    @DisplayName("should reject order with zero price")
    void characterizeRejectZeroPrice() {
        // GIVEN: Order with zero price
        Order order = new Order(
                1L,
                "GLBEQ1",
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("0"),             // price is ZERO
                "CHAR-VALIDATOR-012"
        );

        // WHEN/THEN: Should throw IllegalArgumentException
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(order),
                "Order with zero price should fail validation"
        );
    }

    @Test
    @DisplayName("should reject order with negative price")
    void characterizeRejectNegativePrice() {
        // GIVEN: Order with negative price
        Order order = new Order(
                1L,
                "GLBEQ1",
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("-5.00"),         // price is NEGATIVE
                "CHAR-VALIDATOR-013"
        );

        // WHEN/THEN: Should throw IllegalArgumentException
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(order),
                "Order with negative price should fail validation"
        );
    }

    @Test
    @DisplayName("should accept order with high-precision price")
    void characterizeAcceptHighPrecisionPrice() {
        // GIVEN: Order with high-precision price (e.g., $1.234567)
        Order order = new Order(
                1L,
                "GLBEQ1",
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("1.234567"),      // price with 6 decimal places
                "CHAR-VALIDATOR-014"
        );

        // WHEN/THEN: Should pass validation (positive > 0)
        assertDoesNotThrow(
                () -> validator.validate(order),
                "Order with high-precision price > 0 should pass validation"
        );
    }

    @Test
    @DisplayName("should trim symbol whitespace (normalization)")
    void characterizeSymbolTrimmingNormalization() {
        // GIVEN: Order with symbol containing leading/trailing whitespace
        Order order = new Order(
                1L,
                "  GLBEQ1  ",                    // symbol with whitespace
                OrderSide.BUY,
                new BigDecimal("100"),
                new BigDecimal("12.50"),
                "CHAR-VALIDATOR-015"
        );

        // WHEN: Validate (which should trim)
        validator.validate(order);

        // THEN: Symbol should be trimmed
        assertEquals(
                "GLBEQ1",
                order.getSymbol(),
                "Symbol should be trimmed of leading/trailing whitespace after validation"
        );
    }

    @Test
    @DisplayName("should accept SELL orders (not just BUY)")
    void characterizeAcceptSellOrders() {
        // GIVEN: SELL order with all valid fields
        Order sellOrder = new Order(
                1L,
                "GLBEQ1",
                OrderSide.SELL,                  // SELL instead of BUY
                new BigDecimal("100"),
                new BigDecimal("12.50"),
                "CHAR-VALIDATOR-016"
        );

        // WHEN/THEN: Should pass validation
        assertDoesNotThrow(
                () -> validator.validate(sellOrder),
                "SELL order with valid fields should pass validation"
        );
    }
}