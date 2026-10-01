package com.leapvelocity.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leapvelocity.service.OrderExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ExecutionEventListener {

	private static final Logger log = LoggerFactory.getLogger(ExecutionEventListener.class);

	private final ObjectMapper objectMapper;
	private final OrderExecutionService orderExecutionService;

	public ExecutionEventListener(ObjectMapper objectMapper, OrderExecutionService orderExecutionService) {
		this.objectMapper = objectMapper;
		this.orderExecutionService = orderExecutionService;
	}

	@KafkaListener(topics = "${kafka.topics.trade-events}", groupId = "${spring.kafka.consumer.group-id}")
	public void onExecution(String message) {
		ExecutionEvent event;

		try {
			event = objectMapper.readValue(message, ExecutionEvent.class);
		} catch (JsonProcessingException ex) {
			log.error("Skipping unreadable trade event: {}", message, ex);
			return;
		}

		orderExecutionService.settleExecution(event);
	}
}