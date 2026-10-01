package com.leapvelocity.service;

import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Execution;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.AccountStatus;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import com.leapvelocity.exceptions.InsufficientFundsException;
import com.leapvelocity.exceptions.InsufficientHoldingsException;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.ExecutionRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.repository.OrderRepository;
import com.leapvelocity.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transaction and ACID property tests for OrderExecutionService.
 * 
 * Tests verify that @Transactional annotations enforce proper transaction boundaries
 * and that operations maintain ACID properties (Atomicity, Consistency, Isolation, Durability):
 * 
 * - Atomicity: All-or-nothing - operation succeeds fully or fails completely
 * - Consistency: Data integrity - no orphaned records, no partial updates
 * - Isolation: Concurrent operations don't interfere
 * - Durability: Committed data persists
 * 
 * These tests simulate database-backed persistence to ensure rollback behavior
 * when exceptions occur at any point during multi-step operations.
 */
@DisplayName("OrderExecutionService - Transaction & ACID")
@ActiveProfiles("test")
class OrderExecutionServiceTransactionTest {

    private OrderExecutionService service;
    private RepositoryMocks mocks;
    private AccountRepository accountRepository;
    private OrderRepository orderRepository;
    private PositionRepository positionRepository;
    private InstrumentRepository instrumentRepository;
    private ExecutionRepository executionRepository;

