package com.neueda.trading.engine;

import com.neueda.trading.engine.exceptions.PermanentFailureException;
import com.neueda.trading.engine.exceptions.TemporaryFailureException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerAwareListenerErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom error handler for Kafka listener errors in the order processing pipeline.
 *
 * Implements two distinct failure handling strategies:
 * - **Permanent Failures**: Sent immediately to the Dead Letter Topic (DLT).
 *   Examples: invalid message format, malformed JSON
 * - **Temporary Failures**: Retried with exponential backoff until retry budget exhausted,
 *   then sent to DLT. Examples: database unavailable, service timeout
 *
 * The handler uses an in-memory retry counter to track attempts per message.
 */
@Component
public class ExecutionEngineErrorHandler implements ConsumerAwareListenerErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEngineErrorHandler.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RetryProperties retryProperties;

    @Value("${engine.topics.dead-letter}")
    private String deadLetterTopic;

    // Simple in-memory tracker: maps (topic-partition-offset) to retry count
    // In production, this could be enhanced with Redis or a database
    private final Map<String, Integer> retryCounters = new HashMap<>();

    public ExecutionEngineErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                       RetryProperties retryProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.retryProperties = retryProperties;
    }

    /**
     * Setter for testing - allows injecting the DLT topic name without Spring configuration.
     */
    public void setDeadLetterTopic(String deadLetterTopic) {
        this.deadLetterTopic = deadLetterTopic;
    }

    @Override
    public Object handleError(Message<?> message, ListenerExecutionFailedException exception, Consumer<?, ?> consumer) {
        Throwable cause = exception.getCause();
        Object recoveredPayload = message.getPayload();
        
        // Try to get ConsumerRecord from message headers
        ConsumerRecord<?, ?> record = (ConsumerRecord<?, ?>) message.getHeaders().get("kafka_receivedRecord");


        if (record == null) {
            // If we still can't get the record, route by exception type
            
            // Permanent failures: send fallback DLT message and don't retry
            if (cause instanceof PermanentFailureException) {
                log.warn("Permanent failure (no ConsumerRecord available): {}", cause.getMessage());
                sendFallbackToDLT(message, "PERMANENT_FAILURE", cause.getMessage());
                return recoveredPayload;  // Swallow the exception - don't retry
            }
            
            // Temporary failures: still need to retry, so re-throw
            if (cause instanceof TemporaryFailureException) {
                log.warn("Temporary failure (no ConsumerRecord available): {}", cause.getMessage());
                throw exception;
            }
            
            // For other exceptions, send fallback DLT and don't retry
            log.error("Unhandled exception (no ConsumerRecord available): {}", cause.getClass().getSimpleName());
            sendFallbackToDLT(message, "UNHANDLED_EXCEPTION", cause.getClass().getSimpleName());
            return recoveredPayload;
        }

        // Permanent failures go to DLT immediately
        if (cause instanceof PermanentFailureException) {
            log.warn("Permanent failure for message at offset {}: {}", record.offset(), cause.getMessage());
            sendToDLT(record, "PERMANENT_FAILURE: " + cause.getMessage());
            return recoveredPayload;
        }

        // Temporary failures are retried with exponential backoff
        if (cause instanceof TemporaryFailureException || cause instanceof Exception) {
            String messageKey = buildMessageKey(record);
            int attempts = retryCounters.getOrDefault(messageKey, 0);

            if (attempts < retryProperties.maxRetries()) {
                attempts++;
                retryCounters.put(messageKey, attempts);
                long delayMs = retryProperties.backoffFor(attempts - 1).toMillis();

                log.info("Temporary failure for message at offset {} - retry {} of {} after {}ms backoff: {}",
                        record.offset(), attempts, retryProperties.maxRetries(), delayMs, cause.getMessage());

                // In a real implementation, use Spring Retry or Kafka's RetryTemplate
                // For now, log the backoff strategy
                try {
                    Thread.sleep(delayMs);
                    // Re-throw to let Spring Kafka retry the message
                    throw exception;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Backoff interrupted", e);
                    sendToDLT(record, "BACKOFF_INTERRUPTED: " + e.getMessage());
                }
            } else {
                // Retry budget exhausted
                log.error("Retry budget exhausted ({} attempts) for message at offset {}: {}",
                        attempts, record.offset(), cause.getMessage());
                sendToDLT(record, "TEMPORARY_FAILURE_RETRIES_EXHAUSTED: " + cause.getMessage());
                retryCounters.remove(messageKey);
            }
        }

        return recoveredPayload;
    }

    @Override
    public Object handleError(Message<?> message, ListenerExecutionFailedException exception) {
        log.error("Error handling message without consumer context", exception);
        ConsumerRecord<?, ?> record = (ConsumerRecord<?, ?>) message.getHeaders()
                .get("kafka_receivedRecord");
        if (record != null) {
            sendToDLT(record, "ERROR_NO_CONSUMER_CONTEXT: " + exception.getMessage());
        }
        return null;
    }

    /**
     * Sends a message to the Dead Letter Topic for later analysis and recovery.
     *
     * @param record the original consumer record that failed
     * @param failureReason human-readable reason for the failure
     */
    private void sendToDLT(ConsumerRecord<?, ?> record, String failureReason) {
        try {
            ProducerRecord<String, String> dltRecord = new ProducerRecord<>(
                    deadLetterTopic,
                    String.valueOf(record.key()),
                    String.format("{\"originalTopic\":\"%s\",\"originalOffset\":%d,\"originalPartition\":%d," +
                                    "\"failureReason\":\"%s\",\"message\":\"%s\"}",
                            record.topic(), record.offset(), record.partition(),
                            escapeJson(failureReason), escapeJson(String.valueOf(record.value())))
            );

            kafkaTemplate.send(dltRecord).get();
            log.info("Message sent to DLT topic '{}' from original topic '{}'", deadLetterTopic, record.topic());
        } catch (Exception e) {
            log.error("Failed to send message to DLT topic", e);
        }
    }

    /**
     * Build a unique key for tracking retry attempts on this message.
     */
    private String buildMessageKey(ConsumerRecord<?, ?> record) {
        return record.topic() + "-" + record.partition() + "-" + record.offset();
    }

    /**
     * Send a message to the Dead Letter Topic when ConsumerRecord is unavailable.
     * Creates a fallback format with message content and failure reason.
     *
     * @param message the failed message from Spring Kafka
     * @param failureType type of failure (e.g., PERMANENT_FAILURE, UNHANDLED_EXCEPTION)
     * @param failureReason human-readable reason for the failure
     */
    private void sendFallbackToDLT(Message<?> message, String failureType, String failureReason) {
        try {
            String messageContent = message.getPayload() != null ? String.valueOf(message.getPayload()) : "";
            Object keyHeader = message.getHeaders().get("kafka_receivedTopic");
            String key = keyHeader != null ? String.valueOf(keyHeader) : "unknown";

            ProducerRecord<String, String> dltRecord = new ProducerRecord<>(
                    deadLetterTopic,
                    key,
                    String.format("{\"failureType\":\"%s\",\"failureReason\":\"%s\"," +
                                    "\"originalTopic\":\"orders\",\"originalOffset\":\"unknown\"," +
                                    "\"originalPartition\":\"unknown\",\"message\":\"%s\"}",
                            escapeJson(failureType), escapeJson(failureReason), escapeJson(messageContent))
            );

            kafkaTemplate.send(dltRecord).get();
            log.info("Fallback message sent to DLT topic '{}' due to {}: {}", 
                    deadLetterTopic, failureType, failureReason);
        } catch (Exception e) {
            log.error("Failed to send fallback message to DLT topic", e);
        }
    }

    /**
     * Simple JSON string escaping for the failure reason.
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
