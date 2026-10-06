package com.neueda.trading.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryPropertiesTest {

    @Test
    void testBackoffFor_CalculatesExponentialBackoff() {
        RetryProperties props = new RetryProperties(4, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(8));

        // Attempt 0: 1s * 2^0 = 1s
        assertEquals(Duration.ofSeconds(1), props.backoffFor(0));

        // Attempt 1: 1s * 2^1 = 2s
        assertEquals(Duration.ofSeconds(2), props.backoffFor(1));

        // Attempt 2: 1s * 2^2 = 4s
        assertEquals(Duration.ofSeconds(4), props.backoffFor(2));

        // Attempt 3: 1s * 2^3 = 8s (capped at max-backoff)
        assertEquals(Duration.ofSeconds(8), props.backoffFor(3));

        // Attempt 4: 1s * 2^4 = 16s (capped at max-backoff of 8s)
        assertEquals(Duration.ofSeconds(8), props.backoffFor(4));
    }

    @Test
    void testBackoffFor_CappsAtMaxBackoff() {
        RetryProperties props = new RetryProperties(10, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(4));

        // After a few attempts, should be capped at max-backoff
        for (int i = 3; i < 10; i++) {
            assertEquals(Duration.ofSeconds(4), props.backoffFor(i));
        }
    }

    @Test
    void testBackoffFor_WithDifferentMultiplier() {
        RetryProperties props = new RetryProperties(5, Duration.ofMillis(500), 3.0, Duration.ofSeconds(10));

        // 500ms * 3^0 = 500ms
        assertEquals(Duration.ofMillis(500), props.backoffFor(0));

        // 500ms * 3^1 = 1500ms
        assertEquals(Duration.ofMillis(1500), props.backoffFor(1));

        // 500ms * 3^2 = 4500ms
        assertEquals(Duration.ofMillis(4500), props.backoffFor(2));

        // 500ms * 3^3 = 13500ms (capped at 10s)
        assertEquals(Duration.ofSeconds(10), props.backoffFor(3));
    }

    @Test
    void testConstructor_RejectsNegativeMaxRetries() {
        assertThrows(IllegalArgumentException.class, () ->
                new RetryProperties(-1, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(8))
        );
    }

    @Test
    void testConstructor_RejectsNegativeInitialBackoff() {
        assertThrows(IllegalArgumentException.class, () ->
                new RetryProperties(4, Duration.ofSeconds(-1), 2.0, Duration.ofSeconds(8))
        );
    }

    @Test
    void testConstructor_RejectsZeroBackoffMultiplier() {
        assertThrows(IllegalArgumentException.class, () ->
                new RetryProperties(4, Duration.ofSeconds(1), 0.0, Duration.ofSeconds(8))
        );
    }

    @Test
    void testConstructor_RejectsNegativeMaxBackoff() {
        assertThrows(IllegalArgumentException.class, () ->
                new RetryProperties(4, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(-1))
        );
    }

    @Test
    void testConstructor_RejectsMaxBackoffLessThanInitialBackoff() {
        assertThrows(IllegalArgumentException.class, () ->
                new RetryProperties(4, Duration.ofSeconds(2), 2.0, Duration.ofSeconds(1))
        );
    }

    @Test
    void testConstructor_AcceptsValidConfiguration() {
        RetryProperties props = new RetryProperties(5, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(16));
        assertEquals(5, props.maxRetries());
        assertEquals(Duration.ofSeconds(1), props.initialBackoff());
        assertEquals(2.0, props.backoffMultiplier());
        assertEquals(Duration.ofSeconds(16), props.maxBackoff());
    }
}
