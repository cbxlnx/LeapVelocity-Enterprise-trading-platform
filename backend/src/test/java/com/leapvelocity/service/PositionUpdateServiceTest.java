package com.leapvelocity.service;

import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.exceptions.InsufficientHoldingsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for PositionUpdateService.
 * 
 * Tests cover:
 * - Position storage, retrieval, and lifecycle management
 * - Buy order logic with proper average cost calculation
 * - Sell order logic with position reduction and closure
 * - Multi-account and multi-symbol isolation
 * - Input validation for positions and operations
 * - Edge cases including fractional shares and high-precision decimals
 * 
 * The service uses in-memory HashMap storage, suitable for unit testing.
 */
@DisplayName("PositionUpdateService")
class PositionUpdateServiceTest {

    private PositionUpdateService service;

    /**
     * Initializes a fresh service instance before each test.
     */
    @BeforeEach
    void setUp() {
        service = new PositionUpdateService();
    }

    // ==================== POSITION STORAGE TESTS ====================
    // Verify positions can be added and retrieved correctly

    @Nested
    @DisplayName("Position Storage")
    class PositionStorageTests {

        @Test
        @DisplayName("should store position and retrieve it with exact values")
        void addPositionSuccess() {
            // Verifies: Position can be stored and retrieved with unchanged data
            Position position = new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
            service.addPosition(position);

            Position retrieved = service.getPosition(1L, "AAPL");
            assertNotNull(retrieved);
            assertEquals(new BigDecimal("100"), retrieved.getQuantity());
            assertEquals(new BigDecimal("150.00"), retrieved.getAverageCost());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when adding null position")
        void addNullPosition() {
            // Verifies: Null positions are rejected
            assertThrows(IllegalArgumentException.class, () -> service.addPosition(null));
        }
    }

    // ==================== BUY ORDER LOGIC TESTS ====================
    // Verify buy orders correctly create and update positions with proper average cost

    @Nested
    @DisplayName("Buy Order Logic")
    class BuyOrderLogicTests {

        @Test
        @DisplayName("should create new position when none exists")
        void applyBuyCreatesNewPosition() {
            // Verifies: First buy creates position with correct quantity and price as average cost
            Order order = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("100"), new BigDecimal("150.00"), "buy-1");

            service.applyBuy(order);

            Position position = service.getPosition(1L, "AAPL");
            assertNotNull(position);
            assertEquals(new BigDecimal("100"), position.getQuantity());
            assertEquals(new BigDecimal("150.00"), position.getAverageCost());
        }

        @Test
        @DisplayName("should update position and recalculate average cost on second buy")
        void applyBuyUpdatesPosition() {
            // Verifies: Multiple buys update quantity and recalculate weighted average cost
            Position position = new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
            service.addPosition(position);

            Order order = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("50"), new BigDecimal("160.00"), "buy-2");
            service.applyBuy(order);

            Position updated = service.getPosition(1L, "AAPL");
            assertEquals(new BigDecimal("150"), updated.getQuantity());
            // (100*150 + 50*160) / 150 = 23000 / 150 = 153.33
            assertEquals(new BigDecimal("153.33"), updated.getAverageCost());
        }

        @Test
        @DisplayName("should handle multiple sequential buy orders with correct average cost")
        void applyMultipleBuys() {
            // Verifies: Complex multi-buy scenario calculates weighted average correctly
            Order order1 = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("100"), new BigDecimal("100.00"), "buy-1");
            service.applyBuy(order1);

