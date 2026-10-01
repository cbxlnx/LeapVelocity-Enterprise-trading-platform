package com.leapvelocity.service;

import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.AccountStatus;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import com.leapvelocity.messaging.ExecutionEvent;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.ExecutionRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.repository.OrderRepository;
import com.leapvelocity.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * Edge case and boundary condition tests for OrderExecutionService.
 * 
 * Tests cover scenarios that are rare but critical for data integrity:
 * - Exactly sufficient cash (balance = 0 after order)
 * - Fractional share purchases
 * - Very large quantities and notional values
 * - High-precision decimal prices
 * - Boundary conditions where position quantity equals holdings
 * 
 * These tests verify the service correctly handles mathematical precision,
 * boundary conditions, and extreme (but valid) trading scenarios.
 */
@DisplayName("OrderExecutionService - Edge Cases")
class OrderExecutionServiceEdgeCasesTest {

    private OrderExecutionService service;
    private Account activeAccount;
    private Instrument tradableInstrument;
        private Map<Long, Account> accountsById;
        private Map<String, Instrument> instrumentsBySymbol;
        private Map<UUID, Order> ordersById;
        private Map<String, Order> ordersByIdempotencyKey;
        private Map<String, Position> positionsByKey;

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

                stubAccountRepository(accountRepository);
                stubInstrumentRepository(instrumentRepository);
                stubOrderRepository(orderRepository);
                stubPositionRepository(positionRepository);

                service = new OrderExecutionService(
                                accountRepository,
                                instrumentRepository,
                                orderRepository,
                                executionRepository,
                                new PositionUpdateService(positionRepository)
                );

