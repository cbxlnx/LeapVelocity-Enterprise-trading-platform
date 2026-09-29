package com.leapvelocity.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Configuration for LeapVelocity Trading Platform.
 * Defines three main topics (orders, trade-events, market-data).
 *  * 
 * Summary:
 * - THREE topics to optimize partition keys and avoid duplication
 * - orders & trade-events: key=accountId (Settlement/Risk/Fraud need per-account ordering)
 * - market-data: key=instrumentId (Risk needs per-instrument price ordering)
 * - 3 partitions each (development scale; grows to 6+ in production)
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * ORDERS TOPIC
     * 
     * Partition key: accountId
     * Purpose: Newly placed orders from OrderService
     * 
     * Downstream consumers:
     * - settlement-consumer: needs per-account order sequence (BUY before SELL)
     * - risk-dashboard-consumer: needs per-account order sequence for position updates
     * - fraud-detection-consumer: needs per-account rapid sequences for pattern detection
     */
    @Bean
    public NewTopic ordersTopic() {
        return new NewTopic("orders", 3, (short) 1);
    }

    /**
     * TRADE-EVENTS TOPIC
     * 
     * Partition key: accountId (same reasoning as orders topic)
     * Purpose: Lifecycle events (filled, rejected, cancelled, executed)
     * 
     * Downstream consumers:
     * - settlement-consumer: per-account settlement sequence
     * - risk-dashboard-consumer: per-account position updates
     * - fraud-detection-consumer: per-account rapid trading patterns
     */
    @Bean
    public NewTopic tradeEventsTopic() {
        return new NewTopic("trade-events", 3, (short) 1);
    }

    /**
     * MARKET-DATA TOPIC
     * 
     * Partition key: instrumentId (DIFFERENT from orders/trade-events!)
     * Purpose: Price updates from external pricing feed
     * 
     * Downstream consumers:
     * - risk-dashboard-consumer: needs per-instrument price sequence
     * 
     * Why instrumentId (not accountId):
     * Risk Dashboard needs prices in correct sequence per instrument (AAPL: 100→101→102, etc).
     * If we used accountId, AAPL prices from different accounts would mix and lose ordering.
     * With instrumentId key, all AAPL prices stay together in same partition—ordering preserved.
     */
    @Bean
    public NewTopic marketDataTopic() {
        return new NewTopic("market-data", 3, (short) 1);
    }

     // ============ DEAD-LETTER TOPICS (DLQ) ============

    /**
     * ORDERS DEAD-LETTER TOPIC
     * Captures OrderPlacedEvent messages that fail processing after 3 retries.
     * Operations team inspects and replays from DLQ after fix.
     */
    @Bean
    public NewTopic ordersDltTopic() {
        return new NewTopic("orders-dlq", 3, (short) 1);
    }

    /**
     * TRADE-EVENTS DEAD-LETTER TOPIC
     * Captures OrderFilledEvent, OrderRejectedEvent messages that fail after 3 retries.
     * Operations team inspects and replays from DLQ after fix.
     */
    @Bean
    public NewTopic tradeEventsDltTopic() {
        return new NewTopic("trade-events-dlq", 3, (short) 1);
    }

    /**
     * MARKET-DATA DEAD-LETTER TOPIC
     * Captures PriceUpdateEvent messages that fail after 3 retries.
     * Operations team inspects and replays from DLQ after fix.
     */
    @Bean
    public NewTopic marketDataDltTopic() {
        return new NewTopic("market-data-dlq", 3, (short) 1);
    }

    /**
     * Kafka Admin client for topic management.
     * Automatically creates all topics defined above when Spring Boot app starts.
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }
}