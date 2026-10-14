package com.leapvelocity.service;

import com.leapvelocity.messaging.MarketDataEvent;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the latest quote per symbol for frontend reads.
 * Quotes are keyed by symbol, so each symbol stays on one partition and arrives in order.
 */
@Service
public class MarketDataService {

    private final Map<String, MarketDataEvent> latestQuotes = new ConcurrentHashMap<>();

    public void update(MarketDataEvent quote) {
        if (quote == null || quote.symbol() == null) {
            return;
        }
        latestQuotes.put(normalize(quote.symbol()), quote);
    }

    public Optional<MarketDataEvent> latestQuote(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(latestQuotes.get(normalize(symbol)));
    }

    private String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
