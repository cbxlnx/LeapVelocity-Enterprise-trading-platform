package com.neueda.trading.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineConfigTest {

    private final EngineConfig config = new EngineConfig();

    @Test
    void objectMapperSerializesAndDeserializesJavaTimeEvents() throws Exception {
        ObjectMapper mapper = config.objectMapper();
        ExecutionEvent event = new ExecutionEvent(UUID.randomUUID(), UUID.randomUUID(), 4L, "VERDA", Side.BUY,
                5, new BigDecimal("4.20"), new BigDecimal("4.25"), "SIM", Instant.EPOCH);

        String json = mapper.writeValueAsString(event);
        ExecutionEvent restored = mapper.readValue(json, ExecutionEvent.class);

        assertTrue(json.contains("\"executedOn\":\"1970-01-01T00:00:00Z\""));
        assertEquals(event, restored);
    }

    @Test
    void simulatedMarketBeanUsesConfiguredVenue() {
        EngineProperties properties = new EngineProperties(Duration.ZERO, Duration.ZERO, 0, "SIMX");

        SimulatedMarket market = config.simulatedMarket(properties);
        ExecutionEvent execution = market.execute(new OrderEvent(UUID.randomUUID(), 9L, "VERDA", Side.SELL,
                3, new BigDecimal("12.50"), Instant.EPOCH));

        assertEquals("SIMX", execution.venue());
        assertNotNull(execution.executedOn());
        assertEquals(new BigDecimal("12.50"), execution.price());
    }

    @Test
    void pauserBeanAcceptsZeroDelay() throws Exception {
        Pauser pauser = config.pauser();

        assertNotNull(pauser);
        assertDoesNotThrow(() -> pauser.pause(Duration.ZERO));
    }

    @Test
    void topicBeansUseProvidedNamesAndPartitions() {
        assertTopic(config.ordersTopic("orders", 4), "orders", 4);
        assertTopic(config.executionsTopic("executions", 4), "executions", 4);
        assertTopic(config.deadLetterTopic("orders-dlt", 4), "orders-dlt", 4);
    }

    private void assertTopic(NewTopic topic, String name, int partitions) {
        assertEquals(name, topic.name());
        assertEquals(partitions, topic.numPartitions());
        assertEquals((short) 1, topic.replicationFactor());
    }
}