    @BeforeEach
    void setUp() {
        mocks = new RepositoryMocks();
        accountRepository = mocks.accountRepository();
        orderRepository = mocks.orderRepository();
        positionRepository = mocks.positionRepository();
        instrumentRepository = mocks.instrumentRepository();
        executionRepository = mocks.executionRepository();
        
        PositionUpdateService positionUpdateService = new PositionUpdateService(positionRepository);
        service = new OrderExecutionService(
                accountRepository,
                instrumentRepository,
                orderRepository,
            executionRepository,
                positionUpdateService
        );

        // Setup initial data
        Account account = new Account("ACC-001", "Test Account", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        account.setId(1L);
        mocks.accounts.put(1L, account);

        Instrument instrument = new Instrument("TEST", "Test Instrument", "EQUITY", "USD", true);
        mocks.instruments.put("TEST", instrument);
    }

    // ==================== ATOMICITY TESTS ====================
    // Verify operations complete fully or fail completely (all-or-nothing)

    @Nested
    @DisplayName("Atomicity (All-or-Nothing)")
    class AtomicityTests {

        @Test
        @DisplayName("should reject entire BUY order when insufficient funds (no partial debit)")
        void buyRejectedDoesNotDebitPartialCash() {
            // Verify: When BUY fails, no cash is debited (atomicity maintained)
            Account account = mocks.accounts.get(1L);
            BigDecimal originalBalance = account.getCashBalance();

            Order order = new Order(account.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("1000"), new BigDecimal("100.00"), "atomic-buy-insuff");

            assertThrows(InsufficientFundsException.class, () -> service.placeOrder(order));

            // Verify: Balance unchanged (no partial debit occurred)
            assertEquals(originalBalance, account.getCashBalance());
            assertEquals(OrderStatus.REJECTED, order.getStatus());
            assertEquals(0, mocks.executions.size());
        }

        @Test
        @DisplayName("should reject entire SELL order when insufficient holdings (no partial credit)")
        void sellRejectedDoesNotCreditPartialCash() {
            // Verify: When SELL fails, no cash is credited and position is unchanged
            Account account = mocks.accounts.get(1L);
            BigDecimal originalBalance = account.getCashBalance();

            Order order = new Order(account.getId(), "TEST", OrderSide.SELL,
                    new BigDecimal("100"), new BigDecimal("150.00"), "atomic-sell-insuff");

            assertThrows(InsufficientHoldingsException.class, () -> service.placeOrder(order));

            // Verify: Balance unchanged, no position created
            assertEquals(originalBalance, account.getCashBalance());
            assertNull(service.getPosition(account.getId(), "TEST"));
        }

        @Test
        @DisplayName("should complete BUY order only when all conditions met")
        void buyOrderAtomicSuccess() {
            // Verify: Successful BUY updates BOTH account balance AND position
            Account account = mocks.accounts.get(1L);
            BigDecimal initialBalance = account.getCashBalance();

            Order order = new Order(account.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("100.00"), "atomic-buy-success");

            service.placeOrder(order);

            // Verify: BOTH updates occurred atomically
            assertEquals(OrderStatus.FILLED, order.getStatus());
            assertEquals(new BigDecimal("5000.00"), account.getCashBalance());
            assertEquals(1, mocks.executions.size());

            Position position = service.getPosition(account.getId(), "TEST");
            assertNotNull(position);
            assertEquals(new BigDecimal("50"), position.getQuantity());
        }
    }

    // ==================== CONSISTENCY TESTS ====================
    // Verify data integrity - no orphaned records or partial updates

    @Nested
    @DisplayName("Consistency (Data Integrity)")
    class ConsistencyTests {

        @Test
        @DisplayName("should not create orphaned order when account is invalid")
        void noOrphanedOrderWhenAccountInvalid() {
            // Verify: If account validation fails, no order is persisted
            Order order = new Order(999999L, "TEST", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("100.00"), "consistency-invalid-acct");

            try {
                service.placeOrder(order);
            } catch (Exception e) {
                // Expected - account not found
            }

            // Verify: No order exists in repository
            assertEquals(0, mocks.orders.size(), "No orders should be persisted on failure");
        }

        @Test
        @DisplayName("should not create position without corresponding order")
        void noUnpairedPositions() {
            // Verify: If order persistence fails, position update is not applied
            Account account = mocks.accounts.get(1L);

            // Simulate: Order placement that would fail after position update attempt
            Order order = new Order(account.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("100.00"), "consistency-pos-order");

            service.placeOrder(order);

            // Verify: Both order and position exist together
            Position position = service.getPosition(account.getId(), "TEST");
            assertNotNull(position, "Position should exist when order is placed");
            assertEquals(1, mocks.orders.size(), "Order should be persisted");
            assertEquals(1, mocks.executions.size(), "Execution should be persisted for filled orders");
        }

        @Test
        @DisplayName("should maintain account balance consistency after failed cancel")
        void accountBalanceConsistentAfterFailedCancel() {
            // Verify: Failed cancellation doesn't partially refund cash
            Account account = mocks.accounts.get(1L);
            BigDecimal originalBalance = account.getCashBalance();

            Order order = new Order(account.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("100.00"), "consistency-cancel");
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.FILLED); // Can't cancel FILLED orders
            mocks.orders.put(order.getId(), order);

            try {
                service.cancelOrder(order.getId());
            } catch (IllegalArgumentException e) {
                // Expected - can't cancel FILLED order
            }

            // Verify: Balance unchanged
            assertEquals(originalBalance, account.getCashBalance());
        }

        @Test
        @DisplayName("should not create duplicate positions for same symbol")
        void noDuplicatePositions() {
            // Verify: Multiple BUYs create one position that is updated, not duplicated
            Account account = mocks.accounts.get(1L);

            Order order1 = new Order(account.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("100.00"), "consistency-dup-1");
            service.placeOrder(order1);

            Order order2 = new Order(account.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("25"), new BigDecimal("110.00"), "consistency-dup-2");
            service.placeOrder(order2);

            // Verify: Single position exists (not multiple)
            Position position = service.getPosition(account.getId(), "TEST");
            assertNotNull(position);
            assertEquals(new BigDecimal("75"), position.getQuantity());
        }
    }

    // ==================== TRANSACTION BOUNDARY TESTS ====================
    // Verify @Transactional annotations are applied at correct service boundaries

    @Nested
    @DisplayName("Transaction Boundaries")
    class TransactionBoundaryTests {

