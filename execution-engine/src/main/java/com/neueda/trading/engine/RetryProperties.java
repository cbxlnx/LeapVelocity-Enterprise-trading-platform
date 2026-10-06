package com.neueda.trading.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;

/**
 * Configuration for retry and backoff strategy for handling temporary failures.
 * Bound from {@code engine.retry.*}.
 *
 * @param maxRetries          maximum number of retry attempts for temporary failures
 * @param initialBackoff      initial backoff delay between retries
 * @param backoffMultiplier   exponential multiplier for each retry (e.g., 2.0 for doubling)
 * @param maxBackoff          maximum backoff delay (cap for exponential growth)
 */
@ConfigurationProperties("engine.retry")
public record RetryProperties(
        int maxRetries,
        Duration initialBackoff,
        double backoffMultiplier,
        Duration maxBackoff
) {
    public RetryProperties {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("engine.retry.max-retries must be non-negative");
        }
        if (initialBackoff.isNegative()) {
            throw new IllegalArgumentException("engine.retry.initial-backoff must be non-negative");
        }
        if (backoffMultiplier <= 0) {
            throw new IllegalArgumentException("engine.retry.backoff-multiplier must be positive");
        }
        if (maxBackoff.isNegative()) {
            throw new IllegalArgumentException("engine.retry.max-backoff must be non-negative");
        }
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("engine.retry.max-backoff must be >= initial-backoff");
        }
    }

    /**
     * Calculate the backoff delay for the given attempt number.
     * Uses exponential backoff: delay = min(initialBackoff * (multiplier ^ attempt), maxBackoff)
     *
     * @param attemptNumber the attempt number (0-based)
     * @return the backoff delay for this attempt
     */
    public Duration backoffFor(int attemptNumber) {
        double delayMillis = initialBackoff.toMillis() * Math.pow(backoffMultiplier, attemptNumber);
        long capped = Math.min((long) delayMillis, maxBackoff.toMillis());
        return Duration.ofMillis(capped);
    }
}
