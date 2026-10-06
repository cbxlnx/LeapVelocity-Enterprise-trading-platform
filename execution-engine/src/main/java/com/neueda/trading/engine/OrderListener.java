package com.neueda.trading.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.trading.engine.exceptions.PermanentFailureException;
import com.neueda.trading.engine.exceptions.TemporaryFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Step 2 of the event flow: reads each order from the {@code orders} topic,
 * works it on the {@link SimulatedMarket}, and writes the fill to the
 * {@code executions} topic keyed by account.
 *
 * <p>The fill is sent and acknowledged by Kafka before this method returns,
 * so the order's offset is only committed once its fill is safely on the
 * executions topic: at-least-once. If the engine dies mid-order it works the
 * order again on restart, and the trade API ignores the duplicate fill.
 *
 * <p>Failure Handling:
 * - **Permanent Failures** (e.g., invalid JSON): Thrown as {@link PermanentFailureException}
 *   and immediately sent to the Dead Letter Topic by the error handler.
 * - **Temporary Failures** (e.g., Kafka send timeout): Thrown as {@link TemporaryFailureException}
 *   and retried with exponential backoff until the retry budget is exhausted, then sent to DLT.
 */
@Component
public class OrderListener {

    private static final Logger log = LoggerFactory.getLogger(OrderListener.class);

    private final SimulatedMarket market;
    private final Pauser pauser;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String executionsTopic;

    public OrderListener(SimulatedMarket market, Pauser pauser, KafkaTemplate<String, String> kafkaTemplate,
                         ObjectMapper objectMapper, @Value("${engine.topics.executions}") String executionsTopic) {
        this.market = market;
        this.pauser = pauser;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.executionsTopic = executionsTopic;
    }

    @KafkaListener(topics = "${engine.topics.orders}", groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${engine.topics.partitions}", errorHandler = "orderListenerErrorHandler")
    public void onOrder(String message) throws PermanentFailureException, TemporaryFailureException, InterruptedException {
        OrderEvent order;
        try {
            order = objectMapper.readValue(message, OrderEvent.class);
        } catch (JsonProcessingException e) {
            // Invalid message format is a permanent failure - no point retrying
            String errorMsg = "Skipping unreadable order message: " + message;
            log.error(errorMsg, e);
            throw new PermanentFailureException(errorMsg, e);
        }

        log.info("Working order {}: {} {} {} limit {}", order.orderId(), order.side(), order.quantity(),
                order.symbol(), order.price());

        try {
            pauser.pause(market.nextDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }

        ExecutionEvent fill = market.execute(order);

        try {
            String executionJson = objectMapper.writeValueAsString(fill);
            kafkaTemplate.send(executionsTopic, String.valueOf(fill.accountId()), executionJson)
                    .get(10, TimeUnit.SECONDS);
            log.info("Filled order {} at {} on {}", fill.orderId(), fill.price(), fill.venue());
        } catch (JsonProcessingException e) {
            // Serialization errors are permanent - message cannot be recovered
            String errorMsg = "Failed to serialize execution for order " + order.orderId();
            log.error(errorMsg, e);
            throw new PermanentFailureException(errorMsg, e);
        } catch (TimeoutException e) {
            // Timeout publishing fill is a temporary failure - should retry
            String errorMsg = "Timeout sending execution to topic '" + executionsTopic + "' for order " + order.orderId();
            log.warn(errorMsg, e);
            throw new TemporaryFailureException(errorMsg, e);
        } catch (ExecutionException e) {
            // Check if the underlying cause indicates a permanent or temporary failure
            Throwable cause = e.getCause();
            if (cause instanceof org.apache.kafka.common.errors.SerializationException) {
                // Serialization errors are permanent
                String errorMsg = "Failed to serialize execution for order " + order.orderId();
                log.error(errorMsg, cause);
                throw new PermanentFailureException(errorMsg, cause);
            } else {
                // Other execution errors are treated as temporary
                String errorMsg = "Failed to send execution for order " + order.orderId();
                log.warn(errorMsg, cause);
                throw new TemporaryFailureException(errorMsg, cause);
            }
        }
    }
}
