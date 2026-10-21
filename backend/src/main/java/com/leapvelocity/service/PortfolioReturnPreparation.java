package com.leapvelocity.service;

import com.leapvelocity.dto.request.RiskRequestDto;
import com.leapvelocity.dto.response.PortfolioHoldingDto;
import com.leapvelocity.dto.response.PortfolioRiskDto.History;
import com.leapvelocity.dto.response.PortfolioRiskDto.Issue;
import com.leapvelocity.dto.response.PortfolioRiskDto.IssueCode;
import com.leapvelocity.dto.response.PortfolioRiskDto.ResultStatus;
import com.leapvelocity.dto.response.PortfolioRiskDto.Severity;
import com.leapvelocity.dto.response.PortfolioSummaryDto;
import com.leapvelocity.dto.response.PortfolioSummaryDto.ValuationStatus;
import com.leapvelocity.repository.HistoricalPriceRepository.AdjustedPrice;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.leapvelocity.dto.response.PortfolioRiskDto.IssueCode.*;
import static com.leapvelocity.dto.response.PortfolioRiskDto.ResultStatus.*;

/** Pure preparation; DECIMAL128 arithmetic, no presentation rounding, persistence or risk statistics. */
public final class PortfolioReturnPreparation {
    static final MathContext PRECISION = MathContext.DECIMAL128;
    public static final int MINIMUM_CORE_RETURNS = 60;

    private PortfolioReturnPreparation() { }

    public record WealthPoint(LocalDate date, BigDecimal value) { }
    public record ReturnPoint(LocalDate from, LocalDate to, BigDecimal value) { }
    public record PairedReturn(LocalDate from, LocalDate to, BigDecimal portfolio, BigDecimal benchmark) { }
    public record Series(ResultStatus status, List<WealthPoint> wealth, List<ReturnPoint> returns, List<IssueCode> reasons) {
        public Series { wealth = List.copyOf(wealth); returns = List.copyOf(returns); reasons = List.copyOf(reasons); }
    }
    /** Eligibility for later metrics, not a computed metric result (e.g. zero benchmark variance is RA-03). */
    public record Availability(ResultStatus status, int observations, int requiredObservations, List<IssueCode> reasons) {
        public Availability { reasons = List.copyOf(reasons); }
    }
    public record PreparedInputs(PortfolioSummaryDto valuation, RiskRequestDto parameters, LocalDate calculationDate,
                                 String calendarVersion, ResultStatus valuationStatus, History history,
                                 Series portfolio, Series benchmark, List<PairedReturn> pairedReturns,
                                 Availability core, Availability beta, Availability historicalVar, List<Issue> issues) {
        public PreparedInputs {
            pairedReturns = List.copyOf(pairedReturns);
            issues = List.copyOf(issues);
        }
    }

    public static LocalDate lookbackStart(LocalDate end, String period) {
        // Validate the period even when valuation has no end date.
        int months = switch (period) {
            case "1M" -> 1; case "3M" -> 3; case "6M" -> 6; case "1Y" -> 12; case "2Y" -> 24;
            default -> throw new IllegalArgumentException("period must be 1M, 3M, 6M, 1Y or 2Y");
        };
        return end == null ? null : end.minusMonths(months);
    }

    public static int minimumVarObservations(BigDecimal confidence) {
        if (confidence == null || confidence.compareTo(new BigDecimal("0.90")) < 0
                || confidence.compareTo(new BigDecimal("0.99")) > 0) {
            throw new IllegalArgumentException("confidenceLevel must be between 0.90 and 0.99");
        }
        return Math.max(100, new BigDecimal("5").divide(BigDecimal.ONE.subtract(confidence), 0, RoundingMode.CEILING).intValueExact());
    }

    public static boolean isFresh(LocalDate date, LocalDate calculationDate, int maximumAge) {
        return date != null && date.isBefore(calculationDate) && ChronoUnit.DAYS.between(date, calculationDate) <= maximumAge;
    }

