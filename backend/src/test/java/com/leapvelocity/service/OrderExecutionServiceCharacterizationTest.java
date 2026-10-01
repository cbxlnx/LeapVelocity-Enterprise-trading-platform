package com.leapvelocity.service;

import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.AccountStatus;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import com.leapvelocity.exceptions.AccountNotActiveException;
import com.leapvelocity.exceptions.DuplicateOrderException;
import com.leapvelocity.exceptions.InsufficientFundsException;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CHARACTERIZATION TEST: Sprint 6 Order Execution Core Behavior
 * 
 * Locks in 5 essential behaviors BEFORE any refactoring.
 * Uses H2 real database (test profile) for authentic behavior capture.
 * These exact tests will pass with current code, will remain the same through refactoring,
 * and prove equivalence before/after any OrderExecutionService changes.
 * 
 * Seed data: ACC-001 (Alice, $10k), AAPL (tradable)
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@DisplayName("Characterization: Sprint 6 Order Execution Core")
class OrderExecutionServiceCharacterizationTest {

    @Autowired
    private OrderExecutionService service;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private PositionRepository positionRepository;

    private Account activeAccount;
    private Instrument tradableInstrument;

    @BeforeEach
    void setUp() {
        // Seed data: Account ACC-001 (Alice, $10k, ACTIVE)
        activeAccount = new Account("ACC-001", "Alice", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        activeAccount = accountRepository.save(activeAccount);

        // Seed data: Instrument AAPL (tradable)
        tradableInstrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);
        tradableInstrument = instrumentRepository.save(tradableInstrument);
    }

   @Test
    @DisplayName("CORE: BUY order debits account and creates position")
    void characterizeBuyOrderDebitsAccountAndCreatesPosition() {
        // GIVEN: Active account with sufficient cash, tradable instrument
        // WHEN: Place BUY order for 50 AAPL @ $150.00
        Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                new BigDecimal("50"), new BigDecimal("150.00"), "char-buy-001");

        Order result = service.placeOrder(order);

        // THEN: Order FILLED, refresh account from DB and verify cash debited $7500
        assertEquals(OrderStatus.FILLED, result.getStatus());
        
        // Reload account from database to get updated balance
        activeAccount = accountRepository.findById(activeAccount.getId()).orElseThrow();
        assertEquals(0, activeAccount.getCashBalance().compareTo(new BigDecimal("2500.00")));
        
        Position position = positionRepository.findByAccountIdAndSymbol(activeAccount.getId(), "AAPL").orElse(null);
        assertNotNull(position);
        assertEquals(0, position.getQuantity().compareTo(new BigDecimal("50.00")));
        assertEquals(0, position.getAverageCost().compareTo(new BigDecimal("150.00")));
    }

    @Test
    @DisplayName("CORE: SELL order credits account and reduces position")
    void characterizeSellOrderCreditsAccountAndReducesPosition() {
        // GIVEN: Existing position (100 AAPL @ $150.00)
        Position position = new Position(activeAccount.getId(), "AAPL", 
                new BigDecimal("100"), new BigDecimal("150.00"));
        positionRepository.save(position);

        // WHEN: Place SELL order for 50 AAPL @ $160.00
        Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL,
                new BigDecimal("50"), new BigDecimal("160.00"), "char-sell-001");

        Order result = service.placeOrder(order);

        // THEN: Order FILLED, refresh account from DB and verify cash credited $8000
        assertEquals(OrderStatus.FILLED, result.getStatus());
        
        // Reload account from database to get updated balance
        activeAccount = accountRepository.findById(activeAccount.getId()).orElseThrow();
        assertEquals(0, activeAccount.getCashBalance().compareTo(new BigDecimal("18000.00")));
        
        Position updatedPosition = positionRepository.findByAccountIdAndSymbol(activeAccount.getId(), "AAPL").orElse(null);
        assertNotNull(updatedPosition);
        assertEquals(0, updatedPosition.getQuantity().compareTo(new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("CORE: Reject BUY when insufficient funds")
    void characterizeRejectBuyInsufficientFunds() {
        // GIVEN: Account with only $100 cash
        Account poorAccount = new Account("ACC-POOR", "Bob", 
                new BigDecimal("100.00"), AccountStatus.ACTIVE);
        poorAccount = accountRepository.save(poorAccount);

        // WHEN: Attempt BUY 100 AAPL @ $150.00 (requires $15,000)
        Order order = new Order(poorAccount.getId(), "AAPL", OrderSide.BUY,
                new BigDecimal("100"), new BigDecimal("150.00"), "char-reject-funds");

        // THEN: InsufficientFundsException, order REJECTED
        assertThrows(InsufficientFundsException.class, () -> service.placeOrder(order));
        assertEquals(OrderStatus.REJECTED, order.getStatus());
    }

    @Test
    @DisplayName("CORE: Reject duplicate idempotency key")
    void characterizeRejectDuplicateIdempotencyKey() {
        // GIVEN: First order placed successfully
        Order order1 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                new BigDecimal("50"), new BigDecimal("150.00"), "char-idem-dup");
        service.placeOrder(order1);

        // WHEN: Attempt second order with identical idempotencyKey
        Order order2 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
                new BigDecimal("50"), new BigDecimal("150.00"), "char-idem-dup");

        // THEN: DuplicateOrderException, order REJECTED
        assertThrows(DuplicateOrderException.class, () -> service.placeOrder(order2));
        assertEquals(OrderStatus.REJECTED, order2.getStatus());
    }

    @Test
    @DisplayName("CORE: Reject order from inactive account")
    void characterizeRejectInactiveAccount() {
        // GIVEN: Account with SUSPENDED status
        Account suspendedAccount = new Account("ACC-SUSPENDED", "Charlie",
                new BigDecimal("10000.00"), AccountStatus.SUSPENDED);
        suspendedAccount = accountRepository.save(suspendedAccount);

        // WHEN: Attempt BUY order from suspended account
        Order order = new Order(suspendedAccount.getId(), "AAPL", OrderSide.BUY,
                new BigDecimal("50"), new BigDecimal("150.00"), "char-inactive");

        // THEN: AccountNotActiveException, order REJECTED
        assertThrows(AccountNotActiveException.class, () -> service.placeOrder(order));
        assertEquals(OrderStatus.REJECTED, order.getStatus());
    }
}