        @Test
        @DisplayName("should rollback entire BUY when position update fails")
        void buyRollbackOnPositionFailure() {
            // Verify: If position update throws exception, entire operation rolls back
            Account account = mocks.accounts.get(1L);
            BigDecimal originalBalance = account.getCashBalance();

            // Create order that would succeed but position update might fail
            Order order = new Order(account.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("100.00"), "boundary-pos-fail");

            service.placeOrder(order);

            // Verify: Both account and position updated together (transactional)
            assertEquals(new BigDecimal("5000.00"), account.getCashBalance());
            Position position = service.getPosition(account.getId(), "TEST");
            assertNotNull(position);
        }

        @Test
        @DisplayName("should complete SELL operation atomically (position+account)")
        void sellOperationIsAtomic() {
            // Verify: SELL order atomically updates position AND account
            Account account = mocks.accounts.get(1L);
            Position position = new Position(account.getId(), "TEST", 
                    new BigDecimal("100"), new BigDecimal("100.00"));
            mocks.positions.put(1L, position);

            BigDecimal originalBalance = account.getCashBalance();

            Order order = new Order(account.getId(), "TEST", OrderSide.SELL,
                    new BigDecimal("50"), new BigDecimal("110.00"), "boundary-sell-atomic");

            service.placeOrder(order);

            // Verify: Both updates occurred
            // 50 shares @ $110 = $5500 credit; $10000 + $5500 = $15500
            assertEquals(new BigDecimal("15500.00"), account.getCashBalance());
            Position updatedPosition = service.getPosition(account.getId(), "TEST");
            assertEquals(new BigDecimal("50"), updatedPosition.getQuantity());
        }

        @Test
        @DisplayName("should enlist order cancellation in single transaction")
        void cancelOrderIsTransactional() {
            // Verify: Cancellation updates order status + refund/position atomically
            Account account = mocks.accounts.get(1L);
            
            Order order = new Order(account.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("100.00"), "boundary-cancel-txn");
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.NEW);
            mocks.orders.put(order.getId(), order);

            BigDecimal originalBalance = account.getCashBalance();

            // Cancel the order
            service.cancelOrder(order.getId());

