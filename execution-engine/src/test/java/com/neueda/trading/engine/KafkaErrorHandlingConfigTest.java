package com.neueda.trading.engine;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ConsumerAwareListenerErrorHandler;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class KafkaErrorHandlingConfigTest {

    @Test
    void orderListenerErrorHandlerBeanReturnsProvidedHandler() {
        ExecutionEngineErrorHandler handler = mock(ExecutionEngineErrorHandler.class);

        ConsumerAwareListenerErrorHandler bean = new KafkaErrorHandlingConfig().orderListenerErrorHandler(handler);

        assertSame(handler, bean);
    }
}