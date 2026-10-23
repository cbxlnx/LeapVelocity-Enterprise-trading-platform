package com.neueda.trading.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the whole engine against an in-process Kafka broker: an order
 * published to {@code orders} comes back as a fill on {@code trade-events}.
 * No Docker needed.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "engine.retry.max-retries=1000",
        "engine.retry.initial-backoff=10ms",
        "engine.retry.max-backoff=50ms",
        "server.port=0"
})
@EmbeddedKafka(partitions = 1, topics = {"orders", "market-data", "trade-events"})
class ExecutionEngineKafkaTest {

    private static final String MARKET_DATA_TOPIC = "market-data";
    private static final String EXECUTIONS_TOPIC = "trade-events";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void testOrderOnOrdersTopic_ProducesFillOnExecutionsTopic() throws Exception {
        OrderEvent order = new OrderEvent(UUID.randomUUID(), 1L, "ACME", Side.BUY, new BigDecimal("10"),
                new BigDecimal("25.50"), Instant.now());

        try (Consumer<String, String> consumer = executionsConsumer()) {
            kafkaTemplate.send("orders", String.valueOf(order.accountId()), objectMapper.writeValueAsString(order)).get();

            ConsumerRecord<String, String> record = awaitFill(consumer, order.orderId());
            ExecutionEvent fill = objectMapper.readValue(record.value(), ExecutionEvent.class);

            assertEquals("1", record.key());
            assertEquals(order.orderId(), fill.orderId());
            assertEquals(new BigDecimal("10"), fill.quantity());
            assertEquals(new BigDecimal("25.20"), fill.price());
            assertTrue(record.value().contains("\"executedOn\":\""), "timestamps should be ISO strings");
        }
    }

    /**
     * The market-data listener starts at the latest offset, so a quote published before it is assigned
     * is lost. The order keeps retrying until a marketable quote arrives, so keep publishing one until
     * the fill shows up on the executions topic.
     */
    private ConsumerRecord<String, String> awaitFill(Consumer<String, String> consumer, UUID orderId)
            throws Exception {
        String quote = objectMapper.writeValueAsString(new MarketDataEvent("ACME", new BigDecimal("25.10"),
                new BigDecimal("25.00"), new BigDecimal("25.20"), "USD", Instant.now(), "test"));
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            kafkaTemplate.send(MARKET_DATA_TOPIC, "ACME", quote).get();
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                ExecutionEvent fill = objectMapper.readValue(record.value(), ExecutionEvent.class);
                if (fill.orderId().equals(orderId)) {
                    return record;
                }
            }
        }
        throw new AssertionError("No fill for order " + orderId + " within 30 seconds");
    }

    private Consumer<String, String> executionsConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-" + UUID.randomUUID(), "true", broker);
        props.put("auto.offset.reset", "earliest");
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, EXECUTIONS_TOPIC);
        return consumer;
    }
}
