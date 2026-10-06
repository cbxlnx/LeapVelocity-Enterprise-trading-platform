package com.neueda.trading.engine;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConsumerAwareListenerErrorHandler;

/**
 * Kafka configuration for error handling in the execution engine.
 *
 * Registers the custom error handler for the OrderListener so that permanent
 * and temporary failures are handled according to the configured retry strategy.
 *
 * Note: The error handler is wired into @KafkaListener via the errorHandler
 * attribute in the listener annotation itself.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    /**
     * Provides the custom error handler as a bean that Spring Kafka can inject
     * into the @KafkaListener via its errorHandler attribute.
     *
     * The error handler implements:
     * - Immediate DLT routing for permanent failures (PermanentFailureException)
     * - Exponential backoff retry for temporary failures (TemporaryFailureException)
     * - Final DLT routing after retry budget exhaustion
     */
    @Bean(name = "orderListenerErrorHandler")
    public ConsumerAwareListenerErrorHandler orderListenerErrorHandler(ExecutionEngineErrorHandler handler) {
        return handler;
    }
}
