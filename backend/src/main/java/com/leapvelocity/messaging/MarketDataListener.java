package com.leapvelocity.messaging;

import com.leapvelocity.service.MarketDataService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the latest quote per symbol for trade-api reads.
 * Uses its own consumer group so it does not share offsets with the execution engine.
 */
@Component
public class MarketDataListener {

    private final MarketDataService marketDataService;

    public MarketDataListener(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @KafkaListener(topics = KafkaTopics.MARKET_DATA, groupId = "trade-api-market-data",
            containerFactory = "marketDataListenerContainerFactory")
    public void onQuote(MarketDataEvent quote) {
        marketDataService.update(quote);
    }
}
