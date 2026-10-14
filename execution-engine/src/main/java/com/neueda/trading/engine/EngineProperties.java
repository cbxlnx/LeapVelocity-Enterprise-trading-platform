package com.neueda.trading.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for quote-driven execution, bound from {@code engine.*}.
 *
 * @param quoteStaleness longest time a quote is trusted after the engine received it
 * @param venue          name stamped on every fill
 */
@ConfigurationProperties("engine")
public record EngineProperties(
        Duration quoteStaleness,
        String venue
) {
    public EngineProperties {
        if (quoteStaleness == null || !quoteStaleness.isPositive()) {
            throw new IllegalArgumentException("engine.quote-staleness must be positive");
        }
    }
}
