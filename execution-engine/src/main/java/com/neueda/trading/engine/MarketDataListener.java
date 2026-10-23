package com.neueda.trading.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the {@link QuoteMarket} supplied with live quotes from the
 * {@code market-data} topic. A new group starts at the latest offset, because
 * only a current quote is useful to an order. After a restart the group resumes
 * from its committed offset, and replayed quotes are stamped with receipt time.
 *
 * <p>This listener never throws. It uses the shared container error handler,
 * which re-delivers a failed record without limit, so one unreadable quote
 * would block the whole topic. Unreadable quotes are logged and skipped.
 */
@Component
public class MarketDataListener {

    private static final Logger log = LoggerFactory.getLogger(MarketDataListener.class);

    private final QuoteMarket market;
    private final ObjectMapper objectMapper;

    public MarketDataListener(QuoteMarket market, ObjectMapper objectMapper) {
        this.market = market;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${engine.topics.market-data}", groupId = "${spring.kafka.consumer.group-id}-market-data",
            properties = "auto.offset.reset:latest")
    public void onQuote(String message) {
        try {
            market.update(objectMapper.readValue(message, MarketDataEvent.class));
        } catch (JsonProcessingException e) {
            log.warn("Skipping unreadable market-data message: {}", message, e);
        }
    }
}
