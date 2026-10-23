package com.neueda.trading.engine;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerAwareListenerErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

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

    /**
     * Container-level handler for anything the listener error handler rethrows. Spring Kafka's default
     * gives up after about 9 re-deliveries and then skips the record, which would bypass the retry budget
     * and the dead-letter topic. Unlimited re-deliveries with no delay keep the record in place until the
     * custom handler decides to dead-letter it (the custom handler already sleeps between attempts).
     *
     * The market-data listener shares this factory, so it must never throw.
     */
    @Bean
    public CommonErrorHandler commonErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(0L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
