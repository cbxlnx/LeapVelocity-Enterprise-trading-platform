package com.leapvelocity.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares all Kafka topics (3 main + 3 DLQ).
 * Spring's KafkaAdmin creates them on startup if they don't exist.
 */
@Configuration
public class KafkaTopics {

    @Bean
    public NewTopic ordersTopic(
            @Value("${kafka.topics.orders}") String name,
            @Value("${kafka.topics.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic tradeEventsTopic(
            @Value("${kafka.topics.trade-events}") String name,
            @Value("${kafka.topics.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic marketDataTopic(
            @Value("${kafka.topics.market-data}") String name,
            @Value("${kafka.topics.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic ordersDltTopic(
            @Value("${kafka.topics.orders-dlq}") String name,
            @Value("${kafka.topics.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic tradeEventsDltTopic(
            @Value("${kafka.topics.trade-events-dlq}") String name,
            @Value("${kafka.topics.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic marketDataDltTopic(
            @Value("${kafka.topics.market-data-dlq}") String name,
            @Value("${kafka.topics.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}