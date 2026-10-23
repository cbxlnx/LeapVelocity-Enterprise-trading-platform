package com.leapvelocity.entities;

import com.leapvelocity.entities.enums.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Execution Entity")
class ExecutionTest {

    @Test
    @DisplayName("constructor stores execution details and sets timestamp")
    void constructorStoresExecutionDetailsAndSetsTimestamp() {
        UUID orderId = UUID.randomUUID();
        Execution execution = new Execution(orderId, 5L, "AAPL", OrderSide.SELL,
                new BigDecimal("3.50"), new BigDecimal("210.15"));

        assertEquals(orderId, execution.getOrderId());
        assertEquals(5L, execution.getAccountId());
        assertEquals("AAPL", execution.getSymbol());
        assertEquals(OrderSide.SELL, execution.getSide());
        assertEquals(new BigDecimal("3.50"), execution.getQuantity());
        assertEquals(new BigDecimal("210.15"), execution.getPrice());
        assertNotNull(execution.getExecutedOn());
        assertNull(execution.getId());
    }

    @Test
    @DisplayName("pre persist fills missing id and timestamp")
    void prePersistFillsMissingIdAndTimestamp() {
        Execution execution = new Execution();
        execution.setOrderId(UUID.randomUUID());
        execution.setAccountId(9L);
        execution.setSymbol("MSFT");
        execution.setSide(OrderSide.BUY);
        execution.setQuantity(new BigDecimal("1.00"));
        execution.setPrice(new BigDecimal("100.00"));

        execution.ensurePersistentDefaults();

        assertNotNull(execution.getId());
        assertNotNull(execution.getExecutedOn());
    }

    @Test
    @DisplayName("pre persist preserves existing id and timestamp")
    void prePersistPreservesExistingIdAndTimestamp() {
        Execution execution = new Execution();
        UUID id = UUID.randomUUID();
        LocalDateTime executedOn = LocalDateTime.of(2026, 10, 9, 16, 0);
        execution.setId(id);
        execution.setExecutedOn(executedOn);

        execution.ensurePersistentDefaults();

        assertEquals(id, execution.getId());
        assertEquals(executedOn, execution.getExecutedOn());
    }

    @Test
    @DisplayName("setters update execution fields")
    void settersUpdateExecutionFields() {
        Execution execution = new Execution();
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        LocalDateTime executedOn = LocalDateTime.of(2026, 10, 9, 17, 30);

        execution.setId(id);
        execution.setOrderId(orderId);
        execution.setAccountId(7L);
        execution.setSymbol("NVDA");
        execution.setSide(OrderSide.BUY);
        execution.setQuantity(new BigDecimal("2.25"));
        execution.setPrice(new BigDecimal("950.50"));
        execution.setExecutedOn(executedOn);

        assertEquals(id, execution.getId());
        assertEquals(orderId, execution.getOrderId());
        assertEquals(7L, execution.getAccountId());
        assertEquals("NVDA", execution.getSymbol());
        assertEquals(OrderSide.BUY, execution.getSide());
        assertEquals(new BigDecimal("2.25"), execution.getQuantity());
        assertEquals(new BigDecimal("950.50"), execution.getPrice());
        assertEquals(executedOn, execution.getExecutedOn());
    }
}