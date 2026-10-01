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
        "engine.min-delay=0ms",
        "engine.max-delay=0ms",
        "server.port=0"
})
@EmbeddedKafka(partitions = 1, topics = {"orders", "trade-events"})
class ExecutionEngineKafkaTest {

    private static final String EXECUTIONS_TOPIC = "trade-events";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void testOrderOnOrdersTopic_ProducesFillOnExecutionsTopic() throws Exception {
        OrderEvent order = new OrderEvent(UUID.randomUUID(), 1L, "ACME", Side.BUY, 10,
                new BigDecimal("25.50"), Instant.now());

        try (Consumer<String, String> consumer = executionsConsumer()) {
            kafkaTemplate.send("orders", String.valueOf(order.accountId()), objectMapper.writeValueAsString(order)).get();

            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, EXECUTIONS_TOPIC, Duration.ofSeconds(20));
            ExecutionEvent fill = objectMapper.readValue(record.value(), ExecutionEvent.class);

            assertEquals("1", record.key());
            assertEquals(order.orderId(), fill.orderId());
            assertEquals(10, fill.quantity());
            assertTrue(fill.price().compareTo(order.price()) <= 0);
            assertTrue(record.value().contains("\"executedOn\":\""), "timestamps should be ISO strings");
        }
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