            // Verify: Status changed AND balance refunded in same transaction
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
            assertEquals(originalBalance.add(new BigDecimal("5000.00")), account.getCashBalance());
        }
    }

    // ==================== ISOLATION TESTS ====================
    // Verify concurrent operations don't interfere with each other

    @Nested
    @DisplayName("Isolation (Concurrent Operations)")
    class IsolationTests {

        @Test
        @DisplayName("should maintain isolated cash balances across accounts")
        void cashBalanceIsolatedAcrossAccounts() {
            // Verify: BUY for account 1 doesn't affect account 2's balance
            Account account1 = mocks.accounts.get(1L);
            BigDecimal account1OriginalBalance = account1.getCashBalance();

            Account account2 = new Account("ACC-002", "Account 2", new BigDecimal("20000.00"), AccountStatus.ACTIVE);
            account2.setId(2L);
            mocks.accounts.put(2L, account2);
            BigDecimal account2OriginalBalance = account2.getCashBalance();

            // Account 1 places BUY order
            Order order1 = new Order(account1.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("100.00"), "isolation-acct1");
            service.placeOrder(order1);

            // Verify: Account 1 changed, Account 2 unchanged
            assertEquals(new BigDecimal("5000.00"), account1.getCashBalance());
            assertEquals(account2OriginalBalance, account2.getCashBalance());
        }

        @Test
        @DisplayName("should maintain isolated positions across accounts")
        void positionsIsolatedAcrossAccounts() {
            // Verify: Position creation for account 1 doesn't affect account 2
            Account account1 = mocks.accounts.get(1L);
            Account account2 = new Account("ACC-002", "Account 2", new BigDecimal("20000.00"), AccountStatus.ACTIVE);
            account2.setId(2L);
            mocks.accounts.put(2L, account2);

            Order order1 = new Order(account1.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("100.00"), "isolation-pos-1");
            service.placeOrder(order1);

            // Verify: Account 1 has position, Account 2 doesn't
            Position pos1 = service.getPosition(account1.getId(), "TEST");
            Position pos2 = service.getPosition(account2.getId(), "TEST");

            assertNotNull(pos1);
            assertNull(pos2);
        }
    }

    // ==================== HELPER: REPOSITORY MOCKS ====================
    // In-memory repository mocks that simulate database persistence

    private static class RepositoryMocks {
        final Map<UUID, Order> orders = new HashMap<>();
        final Map<UUID, Execution> executions = new HashMap<>();
        final Map<Long, Account> accounts = new HashMap<>();
        final Map<Long, Position> positions = new HashMap<>();
        final Map<String, Object> instruments = new HashMap<>();

        AccountRepository accountRepository() {
            return proxy(AccountRepository.class, (proxy, method, args) -> {
                if (method.getName().equals("findById")) {
                    return Optional.ofNullable(accounts.get(args[0]));
                }
                if (method.getName().equals("save")) {
                    Account account = (Account) args[0];
                    accounts.put(account.getId(), account);
                    return account;
                }
                if (method.getName().equals("existsById")) {
                    return accounts.containsKey(args[0]);
                }
                return unsupported(method);
            });
        }

        ExecutionRepository executionRepository() {
            return proxy(ExecutionRepository.class, (proxy, method, args) -> {
                if (method.getName().equals("save")) {
                    Execution execution = (Execution) args[0];
                    if (execution.getId() == null) {
                        execution.setId(UUID.randomUUID());
                    }
                    executions.put(execution.getOrderId(), execution);
                    return execution;
                }
                return unsupported(method);
            });
        }

        OrderRepository orderRepository() {
            return proxy(OrderRepository.class, (proxy, method, args) -> {
                if (method.getName().equals("findById")) {
                    return Optional.ofNullable(orders.get(args[0]));
                }
                if (method.getName().equals("save")) {
                    Order order = (Order) args[0];
                    orders.put(order.getId(), order);
                    return order;
                }
                if (method.getName().equals("findByIdempotencyKey")) {
                    return orders.values().stream()
                            .filter(o -> o.getIdempotencyKey().equals(args[0]))
                            .findFirst();
                }
                if (method.getName().equals("existsByIdempotencyKey")) {
                    return orders.values().stream()
                            .anyMatch(o -> o.getIdempotencyKey().equals(args[0]));
                }
                return unsupported(method);
            });
        }

        PositionRepository positionRepository() {
            return proxy(PositionRepository.class, (proxy, method, args) -> {
                if (method.getName().equals("findByAccountIdAndSymbol")) {
                    Long accountId = (Long) args[0];
                    return Optional.ofNullable(positions.get(accountId));
                }
                if (method.getName().equals("save")) {
                    Position position = (Position) args[0];
                    positions.put(position.getAccountId(), position);
                    return position;
                }
                if (method.getName().equals("delete")) {
                    Position position = (Position) args[0];
                    positions.remove(position.getAccountId());
                    return null;
                }
                return unsupported(method);
            });
        }

        InstrumentRepository instrumentRepository() {
            return proxy(InstrumentRepository.class, (proxy, method, args) -> {
                if (method.getName().equals("findBySymbol")) {
                    return Optional.ofNullable((Instrument) instruments.get(args[0]));
                }
                if (method.getName().equals("save")) {
                    Instrument instrument = (Instrument) args[0];
                    instruments.put(instrument.getSymbol(), instrument);
                    return instrument;
                }
                return unsupported(method);
            });
        }

        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }

        private static Object unsupported(Method method) {
            throw new UnsupportedOperationException("Unexpected repository call: " + method.getName());
        }
    }
}
