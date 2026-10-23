package com.leapvelocity.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leapvelocity.service.OrderExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderDeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(OrderDeadLetterListener.class);

    private final OrderExecutionService orderExecutionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderDeadLetterListener(OrderExecutionService orderExecutionService) {
        this.orderExecutionService = orderExecutionService;
    }

    @KafkaListener(topics = KafkaTopics.ORDERS_DLQ, groupId = "trade-api-dlq",
            containerFactory = "orderDeadLetterListenerContainerFactory")
    public void onDeadLetter(String deadLetter) throws JsonProcessingException {
        JsonNode entry = objectMapper.readTree(deadLetter);
        JsonNode order = objectMapper.readTree(entry.path("message").asText());
        UUID orderId = UUID.fromString(order.path("orderId").asText());
        log.warn("Order {} dead-lettered: {}", orderId, entry.path("failureReason").asText());
        orderExecutionService.rejectDeadLetteredOrder(orderId);
    }
}
