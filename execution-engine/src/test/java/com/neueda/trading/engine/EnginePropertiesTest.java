package com.neueda.trading.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EnginePropertiesTest {

    @Test
    void testNonPositiveQuoteStaleness_Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EngineProperties(Duration.ZERO, "SIM"));
    }

    @Test
    void testNullQuoteStaleness_Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new EngineProperties(null, "SIM"));
    }
}
