package com.leapvelocity.service;

import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.AccountStatus;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.exceptions.AccountNotActiveException;
import com.leapvelocity.exceptions.OrderNotFoundException;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.ExecutionRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.repository.OrderRepository;
import com.leapvelocity.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
 * Characterization tests for order cancellation flow.
 * 
 * Tests verify:
 * - BUY order cancellations: Cash is refunded to account, position unchanged
 * - SELL order cancellations: Cash unchanged, position is restored
 * - Edge cases: Non-existent orders, already-cancelled orders, non-NEW orders
 * - State transitions: Order status correctly moves to CANCELLED
 * - Account state consistency: Cash balance is correct after cancellation
 * - Position state consistency: Positions are properly restored
 * 
 * NOTE: This test uses repository mocks (not in-memory HashMaps) because
 * cancelOrder() implementation requires repository access. This is a critical gap
 * identified in Sprint 6 test coverage analysis.
 */
@DisplayName("OrderCancellationService")
class OrderCancellationServiceTest {

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
    }

    // ==================== BUY ORDER CANCELLATION TESTS ====================
    // Verify BUY order cancellations refund cash and leave position unchanged

    @Nested
    @DisplayName("BUY Order Cancellations")
    class BuyOrderCancellationTests {



        @Test
        @DisplayName("should leave position unchanged when cancelling BUY order with existing position")
        void cancelBuyOrderDoesNotAffectPosition() {
            // Arrange: Account with existing position
            Account account = account(1L, "ACC-001", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
            mocks.accounts.put(1L, account);

            Position position = new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("90.00"));
            mocks.positions.put(1L, position);

            Order order = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("50"), new BigDecimal("100.00"), "cancel-buy-002");
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.NEW);
            mocks.orders.put(order.getId(), order);

            BigDecimal positionQtyBefore = position.getQuantity();
            BigDecimal positionCostBefore = position.getAverageCost();

            // Act: Cancel BUY order
            service.cancelOrder(order.getId());

            // Assert: Position unchanged
            assertEquals(positionQtyBefore, position.getQuantity());
            assertEquals(positionCostBefore, position.getAverageCost());
        }


    }

    // ==================== SELL ORDER CANCELLATION TESTS ====================
    // Verify SELL order cancellations restore positions and leave cash unchanged

    @Nested
    @DisplayName("SELL Order Cancellations")
    class SellOrderCancellationTests {

        @Test
        @DisplayName("should restore position when cancelling NEW SELL order")
        void cancelNewSellOrderRestoresPosition() {
            // Arrange: Account with position, SELL order in NEW state
            Account account = account(1L, "ACC-001", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
            mocks.accounts.put(1L, account);

            Position position = new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("90.00"));
            mocks.positions.put(1L, position);

            Order order = new Order(1L, "AAPL", OrderSide.SELL, new BigDecimal("50"), new BigDecimal("100.00"), "cancel-sell-001");
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.NEW);
            mocks.orders.put(order.getId(), order);

            BigDecimal positionQtyBefore = position.getQuantity(); // 100

            // Act: Cancel the SELL order (should restore 50 shares)
            service.cancelOrder(order.getId());

            // Assert: Position quantity restored
            Position restored = mocks.positions.get(1L);
            assertEquals(positionQtyBefore.add(new BigDecimal("50")), restored.getQuantity()); // 150
        }

        @Test
        @DisplayName("should create position if none exists when restoring from SELL cancellation")
        void cancelSellOrderCreatesPositionIfNoneExists() {
            // Arrange: Account with no position for symbol
            Account account = account(1L, "ACC-001", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
            mocks.accounts.put(1L, account);

            Order order = new Order(1L, "NVDA", OrderSide.SELL, new BigDecimal("100"), new BigDecimal("500.00"), "cancel-sell-003");
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.NEW);
            mocks.orders.put(order.getId(), order);

            // Act: Cancel SELL order (should create position with restored quantity)
            service.cancelOrder(order.getId());

            // Assert: New position created with cancelled order quantity
            Position created = mocks.positions.get(1L);
            assertNotNull(created);
            assertEquals(new BigDecimal("100"), created.getQuantity());
            assertEquals(new BigDecimal("500.00"), created.getAverageCost());
        }
    }

    // ==================== EDGE CASE TESTS ====================
    // Verify boundary conditions and error scenarios

    @Nested
    @DisplayName("Edge Cases & Error Scenarios")
    class EdgeCaseTests {

        @Test
        @DisplayName("should throw OrderNotFoundException when order ID does not exist")
        void cancelNonExistentOrder() {
            // Arrange: Non-existent order ID
            UUID nonExistentId = UUID.randomUUID();

            // Act & Assert: Exception thrown
            assertThrows(OrderNotFoundException.class, () -> service.cancelOrder(nonExistentId));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when cancelling FILLED order")
        void cancelFilledOrderThrowsException() {
            // Arrange: Order already FILLED
            Account account = account(1L, "ACC-001", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
            mocks.accounts.put(1L, account);

            Order order = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("50"), new BigDecimal("100.00"), "cancel-filled");
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.FILLED); // Already filled
            mocks.orders.put(order.getId(), order);

            // Act & Assert: Cannot cancel filled order
            assertThrows(IllegalArgumentException.class, () -> service.cancelOrder(order.getId()));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when cancelling already-CANCELLED order")
        void cancelAlreadyCancelledOrderThrowsException() {
            // Arrange: Order already cancelled
            Account account = account(1L, "ACC-001", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
            mocks.accounts.put(1L, account);

            Order order = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("50"), new BigDecimal("100.00"), "cancel-cancelled");
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.CANCELLED); // Already cancelled
            mocks.orders.put(order.getId(), order);

            // Act & Assert: Cannot cancel already-cancelled order
            assertThrows(IllegalArgumentException.class, () -> service.cancelOrder(order.getId()));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when cancelling REJECTED order")
        void cancelRejectedOrderThrowsException() {
            // Arrange: Order was rejected
            Account account = account(1L, "ACC-001", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
            mocks.accounts.put(1L, account);

            Order order = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("50"), new BigDecimal("100.00"), "cancel-rejected");
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.REJECTED);
            mocks.orders.put(order.getId(), order);

            // Act & Assert: Cannot cancel rejected order
            assertThrows(IllegalArgumentException.class, () -> service.cancelOrder(order.getId()));
        }

        @Test
        @DisplayName("should throw AccountNotFoundException when account no longer exists")
        void cancelOrderAccountNotFound() {
            // Arrange: Order exists but associated account doesn't
            Order order = new Order(999L, "AAPL", OrderSide.BUY, new BigDecimal("50"), new BigDecimal("100.00"), "cancel-no-acct");
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.NEW);
            mocks.orders.put(order.getId(), order);
            
            // NO account in mocks

            // Act & Assert: Account not found throws exception
            assertThrows(AccountNotFoundException.class, () -> service.cancelOrder(order.getId()));
        }
    }

    // ==================== STATE CONSISTENCY TESTS ====================
    // Verify order state and account state consistency after cancellation

    @Nested
    @DisplayName("State Consistency")
    class StateConsistencyTests {

        @Test
        @DisplayName("should update order status to CANCELLED in repository")
        void cancelOrderUpdatesStatusInRepository() {
            // Arrange: Create order in NEW state
            Account account = account(1L, "ACC-001", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
            mocks.accounts.put(1L, account);

            Order order = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("50"), new BigDecimal("100.00"), "status-001");
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.NEW);
            mocks.orders.put(order.getId(), order);

            // Act: Cancel order
            Order cancelled = service.cancelOrder(order.getId());

            // Assert: Status changed to CANCELLED
            assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
            assertEquals(OrderStatus.CANCELLED, mocks.orders.get(order.getId()).getStatus());
        }


    }

    // ==================== MULTI-CANCELLATION TESTS ====================
    // Verify system handles multiple sequential cancellations correctly  
    @Nested
    @DisplayName("Account Status Checks")
    class AccountStatusCheckTests {

        @Test
        @DisplayName("should allow cancellation for ACTIVE account")
        void cancelOrderActiveAccount() {
            // Arrange: ACTIVE account with NEW order
            Account account = account(1L, "ACC-001", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
            mocks.accounts.put(1L, account);

            Order order = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("50"), new BigDecimal("100.00"), "active-cancel");
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.NEW);
            mocks.orders.put(order.getId(), order);

            // Act & Assert: Should succeed without exception
            Order cancelled = assertDoesNotThrow(() -> service.cancelOrder(order.getId()));
            assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        }

        @Test
        @DisplayName("should complete cancellation for SUSPENDED account (account still exists)")
        void cancelOrderSuspendedAccount() {
            // Arrange: SUSPENDED account with NEW order
            Account account = account(1L, "ACC-SUSPENDED", new BigDecimal("10000.00"), AccountStatus.SUSPENDED);
            mocks.accounts.put(1L, account);

            Order order = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("50"), new BigDecimal("100.00"), "suspend-cancel");
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.NEW);
            mocks.orders.put(order.getId(), order);

            // Act: Cancellation should still work (account exists, just suspended from new orders)
            Order cancelled = service.cancelOrder(order.getId());

            // Assert: Order cancelled, cash refunded
            assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
            assertEquals(new BigDecimal("15000.00"), account.getCashBalance());
        }
    }
        
    // ==================== HELPER: REPOSITORY MOCKS ====================

    private static class RepositoryMocks {
        private final Map<UUID, Order> orders = new HashMap<>();
        private final Map<Long, Account> accounts = new HashMap<>();
        private final Map<Long, Position> positions = new HashMap<>();

        private ExecutionRepository executionRepository() {
            return proxy(ExecutionRepository.class, (proxy, method, args) -> {
                if (method.getName().equals("save")) {
                    return args[0];
                }
                return unsupported(method);
            });
        }

        private OrderRepository orderRepository() {
            return proxy(OrderRepository.class, (proxy, method, args) -> {
                if (method.getName().equals("findById")) {
                    return Optional.ofNullable(orders.get(args[0]));
                }
                if (method.getName().equals("save")) {
                    Order order = (Order) args[0];
                    orders.put(order.getId(), order);
                    return order;
                }
                if (method.getName().equals("existsByIdempotencyKey")) {
                    return orders.values().stream()
                            .anyMatch(o -> o.getIdempotencyKey().equals(args[0]));
                }
                if (method.getName().equals("findByIdempotencyKey")) {
                    return orders.values().stream()
                            .filter(o -> o.getIdempotencyKey().equals(args[0]))
                            .findFirst();
                }
                return unsupported(method);
            });
        }

        private AccountRepository accountRepository() {
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

        private PositionRepository positionRepository() {
            return proxy(PositionRepository.class, (proxy, method, args) -> {
                if (method.getName().equals("findByAccountIdAndSymbol")) {
                    Long accountId = (Long) args[0];
                    String symbol = (String) args[1];
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

        private InstrumentRepository instrumentRepository() {
            return proxy(InstrumentRepository.class, (proxy, method, args) -> 
                unsupported(method)
            );
        }

        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }

        private static Object unsupported(Method method) {
            throw new UnsupportedOperationException("Unexpected repository call: " + method.getName());
        }
    }

    private static Account account(Long id, String accountId, BigDecimal cashBalance, AccountStatus status) {
        Account account = new Account(accountId, "Test Account", cashBalance, status);
        account.setId(id);
        return account;
    }
}