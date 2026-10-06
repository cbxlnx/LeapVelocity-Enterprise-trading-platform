package com.neueda.trading.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Clock;
import java.util.Random;

@Configuration
@EnableConfigurationProperties(RetryProperties.class)
public class EngineConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Fail on unknown properties to catch malformed JSON early
        mapper.findAndRegisterModules();
        return mapper;
    }

    @Bean
    public SimulatedMarket simulatedMarket(EngineProperties properties) {
        // java.util.Random is thread-safe (the listener runs one thread per partition) and lives in
        // java.base, unlike RandomGenerator.getDefault(), whose jdk.random module the slim JRE image lacks.
        return new SimulatedMarket(properties, new Random(), Clock.systemUTC());
    }

    @Bean
    public Pauser pauser() {
        return duration -> Thread.sleep(duration);
    }

    @Bean
    public NewTopic ordersTopic(@Value("${engine.topics.orders}") String name,
                                @Value("${engine.topics.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic executionsTopic(@Value("${engine.topics.executions}") String name,
                                    @Value("${engine.topics.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic deadLetterTopic(@Value("${engine.topics.dead-letter}") String name,
                                    @Value("${engine.topics.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
