package com.leapvelocity.service;
import com.leapvelocity.service.PositionUpdateService;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.exceptions.InsufficientHoldingsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PositionUpdateService")
class PositionUpdateServiceTest {

    private PositionUpdateService service;

    @BeforeEach
    void setUp() {
        service = new PositionUpdateService();
    }

    // ===== POSITION STORAGE TESTS - Verify positions can be added and retrieved correctly =====
    
    @Test
    @DisplayName("add position stores it for retrieval")
    void addPositionSuccess() {
        // Add a position for account 1: 100 shares of AAPL at $150 average cost
        Position position = new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
        service.addPosition(position);
        
        // Verify it can be retrieved with exact same values
        Position retrieved = service.getPosition(1L, "AAPL");
        assertNotNull(retrieved);
        assertEquals(new BigDecimal("100"), retrieved.getQuantity());
        assertEquals(new BigDecimal("150.00"), retrieved.getAverageCost());
    }

    @Test
    @DisplayName("add null position throws exception")
    void addNullPosition() {
        assertThrows(IllegalArgumentException.class, () -> service.addPosition(null));
    }

    // ===== BUY ORDER LOGIC TESTS - Verify buy orders correctly create and update positions with proper average cost =====
    
    @Test
    @DisplayName("apply buy creates new position when none exists")
    void applyBuyCreatesNewPosition() {
        // Buy order for 100 shares at $150 each
        Order order = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("100"), new BigDecimal("150.00"), "buy-1");
        
        service.applyBuy(order);
        
