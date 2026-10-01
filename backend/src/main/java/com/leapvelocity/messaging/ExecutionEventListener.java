package com.leapvelocity.messaging;

import com.leapvelocity.service.OrderExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class ExecutionEventListener {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEventListener.class);

    private final OrderExecutionService orderExecutionService;

    public ExecutionEventListener(OrderExecutionService orderExecutionService) {
        this.orderExecutionService = orderExecutionService;
    }

    @KafkaListener(topics = KafkaTopics.TRADE_EVENTS, groupId = "trade-api")
    public void onExecution(ExecutionEvent event, Acknowledgment acknowledgment) {
        log.info("Received execution {} for order {} at {}", event.executionId(), event.orderId(), event.price());
        orderExecutionService.settleExecution(event);
        acknowledgment.acknowledge();
    }
}
