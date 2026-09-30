package com.leapvelocity.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Generic message envelope for all Kafka events.
 * Wraps every event payload with five required metadata fields for traceability, 
 * idempotence, and schema evolution.
 * 
 * Five Fields:
 * 1. eventId: Unique identifier for deduplication (idempotence)
 * 2. timestamp: Event creation time in milliseconds
 * 3. source: Service name that produced this event
 * 4. correlationId: Trace ID linking events across services
 * 5. version: Schema version for forward/backward compatibility
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEnvelope<T> {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("source")
    private String source;

    @JsonProperty("correlationId")
    private String correlationId;

    @JsonProperty("version")
    private int version;

    @JsonProperty("payload")
    private T payload;

    /**
     * Factory method to create envelope with auto-generated eventId and timestamp.
     * 
     * Example:
     *   MessageEnvelope.of("OrderService", "req-12345", orderEvent)
     */
    public static <T> MessageEnvelope<T> of(String source, String correlationId, T payload) {
        return MessageEnvelope.<T>builder()
                .eventId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .source(source)
                .correlationId(correlationId)
                .version(1)
                .payload(payload)
                .build();
    }
}