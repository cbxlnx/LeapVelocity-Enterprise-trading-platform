package com.leapvelocity.service;

import com.leapvelocity.messaging.MarketDataEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    public QuoteLookup latestQuotes(Collection<String> symbols) {
        Set<String> requested = new LinkedHashSet<>();
        for (String symbol : symbols) {
            if (symbol != null) {
                requested.add(normalize(symbol));
            }
        }
        List<MarketDataEvent> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String symbol : requested) {
            MarketDataEvent quote = latestQuotes.get(symbol);
            if (quote == null) {
                missing.add(symbol);
            } else {
                found.add(quote);
            }
        }
        return new QuoteLookup(found, missing);
    }

    public record QuoteLookup(List<MarketDataEvent> quotes, List<String> missingSymbols) {
    }

    private String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
