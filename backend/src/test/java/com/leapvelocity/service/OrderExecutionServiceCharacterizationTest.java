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
import com.leapvelocity.exceptions.DuplicateOrderException;
import com.leapvelocity.exceptions.InsufficientFundsException;
import com.leapvelocity.messaging.ExecutionEvent;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.ExecutionRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.repository.OrderRepository;
import com.leapvelocity.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
import static org.mockito.Mockito.when;

/**
 * CHARACTERIZATION TEST: Sprint 6 Order Execution Core Behavior
 * 
 * Locks in the current order lifecycle for this branch.
 * Order submission is synchronous, but settlement is a separate step that applies cash
 * and position changes after an execution event arrives.
 */
@DisplayName("Characterization: Sprint 6 Order Execution Core")
class OrderExecutionServiceCharacterizationTest {

    private OrderExecutionService service;

    private Account activeAccount;
    private Instrument tradableInstrument;
        private Map<Long, Account> accountsById;
        private Map<String, Instrument> instrumentsBySymbol;
        private Map<UUID, Order> ordersById;
        private Map<String, Order> ordersByIdempotencyKey;
        private Map<String, Position> positionsByKey;
        private Map<UUID, Execution> executionsByOrderId;

    @BeforeEach
    void setUp() {
                AccountRepository accountRepository = mock(AccountRepository.class);
                InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);
                OrderRepository orderRepository = mock(OrderRepository.class);
                ExecutionRepository executionRepository = mock(ExecutionRepository.class);
                PositionRepository positionRepository = mock(PositionRepository.class);

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
                                null
                );

        activeAccount = new Account("ACC-001", "Alice", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
                activeAccount.setId(1L);
                service.addAccount(activeAccount);

        tradableInstrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);
                service.addInstrument(tradableInstrument);
    }

        @Test
        @DisplayName("CORE: BUY order stays NEW until settlement, then debits account and creates position")
        void characterizeBuyOrderSubmissionAndSettlementLifecycle() {
        Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                new BigDecimal("50"), new BigDecimal("150.00"), "char-buy-001");

                Order result = service.placeOrder(order);

                assertEquals(OrderStatus.NEW, result.getStatus());
                assertEquals(0, activeAccount.getCashBalance().compareTo(new BigDecimal("10000.00")));
                assertNull(service.getPosition(activeAccount.getId(), "AAPL"));

                service.settleExecution(executionEventFor(result));

                assertEquals(OrderStatus.FILLED, result.getStatus());
                assertEquals(0, activeAccount.getCashBalance().compareTo(new BigDecimal("2500.00")));

                Position position = service.getPosition(activeAccount.getId(), "AAPL");
                assertNotNull(position);
                assertEquals(0, position.getQuantity().compareTo(new BigDecimal("50.00")));
                assertEquals(0, position.getAverageCost().compareTo(new BigDecimal("150.00")));
    }

    @Test
        @DisplayName("CORE: SELL order stays NEW until settlement, then credits account and reduces position")
        void characterizeSellOrderSubmissionAndSettlementLifecycle() {
        Position position = new Position(activeAccount.getId(), "AAPL", 
                new BigDecimal("100"), new BigDecimal("150.00"));
                service.addPosition(position);

        Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL,
                new BigDecimal("50"), new BigDecimal("160.00"), "char-sell-001");

        Order result = service.placeOrder(order);

                assertEquals(OrderStatus.NEW, result.getStatus());
                assertEquals(0, activeAccount.getCashBalance().compareTo(new BigDecimal("10000.00")));
                assertEquals(0, service.getPosition(activeAccount.getId(), "AAPL").getQuantity().compareTo(new BigDecimal("100.00")));

                service.settleExecution(executionEventFor(result));

                assertEquals(OrderStatus.FILLED, result.getStatus());
                assertEquals(0, activeAccount.getCashBalance().compareTo(new BigDecimal("18000.00")));

                Position updatedPosition = service.getPosition(activeAccount.getId(), "AAPL");
        assertNotNull(updatedPosition);
        assertEquals(0, updatedPosition.getQuantity().compareTo(new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("CORE: Reject BUY when insufficient funds")
    void characterizeRejectBuyInsufficientFunds() {
        Account poorAccount = new Account("ACC-POOR", "Bob", 
                new BigDecimal("100.00"), AccountStatus.ACTIVE);
        poorAccount.setId(2L);
        service.addAccount(poorAccount);

        Order order = new Order(poorAccount.getId(), "AAPL", OrderSide.BUY,
                new BigDecimal("100"), new BigDecimal("150.00"), "char-reject-funds");

        assertThrows(InsufficientFundsException.class, () -> service.placeOrder(order));
        assertEquals(OrderStatus.REJECTED, order.getStatus());
    }

    @Test
    @DisplayName("CORE: Reject duplicate idempotency key")
    void characterizeRejectDuplicateIdempotencyKey() {
        Order order1 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                new BigDecimal("50"), new BigDecimal("150.00"), "char-idem-dup");
        service.placeOrder(order1);

        Order order2 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                new BigDecimal("50"), new BigDecimal("150.00"), "char-idem-dup");

        assertThrows(DuplicateOrderException.class, () -> service.placeOrder(order2));
        assertEquals(OrderStatus.REJECTED, order2.getStatus());
    }

    @Test
    @DisplayName("CORE: Reject order from inactive account")
    void characterizeRejectInactiveAccount() {
        Account suspendedAccount = new Account("ACC-SUSPENDED", "Charlie",
                new BigDecimal("10000.00"), AccountStatus.SUSPENDED);
                suspendedAccount.setId(3L);
                service.addAccount(suspendedAccount);

        Order order = new Order(suspendedAccount.getId(), "AAPL", OrderSide.BUY,
                new BigDecimal("50"), new BigDecimal("150.00"), "char-inactive");

        assertThrows(AccountNotActiveException.class, () -> service.placeOrder(order));
        assertEquals(OrderStatus.REJECTED, order.getStatus());
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

        private String positionKey(Long accountId, String symbol) {
                return accountId + "|" + symbol.trim();
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
}