package com.neueda.trading.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.trading.engine.exceptions.PermanentFailureException;
import com.neueda.trading.engine.exceptions.TemporaryFailureException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderListenerTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final QuoteMarket market = new QuoteMarket(Duration.ofSeconds(15), "SIM",
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    private OrderListener listener() {
        return new OrderListener(market, kafka, mapper, "executions");
    }

    private void publishQuote(String symbol, String bid, String ask) {
        market.update(new MarketDataEvent(symbol, new BigDecimal(ask), new BigDecimal(bid), new BigDecimal(ask),
                "USD", Instant.EPOCH, "test"));
    }

    @Test
    void testOnOrder_MarketableQuote_PublishesFillAtQuotePrice() throws Exception {
        @SuppressWarnings("unchecked")
        SendResult<String, String> sent = mock(SendResult.class);
        when(kafka.send(eq("executions"), eq("4"), anyString())).thenReturn(CompletableFuture.completedFuture(sent));
        publishQuote("VERDA", "4.25", "4.30");
        OrderEvent order = new OrderEvent(UUID.randomUUID(), 4L, "VERDA", Side.SELL, new BigDecimal("5"),
                new BigDecimal("4.20"), Instant.EPOCH);

        listener().onOrder(mapper.writeValueAsString(order));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("executions"), eq("4"), json.capture());
        ExecutionEvent fill = mapper.readValue(json.getValue(), ExecutionEvent.class);
        assertEquals(new BigDecimal("4.25"), fill.price());
    }

    @Test
    void testOnOrder_NoQuote_ThrowsTemporaryFailure() throws Exception {
        OrderEvent order = new OrderEvent(UUID.randomUUID(), 4L, "VERDA", Side.SELL, new BigDecimal("5"),
                new BigDecimal("4.20"), Instant.EPOCH);

        TemporaryFailureException exception = assertThrows(TemporaryFailureException.class, () ->
                listener().onOrder(mapper.writeValueAsString(order))
        );

        assertTrue(exception.getMessage().contains("No quote yet for VERDA"));
        verifyNoInteractions(kafka);
    }

    @Test
    void testOnOrder_UnreadableMessage_ThrowsPermanentFailure() {
        PermanentFailureException exception = assertThrows(PermanentFailureException.class, () ->
                listener().onOrder("not json")
        );

        assertTrue(exception.getMessage().contains("Skipping unreadable order message"));
        verifyNoInteractions(kafka);
    }

    @Test
    void testOnOrder_InvalidJSON_ThrowsPermanentFailure() {
        String invalidJson = "{\"orderId\": \"not-a-uuid\"}";
        PermanentFailureException exception = assertThrows(PermanentFailureException.class, () ->
                listener().onOrder(invalidJson)
        );

        assertTrue(exception.getMessage().contains("unreadable order message"));
        verifyNoInteractions(kafka);
    }

    @Test
    void testOnOrder_KafkaSendFailure_ThrowsTemporaryFailure() throws Exception {
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new ExecutionException("Send failed", new RuntimeException("Broker unavailable")));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(future);

        publishQuote("VERDA", "4.25", "4.30");
        OrderEvent order = new OrderEvent(UUID.randomUUID(), 4L, "VERDA", Side.SELL, new BigDecimal("5"),
                new BigDecimal("4.20"), Instant.EPOCH);

        TemporaryFailureException exception = assertThrows(TemporaryFailureException.class, () ->
                listener().onOrder(mapper.writeValueAsString(order))
        );

        assertTrue(exception.getMessage().contains("Failed to send execution"));
    }
}