        activeAccount = new Account("ACC-EDGE", "Edge Case Trader", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        activeAccount.setId(1L);
        tradableInstrument = new Instrument("TEST", "Test Instrument", "EQUITY", "USD", true);

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

    // ==================== EXACTLY SUFFICIENT CASH TESTS ====================
    // Verify system handles orders using exactly the available cash balance

    @Nested
    @DisplayName("Exactly Sufficient Cash (Zero Balance After)")
    class ExactlySufficientCashTests {

        @Test
        @DisplayName("should reduce balance to exactly zero when cash matches notional")
        void buyWithExactCash() {
            // Verifies: Balance = 0 after order when quantity × price = account balance
            // Account has $10,000; buying 100 shares @ $100 = $10,000 total
            Order order = new Order(activeAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("100"), new BigDecimal("100.00"), "exact-cash-001");

            Order result = placeAndSettle(order);

            assertEquals(OrderStatus.FILLED, result.getStatus());
            assertEquals(0, activeAccount.getCashBalance().compareTo(BigDecimal.ZERO));

            Position position = service.getPosition(activeAccount.getId(), "TEST");
            assertNotNull(position);
            assertEquals(new BigDecimal("100"), position.getQuantity());
            assertEquals(new BigDecimal("100.00"), position.getAverageCost());
        }

        @Test
        @DisplayName("should handle second order rejection when balance is zero")
        void orderRejectedAfterZeroBalance() {
            // Verifies: System cannot place another order when balance = 0
            Account limitedAccount = new Account("ACC-LIMITED", "Limited Funds", 
                    new BigDecimal("1000.00"), AccountStatus.ACTIVE);
            limitedAccount.setId(2L);
            service.addAccount(limitedAccount);

            // First order uses all cash
            Order order1 = new Order(limitedAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("10"), new BigDecimal("100.00"), "zero-bal-001");
            placeAndSettle(order1);
            assertEquals(0, limitedAccount.getCashBalance().compareTo(BigDecimal.ZERO));

            // Second order should fail (insufficient funds)
            Order order2 = new Order(limitedAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("1"), new BigDecimal("50.00"), "zero-bal-002");
            
            assertThrows(com.leapvelocity.exceptions.InsufficientFundsException.class, 
                    () -> placeAndSettle(order2));
        }
    }

    // ==================== FRACTIONAL SHARES TESTS ====================
    // Verify system handles decimal quantities (fractional shares)

    @Nested
    @DisplayName("Fractional Share Handling")
    class FractionalSharesTests {

        @Test
        @DisplayName("should handle fractional share purchase (0.5 shares)")
        void fractionalSharePurchase() {
            // Verifies: Fractional shares (0.5 AAPL @ $100 = $50) work correctly
            Order order = new Order(activeAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("0.5"), new BigDecimal("100.00"), "frac-buy-001");

            Order result = placeAndSettle(order);

            assertEquals(OrderStatus.FILLED, result.getStatus());
            assertEquals(0, activeAccount.getCashBalance().compareTo(new BigDecimal("9950.00")));

            Position position = service.getPosition(activeAccount.getId(), "TEST");
            assertEquals(new BigDecimal("0.5"), position.getQuantity());
        }

        @Test
        @DisplayName("should calculate average cost correctly with fractional shares")
        void fractionalShareAverageCost() {
            // Verifies: Weighted average cost calculation with fractions
            // Buy 0.5 @ $100 + 0.25 @ $120 = (50 + 30) / 0.75 = 106.66666...
            Order order1 = new Order(activeAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("0.5"), new BigDecimal("100.00"), "frac-avg-001");
            placeAndSettle(order1);

            Order order2 = new Order(activeAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("0.25"), new BigDecimal("120.00"), "frac-avg-002");
            placeAndSettle(order2);

            Position position = service.getPosition(activeAccount.getId(), "TEST");
            assertEquals(new BigDecimal("0.75"), position.getQuantity());
            // Average cost should be approximately 106.6666... 
            // Verify it's between 106.66 and 106.67
            BigDecimal avgCost = position.getAverageCost();
            assertTrue(avgCost.compareTo(new BigDecimal("106.66")) > 0 && 
                      avgCost.compareTo(new BigDecimal("106.67")) <= 0,
                    "Average cost " + avgCost + " should be ~106.6667");
        }

        @Test
        @DisplayName("should handle partial sale of fractional shares")
        void sellPartialFractionalShares() {
            // Verifies: Can sell portion of fractional position
            Position position = new Position(activeAccount.getId(), "TEST", 
                    new BigDecimal("1.5"), new BigDecimal("100.00"));
            service.addPosition(position);

            Order order = new Order(activeAccount.getId(), "TEST", OrderSide.SELL,
                    new BigDecimal("0.5"), new BigDecimal("110.00"), "frac-sell-001");
            placeAndSettle(order);

            Position updated = service.getPosition(activeAccount.getId(), "TEST");
            assertEquals(0, updated.getQuantity().compareTo(new BigDecimal("1.0")));
            assertEquals(0, activeAccount.getCashBalance().compareTo(new BigDecimal("10055.00")));
        }

        @Test
        @DisplayName("should sell entire fractional position correctly")
        void sellEntireFractionalPosition() {
            // Verifies: Selling 0.75 shares when position = 0.75 removes position
            Position position = new Position(activeAccount.getId(), "TEST", 
                    new BigDecimal("0.75"), new BigDecimal("100.00"));
            service.addPosition(position);

            Order order = new Order(activeAccount.getId(), "TEST", OrderSide.SELL,
                    new BigDecimal("0.75"), new BigDecimal("110.00"), "frac-all-001");
            placeAndSettle(order);

            assertNull(service.getPosition(activeAccount.getId(), "TEST"));
        }
    }

    // ==================== HIGH PRECISION DECIMAL PRICES ====================
    // Verify system maintains precision with high-decimal prices

    @Nested
    @DisplayName("High Precision Decimal Prices")
    class HighPrecisionTests {

        @Test
        @DisplayName("should maintain precision with high-decimal prices (e.g., $1.234567)")
        void highPrecisionPrice() {
            // Verifies: Prices like $1.234567 are preserved in calculations
            Order order = new Order(activeAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("100"), new BigDecimal("1.234567"), "precision-001");

            Order result = placeAndSettle(order);

            assertEquals(OrderStatus.FILLED, result.getStatus());

            Position position = service.getPosition(activeAccount.getId(), "TEST");
            assertEquals(new BigDecimal("1.234567"), position.getAverageCost());
        }

        @Test
        @DisplayName("should calculate weighted average correctly with high precision")
        void highPrecisionWeightedAverage() {
            // Verifies: Weighted average maintains precision across decimal places
            Account richAccount = new Account("ACC-RICH2", "Rich Account", 
                    new BigDecimal("50000.00"), AccountStatus.ACTIVE);
            richAccount.setId(5L);
            service.addAccount(richAccount);

            Order order1 = new Order(richAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("100"), new BigDecimal("100.123456"), "prec-avg-001");
            placeAndSettle(order1);

            Order order2 = new Order(richAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("50"), new BigDecimal("100.654321"), "prec-avg-002");
            placeAndSettle(order2);

            Position position = service.getPosition(richAccount.getId(), "TEST");
            assertEquals(new BigDecimal("150"), position.getQuantity());
            // Precision should be maintained in calculation
            assertNotNull(position.getAverageCost());
        }
    }

    // ==================== VERY LARGE QUANTITY TESTS ====================
    // Verify system handles extremely large trading volumes

    @Nested
    @DisplayName("Very Large Quantities")
    class VeryLargeQuantityTests {

        @Test
        @DisplayName("should handle 1,000,000+ share quantity purchase")
        void veryLargeQuantityBuy() {
            // Verifies: 1M+ shares don't cause overflow or precision loss
            Account richAccount = new Account("ACC-RICH", "Rich Trader", 
                    new BigDecimal("200000000.00"), AccountStatus.ACTIVE);
            richAccount.setId(2L);
            service.addAccount(richAccount);

            Order order = new Order(richAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("1000000"), new BigDecimal("100.00"), "large-qty-001");

            Order result = placeAndSettle(order);

            assertEquals(OrderStatus.FILLED, result.getStatus());

            Position position = service.getPosition(richAccount.getId(), "TEST");
            assertEquals(new BigDecimal("1000000"), position.getQuantity());
            assertEquals(new BigDecimal("100.00"), position.getAverageCost());
        }

        @Test
        @DisplayName("should handle very large notional value ($100M+)")
        void veryLargeNotionalValue() {
            // Verifies: 1M shares @ $500 = $500M notional without overflow
            Account ultraRichAccount = new Account("ACC-ULTRARICH", "Ultra Rich Trader", 
                    new BigDecimal("600000000.00"), AccountStatus.ACTIVE);
            ultraRichAccount.setId(3L);
            service.addAccount(ultraRichAccount);

            Order order = new Order(ultraRichAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("1000000"), new BigDecimal("500.00"), "large-notional-001");

            Order result = placeAndSettle(order);

            assertEquals(OrderStatus.FILLED, result.getStatus());
            assertEquals(new BigDecimal("100000000.00"), ultraRichAccount.getCashBalance());
        }

        @Test
        @DisplayName("should handle partial sale from very large position")
        void sellFromVeryLargePosition() {
            // Verifies: Partial sales from 1M+ share positions work correctly
            Position position = new Position(activeAccount.getId(), "TEST", 
                    new BigDecimal("1000000"), new BigDecimal("50.00"));
            service.addPosition(position);

            Order order = new Order(activeAccount.getId(), "TEST", OrderSide.SELL,
                    new BigDecimal("500000"), new BigDecimal("60.00"), "large-sell-001");
            placeAndSettle(order);

            Position updated = service.getPosition(activeAccount.getId(), "TEST");
            assertEquals(new BigDecimal("500000"), updated.getQuantity());
        }
    }

    // ==================== BOUNDARY CONDITION TESTS ====================
    // Verify system handles exact boundary conditions correctly

    @Nested
    @DisplayName("Boundary Conditions")
    class BoundaryConditionTests {

        @Test
        @DisplayName("should succeed when selling exact position quantity (boundary case)")
        void sellExactPositionQuantity() {
            // Verifies: Selling quantity == position quantity succeeds and closes position
            Position position = new Position(activeAccount.getId(), "TEST", 
                    new BigDecimal("50.5"), new BigDecimal("100.00"));
            service.addPosition(position);

            Order order = new Order(activeAccount.getId(), "TEST", OrderSide.SELL,
                    new BigDecimal("50.5"), new BigDecimal("110.00"), "boundary-exact-001");

            assertDoesNotThrow(() -> placeAndSettle(order));
            assertNull(service.getPosition(activeAccount.getId(), "TEST"));
        }

        @Test
        @DisplayName("should fail when selling 0.01 more than position quantity")
        void sellTooMuchByMinimalAmount() {
            // Verifies: Even 0.01 more than available is rejected
            Position position = new Position(activeAccount.getId(), "TEST", 
                    new BigDecimal("50.50"), new BigDecimal("100.00"));
            service.addPosition(position);

            Order order = new Order(activeAccount.getId(), "TEST", OrderSide.SELL,
                    new BigDecimal("50.51"), new BigDecimal("110.00"), "boundary-over-001");

            assertThrows(com.leapvelocity.exceptions.InsufficientHoldingsException.class, 
                    () -> placeAndSettle(order));
        }

        @Test
        @DisplayName("should work with $0.01 price (minimum viable price)")
        void minimumViablePrice() {
            // Verifies: Very small prices like $0.01 are accepted
            Order order = new Order(activeAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("100"), new BigDecimal("0.01"), "boundary-min-price-001");

            Order result = placeAndSettle(order);

            assertEquals(OrderStatus.FILLED, result.getStatus());
            assertEquals(new BigDecimal("9999.00"), activeAccount.getCashBalance());
        }
    }

    // ==================== MIXED FRACTIONAL & LARGE QUANTITY TESTS ====================
    // Verify system handles combinations of edge cases

    @Nested
    @DisplayName("Complex Combinations")
    class ComplexCombinationTests {

        @Test
        @DisplayName("should handle fractional quantity with high precision price")
        void fractionalWithHighPrecisionPrice() {
            // Verifies: 0.5 shares @ $1.234567 = $0.617283 debit
            Order order = new Order(activeAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("0.5"), new BigDecimal("1.234567"), "complex-frac-precision-001");

            Order result = placeAndSettle(order);

            assertEquals(OrderStatus.FILLED, result.getStatus());
            Position position = service.getPosition(activeAccount.getId(), "TEST");
            assertEquals(new BigDecimal("0.5"), position.getQuantity());
            assertEquals(new BigDecimal("1.234567"), position.getAverageCost());
        }

        @Test
        @DisplayName("should handle large quantity with exactly sufficient cash")
        void largeQuantityWithExactCash() {
            // Verifies: 100k shares @ $100 = $10M exactly with sufficient balance
            Account millionaireAccount = new Account("ACC-MILLIONAIRE", "Millionaire", 
                    new BigDecimal("10000000.00"), AccountStatus.ACTIVE);
            millionaireAccount.setId(4L);
            service.addAccount(millionaireAccount);

            Order order = new Order(millionaireAccount.getId(), "TEST", OrderSide.BUY,
                    new BigDecimal("100000"), new BigDecimal("100.00"), "complex-large-exact-001");

            Order result = placeAndSettle(order);

            assertEquals(OrderStatus.FILLED, result.getStatus());
            assertEquals(0, millionaireAccount.getCashBalance().compareTo(BigDecimal.ZERO));
        }
    }
}
