package com.leapvelocity.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the Kafka topics used by the trading platform.
 *
 * Spring Kafka creates these topics through KafkaAdmin when the application
 * starts. Broker auto-creation stays disabled in Docker Compose so topic
 * names, partitions, and replicas remain explicit in code.
 */
@Configuration
public class KafkaTopics {

    public static final String ORDERS = "orders";
    public static final String TRADE_EVENTS = "trade-events";
    public static final String MARKET_DATA = "market-data";
    public static final String ORDERS_DLQ = "orders-dlq";
    public static final String TRADE_EVENTS_DLQ = "trade-events-dlq";
    public static final String MARKET_DATA_DLQ = "market-data-dlq";

    private static final int PARTITIONS = 3;
    private static final int REPLICAS = 1;

    @Bean
    public NewTopic ordersTopic() {
        return topic(ORDERS);
    }

    @Bean
    public NewTopic tradeEventsTopic() {
        return topic(TRADE_EVENTS);
    }

    @Bean
    public NewTopic marketDataTopic() {
        return topic(MARKET_DATA);
    }

    @Bean
    public NewTopic ordersDlqTopic() {
        return topic(ORDERS_DLQ);
    }

    @Bean
    public NewTopic tradeEventsDlqTopic() {
        return topic(TRADE_EVENTS_DLQ);
    }

    @Bean
    public NewTopic marketDataDlqTopic() {
        return topic(MARKET_DATA_DLQ);
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }
}
