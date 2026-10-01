package com.leapvelocity.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leapvelocity.TradingPlatformApplication;
import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Execution;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.AccountStatus;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.ExecutionRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.repository.OrderRepository;
import com.leapvelocity.repository.PositionRepository;
import com.leapvelocity.service.OrderExecutionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = TradingPlatformApplication.class,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.datasource.url=jdbc:h2:mem:listener-settlement;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.consumer.group-id=listener-settlement-test",
                "spring.kafka.listener.auto-startup=false",
                "server.port=0"
        })
@EmbeddedKafka(partitions = 1, topics = {"orders", "trade-events"})
@DisplayName("ExecutionEventListener Integration")
class ExecutionEventListenerIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExecutionEventListener executionEventListener;

    @Autowired
    private OrderExecutionService orderExecutionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @AfterEach
    void tearDown() {
        executionRepository.deleteAll();
        positionRepository.deleteAll();
        orderRepository.deleteAll();
        instrumentRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("should settle a NEW order when a trade-events message arrives")
    void tradeEventSettlesAcceptedOrder() throws Exception {
        Account account = accountRepository.save(
                new Account("ACC-IT-001", "Integration Trader", new BigDecimal("10000.00"), AccountStatus.ACTIVE));
        Instrument instrument = instrumentRepository.save(
                new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true));

        Order acceptedOrder = orderExecutionService.placeOrder(
                new Order(account.getId(), instrument.getSymbol(), OrderSide.BUY,
                        new BigDecimal("50"), new BigDecimal("100.00"), "it-fill-001"));

        Order persistedBeforeFill = orderRepository.findById(acceptedOrder.getId()).orElseThrow();
        assertEquals(OrderStatus.NEW, persistedBeforeFill.getStatus());
        assertEquals(new BigDecimal("10000.00"), accountRepository.findById(account.getId()).orElseThrow().getCashBalance());
        assertTrue(positionRepository.findByAccountIdAndSymbol(account.getId(), instrument.getSymbol()).isEmpty());
        assertEquals(0, executionRepository.count());

        ExecutionEvent fill = new ExecutionEvent(
                UUID.randomUUID(),
                acceptedOrder.getId(),
                account.getId(),
                instrument.getSymbol(),
                OrderSide.BUY,
                new BigDecimal("50"),
                new BigDecimal("99.50"),
                new BigDecimal("100.00"),
                "SIM",
                Instant.now());

        executionEventListener.onExecution(objectMapper.writeValueAsString(fill));

        Order settledOrder = orderRepository.findById(acceptedOrder.getId()).orElseThrow();
        Account settledAccount = accountRepository.findById(account.getId()).orElseThrow();
        Position settledPosition = positionRepository.findByAccountIdAndSymbol(account.getId(), instrument.getSymbol())
                .orElseThrow();
        Execution persistedExecution = executionRepository.findAll().stream()
                .filter(execution -> execution.getOrderId().equals(acceptedOrder.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(OrderStatus.FILLED, settledOrder.getStatus());
        assertEquals(new BigDecimal("5025.00"), settledAccount.getCashBalance());
        assertEquals(0, settledPosition.getQuantity().compareTo(new BigDecimal("50")));
        assertEquals(new BigDecimal("99.50"), settledPosition.getAverageCost());
        assertEquals(new BigDecimal("99.50"), persistedExecution.getPrice());
        assertNotNull(persistedExecution.getExecutedOn());
    }
}