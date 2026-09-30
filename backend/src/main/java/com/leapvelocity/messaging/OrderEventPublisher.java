package com.leapvelocity.messaging;

import com.leapvelocity.entities.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

/**
 * Writes accepted orders to the {@code orders} topic.
 *
 * The database model uses the internal numeric account id, so the Kafka
 * contract uses {@code Long accountId} too. The Kafka record key is still a
 * string because Kafka keys are serialized independently from the JSON payload.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final String topic;

    public OrderEventPublisher(
            KafkaTemplate<String, OrderEvent> kafkaTemplate,
            @Value("${kafka.topics.orders:orders}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(Order order) {
        publish(toEvent(order));
    }

    public void publish(OrderEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Order event is required");
        }
        kafkaTemplate.send(topic, String.valueOf(event.accountId()), event).whenComplete((result, error) -> {
            if (error != null) {
                log.warn("Failed to publish order {} to {}: {}", event.orderId(), topic, error.getMessage());
            } else {
                log.info("Published order {} to {}-{}@{}", event.orderId(), topic,
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }

    private OrderEvent toEvent(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order is required");
        }
        return new OrderEvent(
                order.getId(),
                order.getAccountId(),
                order.getSymbol(),
                order.getSide().name(),
                order.getQuantity(),
                order.getPrice(),
                order.getCreatedOn().toInstant(ZoneOffset.UTC)
        );
    }
}
