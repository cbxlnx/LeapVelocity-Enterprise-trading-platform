package com.leapvelocity.service;
import com.leapvelocity.service.OrderExecutionService;

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
import com.leapvelocity.repository.inmemory.InMemoryAccountRepository;
import com.leapvelocity.repository.inmemory.InMemoryInstrumentRepository;
import com.leapvelocity.repository.inmemory.InMemoryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OrderExecutionService")
class OrderExecutionServiceTest {

    private OrderExecutionService service;
    private Account activeAccount;
    private Instrument tradableInstrument;

    @BeforeEach
    void setUp() {
        service = new OrderExecutionService();
        activeAccount = new Account("ACC-001", "Alice", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        activeAccount.setId(1L);
        tradableInstrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);
        
        service.addAccount(activeAccount);
        service.addInstrument(tradableInstrument);
    }

    // ===== BUY ORDER TESTS - Verify buy orders correctly debit cash and create/update positions =====
    
    @Test
    @DisplayName("buy order success: debits account and creates position")
    void buyOrderSuccess() {
        // Buy 50 shares at $150 each = $7500 debit from $10,000 starting balance
        Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY, 
            new BigDecimal("50"), new BigDecimal("150.00"), "buy-001");
        
        Order result = service.placeOrder(order);
        
        // Verify order was filled and cash balance reduced
        assertEquals(OrderStatus.FILLED, result.getStatus());
        assertEquals(new BigDecimal("10000.00").subtract(new BigDecimal("7500.00")), 
            activeAccount.getCashBalance()); // 10000 - (50*150) = 2500
        
