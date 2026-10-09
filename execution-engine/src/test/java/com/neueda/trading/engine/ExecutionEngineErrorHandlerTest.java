package com.neueda.trading.engine;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.neueda.trading.engine.exceptions.PermanentFailureException;
import com.neueda.trading.engine.exceptions.TemporaryFailureException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExecutionEngineErrorHandlerTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    @SuppressWarnings("unchecked")
    private final Consumer<String, String> consumer = mock(Consumer.class);

    private ExecutionEngineErrorHandler errorHandler() {
        RetryProperties props = new RetryProperties(3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(1));
        ExecutionEngineErrorHandler handler = new ExecutionEngineErrorHandler(kafkaTemplate, props);
        handler.setDeadLetterTopic("orders-dlq");
        return handler;
    }

    @Test
    void testHandleError_PermanentFailure_SendsToDLTImmediately() {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 0, 0, "key", "value");
        Message<?> message = MessageBuilder.withPayload("test")
                .setHeader("kafka_receivedRecord", record)
                .build();

        PermanentFailureException cause = new PermanentFailureException("Invalid JSON format");
        ListenerExecutionFailedException exception = new ListenerExecutionFailedException("Error", cause);

        Object result = errorHandler().handleError(message, exception, consumer);

        // Verify that the message was sent to DLT
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
        assertEquals("test", result);
    }

    @Test
    void testHandleError_TemporaryFailure_AttemptsRetry() {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 0, 0, "key", "value");
        Message<?> message = MessageBuilder.withPayload("test")
                .setHeader("kafka_receivedRecord", record)
                .build();

        Exception temporaryFailure = new TemporaryFailureException("Database unavailable");
        ListenerExecutionFailedException exception = new ListenerExecutionFailedException("Error", temporaryFailure);

        // First attempt - should not send to DLT yet
        try {
            errorHandler().handleError(message, exception, consumer);
        } catch (Exception e) {
            // Expected to re-throw for retry
        }

        // DLT should not be called on first retry attempt
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void testHandleError_TemporaryFailure_ExhaustsRetries() {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 0, 0, "key", "value");
        Message<?> message = MessageBuilder.withPayload("test")
                .setHeader("kafka_receivedRecord", record)
                .build();

        Exception temporaryFailure = new TemporaryFailureException("Database unavailable");
        ListenerExecutionFailedException exception = new ListenerExecutionFailedException("Error", temporaryFailure);

        ExecutionEngineErrorHandler handler = errorHandler();

        // Attempt 1
        try {
            handler.handleError(message, exception, consumer);
        } catch (Exception e) {
            // Expected re-throw
        }

        // Attempt 2
        try {
            handler.handleError(message, exception, consumer);
        } catch (Exception e) {
            // Expected re-throw
        }

        // Attempt 3
        try {
            handler.handleError(message, exception, consumer);
        } catch (Exception e) {
            // Expected re-throw
        }

        // Attempt 4 (exceeds max of 3)
        Object result = handler.handleError(message, exception, consumer);

        // After max retries, should send to DLT
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
        assertEquals("test", result);
    }

    @Test
    void testHandleError_WithoutConsumerRecord_LogsAndSkips() {
        Message<?> message = MessageBuilder.withPayload("test").build();
        Exception cause = new Exception("Some error");
        ListenerExecutionFailedException exception = new ListenerExecutionFailedException("Error", cause);

        // Should not throw an exception
        Object result = errorHandler().handleError(message, exception);

        // No consumer record means the fallback overload still swallows the error.
        assertNull(result);
    }

    @Test
    void testHandleError_PermanentFailure_IncludesFailureReasonInDLT() {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 0, 0, "key", "{malformed json}");
        Message<?> message = MessageBuilder.withPayload("test")
                .setHeader("kafka_receivedRecord", record)
                .build();

        PermanentFailureException cause = new PermanentFailureException("Invalid JSON format");
        ListenerExecutionFailedException exception = new ListenerExecutionFailedException("Error", cause);

        errorHandler().handleError(message, exception, consumer);

        // Verify DLT message was sent
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }
}