        // Verify new position was created with correct quantity and average cost
        Position position = service.getPosition(1L, "AAPL");
        assertNotNull(position);
        assertEquals(new BigDecimal("100"), position.getQuantity());
        assertEquals(new BigDecimal("150.00"), position.getAverageCost());
    }

    @Test
    @DisplayName("apply buy updates existing position with new average cost")
    void applyBuyUpdatesPosition() {
        // Existing position: 100 shares at $150 average cost
        Position position = new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
        service.addPosition(position);
        
        // Buy additional 50 shares at $160
        Order order = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("50"), new BigDecimal("160.00"), "buy-2");
        service.applyBuy(order);
        
        // Verify position quantity increased and average cost recalculated
        Position updated = service.getPosition(1L, "AAPL");
        assertEquals(new BigDecimal("150"), updated.getQuantity()); // 100 + 50
        // New average: (100*150 + 50*160) / 150 = (15000 + 8000) / 150 = 23000 / 150 = 153.33
        assertEquals(new BigDecimal("153.33"), updated.getAverageCost());
    }

    @Test
    @DisplayName("apply multiple buys calculates correct average cost")
    void applyMultipleBuys() {
        // Buy 1: 100 shares at $100
        Order order1 = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("100"), new BigDecimal("100.00"), "buy-1");
        service.applyBuy(order1);
        
        // Buy 2: 100 shares at $120
        Order order2 = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("100"), new BigDecimal("120.00"), "buy-2");
        service.applyBuy(order2);
        
        // Buy 3: 100 shares at $140
        Order order3 = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("100"), new BigDecimal("140.00"), "buy-3");
        service.applyBuy(order3);
        
        // Verify total position and correct average cost calculation
        Position position = service.getPosition(1L, "AAPL");
        assertEquals(new BigDecimal("300"), position.getQuantity()); // 100 + 100 + 100
        // Average: (100*100 + 100*120 + 100*140) / 300 = (10000 + 12000 + 14000) / 300 = 36000 / 300 = 120.00
        assertEquals(new BigDecimal("120.00"), position.getAverageCost());
    }

    // ===== SELL ORDER LOGIC TESTS - Verify sell orders correctly reduce positions and handle edge cases =====
    
    @Test
    @DisplayName("apply sell reduces position quantity")
    void applySellReducesPosition() {
        // Existing position: 100 shares at $150 average cost
        Position position = new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
        service.addPosition(position);
        
        // Sell 30 shares at $160
        Order order = new Order(1L, "AAPL", OrderSide.SELL, new BigDecimal("30"), new BigDecimal("160.00"), "sell-1");
        service.applySell(order);
        
        // Verify position reduced (100 - 30 = 70)
        Position updated = service.getPosition(1L, "AAPL");
        assertEquals(new BigDecimal("70"), updated.getQuantity());
    }

    @Test
    @DisplayName("apply sell removes position when all shares sold")
    void applySellClosesPosition() {
        // Existing position: 100 shares at $150 average cost
        Position position = new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
        service.addPosition(position);
        
        // Sell all 100 shares
        Order order = new Order(1L, "AAPL", OrderSide.SELL, new BigDecimal("100"), new BigDecimal("160.00"), "sell-all");
        service.applySell(order);
        
        // Verify position is completely removed when quantity reaches zero
        Position closed = service.getPosition(1L, "AAPL");
        assertNull(closed);
    }

    @Test
    @DisplayName("apply sell for non-existent position throws exception")
    void applySellNoPosition() {
        // Attempt to sell when account has no position in this symbol
        Order order = new Order(1L, "AAPL", OrderSide.SELL, new BigDecimal("50"), new BigDecimal("160.00"), "sell-no-pos");
        
        assertThrows(InsufficientHoldingsException.class, () -> service.applySell(order));
    }

    @Test
    @DisplayName("apply sell with insufficient holdings throws exception")
    void applySellInsufficientHoldings() {
        // Existing position: 30 shares
        Position position = new Position(1L, "AAPL", new BigDecimal("30"), new BigDecimal("150.00"));
        service.addPosition(position);
        
        // Attempt to sell 50 shares (more than available)
        Order order = new Order(1L, "AAPL", OrderSide.SELL, new BigDecimal("50"), new BigDecimal("160.00"), "sell-insuff");
        
        assertThrows(InsufficientHoldingsException.class, () -> service.applySell(order));
        
        // Verify position unchanged after failed sell (transactional integrity)
        Position unchanged = service.getPosition(1L, "AAPL");
        assertEquals(new BigDecimal("30"), unchanged.getQuantity());
    }

    @Test
    @DisplayName("apply sell with exact holdings matches succeeds")
    void applySellExactHoldings() {
        // Existing position: exactly 50 shares
        Position position = new Position(1L, "AAPL", new BigDecimal("50"), new BigDecimal("150.00"));
        service.addPosition(position);
        
        // Sell exactly 50 shares (boundary case: selling everything)
        Order order = new Order(1L, "AAPL", OrderSide.SELL, new BigDecimal("50"), new BigDecimal("160.00"), "sell-exact");
        
        // Should succeed without exception
        assertDoesNotThrow(() -> service.applySell(order));
        
        // Verify position completely removed
        Position closed = service.getPosition(1L, "AAPL");
        assertNull(closed);
    }

    // ===== MULTI-ACCOUNT ISOLATION TESTS - Verify positions for different accounts don't interfere =====
    
    @Test
    @DisplayName("positions for different accounts are isolated")
    void multiAccountPositionsIsolated() {
        Position pos1 = new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
        Position pos2 = new Position(2L, "AAPL", new BigDecimal("50"), new BigDecimal("140.00"));
        service.addPosition(pos1);
        service.addPosition(pos2);
        
        Order buy1 = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("25"), new BigDecimal("160.00"), "buy-acct1");
        service.applyBuy(buy1);
        
        Position acct1Pos = service.getPosition(1L, "AAPL");
        Position acct2Pos = service.getPosition(2L, "AAPL");
        
        assertEquals(new BigDecimal("125"), acct1Pos.getQuantity());
        assertEquals(new BigDecimal("50"), acct2Pos.getQuantity()); // Unchanged
    }

    @Test
    @DisplayName("get positions for account returns all positions for that account")
    void getPositionsForAccount() {
        service.addPosition(new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00")));
        service.addPosition(new Position(1L, "GOOGL", new BigDecimal("50"), new BigDecimal("2800.00")));
        service.addPosition(new Position(1L, "MSFT", new BigDecimal("75"), new BigDecimal("320.00")));
        service.addPosition(new Position(2L, "AAPL", new BigDecimal("30"), new BigDecimal("150.00"))); // Different account
        
        List<Position> acct1Positions = service.getPositionsForAccount(1L);
        
        assertEquals(3, acct1Positions.size());
        assertTrue(acct1Positions.stream().allMatch(p -> p.getAccountId().equals(1L)));
    }

    @Test
    @DisplayName("get positions for account with no positions returns empty list")
    void getPositionsForAccountEmpty() {
        List<Position> positions = service.getPositionsForAccount(999L);
        
        assertNotNull(positions);
        assertEquals(0, positions.size());
    }

    // ===== MULTI-SYMBOL ISOLATION TESTS - Verify different symbols in same account don't interfere =====
    
    @Test
    @DisplayName("positions for different symbols are isolated")
    void multiSymbolPositionsIsolated() {
        service.addPosition(new Position(1L, "AAPL", new BigDecimal("100"), new BigDecimal("150.00")));
        service.addPosition(new Position(1L, "GOOGL", new BigDecimal("50"), new BigDecimal("2800.00")));
        
        Order buyAapl = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("25"), new BigDecimal("160.00"), "buy-aapl");
        service.applyBuy(buyAapl);
        
        Position aaplPos = service.getPosition(1L, "AAPL");
        Position googlPos = service.getPosition(1L, "GOOGL");
        
        assertEquals(new BigDecimal("125"), aaplPos.getQuantity());
        assertEquals(new BigDecimal("50"), googlPos.getQuantity()); // Unchanged
    }

    // ===== INPUT VALIDATION TESTS - Verify invalid inputs are rejected =====
    
    @Test
    @DisplayName("add position with null account ID throws exception")
    void addPositionNullAccountId() {
        Position position = new Position(null, "AAPL", new BigDecimal("100"), new BigDecimal("150.00"));
        
        assertThrows(IllegalArgumentException.class, () -> service.addPosition(position));
    }

    @Test
    @DisplayName("add position with blank symbol throws exception")
    void addPositionBlankSymbol() {
        Position position = new Position(1L, "", new BigDecimal("100"), new BigDecimal("150.00"));
        
        assertThrows(IllegalArgumentException.class, () -> service.addPosition(position));
    }

    @Test
    @DisplayName("get position with null account ID throws exception")
    void getPositionNullAccountId() {
        assertThrows(IllegalArgumentException.class, () -> service.getPosition(null, "AAPL"));
    }

    @Test
    @DisplayName("get position with blank symbol throws exception")
    void getPositionBlankSymbol() {
        assertThrows(IllegalArgumentException.class, () -> service.getPosition(1L, ""));
    }

    // ===== EDGE CASES & SPECIAL SCENARIOS - Verify system handles fractional shares, high precision, and large quantities =====
    
    @Test
    @DisplayName("fractional share quantities handled correctly")
    void fractionalShares() {
        Order order = new Order(1L, "AAPL", OrderSide.BUY, 
            new BigDecimal("0.5"), new BigDecimal("150.00"), "frac-1");
        service.applyBuy(order);
        
        Position position = service.getPosition(1L, "AAPL");
        assertEquals(new BigDecimal("0.5"), position.getQuantity());
    }

    @Test
    @DisplayName("sell partial fractional shares")
    void sellFractionalShares() {
        Position position = new Position(1L, "AAPL", new BigDecimal("1.5"), new BigDecimal("150.00"));
        service.addPosition(position);
        
        Order order = new Order(1L, "AAPL", OrderSide.SELL, new BigDecimal("0.5"), new BigDecimal("160.00"), "frac-sell");
        service.applySell(order);
        
        Position updated = service.getPosition(1L, "AAPL");
        assertEquals(0, updated.getQuantity().compareTo(new BigDecimal("1.0"))); // 1.5 - 0.5
    }

    @Test
    @DisplayName("high precision decimal prices maintain accuracy")
    void highPrecisionPrices() {
        Order order = new Order(1L, "AAPL", OrderSide.BUY, 
            new BigDecimal("100"), new BigDecimal("150.12345"), "precise-1");
        service.applyBuy(order);
        
        Position position = service.getPosition(1L, "AAPL");
        assertEquals(new BigDecimal("150.12345"), position.getAverageCost());
    }

    @Test
    @DisplayName("buy order with very large quantity")
    void veryLargeQuantity() {
        Order order = new Order(1L, "AAPL", OrderSide.BUY, 
            new BigDecimal("1000000"), new BigDecimal("150.00"), "large-qty");
        service.applyBuy(order);
        
        Position position = service.getPosition(1L, "AAPL");
        assertEquals(new BigDecimal("1000000"), position.getQuantity());
    }
}
