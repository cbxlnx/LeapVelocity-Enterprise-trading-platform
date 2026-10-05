package com.leapvelocity.service;

import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Execution;
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
import com.leapvelocity.messaging.ExecutionEvent;
import com.leapvelocity.messaging.OrderEventPublisher;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.ExecutionRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.repository.OrderRepository;
import com.leapvelocity.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization test suite for OrderExecutionService - Sprint 6.
 * 
 * Pins down core order placement behavior before Sprint 7 refactoring.
 * Tests cover:
 * - Buy and sell order execution with proper account debiting/crediting
 * - Account validation (status checks, existence verification)
 * - Input validation (null checks, blank values)
 * - Idempotency (duplicate order detection)
 * - Position management (creation, updates, closure)
 * - Integration scenarios (multi-order sequences)
 *
 * Uses mocked repositories backed by local Maps for focused testing.
 */
@DisplayName("Characterization: Sprint 6 Order Execution")
class OrderExecutionServiceTest {

    private OrderExecutionService service;
    private Account activeAccount;
    private Instrument tradableInstrument;
    private Map<Long, Account> accountsById;
    private Map<String, Instrument> instrumentsBySymbol;
    private Map<UUID, Order> ordersById;
    private Map<String, Order> ordersByIdempotencyKey;
    private Map<String, Position> positionsByKey;
    private OrderEventPublisher orderEventPublisher;
    private Map<UUID, Execution> executionsByOrderId;

    @BeforeEach
    void setUp() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        ExecutionRepository executionRepository = mock(ExecutionRepository.class);
        PositionRepository positionRepository = mock(PositionRepository.class);
        orderEventPublisher = mock(OrderEventPublisher.class);

        accountsById = new HashMap<>();
        instrumentsBySymbol = new HashMap<>();
        ordersById = new HashMap<>();
        ordersByIdempotencyKey = new HashMap<>();
        positionsByKey = new HashMap<>();
        executionsByOrderId = new HashMap<>();

        stubAccountRepository(accountRepository);
        stubInstrumentRepository(instrumentRepository);
        stubOrderRepository(orderRepository);
        stubExecutionRepository(executionRepository);
        stubPositionRepository(positionRepository);

        service = new OrderExecutionService(
                accountRepository,
                instrumentRepository,
                orderRepository,
                executionRepository,
                new PositionUpdateService(positionRepository),
                orderEventPublisher
        );

        activeAccount = new Account("ACC-001", "Alice", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        activeAccount.setId(1L);
        tradableInstrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);

