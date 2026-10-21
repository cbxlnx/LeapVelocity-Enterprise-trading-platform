package com.leapvelocity.service;

import com.leapvelocity.dto.response.PortfolioHoldingDto;
import com.leapvelocity.dto.response.PortfolioReplayDto;
import com.leapvelocity.dto.response.PortfolioReplayDto.HoldingReplay;
import com.leapvelocity.dto.response.PortfolioRiskDto.ResultStatus;
import com.leapvelocity.repository.HistoricalPriceRepository.ReplayPrice;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Cumulative endpoint-return sensitivity, not a daily path or historical account reconstruction. */
public final class HistoricalReplayCalculator {
    public record HoldingInput(PortfolioHoldingDto holding, String instrumentName, String assetClass, List<ReplayPrice> prices) { }
    public record Inputs(PortfolioSnapshotService.ShockSnapshot snapshot, List<HoldingInput> holdings) { }
    private HistoricalReplayCalculator() { }

    public static PortfolioReplayDto calculate(Inputs inputs, HistoricalReplayCatalog.Event event, Instant at, LocalDate date) {
        var valuation = inputs.snapshot().valuation();
        var reasons = new ArrayList<String>();
        inputs.snapshot().issues().stream().filter(issue -> issue.severity() == com.leapvelocity.dto.response.PortfolioRiskDto.Severity.ERROR)
                .forEach(issue -> reasons.add(issue.code() + ": " + issue.message()));
        if (reasons.isEmpty() && valuation.valuationStatus() == com.leapvelocity.dto.response.PortfolioSummaryDto.ValuationStatus.UNAVAILABLE) {
            reasons.add("UNAVAILABLE_VALUATION: a valid current holdings valuation is required.");
        }
        if (reasons.isEmpty() && !inputs.holdings().stream().map(input -> input.holding().symbol()).sorted().toList().equals(
                valuation.holdings().stream().map(PortfolioHoldingDto::symbol).sorted().toList())) {
            reasons.add("INCOMPLETE_HOLDING_COVERAGE: every current holding must have exactly one replay input.");
        }
        var rows = new ArrayList<HoldingReplay>();
        if (reasons.isEmpty()) {
            for (var holding : inputs.holdings()) rows.add(calculateHolding(holding, event));
            rows.stream().filter(row -> row.status() != ResultStatus.READY)
                    .forEach(row -> reasons.add(row.symbol() + ": " + String.join("; ", row.reasons())));
        }
        int valid = (int) rows.stream().filter(row -> row.status() == ResultStatus.READY).count();
        var status = !reasons.isEmpty() ? ResultStatus.UNAVAILABLE : valuation.holdings().isEmpty() ? ResultStatus.EMPTY : ResultStatus.READY;
        if (status == ResultStatus.EMPTY) reasons.add("EMPTY_PORTFOLIO: no invested holdings to replay; cash is unchanged.");
        BigDecimal pnl = status == ResultStatus.READY ? rows.stream().map(HoldingReplay::pnl)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add) : null;
        return new PortfolioReplayDto("1.0", at, date, status, List.copyOf(reasons), event, HistoricalReplayCatalog.METHOD,
                HistoricalReplayCatalog.SOURCE, "adjusted_close", HistoricalReplayCatalog.REVIEW.version(),
                HistoricalReplayCatalog.REVIEW.reviewedOn(), valuation, valuation.holdings().size(), valid, pnl,
                pnl == null ? null : PriceSensitivity.impact(pnl, valuation.totalEquity()),
                pnl == null ? null : valuation.totalEquity().add(pnl), List.copyOf(rows),
                List.of("Cumulative historical security returns applied once to today's exposures; not actual account history, a single-day shock, or a daily exposure path.",
                        "Exact endpoint observations are required; intermediate sessions are not inputs to this endpoint method.",
                        "Uses provider-adjusted closes for distributions/splits; no extra corporate-action or proxy-return multiplier is applied.",
                        "Identity coverage is restricted to the reviewed security catalog; cash is unchanged; no holdings are silently omitted."));
    }

    private static HoldingReplay calculateHolding(HoldingInput input, HistoricalReplayCatalog.Event event) {
        var holding = input.holding();
        var mapping = HistoricalReplayCatalog.mapping(holding.symbol());
        var reasons = new ArrayList<String>();
        if (mapping == null) reasons.add("UNRESOLVED_INSTRUMENT_MAPPING: no reviewed issuer/share-class binding; ticker equality is insufficient.");
        else {
            if (input.instrumentName() == null || !mapping.instrumentNames().contains(input.instrumentName()) || !mapping.assetClass().equals(input.assetClass())
                    || !mapping.currency().equals(holding.currency())) {
                reasons.add("CURRENT_METADATA_MISMATCH: instrument name, asset class or currency differs from the reviewed security binding.");
            }
            if (mapping.availableFrom().isAfter(event.from())) reasons.add("HISTORICAL_INSTRUMENT_NOT_LISTED: reviewed security coverage starts " + mapping.availableFrom() + ".");
        }
        var start = input.prices().stream().filter(price -> event.from().equals(price.date())).toList();
        var end = input.prices().stream().filter(price -> event.to().equals(price.date())).toList();
        ReplayPrice first = start.size() == 1 ? start.get(0) : null;
        ReplayPrice last = end.size() == 1 ? end.get(0) : null;
        if (first == null || last == null) reasons.add("MISSING_EVENT_PRICE: exactly one adjusted observation is required on " + event.from() + " and " + event.to() + "; no nearest-date filling.");
        for (var price : input.prices()) {
            if (!holding.symbol().equals(price.symbol()) || price.adjustedClose() == null || price.adjustedClose().signum() <= 0
                    || price.adjustedClose().compareTo(new BigDecimal("0.000001")) < 0
                    || price.adjustedClose().compareTo(new BigDecimal("1e14")) >= 0) reasons.add("INVALID_EVENT_PRICE: positive security-matched adjusted closes within the imported NUMERIC(20,6) range are required.");
            if (!HistoricalReplayCatalog.SOURCE.equals(price.source()) || price.loadedAt() == null) reasons.add("UNVERIFIED_EVENT_SOURCE: yfinance provenance and ingestion timestamps are required.");
        }
        if (first != null && last != null && !java.util.Objects.equals(first.loadedAt(), last.loadedAt())) {
            reasons.add("MIXED_ADJUSTMENT_BATCH: re-import the entire event window in one batch; adjustment bases may differ.");
        }
        boolean ready = reasons.isEmpty();
        BigDecimal fraction = ready ? last.adjustedClose().divide(first.adjustedClose(), MathContext.DECIMAL128).subtract(BigDecimal.ONE) : null;
        BigDecimal pnl = ready ? PriceSensitivity.pnl(holding.marketValue(), fraction) : null;
        return new HoldingReplay(holding.symbol(), HistoricalReplayCatalog.historicalTicker(holding.symbol(), event), holding.symbol(),
                mapping == null ? null : mapping.securityIdentity(), mapping == null ? List.of() : mapping.sources(), mapping == null ? null : mapping.notes(),
                ready ? ResultStatus.READY : ResultStatus.UNAVAILABLE, List.copyOf(reasons), holding.marketValue(),
                first == null ? null : first.adjustedClose(), last == null ? null : last.adjustedClose(),
                first == null ? null : first.loadedAt(), fraction == null ? null : fraction.movePointRight(2).setScale(8, RoundingMode.HALF_UP),
                pnl, pnl == null ? null : holding.marketValue().add(pnl));
    }
}