        // Verify new position was created with correct quantity and average cost
        Position position = service.getPosition(activeAccount.getId(), "AAPL");
        assertNotNull(position);
        assertEquals(new BigDecimal("50"), position.getQuantity());
        assertEquals(new BigDecimal("150.00"), position.getAverageCost());
    }

    @Test
    @DisplayName("constructor accepts service dependencies")
    void constructorAcceptsServiceDependencies() {
        PositionUpdateService injectedPositionUpdateService = new PositionUpdateService();
        OrderExecutionService injectedService = new OrderExecutionService(
            new InMemoryAccountRepository(),
            new InMemoryInstrumentRepository(),
            new InMemoryOrderRepository(),
            injectedPositionUpdateService,
            new OrderValidator());

        injectedService.addAccount(activeAccount);
        injectedService.addInstrument(tradableInstrument);
        injectedService.placeOrder(new Order(activeAccount.getId(), "AAPL", OrderSide.BUY,
            new BigDecimal("10"), new BigDecimal("100.00"), "injected-buy"));

        assertNotNull(injectedPositionUpdateService.getPosition(activeAccount.getId(), "AAPL"));
    }

    @Test
    @DisplayName("buy order with insufficient funds throws exception and rejects order")
    void buyOrderInsufficientFunds() {
        Account poorAccount = new Account("ACC-POOR", "Bob", new BigDecimal("100.00"), AccountStatus.ACTIVE);
        poorAccount.setId(2L);
        service.addAccount(poorAccount);
        
        Order order = new Order(poorAccount.getId(), "AAPL", OrderSide.BUY, 
            new BigDecimal("100"), new BigDecimal("150.00"), "buy-poor");
        
        assertThrows(InsufficientFundsException.class, () -> service.placeOrder(order));
        
        // Order should be rejected
        assertEquals(OrderStatus.REJECTED, order.getStatus());
        assertNotNull(service.getOrder("buy-poor"));
        assertEquals(OrderStatus.REJECTED, service.getOrder("buy-poor").getStatus());
    }

    @Test
    @DisplayName("buy order for non-tradable instrument throws exception")
    void buyOrderNonTradableInstrument() {
        Instrument nonTradable = new Instrument("DELISTED", "Delisted Corp", "EQUITY", "USD", false);
        service.addInstrument(nonTradable);
        
        Order order = new Order(activeAccount.getId(), "DELISTED", OrderSide.BUY, 
            new BigDecimal("50"), new BigDecimal("100.00"), "buy-delisted");
        
        assertThrows(InstrumentNotFoundException.class, () -> service.placeOrder(order));
        assertEquals(OrderStatus.REJECTED, order.getStatus());
    }

    // ===== SELL ORDER TESTS - Verify sell orders correctly credit cash and reduce positions =====
    
    @Test
    @DisplayName("sell order success: credits account and reduces position")
    void sellOrderSuccess() {
        // Setup: create initial position of 100 shares at $150 average cost
        Position position = new Position(activeAccount.getId(), "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
        service.addPosition(position);
        
        // Sell 50 shares at $160 each = $8000 credit to starting balance
        Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL, 
            new BigDecimal("50"), new BigDecimal("160.00"), "sell-001");
        
        Order result = service.placeOrder(order);
        
        // Verify order was filled and cash balance increased
        assertEquals(OrderStatus.FILLED, result.getStatus());
        // Cash: $10,000 + (50 * $160) = $18,000
        assertEquals(new BigDecimal("10000.00").add(new BigDecimal("8000.00")), 
            activeAccount.getCashBalance());
        
        // Verify position reduced from 100 to 50 shares
        Position updatedPosition = service.getPosition(activeAccount.getId(), "AAPL");
        assertEquals(new BigDecimal("50"), updatedPosition.getQuantity());
    }

    @Test
    @DisplayName("sell order with insufficient holdings throws exception")
    void sellOrderInsufficientHoldings() {
        Position position = new Position(activeAccount.getId(), "AAPL", new BigDecimal("10"), new BigDecimal("150.00"));
        service.addPosition(position);
        
        Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL, 
            new BigDecimal("50"), new BigDecimal("160.00"), "sell-insufficient");
        
        assertThrows(InsufficientHoldingsException.class, () -> service.placeOrder(order));
    }

    @Test
    @DisplayName("sell order for non-existent position throws exception")
    void sellOrderNoPosition() {
        Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL, 
            new BigDecimal("50"), new BigDecimal("160.00"), "sell-no-position");
        
        assertThrows(InsufficientHoldingsException.class, () -> service.placeOrder(order));
    }

    @Test
    @DisplayName("sell order closes position when all shares sold")
    void sellOrderClosesPosition() {
        // Setup: create position with 100 shares
        Position position = new Position(activeAccount.getId(), "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
        service.addPosition(position);
        
        // Sell all 100 shares
        Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL, 
            new BigDecimal("100"), new BigDecimal("160.00"), "sell-all");
        
        service.placeOrder(order);
        
        // Verify position is completely removed when quantity reaches zero
        Position closedPosition = service.getPosition(activeAccount.getId(), "AAPL");
        assertNull(closedPosition);
    }

    // ===== ACCOUNT VALIDATION TESTS - Verify account status checks prevent invalid orders =====
    
    @Test
    @DisplayName("order for suspended account throws exception")
    void orderSuspendedAccount() {
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
    @DisplayName("order for closed account throws exception")
    void orderClosedAccount() {
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
    @DisplayName("order for non-existent account throws exception")
    void orderNonExistentAccount() {
        Order order = new Order(999999L, "AAPL", OrderSide.BUY, 
            new BigDecimal("50"), new BigDecimal("150.00"), "order-no-account");
        
        assertThrows(AccountNotFoundException.class, () -> service.placeOrder(order));
        assertEquals(OrderStatus.REJECTED, order.getStatus());
    }

    // ===== IDEMPOTENCY TESTS - Verify duplicate orders are rejected to prevent double-execution =====
    
    @Test
    @DisplayName("duplicate idempotency key throws exception")
    void duplicateIdempotencyKey() {
        Order order1 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY, 
            new BigDecimal("50"), new BigDecimal("150.00"), "idem-dup");
        
        service.placeOrder(order1);
        
        Order order2 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY, 
            new BigDecimal("50"), new BigDecimal("150.00"), "idem-dup");
        
        assertThrows(DuplicateOrderException.class, () -> service.placeOrder(order2));
        assertEquals(OrderStatus.REJECTED, order2.getStatus());
    }

    // ===== INPUT VALIDATION TESTS - Verify invalid order parameters are rejected =====
    
    @Test
    @DisplayName("null order throws exception")
    void nullOrder() {
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(null));
    }

    @Test
    @DisplayName("order with null account ID throws exception")
    void nullAccountId() {
        Order order = new Order(null, "AAPL", OrderSide.BUY, 
            new BigDecimal("50"), new BigDecimal("150.00"), "null-account");
        
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(order));
    }

    @Test
    @DisplayName("order with blank symbol throws exception")
    void blankSymbol() {
        Order order = new Order(activeAccount.getId(), "", OrderSide.BUY, 
            new BigDecimal("50"), new BigDecimal("150.00"), "blank-symbol");
        
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(order));
    }

    @Test
    @DisplayName("order with zero quantity throws exception")
    void zeroQuantity() {
        Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY, 
            BigDecimal.ZERO, new BigDecimal("150.00"), "zero-qty");
        
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(order));
    }

    @Test
    @DisplayName("order with negative quantity throws exception")
    void negativeQuantity() {
        Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY, 
            new BigDecimal("-50"), new BigDecimal("150.00"), "neg-qty");
        
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(order));
    }

    @Test
    @DisplayName("order with zero price throws exception")
    void zeroPrice() {
        Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY, 
            new BigDecimal("50"), BigDecimal.ZERO, "zero-price");
        
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(order));
    }

    @Test
    @DisplayName("order with negative price throws exception")
    void negativePrice() {
        Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY, 
            new BigDecimal("50"), new BigDecimal("-150.00"), "neg-price");
        
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(order));
    }

    @Test
    @DisplayName("order with blank idempotency key throws exception")
    void blankIdempotencyKey() {
        Order order = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY, 
            new BigDecimal("50"), new BigDecimal("150.00"), "");
        
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(order));
    }

    // ===== INTEGRATION SCENARIOS - Verify realistic multi-order sequences work correctly =====
    
    @Test
    @DisplayName("multiple buy orders increase position")
    void multipleBuysIncreasePosition() {
        // First buy: 50 shares at $150 = $7500 debit. Cash: $10000 - $7500 = $2500
        Order order1 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY, 
            new BigDecimal("50"), new BigDecimal("150.00"), "buy-1");
        service.placeOrder(order1);
        
        // Second buy: 15 shares at $160 = $2400 debit. Cash: $2500 - $2400 = $100
        Order order2 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY, 
            new BigDecimal("15"), new BigDecimal("160.00"), "buy-2");
        service.placeOrder(order2);
        
        // Verify position combined and average cost recalculated
        Position position = service.getPosition(activeAccount.getId(), "AAPL");
        assertEquals(new BigDecimal("65"), position.getQuantity()); // 50 + 15
        // Average cost: (50*150 + 15*160) / 65 = (7500 + 2400) / 65 = 9900 / 65 ≈ 152.31
        assertEquals(new BigDecimal("152.31"), position.getAverageCost());
    }

    @Test
    @DisplayName("buy, sell partial, buy again sequence")
    void buyPartialSellBuySequence() {
        // Buy 50 at $150 = $7500
        Order buy1 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY, 
            new BigDecimal("50"), new BigDecimal("150.00"), "buy-1");
        service.placeOrder(buy1);
        assertEquals(new BigDecimal("10000.00").subtract(new BigDecimal("7500.00")), 
            activeAccount.getCashBalance());
        
        // Sell 20 at $160
        Order sell1 = new Order(activeAccount.getId(), "AAPL", OrderSide.SELL, 
            new BigDecimal("20"), new BigDecimal("160.00"), "sell-1");
        service.placeOrder(sell1);
        assertEquals(new BigDecimal("10000.00").subtract(new BigDecimal("7500.00"))
            .add(new BigDecimal("3200.00")), activeAccount.getCashBalance());
        
        // Buy 10 at $155 = $1550
        Order buy2 = new Order(activeAccount.getId(), "AAPL", OrderSide.BUY, 
            new BigDecimal("10"), new BigDecimal("155.00"), "buy-2");
        service.placeOrder(buy2);
        
        Position position = service.getPosition(activeAccount.getId(), "AAPL");
        assertEquals(new BigDecimal("40"), position.getQuantity()); // 50 - 20 + 10
    }

    @Test
    @DisplayName("add account validation: null account throws exception")
    void addNullAccount() {
        assertThrows(IllegalArgumentException.class, () -> service.addAccount(null));
    }

    @Test
    @DisplayName("add instrument validation: blank symbol throws exception")
    void addInstrumentBlankSymbol() {
        Instrument instrument = new Instrument("", "Test", "EQUITY", "USD", true);
        assertThrows(IllegalArgumentException.class, () -> service.addInstrument(instrument));
    }

    @Test
    @DisplayName("add instrument validation: null symbol throws exception")
    void addInstrumentNullSymbol() {
        Instrument instrument = new Instrument(null, "Test", "EQUITY", "USD", true);
        assertThrows(IllegalArgumentException.class, () -> service.addInstrument(instrument));
    }
}
