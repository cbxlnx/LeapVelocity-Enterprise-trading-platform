package com.leapvelocity.messaging;

import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.service.OrderExecutionService;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagingComponentsTest {

    @Test
    void publishRejectsNullOrderAndNullEvent() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, OrderEvent> kafkaTemplate = mock(KafkaTemplate.class);
        OrderEventPublisher publisher = new OrderEventPublisher(kafkaTemplate);

        assertThrows(IllegalArgumentException.class, () -> publisher.publish((Order) null));
        assertThrows(IllegalArgumentException.class, () -> publisher.publish((OrderEvent) null));
    }

    @Test
    void publishOrderConvertsEntityToKafkaEvent() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, OrderEvent> kafkaTemplate = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        SendResult<String, OrderEvent> sendResult = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.partition()).thenReturn(1);
        when(metadata.offset()).thenReturn(22L);
        when(kafkaTemplate.send(eq(KafkaTopics.ORDERS), eq("7"), org.mockito.ArgumentMatchers.any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        OrderEventPublisher publisher = new OrderEventPublisher(kafkaTemplate);
        Order order = new Order(7L, "AAPL", OrderSide.BUY, new BigDecimal("10.50"), new BigDecimal("187.25"), "idem-7");
        UUID orderId = UUID.randomUUID();
        LocalDateTime createdOn = LocalDateTime.of(2026, 10, 9, 12, 30);
        order.setId(orderId);
        order.setCreatedOn(createdOn);

        publisher.publish(order);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.ORDERS), eq("7"), captor.capture());
        OrderEvent event = captor.getValue();
        assertEquals(orderId, event.orderId());
        assertEquals(7L, event.accountId());
        assertEquals("AAPL", event.symbol());
        assertEquals(OrderSide.BUY, event.side());
        assertEquals(new BigDecimal("10.50"), event.quantity());
        assertEquals(new BigDecimal("187.25"), event.price());
        assertEquals(createdOn.toInstant(ZoneOffset.UTC), event.createdOn());
    }

    @Test
    void publishEventUsesAccountIdAsKafkaKey() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, OrderEvent> kafkaTemplate = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        SendResult<String, OrderEvent> sendResult = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(1L);

        OrderEvent event = new OrderEvent(UUID.randomUUID(), 15L, "MSFT", OrderSide.SELL,
                new BigDecimal("5.00"), new BigDecimal("320.10"), Instant.EPOCH);
        when(kafkaTemplate.send(KafkaTopics.ORDERS, "15", event)).thenReturn(CompletableFuture.completedFuture(sendResult));

        new OrderEventPublisher(kafkaTemplate).publish(event);

        verify(kafkaTemplate).send(KafkaTopics.ORDERS, "15", event);
    }

    @Test
    void executionListenerSettlesExecutionAndAcknowledgesMessage() {
        OrderExecutionService service = mock(OrderExecutionService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ExecutionEvent event = new ExecutionEvent(UUID.randomUUID(), UUID.randomUUID(), 2L, "NVDA", OrderSide.BUY,
                new BigDecimal("2.00"), new BigDecimal("900.00"), new BigDecimal("905.00"), "SIM", Instant.EPOCH);

        new ExecutionEventListener(service).onExecution(event, acknowledgment);

        verify(service).settleExecution(event);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void kafkaTopicsDeclareExpectedNamesAndTopology() {
        KafkaTopics topics = new KafkaTopics();

        assertTopic(topics.ordersTopic(), KafkaTopics.ORDERS);
        assertTopic(topics.tradeEventsTopic(), KafkaTopics.TRADE_EVENTS);
        assertTopic(topics.marketDataTopic(), KafkaTopics.MARKET_DATA);
        assertTopic(topics.ordersDlqTopic(), KafkaTopics.ORDERS_DLQ);
        assertTopic(topics.tradeEventsDlqTopic(), KafkaTopics.TRADE_EVENTS_DLQ);
        assertTopic(topics.marketDataDlqTopic(), KafkaTopics.MARKET_DATA_DLQ);
    }

    private void assertTopic(NewTopic topic, String expectedName) {
        assertNotNull(topic);
        assertEquals(expectedName, topic.name());
        assertEquals(3, topic.numPartitions());
        assertEquals((short) 1, topic.replicationFactor());
    }
}