            Order order2 = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("100"), new BigDecimal("120.00"), "buy-2");
            service.applyBuy(order2);

            Order order3 = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("100"), new BigDecimal("140.00"), "buy-3");
            service.applyBuy(order3);

            Position position = service.getPosition(1L, "AAPL");
            assertEquals(new BigDecimal("300"), position.getQuantity());
            // (100*100 + 100*120 + 100*140) / 300 = 36000 / 300 = 120.00
            assertEquals(new BigDecimal("120.00"), position.getAverageCost());
        }
    }

    // ==================== SELL ORDER LOGIC TESTS ====================
    // Verify sell orders correctly reduce positions and handle edge cases

    @Nested
    @DisplayName("Sell Order Logic")
    class SellOrderLogicTests {

        @Test
        @DisplayName("should reduce position quantity on partial sell")
        void applySellReducesPosition() {
            // Verifies: Selling fewer shares than held reduces quantity correctly
            Position position = new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
            service.addPosition(position);

            Order order = new Order(1L, "AAPL", OrderSide.SELL, new BigDecimal("30"), new BigDecimal("160.00"), "sell-1");
            service.applySell(order);

            Position updated = service.getPosition(1L, "AAPL");
            assertEquals(new BigDecimal("70"), updated.getQuantity());
        }

        @Test
        @DisplayName("should remove position when all shares are sold")
        void applySellClosesPosition() {
            // Verifies: Selling all shares removes the position (returns null)
            Position position = new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
            service.addPosition(position);

            Order order = new Order(1L, "AAPL", OrderSide.SELL, new BigDecimal("100"), new BigDecimal("160.00"), "sell-all");
            service.applySell(order);

            assertNull(service.getPosition(1L, "AAPL"));
        }

        @Test
        @DisplayName("should throw InsufficientHoldingsException when no position exists")
        void applySellNoPosition() {
            // Verifies: Exception thrown when selling security with no existing position
            Order order = new Order(1L, "AAPL", OrderSide.SELL, new BigDecimal("50"), new BigDecimal("160.00"), "sell-no-pos");

            assertThrows(InsufficientHoldingsException.class, () -> service.applySell(order));
        }

        @Test
        @DisplayName("should throw InsufficientHoldingsException when selling more than held")
        void applySellInsufficientHoldings() {
            // Verifies: Exception thrown when quantity exceeds available holdings; position unchanged after failure
            Position position = new Position(1L, "AAPL", new BigDecimal("30"), new BigDecimal("150.00"));
            service.addPosition(position);

            Order order = new Order(1L, "AAPL", OrderSide.SELL, new BigDecimal("50"), new BigDecimal("160.00"), "sell-insuff");

            assertThrows(InsufficientHoldingsException.class, () -> service.applySell(order));

            Position unchanged = service.getPosition(1L, "AAPL");
            assertEquals(new BigDecimal("30"), unchanged.getQuantity());
        }

        @Test
        @DisplayName("should succeed when selling exact quantity held (boundary case)")
        void applySellExactHoldings() {
            // Verifies: Selling exactly the held amount (boundary case) succeeds and closes position
            Position position = new Position(1L, "AAPL", new BigDecimal("50"), new BigDecimal("150.00"));
            service.addPosition(position);

            Order order = new Order(1L, "AAPL", OrderSide.SELL, new BigDecimal("50"), new BigDecimal("160.00"), "sell-exact");

            assertDoesNotThrow(() -> service.applySell(order));
            assertNull(service.getPosition(1L, "AAPL"));
        }
    }

    // ==================== MULTI-ACCOUNT ISOLATION TESTS ====================
    // Verify positions for different accounts don't interfere with each other

    @Nested
    @DisplayName("Multi-Account Isolation")
    class MultiAccountIsolationTests {

        @Test
        @DisplayName("should keep positions isolated between different accounts")
        void multiAccountPositionsIsolated() {
            // Verifies: Operations on one account don't affect another account's positions
            Position pos1 = new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
            Position pos2 = new Position(2L, "AAPL", new BigDecimal("50"), new BigDecimal("140.00"));
            service.addPosition(pos1);
            service.addPosition(pos2);

            Order buy1 = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("25"), new BigDecimal("160.00"), "buy-acct1");
            service.applyBuy(buy1);

            Position acct1Pos = service.getPosition(1L, "AAPL");
            Position acct2Pos = service.getPosition(2L, "AAPL");

            assertEquals(new BigDecimal("125"), acct1Pos.getQuantity());
            assertEquals(new BigDecimal("50"), acct2Pos.getQuantity());
        }

        @Test
        @DisplayName("should return all positions for specified account only")
        void getPositionsForAccount() {
            // Verifies: getPositionsForAccount returns only positions belonging to that account
            service.addPosition(new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00")));
            service.addPosition(new Position(1L, "GOOGL", new BigDecimal("50"), new BigDecimal("2800.00")));
            service.addPosition(new Position(1L, "MSFT", new BigDecimal("75"), new BigDecimal("320.00")));
            service.addPosition(new Position(2L, "AAPL", new BigDecimal("30"), new BigDecimal("150.00")));

            List<Position> acct1Positions = service.getPositionsForAccount(1L);

            assertEquals(3, acct1Positions.size());
            assertTrue(acct1Positions.stream().allMatch(p -> p.getAccountId().equals(1L)));
        }

        @Test
        @DisplayName("should return empty list when account has no positions")
        void getPositionsForAccountEmpty() {
            // Verifies: Non-existent or empty accounts return empty list (never null)
            List<Position> positions = service.getPositionsForAccount(999L);

            assertNotNull(positions);
            assertEquals(0, positions.size());
        }
    }

    // ==================== MULTI-SYMBOL ISOLATION TESTS ====================
    // Verify different symbols in same account are independent

    @Nested
    @DisplayName("Multi-Symbol Isolation")
    class MultiSymbolIsolationTests {

        @Test
        @DisplayName("should keep positions isolated between different symbols")
        void multiSymbolPositionsIsolated() {
            // Verifies: Operations on one symbol don't affect other symbols for same account
            service.addPosition(new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00")));
            service.addPosition(new Position(1L, "GOOGL", new BigDecimal("50"), new BigDecimal("2800.00")));

            Order buyAapl = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("25"), new BigDecimal("160.00"), "buy-aapl");
            service.applyBuy(buyAapl);

            Position aaplPos = service.getPosition(1L, "AAPL");
            Position googlPos = service.getPosition(1L, "GOOGL");

            assertEquals(new BigDecimal("125"), aaplPos.getQuantity());
            assertEquals(new BigDecimal("50"), googlPos.getQuantity());
        }
    }

    // ==================== INPUT VALIDATION TESTS ====================
    // Verify invalid inputs are rejected with appropriate exceptions

    @Nested
    @DisplayName("Input Validation")
    class InputValidationTests {

        @Test
        @DisplayName("should throw IllegalArgumentException when adding position with null account ID")
        void addPositionNullAccountId() {
            // Verifies: Null account ID in position is rejected
            Position position = new Position(null, "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));

            assertThrows(IllegalArgumentException.class, () -> service.addPosition(position));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when adding position with blank symbol")
        void addPositionBlankSymbol() {
            // Verifies: Empty symbol in position is rejected
            Position position = new Position(1L, "", new BigDecimal("100"), new BigDecimal("150.00"));

            assertThrows(IllegalArgumentException.class, () -> service.addPosition(position));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when getting position with null account ID")
        void getPositionNullAccountId() {
            // Verifies: Null account ID in get operation is rejected
            assertThrows(IllegalArgumentException.class, () -> service.getPosition(null, "AAPL"));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when getting position with blank symbol")
        void getPositionBlankSymbol() {
            // Verifies: Empty symbol in get operation is rejected
            assertThrows(IllegalArgumentException.class, () -> service.getPosition(1L, ""));
        }
    }

    // ==================== EDGE CASES & SPECIAL SCENARIOS ====================
    // Verify system handles fractional shares, high precision, and extreme values

    @Nested
    @DisplayName("Edge Cases & Special Scenarios")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle fractional share quantities correctly")
        void fractionalShares() {
            // Verifies: Decimal quantities (fractional shares) work correctly
            Order order = new Order(1L, "AAPL", OrderSide.BUY,
                    new BigDecimal("0.5"), new BigDecimal("150.00"), "frac-1");
            service.applyBuy(order);

            Position position = service.getPosition(1L, "AAPL");
            assertEquals(new BigDecimal("0.5"), position.getQuantity());
        }

        @Test
        @DisplayName("should handle selling partial fractional shares")
        void sellFractionalShares() {
            // Verifies: Selling fractional portions maintains precision
            Position position = new Position(1L, "AAPL", new BigDecimal("1.5"), new BigDecimal("150.00"));
            service.addPosition(position);

            Order order = new Order(1L, "AAPL", OrderSide.SELL, new BigDecimal("0.5"), new BigDecimal("160.00"), "frac-sell");
            service.applySell(order);

            Position updated = service.getPosition(1L, "AAPL");
            assertEquals(0, updated.getQuantity().compareTo(new BigDecimal("1.0")));
        }

        @Test
        @DisplayName("should maintain high precision decimal prices in average cost")
        void highPrecisionPrices() {
            // Verifies: Prices with many decimal places are preserved in average cost
            Order order = new Order(1L, "AAPL", OrderSide.BUY,
                    new BigDecimal("100"), new BigDecimal("150.12345"), "precise-1");
            service.applyBuy(order);

            Position position = service.getPosition(1L, "AAPL");
            assertEquals(new BigDecimal("150.12345"), position.getAverageCost());
        }

        @Test
        @DisplayName("should handle very large quantities without overflow")
        void veryLargeQuantity() {
            // Verifies: Large numbers (1M+ shares) are handled without precision loss
            Order order = new Order(1L, "AAPL", OrderSide.BUY,
                    new BigDecimal("1000000"), new BigDecimal("150.00"), "large-qty");
            service.applyBuy(order);

            Position position = service.getPosition(1L, "AAPL");
            assertEquals(new BigDecimal("1000000"), position.getQuantity());
        }
    }
}
