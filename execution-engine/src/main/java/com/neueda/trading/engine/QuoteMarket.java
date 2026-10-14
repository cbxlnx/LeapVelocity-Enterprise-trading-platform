package com.neueda.trading.engine;

import com.neueda.trading.engine.exceptions.TemporaryFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fills orders against the latest quote for their symbol. The
 * {@link MarketDataListener} keeps the newest quote per symbol through
 * {@link #update(MarketDataEvent)}, and {@link #execute(OrderEvent)} fills an
 * order from it.
 *
 * <p>An order fills only when its symbol has a quote that was received within
 * the staleness window and that is marketable against the limit: a BUY fills
 * at the ask when the ask is at or below the limit, and a SELL fills at the bid
 * when the bid is at or above it. Otherwise {@link #execute(OrderEvent)} throws
 * a {@link TemporaryFailureException}, so the order is retried until a suitable
 * quote arrives or the retry budget runs out.
 */
public class QuoteMarket {

    private static final Logger log = LoggerFactory.getLogger(QuoteMarket.class);

    private final Duration quoteStaleness;
    private final String venue;
    private final Clock clock;
    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();

    public QuoteMarket(Duration quoteStaleness, String venue, Clock clock) {
        this.quoteStaleness = quoteStaleness;
        this.venue = venue;
        this.clock = clock;
    }

    /**
     * Stores the latest quote for its symbol, stamped with the time it was received.
     * Quotes without a valid two-sided market are ignored and the previous quote is kept.
     */
    public void update(MarketDataEvent quote) {
        if (quote.symbol() == null || !isTwoSided(quote)) {
            log.warn("Ignoring market-data quote without a valid bid and ask: {}", quote);
            return;
        }
        quotes.put(quote.symbol(), new Quote(quote, clock.instant()));
    }

    /**
     * Fills the order at the marketable side of the latest fresh quote.
     *
     * @throws TemporaryFailureException when there is no fresh quote, or the quote is not marketable yet
     */
    public ExecutionEvent execute(OrderEvent order) throws TemporaryFailureException {
        Quote quote = quotes.get(order.symbol());
        if (quote == null) {
            throw new TemporaryFailureException("No quote yet for " + order.symbol());
        }

        Instant now = clock.instant();
        if (Duration.between(quote.receivedAt(), now).compareTo(quoteStaleness) > 0) {
            throw new TemporaryFailureException("Quote for " + order.symbol() + " is older than " + quoteStaleness);
        }

        BigDecimal fillPrice = marketablePrice(order.side(), order.price(), quote.event());
        if (fillPrice == null) {
            throw new TemporaryFailureException("No marketable " + order.side() + " price for " + order.symbol()
                    + " at limit " + order.price());
        }

        return new ExecutionEvent(UUID.randomUUID(), order.orderId(), order.accountId(), order.symbol(),
                order.side(), order.quantity(), fillPrice, order.price(), venue, now);
    }

    private static boolean isTwoSided(MarketDataEvent quote) {
        return quote.bid() != null && quote.ask() != null
                && quote.bid().signum() > 0 && quote.ask().compareTo(quote.bid()) >= 0;
    }

    /** Returns the price the order can trade at now, or null when the quote does not cross its limit. */
    private static BigDecimal marketablePrice(Side side, BigDecimal limit, MarketDataEvent quote) {
        if (side == Side.BUY) {
            return quote.ask().compareTo(limit) <= 0 ? quote.ask() : null;
        }
        return quote.bid().compareTo(limit) >= 0 ? quote.bid() : null;
    }

    private record Quote(MarketDataEvent event, Instant receivedAt) {
    }
}