        service.addAccount(activeAccount);
        service.addInstrument(tradableInstrument);
    }

    private void stubAccountRepository(AccountRepository accountRepository) {
        when(accountRepository.findById(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(accountsById.get(invocation.getArgument(0, Long.class))));
        when(accountRepository.existsById(anyLong())).thenAnswer(invocation ->
                accountsById.containsKey(invocation.getArgument(0, Long.class)));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0, Account.class);
            accountsById.put(account.getId(), account);
            return account;
        });
    }

    private void stubInstrumentRepository(InstrumentRepository instrumentRepository) {
        when(instrumentRepository.findBySymbol(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(instrumentsBySymbol.get(invocation.getArgument(0, String.class))));
        when(instrumentRepository.save(any(Instrument.class))).thenAnswer(invocation -> {
            Instrument instrument = invocation.getArgument(0, Instrument.class);
            instrumentsBySymbol.put(instrument.getSymbol(), instrument);
            return instrument;
        });
    }

    private void stubOrderRepository(OrderRepository orderRepository) {
        when(orderRepository.existsByIdempotencyKey(anyString())).thenAnswer(invocation ->
                ordersByIdempotencyKey.containsKey(invocation.getArgument(0, String.class)));
        when(orderRepository.findByIdempotencyKey(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(ordersByIdempotencyKey.get(invocation.getArgument(0, String.class))));
        when(orderRepository.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(ordersById.get(invocation.getArgument(0, UUID.class))));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0, Order.class);
            if (order.getId() == null) {
                order.setId(UUID.randomUUID());
            }
            ordersById.put(order.getId(), order);
            ordersByIdempotencyKey.put(order.getIdempotencyKey(), order);
            return order;
        });
    }

    private void stubPositionRepository(PositionRepository positionRepository) {
        when(positionRepository.findByAccountIdAndSymbol(anyLong(), anyString())).thenAnswer(invocation ->
                Optional.ofNullable(positionsByKey.get(positionKey(
                        invocation.getArgument(0, Long.class),
                        invocation.getArgument(1, String.class)))));
        when(positionRepository.save(any(Position.class))).thenAnswer(invocation -> {
            Position position = invocation.getArgument(0, Position.class);
            positionsByKey.put(positionKey(position.getAccountId(), position.getSymbol()), position);
            return position;
        });
        doAnswer(invocation -> {
            Position position = invocation.getArgument(0, Position.class);
            positionsByKey.remove(positionKey(position.getAccountId(), position.getSymbol()));
            return null;
        }).when(positionRepository).delete(any(Position.class));
    }

    private void stubExecutionRepository(ExecutionRepository executionRepository) {
        when(executionRepository.save(any(Execution.class))).thenAnswer(invocation -> {
            Execution execution = invocation.getArgument(0, Execution.class);
            if (execution.getId() == null) {
                execution.setId(UUID.randomUUID());
            }
            executionsByOrderId.put(execution.getOrderId(), execution);
            return execution;
        });
    }

    private String positionKey(Long accountId, String symbol) {
        return accountId + "|" + symbol.trim();
    }

    private Order placeAndSettle(Order order) {
        Order savedOrder = service.placeOrder(order);
        service.settleExecution(executionEventFor(savedOrder));
        return savedOrder;
    }

    private ExecutionEvent executionEventFor(Order order) {
        return new ExecutionEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getAccountId(),
                order.getSymbol(),
                order.getSide(),
                order.getQuantity(),
                order.getPrice(),
                order.getPrice(),
                "TEST",
                Instant.now()
        );
    }

    // ==================== BUY ORDER TESTS ====================
    
    @Nested
    @DisplayName("Buy Orders")
    class BuyOrderTests {

        @Test
        @DisplayName("should debit account and create position on successful buy")
        void buyOrderSuccess() {
            Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "buy-001");

            Order result = placeAndSettle(order);

            assertEquals(OrderStatus.FILLED, result.getStatus());
            assertEquals(new BigDecimal("2500.00"), activeAccount.getCashBalance());
            assertNotNull(executionsByOrderId.get(result.getId()));

            Position position = service.getPosition(activeAccount.getId(), "AAPL");
            assertNotNull(position);
            assertEquals(new BigDecimal("50"), position.getQuantity());
            assertEquals(new BigDecimal("150.00"), position.getAverageCost());
            verify(orderEventPublisher).publish(result);
        }

        @Test
        @DisplayName("should throw InsufficientFundsException when insufficient cash")
        void buyOrderInsufficientFunds() {
            Account poorAccount = new Account("ACC-POOR", "Bob", new BigDecimal("100.00"), AccountStatus.ACTIVE);
            poorAccount.setId(2L);
            service.addAccount(poorAccount);

            Order order = new Order(poorAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("100"), new BigDecimal("150.00"), "buy-poor");

            assertThrows(InsufficientFundsException.class, () -> placeAndSettle(order));
            assertEquals(OrderStatus.REJECTED, order.getStatus());
            verify(orderEventPublisher, never()).publish(order);
            assertTrue(executionsByOrderId.isEmpty());
        }

        @Test
        @DisplayName("should throw InstrumentNotFoundException for non-tradable instrument")
        void buyOrderNonTradableInstrument() {
            Instrument nonTradable = new Instrument("DELISTED", "Delisted Corp", "EQUITY", "USD", false);
            service.addInstrument(nonTradable);

            Order order = new Order(activeAccount.getId(), "DELISTED", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("100.00"), "buy-delisted");

            assertThrows(InstrumentNotFoundException.class, () -> placeAndSettle(order));
            assertEquals(OrderStatus.REJECTED, order.getStatus());
        }
    }

    // ==================== SELL ORDER TESTS ====================
    
    @Nested
    @DisplayName("Sell Orders")
    class SellOrderTests {

        @Test
        @DisplayName("should credit account and reduce position on successful sell")
        void sellOrderSuccess() {
            Position position = new Position(activeAccount.getId(), "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
            service.addPosition(position);

            Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL,
                    new BigDecimal("50"), new BigDecimal("160.00"), "sell-001");

            Order result = placeAndSettle(order);

            assertEquals(OrderStatus.FILLED, result.getStatus());
            assertEquals(new BigDecimal("18000.00"), activeAccount.getCashBalance());
            assertNotNull(executionsByOrderId.get(result.getId()));

            Position updatedPosition = service.getPosition(activeAccount.getId(), "AAPL");
            assertEquals(new BigDecimal("50"), updatedPosition.getQuantity());
        }

        @Test
        @DisplayName("should throw InsufficientHoldingsException when insufficient shares")
        void sellOrderInsufficientHoldings() {
            Position position = new Position(activeAccount.getId(), "AAPL", new BigDecimal("10"), new BigDecimal("150.00"));
            service.addPosition(position);

            Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL,
                    new BigDecimal("50"), new BigDecimal("160.00"), "sell-insufficient");

            assertThrows(InsufficientHoldingsException.class, () -> placeAndSettle(order));
        }

        @Test
        @DisplayName("should throw InsufficientHoldingsException when no position exists")
        void sellOrderNoPosition() {
            Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL,
                    new BigDecimal("50"), new BigDecimal("160.00"), "sell-no-position");

            assertThrows(InsufficientHoldingsException.class, () -> placeAndSettle(order));
        }

        @Test
        @DisplayName("should remove position when all shares are sold")
        void sellOrderClosesPosition() {
            Position position = new Position(activeAccount.getId(), "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
            service.addPosition(position);

            Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL,
                    new BigDecimal("100"), new BigDecimal("160.00"), "sell-all");

            placeAndSettle(order);

            assertNull(service.getPosition(activeAccount.getId(), "AAPL"));
        }
    }

    // ==================== ACCOUNT VALIDATION TESTS ====================
    
    @Nested
    @DisplayName("Account Validation")
    class AccountValidationTests {

        @Test
        @DisplayName("should throw AccountNotActiveException for suspended account")
        void orderSuspendedAccount() {
            Account suspendedAccount = new Account("ACC-SUSPENDED", "Charlie",
                    new BigDecimal("10000.00"), AccountStatus.SUSPENDED);
            suspendedAccount.setId(3L);
            service.addAccount(suspendedAccount);

            Order order = new Order(suspendedAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "order-suspended");

            assertThrows(AccountNotActiveException.class, () -> placeAndSettle(order));
            assertEquals(OrderStatus.REJECTED, order.getStatus());
        }

        @Test
        @DisplayName("should throw AccountNotActiveException for closed account")
        void orderClosedAccount() {
            Account closedAccount = new Account("ACC-CLOSED", "Dave",
                    new BigDecimal("0.00"), AccountStatus.CLOSED);
            closedAccount.setId(4L);
            service.addAccount(closedAccount);

            Order order = new Order(closedAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "order-closed");

            assertThrows(AccountNotActiveException.class, () -> placeAndSettle(order));
            assertEquals(OrderStatus.REJECTED, order.getStatus());
        }

        @Test
        @DisplayName("should throw AccountNotFoundException for non-existent account")
        void orderNonExistentAccount() {
            Order order = new Order(999999L, "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "order-no-account");

            assertThrows(AccountNotFoundException.class, () -> placeAndSettle(order));
            assertEquals(OrderStatus.REJECTED, order.getStatus());
        }
    }

    // ==================== INPUT VALIDATION TESTS ====================
    
    @Nested
    @DisplayName("Input Validation")
    class InputValidationTests {

        @Test
        @DisplayName("should throw IllegalArgumentException for null order")
        void nullOrder() {
            assertThrows(IllegalArgumentException.class, () -> placeAndSettle(null));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for null account ID")
        void nullAccountId() {
            Order order = new Order(null, "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "null-account");

            assertThrows(IllegalArgumentException.class, () -> placeAndSettle(order));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for blank symbol")
        void blankSymbol() {
            Order order = new Order(activeAccount.getId(), "", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "blank-symbol");

            assertThrows(IllegalArgumentException.class, () -> placeAndSettle(order));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for blank idempotency key")
        void blankIdempotencyKey() {
            Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "");

            assertThrows(IllegalArgumentException.class, () -> placeAndSettle(order));
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

                assertThrows(IllegalArgumentException.class, () -> placeAndSettle(order));
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

                assertThrows(IllegalArgumentException.class, () -> placeAndSettle(order));
            }
        }
    }

    // ==================== IDEMPOTENCY TESTS ====================
    
    @Nested
    @DisplayName("Idempotency")
    class IdempotencyTests {

        @Test
        @DisplayName("should throw DuplicateOrderException for duplicate idempotency key")
        void duplicateIdempotencyKey() {
            Order order1 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "idem-dup");
            placeAndSettle(order1);

            Order order2 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "idem-dup");

            assertThrows(DuplicateOrderException.class, () -> placeAndSettle(order2));
            assertEquals(OrderStatus.REJECTED, order2.getStatus());
        }
    }

    // ==================== EDGE CASES: EXACTLY SUFFICIENT CASH ====================

    @Nested
    @DisplayName("Edge Cases - Exactly Sufficient Cash")
    class ExactlySufficientCashTests {

        @Test
        @DisplayName("should reduce balance to exactly zero when cash matches notional")
        void buyWithExactCash() {
            Account exactAccount = new Account("ACC-EXACT", "Exact Trader", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
            exactAccount.setId(5L);
            service.addAccount(exactAccount);

            Instrument instrument = new Instrument("TEST", "Test Instrument", "EQUITY", "USD", true);
            service.addInstrument(instrument);

            Order order = new Order(exactAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("100"), new BigDecimal("100.00"), "exact-cash-001");

            Order result = placeAndSettle(order);

            assertEquals(OrderStatus.FILLED, result.getStatus());
            assertEquals(0, exactAccount.getCashBalance().compareTo(BigDecimal.ZERO));

            Position position = service.getPosition(exactAccount.getId(), "TEST");
            assertNotNull(position);
            assertEquals(new BigDecimal("100"), position.getQuantity());
            assertEquals(new BigDecimal("100.00"), position.getAverageCost());
        }

        @Test
        @DisplayName("should handle second order rejection when balance is zero")
        void orderRejectedAfterZeroBalance() {
            Account limitedAccount = new Account("ACC-LIMITED", "Limited Funds",
                    new BigDecimal("1000.00"), AccountStatus.ACTIVE);
            limitedAccount.setId(6L);
            service.addAccount(limitedAccount);

            Instrument instrument = new Instrument("TEST2", "Test Instrument 2", "EQUITY", "USD", true);
            service.addInstrument(instrument);

            Order order1 = new Order(limitedAccount.getId(), "TEST2", OrderSide.BUY,
                    new BigDecimal("10"), new BigDecimal("100.00"), "zero-bal-001");
            placeAndSettle(order1);
            assertEquals(0, limitedAccount.getCashBalance().compareTo(BigDecimal.ZERO));

            Order order2 = new Order(limitedAccount.getId(), "TEST2", OrderSide.BUY,
                    new BigDecimal("1"), new BigDecimal("50.00"), "zero-bal-002");

            assertThrows(InsufficientFundsException.class,
                    () -> placeAndSettle(order2));
        }
    }

    // ==================== INTEGRATION SCENARIOS ====================
    
    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationTests {

        @Test
        @DisplayName("should combine multiple buy orders with correct average cost")
        void multipleBuysIncreasePosition() {
            Order order1 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "buy-1");
            placeAndSettle(order1);

            Order order2 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("15"), new BigDecimal("160.00"), "buy-2");
            placeAndSettle(order2);

            Position position = service.getPosition(activeAccount.getId(), "AAPL");
            assertEquals(new BigDecimal("65"), position.getQuantity());
            assertEquals(new BigDecimal("152.31"), position.getAverageCost());
        }

        @Test
        @DisplayName("should handle buy, partial sell, buy again sequence correctly")
        void buyPartialSellBuySequence() {
            Order buy1 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("150.00"), "buy-1");
            placeAndSettle(buy1);
            assertEquals(new BigDecimal("2500.00"), activeAccount.getCashBalance());

            Order sell1 = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL,
                    new BigDecimal("20"), new BigDecimal("160.00"), "sell-1");
            placeAndSettle(sell1);
            assertEquals(new BigDecimal("5700.00"), activeAccount.getCashBalance());

            Order buy2 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                    new BigDecimal("10"), new BigDecimal("155.00"), "buy-2");
            placeAndSettle(buy2);

            Position position = service.getPosition(activeAccount.getId(), "AAPL");
            assertEquals(new BigDecimal("40"), position.getQuantity());
        }
    }

    // ==================== SERVICE INITIALIZATION ====================
    
    @Nested
    @DisplayName("Service Initialization")
    class ServiceInitializationTests {

        @Test
        @DisplayName("should throw IllegalArgumentException when adding null account")
        void addNullAccount() {
            assertThrows(IllegalArgumentException.class, () -> service.addAccount(null));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when adding instrument with blank symbol")
        void addInstrumentBlankSymbol() {
            Instrument instrument = new Instrument("", "Test", "EQUITY", "USD", true);
            assertThrows(IllegalArgumentException.class, () -> service.addInstrument(instrument));
        }
    }
}
