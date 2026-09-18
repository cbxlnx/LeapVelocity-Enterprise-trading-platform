package com.leapvelocity.service;

import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.AccountStatus;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import com.leapvelocity.exceptions.AccountNotActiveException;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.exceptions.DuplicateOrderException;
import com.leapvelocity.exceptions.InsufficientFundsException;
import com.leapvelocity.exceptions.InsufficientHoldingsException;
import com.leapvelocity.exceptions.InstrumentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for OrderExecutionService.
 * 
 * Tests cover:
 * - Buy and sell order execution with proper account debiting/crediting
 * - Account validation (status checks, existence verification)
 * - Input validation (null checks, quantity/price validation)
 * - Idempotency (duplicate order detection)
 * - Position management (creation, updates, closure)
 * - Complex multi-order integration scenarios
 * service uses in-memory storage (HashMaps), making it suitable
 * for unit testing without mocking.
 */
@DisplayName("OrderExecutionService")
class OrderExecutionServiceTest {

    private OrderExecutionService service;
    private Account activeAccount;
    private Instrument tradableInstrument;

    /**
     * Initializes test fixtures before each test.
     * Creates a fresh service instance with an active account and tradable instrument.
     */
    @BeforeEach
    void setUp() {
        service = new OrderExecutionService();
        activeAccount = new Account("ACC-001", "Alice", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        activeAccount.setId(1L);
        tradableInstrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);

        service.addAccount(activeAccount);
        service.addInstrument(tradableInstrument);
    }

    // ==================== BUY ORDER TESTS ====================
    // Verify buy orders correctly debit cash and create/update positions
    
    @Nested
    @DisplayName("Buy Orders")
    class BuyOrderTests {

        @Test
        @DisplayName("should debit account and create position on successful buy")
        void buyOrderSuccess() {
            // Verifies: Cash is debited, position is created with correct qty/cost
            Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "buy-001");

            Order result = service.placeOrder(order);

            assertEquals(OrderStatus.FILLED, result.getStatus());
            assertEquals(new BigDecimal("2500.00"), activeAccount.getCashBalance());

            Position position = service.getPosition(activeAccount.getId(), "AAPL");
            assertNotNull(position);
            assertEquals(new BigDecimal("50"), position.getQuantity());
            assertEquals(new BigDecimal("150.00"), position.getAverageCost());
        }

        @Test
        @DisplayName("should throw InsufficientFundsException when insufficient cash")
        void buyOrderInsufficientFunds() {
            // Verifies: Exception thrown, order rejected when account lacks funds
            Account poorAccount = new Account("ACC-POOR", "Bob", new BigDecimal("100.00"), AccountStatus.ACTIVE);
            poorAccount.setId(2L);
            service.addAccount(poorAccount);

            Order order = new Order(poorAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("100"), new BigDecimal("150.00"), "buy-poor");

            assertThrows(InsufficientFundsException.class, () -> service.placeOrder(order));
            assertEquals(OrderStatus.REJECTED, order.getStatus());
        }