    /** The summary and rows are explicit snapshots, so this method never reads a clock or mutates an input. */
    public static PreparedInputs prepare(PortfolioSummaryDto summary, RiskRequestDto options, LocalDate calculationDate,
                                         List<AdjustedPrice> rows, List<Issue> valuationIssues) {
        Objects.requireNonNull(summary); Objects.requireNonNull(options); Objects.requireNonNull(calculationDate);
        if (options.holdingPeriodDays() < 1 || options.holdingPeriodDays() > 10
                || options.maxPriceAgeDays() < 1 || options.maxPriceAgeDays() > 30) {
            throw new IllegalArgumentException("Invalid risk holding period or maximum price age");
        }
        minimumVarObservations(options.confidenceLevel());
        LocalDate end = summary.asOf(), start = lookbackStart(end, options.period());
        List<Issue> issues = new ArrayList<>(valuationIssues);
        var held = summary.holdings().stream().filter(holding -> holding.quantity() == null || holding.quantity().signum() != 0)
                .sorted(Comparator.comparing(PortfolioHoldingDto::symbol, Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
        validateValuation(summary, held, calculationDate, options, issues);
        if (hasErrors(issues, "valuation")) {
            return blocked(summary, options, calculationDate, start, end, UNAVAILABLE, issues);
        }
        if (held.isEmpty()) {
            issues.add(issue(EMPTY_PORTFOLIO, Severity.INFO, "portfolio", null, null, "No invested holdings; cash is excluded."));
            return blocked(summary, options, calculationDate, start, end, EMPTY, issues);
        }

        List<String> symbols = held.stream().map(PortfolioHoldingDto::symbol).toList();
        Set<String> requested = new TreeSet<>(symbols);
        requested.add(options.benchmark());
        var calendar = UsEquityTradingCalendar.sessions(start, end);
        Set<LocalDate> sessions = calendar.map(TreeSet::new).orElseGet(TreeSet::new);
        List<AdjustedPrice> inRange = new ArrayList<>();
        Set<String> keys = new TreeSet<>();
        for (var row : rows) {
            if (row == null || row.symbol() == null || row.date() == null) {
                issues.add(issue(INVALID_PRICE, Severity.ERROR, "history", null, null, "Malformed adjusted-price observation."));
                continue;
            }
            if (!requested.contains(row.symbol()) || row.date().isBefore(start) || row.date().isAfter(end)) continue;
            String scope = symbols.contains(row.symbol()) ? "history" : "metrics.beta";
            if (!keys.add(row.symbol() + ":" + row.date()) || !positive(row.adjustedClose())
                    || (calendar.isPresent() && !sessions.contains(row.date()))) {
                issues.add(issue(INVALID_PRICE, Severity.ERROR, scope, row.symbol(), row.date(),
                        "Invalid, duplicate or non-session adjusted-price observation."));
            }
            inRange.add(row);
        }
        // Canonical ordering also makes diagnostics deterministic for arbitrarily ordered database rows.
        inRange.sort(Comparator.comparing(AdjustedPrice::date).thenComparing(AdjustedPrice::symbol)
                .thenComparing(row -> Objects.toString(row.adjustedClose(), "")));
        var byDate = indexPrices(inRange);
        var common = commonDates(byDate, symbols);
        if (calendar.isPresent()) common.keySet().removeIf(day -> !sessions.contains(day));

        if (calendar.isEmpty()) {
            issues.add(issue(TRADING_CALENDAR_UNAVAILABLE, Severity.ERROR, "history", null, null,
                    "Requested dates are outside the reviewed US equity calendar coverage."));
        } else {
            checkCoverage(byDate, symbols, sessions, calculationDate, options.maxPriceAgeDays(), "history", MISSING_PRICE, issues);
            checkCoverage(byDate, List.of(options.benchmark()), sessions, calculationDate,
                    options.maxPriceAgeDays(), "metrics.beta", MISSING_BENCHMARK, issues);
        }
        if (common.isEmpty() && !inRange.isEmpty()) issues.add(issue(NO_COMMON_DATES, Severity.ERROR, "history", null, null,
                "No common valid adjusted-price dates for all holdings."));

        Series portfolio;
        if (hasErrors(issues, "history")) {
            portfolio = unavailableSeries(reasons(issues, "history"));
        } else {
            List<WealthPoint> wealth = new ArrayList<>();
            if (!common.isEmpty()) {
                var initial = common.firstEntry().getValue();
                common.forEach((date, values) -> wealth.add(new WealthPoint(date,
                        buyAndHoldWealth(held, summary.holdingsValue(), initial, values))));
            }
            portfolio = series(wealth);
        }
        Series benchmark;
        if (hasErrors(issues, "history") || hasErrors(issues, "metrics.beta")) {
            benchmark = unavailableSeries(mergeReasons(reasons(issues, "history"), reasons(issues, "metrics.beta")));
        } else {
            var benchmarkDays = commonDates(byDate, List.of(options.benchmark()));
            BigDecimal initial = benchmarkDays.isEmpty() ? BigDecimal.ONE : benchmarkDays.firstEntry().getValue().get(options.benchmark());
            benchmark = series(benchmarkDays.entrySet().stream().map(day -> new WealthPoint(day.getKey(),
                    day.getValue().get(options.benchmark()).divide(initial, PRECISION))).toList());
        }
        List<PairedReturn> paired = new ArrayList<>();
        if (portfolio.status() == READY && benchmark.status() == READY) {
            Map<LocalDate, ReturnPoint> benchmarkByEnd = new TreeMap<>();
            benchmark.returns().forEach(point -> benchmarkByEnd.put(point.to(), point));
            for (var point : portfolio.returns()) {
                var other = benchmarkByEnd.get(point.to());
                if (other != null && point.from().equals(other.from())) paired.add(new PairedReturn(point.from(), point.to(), point.value(), other.value()));
            }
        }
        int windows = Math.max(0, portfolio.returns().size() - options.holdingPeriodDays() + 1);
        var core = availability(portfolio.status(), portfolio.returns().size(), MINIMUM_CORE_RETURNS,
                portfolio.reasons(), "metrics", issues);
        var beta = availability(portfolio.status() == READY ? benchmark.status() : portfolio.status(), paired.size(),
                MINIMUM_CORE_RETURNS, mergeReasons(portfolio.reasons(), benchmark.reasons()), "metrics.beta", issues);
        var historicalVar = availability(portfolio.status(), windows, minimumVarObservations(options.confidenceLevel()),
                portfolio.reasons(), "valueAtRisk.HISTORICAL", issues);
        var history = new History(start, end, common.isEmpty() ? null : common.firstKey(), common.isEmpty() ? null : common.lastKey(),
                common.size(), portfolio.returns().size(), paired.size());
        return new PreparedInputs(summary, options, calculationDate, UsEquityTradingCalendar.VERSION, READY, history,
                portfolio, benchmark, paired, core, beta, historicalVar, sortedIssues(issues));
    }

    /** Validate valuation before historical-return preparation. */
    static void validateValuation(PortfolioSummaryDto summary, List<PortfolioHoldingDto> held,
                                           LocalDate date, RiskRequestDto options, List<Issue> issues) {
        if (!"USD".equals(summary.currency()) || !"USD".equals(options.currency())) {
            issues.add(issue(UNSUPPORTED_CURRENCY, Severity.ERROR, "valuation", null, null, "Risk preparation supports USD only."));
        }
        if (summary.cashBalance() == null || summary.cashBalance().signum() < 0) {
            issues.add(issue(INVALID_POSITION, Severity.ERROR, "valuation", null, null, "Cash balance must be non-negative."));
        }
        Set<String> symbols = new TreeSet<>();
        for (var holding : held) {
            if (holding.symbol() == null || holding.symbol().isBlank()) {
                issues.add(issue(MISSING_INSTRUMENT_METADATA, Severity.ERROR, "valuation", holding.symbol(), null, "Holding symbol is required."));
            } else if (!symbols.add(holding.symbol())) {
                issues.add(issue(INVALID_POSITION, Severity.ERROR, "valuation", holding.symbol(), null, "Duplicate holding."));
            }
            if (holding.quantity() == null || holding.averageCost() == null || holding.averageCost().signum() < 0) {
                issues.add(issue(INVALID_POSITION, Severity.ERROR, "valuation", holding.symbol(), null, "Invalid or duplicate holding."));
            } else if (holding.quantity().signum() < 0) {
                issues.add(issue(UNSUPPORTED_POSITION, Severity.ERROR, "valuation", holding.symbol(), null, "Short positions are unsupported."));
            }
            if (!"USD".equals(holding.currency())) issues.add(issue(holding.currency() == null ? MISSING_INSTRUMENT_METADATA : UNSUPPORTED_CURRENCY,
                    Severity.ERROR, "valuation", holding.symbol(), null, "USD instrument metadata is required."));
        }
        if (held.isEmpty()) return;
        if (summary.valuationStatus() != ValuationStatus.READY) {
            if (!hasErrors(issues, "valuation")) issues.add(issue(MISSING_PRICE, Severity.ERROR, "valuation", null, null,
                    "Current holdings valuation is unavailable."));
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (var holding : held) {
            if (!positive(holding.marketValue()) || !positive(holding.price())) {
                issues.add(issue(INVALID_PRICE, Severity.ERROR, "valuation", holding.symbol(), summary.asOf(), "Positive close and market value are required."));
            } else if (holding.quantity() != null && holding.marketValue().compareTo(holding.quantity().multiply(holding.price())) != 0) {
                issues.add(issue(INVALID_POSITION, Severity.ERROR, "valuation", holding.symbol(), null, "Market value must equal quantity times close."));
            }
            if (holding.marketValue() != null) total = total.add(holding.marketValue());
        }
        if (!positive(summary.holdingsValue()) || total.compareTo(summary.holdingsValue()) != 0
                || summary.totalEquity() == null || summary.totalEquity().compareTo(total.add(summary.cashBalance() == null ? BigDecimal.ZERO : summary.cashBalance())) != 0) {
            issues.add(issue(INVALID_POSITION, Severity.ERROR, "valuation", null, null, "Invalid invested valuation denominator or total equity."));
        }
        if (summary.asOf() == null || !summary.asOf().isBefore(date)) {
            issues.add(issue(INVALID_PRICE, Severity.ERROR, "valuation", null, summary.asOf(), "A completed valuation date before calculationDate is required."));
        } else if (!isFresh(summary.asOf(), date, options.maxPriceAgeDays())) {
            issues.add(issue(STALE_PRICE, Severity.ERROR, "valuation", null, summary.asOf(), "Current holdings valuation exceeds maximum calendar-day age."));
        } else if (UsEquityTradingCalendar.sessions(summary.asOf(), summary.asOf()).map(List::isEmpty).orElse(false)) {
            issues.add(issue(INVALID_PRICE, Severity.ERROR, "valuation", null, summary.asOf(), "Valuation date is not a regular trading session."));
        }
    }

    private static void checkCoverage(NavigableMap<LocalDate, Map<String, BigDecimal>> prices, List<String> symbols,
                                       Set<LocalDate> sessions, LocalDate calculationDate, int maximumAge,
                                       String scope, IssueCode missingCode, List<Issue> issues) {
        for (String symbol : symbols) {
            var present = prices.entrySet().stream().filter(day -> day.getValue().containsKey(symbol)).toList();
            if (present.isEmpty()) {
                issues.add(issue(missingCode, Severity.ERROR, scope, symbol, null, "No adjusted-price history in the requested window."));
                continue;
            }
            LocalDate latest = present.get(present.size() - 1).getKey();
            if (!isFresh(latest, calculationDate, maximumAge)) issues.add(issue(STALE_PRICE, Severity.ERROR, scope, symbol, latest,
                    "Adjusted-price history exceeds maximum calendar-day age."));
            for (var day : sessions) {
                if (!prices.getOrDefault(day, Map.of()).containsKey(symbol)) issues.add(issue(INCOMPLETE_HISTORY, Severity.ERROR,
                        scope, symbol, day, "Missing adjusted price for an expected trading session."));
            }
        }
    }

    /** Shared with the existing simulation: date indexing and common-date alignment only, not risk policy. */
    static NavigableMap<LocalDate, Map<String, BigDecimal>> indexPrices(List<AdjustedPrice> rows) {
        NavigableMap<LocalDate, Map<String, BigDecimal>> result = new TreeMap<>();
        rows.forEach(row -> result.computeIfAbsent(row.date(), ignored -> new TreeMap<>()).put(row.symbol(), row.adjustedClose()));
        return result;
    }

    static NavigableMap<LocalDate, Map<String, BigDecimal>> commonDates(NavigableMap<LocalDate, Map<String, BigDecimal>> prices,
                                                                      List<String> symbols) {
        NavigableMap<LocalDate, Map<String, BigDecimal>> common = new TreeMap<>();
        prices.forEach((day, values) -> {
            if (symbols.stream().allMatch(symbol -> positive(values.get(symbol)))) common.put(day, values);
        });
        return common;
    }

    static BigDecimal buyAndHoldWealth(List<PortfolioHoldingDto> holdings, BigDecimal total,
                                       Map<String, BigDecimal> initial, Map<String, BigDecimal> current) {
        BigDecimal grown = BigDecimal.ZERO;
        for (var holding : holdings.stream().sorted(Comparator.comparing(PortfolioHoldingDto::symbol)).toList()) {
            var growth = current.get(holding.symbol()).divide(initial.get(holding.symbol()), PRECISION);
            grown = grown.add(holding.marketValue().multiply(growth, PRECISION), PRECISION);
        }
        return grown.divide(total, PRECISION);
    }

    private static List<ReturnPoint> returns(List<WealthPoint> values) {
        List<ReturnPoint> result = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            var previous = values.get(i - 1); var current = values.get(i);
            result.add(new ReturnPoint(previous.date(), current.date(), current.value().divide(previous.value(), PRECISION).subtract(BigDecimal.ONE)));
        }
        return List.copyOf(result);
    }

    private static Series series(List<WealthPoint> wealth) {
        return wealth.size() < 2 ? new Series(UNAVAILABLE, wealth, List.of(), List.of(INSUFFICIENT_HISTORY))
                : new Series(READY, wealth, returns(wealth), List.of());
    }

    private static Availability availability(ResultStatus status, int count, int required, List<IssueCode> reasons,
                                             String scope, List<Issue> issues) {
        if (status == READY && count >= required) return new Availability(READY, count, required, List.of());
        if (status == READY || reasons.equals(List.of(INSUFFICIENT_HISTORY))) {
            issues.add(issue(INSUFFICIENT_HISTORY, Severity.WARNING, scope, null, null,
                    "Requires " + required + " observations; available " + count + "."));
            return new Availability(UNAVAILABLE, count, required, List.of(INSUFFICIENT_HISTORY));
        }
        return new Availability(status, count, required, reasons);
    }

    private static PreparedInputs blocked(PortfolioSummaryDto summary, RiskRequestDto options, LocalDate date,
                                          LocalDate start, LocalDate end, ResultStatus status, List<Issue> issues) {
        var reasons = status == EMPTY ? List.of(EMPTY_PORTFOLIO) : reasons(issues, "valuation");
        var empty = new Series(status, List.of(), List.of(), reasons);
        var core = new Availability(status, 0, MINIMUM_CORE_RETURNS, reasons);
        return new PreparedInputs(summary, options, date, UsEquityTradingCalendar.VERSION, status,
                new History(start, end, null, null, 0, 0, 0), empty, empty, List.of(), core, core,
                new Availability(status, 0, minimumVarObservations(options.confidenceLevel()), reasons), sortedIssues(issues));
    }

    static Issue issue(IssueCode code, Severity severity, String scope, String symbol, LocalDate date, String message) {
        return new Issue(code, severity, scope, symbol, date, message);
    }

    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private static boolean hasErrors(List<Issue> issues, String scope) {
        return issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR && issue.scope().equals(scope));
    }
    private static List<IssueCode> reasons(List<Issue> issues, String scope) {
        return issues.stream().filter(issue -> issue.severity() == Severity.ERROR && issue.scope().equals(scope))
                .map(Issue::code).distinct().sorted().toList();
    }
    private static List<IssueCode> mergeReasons(List<IssueCode> first, List<IssueCode> second) {
        Set<IssueCode> result = new TreeSet<>(first); result.addAll(second); return List.copyOf(result);
    }
    private static Series unavailableSeries(List<IssueCode> reasons) { return new Series(UNAVAILABLE, List.of(), List.of(), reasons); }
    private static List<Issue> sortedIssues(List<Issue> issues) {
        return issues.stream().distinct().sorted(Comparator.comparing(Issue::scope).thenComparing(Issue::code)
                .thenComparing(issue -> Objects.toString(issue.symbol(), ""))
                .thenComparing(Issue::date, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(Issue::message)).toList();
    }
}
