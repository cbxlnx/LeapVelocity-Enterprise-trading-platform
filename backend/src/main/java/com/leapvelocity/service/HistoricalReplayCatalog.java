package com.leapvelocity.service;

import com.leapvelocity.dto.response.PortfolioRiskDto.ResultStatus;
import com.leapvelocity.dto.response.PortfolioRiskDto.ScenarioAvailability;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import java.util.List;

/** Reviewed security bindings, not a same-ticker join or a general security master. */
public final class HistoricalReplayCatalog {
    public static final String METHOD = "CUMULATIVE_ADJUSTED_CLOSE_RETURN_ON_CURRENT_EXPOSURES";
    public static final String SOURCE = "yfinance";
    public record Event(String id, String title, LocalDate from, LocalDate to) { }
    public record Mapping(String symbol, List<String> instrumentNames, String assetClass, String currency,
                          LocalDate availableFrom, String securityIdentity, List<String> sources, String notes) { }
    public record Review(String version, LocalDate reviewedOn, List<Mapping> mappings) { }
    public static final List<Event> EVENTS = List.of(
            new Event("CRISIS_2008", "2008 financial crisis · calendar year", LocalDate.parse("2007-12-31"), LocalDate.parse("2008-12-31")),
            new Event("MARCH_2020", "March 2020 market stress", LocalDate.parse("2020-02-28"), LocalDate.parse("2020-03-31")));
    public static final Review REVIEW = load();
    private HistoricalReplayCatalog() { }

    private static Review load() {
        try (var stream = HistoricalReplayCatalog.class.getResourceAsStream("/risk/replay-identities-v1.json")) {
            if (stream == null) throw new IllegalStateException("Replay identity review is missing");
            var review = JsonMapper.builder().build().readValue(stream, Review.class);
            if (review.version() == null || review.reviewedOn() == null || review.mappings().isEmpty()
                    || review.mappings().stream().map(Mapping::symbol).distinct().count() != review.mappings().size()) {
                throw new IllegalStateException("Invalid replay identity review");
            }
            for (var mapping : review.mappings()) {
                if (mapping.instrumentNames().isEmpty() || mapping.sources().isEmpty() || mapping.availableFrom() == null
                        || mapping.securityIdentity().isBlank() || !"USD".equals(mapping.currency())) {
                    throw new IllegalStateException("Incomplete replay identity binding");
                }
            }
            return review;
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load replay identity review", error); }
    }
    public static Event event(String id) {
        return EVENTS.stream().filter(event -> event.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown historical scenario: " + id));
    }
    public static Mapping mapping(String symbol) {
        return REVIEW.mappings().stream().filter(mapping -> mapping.symbol().equals(symbol)).findFirst().orElse(null);
    }
    public static List<ScenarioAvailability> capabilities() {
        return EVENTS.stream().map(event -> new ScenarioAvailability(event.id(), event.title(), ResultStatus.UNAVAILABLE,
                List.of("Implemented cumulative endpoint replay: " + event.from() + " to " + event.to()
                        + ". Select Run replay to validate this account's identity mappings and stored endpoint observations."))).toList();
    }
    public static String historicalTicker(String symbol, Event event) {
        if ("GOOGL".equals(symbol) && event.to().isBefore(LocalDate.parse("2014-04-03"))) return "GOOG (Class A)";
        if ("QQQ".equals(symbol) && event.to().isBefore(LocalDate.parse("2011-03-23"))) return "QQQQ";
        return symbol;
    }
}