        @Test
        @DisplayName("should throw InstrumentNotFoundException for non-tradable instrument")
        void buyOrderNonTradableInstrument() {
            // Verifies: Exception thrown, order rejected for delisted/non-tradable symbols
            Instrument nonTradable = new Instrument("DELISTED", "Delisted Corp", "EQUITY", "USD", false);
            service.addInstrument(nonTradable);

            Order order = new Order(activeAccount.getId(), "DELISTED", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("100.00"), "buy-delisted");

            assertThrows(InstrumentNotFoundException.class, () -> service.placeOrder(order));
            assertEquals(OrderStatus.REJECTED, order.getStatus());
        }
    }

    // ==================== SELL ORDER TESTS ====================
    // Verify sell orders correctly credit cash and reduce positions
    
    @Nested
    @DisplayName("Sell Orders")
    class SellOrderTests {

        @Test
        @DisplayName("should credit account and reduce position on successful sell")
        void sellOrderSuccess() {
            // Verifies: Cash is credited, position quantity decreases correctly
            Position position = new Position(activeAccount.getId(), "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
            service.addPosition(position);

            Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL,
                    new BigDecimal("50"), new BigDecimal("160.00"), "sell-001");

            Order result = service.placeOrder(order);

            assertEquals(OrderStatus.FILLED, result.getStatus());
            assertEquals(new BigDecimal("18000.00"), activeAccount.getCashBalance());

            Position updatedPosition = service.getPosition(activeAccount.getId(), "AAPL");
            assertEquals(new BigDecimal("50"), updatedPosition.getQuantity());
        }

        @Test
        @DisplayName("should throw InsufficientHoldingsException when insufficient shares")
        void sellOrderInsufficientHoldings() {
            // Verifies: Exception thrown when trying to sell more shares than held
            Position position = new Position(activeAccount.getId(), "AAPL", new BigDecimal("10"), new BigDecimal("150.00"));
            service.addPosition(position);

            Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL,
                    new BigDecimal("50"), new BigDecimal("160.00"), "sell-insufficient");

            assertThrows(InsufficientHoldingsException.class, () -> service.placeOrder(order));
        }

        @Test
        @DisplayName("should throw InsufficientHoldingsException when no position exists")
        void sellOrderNoPosition() {
            // Verifies: Exception thrown when selling security with no existing position
            Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL,
                    new BigDecimal("50"), new BigDecimal("160.00"), "sell-no-position");

            assertThrows(InsufficientHoldingsException.class, () -> service.placeOrder(order));
        }

        @Test
        @DisplayName("should remove position when all shares are sold")
        void sellOrderClosesPosition() {
            // Verifies: Position is removed (null) when quantity reaches zero
            Position position = new Position(activeAccount.getId(), "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
            service.addPosition(position);

            Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL,
                    new BigDecimal("100"), new BigDecimal("160.00"), "sell-all");

            service.placeOrder(order);

            assertNull(service.getPosition(activeAccount.getId(), "AAPL"));
        }
    }

    // ==================== ACCOUNT VALIDATION TESTS ====================
    // Verify account status checks prevent invalid orders
    
    @Nested
    @DisplayName("Account Validation")
    class AccountValidationTests {

        @Test
        @DisplayName("should throw AccountNotActiveException for suspended account")
        void orderSuspendedAccount() {
            // Verifies: Suspended accounts cannot place orders
            Account suspendedAccount = new Account("ACC-SUSPENDED", "Charlie",
                    new BigDecimal("10000.00"), AccountStatus.SUSPENDED);
            suspendedAccount.setId(3L);
            service.addAccount(suspendedAccount);

            Order order = new Order(suspendedAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "order-suspended");

            assertThrows(AccountNotActiveException.class, () -> service.placeOrder(order));
            assertEquals(OrderStatus.REJECTED, order.getStatus());
        }

        @Test
        @DisplayName("should throw AccountNotActiveException for closed account")
        void orderClosedAccount() {
            // Verifies: Closed accounts cannot place orders
            Account closedAccount = new Account("ACC-CLOSED", "Dave",
                    new BigDecimal("0.00"), AccountStatus.CLOSED);
            closedAccount.setId(4L);
            service.addAccount(closedAccount);

            Order order = new Order(closedAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "order-closed");

            assertThrows(AccountNotActiveException.class, () -> service.placeOrder(order));
            assertEquals(OrderStatus.REJECTED, order.getStatus());
        }

        @Test
        @DisplayName("should throw AccountNotFoundException for non-existent account")
        void orderNonExistentAccount() {
            // Verifies: Exception thrown when account ID doesn't exist
            Order order = new Order(999999L, "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "order-no-account");

            assertThrows(AccountNotFoundException.class, () -> service.placeOrder(order));
            assertEquals(OrderStatus.REJECTED, order.getStatus());
        }
    }

    // ==================== INPUT VALIDATION TESTS ====================
    // Verify invalid order parameters are rejected with appropriate exceptions
    
    @Nested
    @DisplayName("Input Validation")
    class InputValidationTests {

        @Test
        @DisplayName("should throw IllegalArgumentException for null order")
        void nullOrder() {
            // Verifies: Null order is rejected
            assertThrows(IllegalArgumentException.class, () -> service.placeOrder(null));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for null account ID")
        void nullAccountId() {
            // Verifies: Orders with null account ID are rejected
            Order order = new Order(null, "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "null-account");

            assertThrows(IllegalArgumentException.class, () -> service.placeOrder(order));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for blank symbol")
        void blankSymbol() {
            // Verifies: Orders with empty symbol are rejected
            Order order = new Order(activeAccount.getId(), "", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "blank-symbol");

            assertThrows(IllegalArgumentException.class, () -> service.placeOrder(order));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for blank idempotency key")
        void blankIdempotencyKey() {
            // Verifies: Orders without idempotency key are rejected
            Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "");

            assertThrows(IllegalArgumentException.class, () -> service.placeOrder(order));
        }

        // ========== Quantity Validation ==========
        @Nested
        @DisplayName("Quantity Validation")
        class QuantityValidationTests {

            @ParameterizedTest
            @ValueSource(strings = {"0", "-50", "-1"})
            @DisplayName("should reject zero or negative quantity")
            void invalidQuantity(String quantity) {
                // Verifies: Only positive quantities are accepted
                Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                        new BigDecimal(quantity), new BigDecimal("150.00"), "qty-" + quantity);

                assertThrows(IllegalArgumentException.class, () -> service.placeOrder(order));
            }
        }

        // ========== Price Validation ==========
        @Nested
        @DisplayName("Price Validation")
        class PriceValidationTests {

            @ParameterizedTest
            @ValueSource(strings = {"0", "-150", "-0.01"})
            @DisplayName("should reject zero or negative price")
            void invalidPrice(String price) {
                // Verifies: Only positive prices are accepted
                Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                        new BigDecimal("50"), new BigDecimal(price), "price-" + price);

                assertThrows(IllegalArgumentException.class, () -> service.placeOrder(order));
            }
        }
    }

    // ==================== IDEMPOTENCY TESTS ====================
    // Verify duplicate orders are rejected to prevent double-execution
    
    @Nested
    @DisplayName("Idempotency")
    class IdempotencyTests {

        @Test
        @DisplayName("should throw DuplicateOrderException for duplicate idempotency key")
        void duplicateIdempotencyKey() {
            // Verifies: Duplicate idempotency keys are detected and rejected
            Order order1 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "idem-dup");
            service.placeOrder(order1);

            Order order2 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "idem-dup");

            assertThrows(DuplicateOrderException.class, () -> service.placeOrder(order2));
            assertEquals(OrderStatus.REJECTED, order2.getStatus());
        }
    }

    // ==================== INTEGRATION TESTS ====================
    // Verify realistic multi-order sequences and complex scenarios
    
    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationTests {

        @Test
        @DisplayName("should combine multiple buy orders with correct average cost")
        void multipleBuysIncreasePosition() {
            // Verifies: Multiple buys correctly update position quantity and recalculate average cost
            Order order1 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "buy-1");
            service.placeOrder(order1);

            Order order2 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("15"), new BigDecimal("160.00"), "buy-2");
            service.placeOrder(order2);

            Position position = service.getPosition(activeAccount.getId(), "AAPL");
            assertEquals(new BigDecimal("65"), position.getQuantity());
            assertEquals(new BigDecimal("152.31"), position.getAverageCost());
        }

        @Test
        @DisplayName("should handle buy, partial sell, buy again sequence correctly")
        void buyPartialSellBuySequence() {
            // Verifies: Complex sequence of buy/sell/buy maintains correct cash balance and position
            Order buy1 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "buy-1");
            service.placeOrder(buy1);
            assertEquals(new BigDecimal("2500.00"), activeAccount.getCashBalance());

            Order sell1 = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL,
                    new BigDecimal("20"), new BigDecimal("160.00"), "sell-1");
            service.placeOrder(sell1);
            assertEquals(new BigDecimal("5700.00"), activeAccount.getCashBalance());

            Order buy2 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("10"), new BigDecimal("155.00"), "buy-2");
            service.placeOrder(buy2);

            Position position = service.getPosition(activeAccount.getId(), "AAPL");
            assertEquals(new BigDecimal("40"), position.getQuantity());
        }
    }

    // ==================== SERVICE INITIALIZATION TESTS ====================
    // Verify service setup and initialization validation
    
    @Nested
    @DisplayName("Service Initialization")
    class ServiceInitializationTests {

        @Test
        @DisplayName("should throw IllegalArgumentException when adding null account")
        void addNullAccount() {
            // Verifies: Service rejects null accounts during initialization
            assertThrows(IllegalArgumentException.class, () -> service.addAccount(null));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when adding instrument with blank symbol")
        void addInstrumentBlankSymbol() {
            // Verifies: Service rejects instruments with empty symbols
            Instrument instrument = new Instrument("", "Test", "EQUITY", "USD", true);
            assertThrows(IllegalArgumentException.class, () -> service.addInstrument(instrument));
        }
    }
}
