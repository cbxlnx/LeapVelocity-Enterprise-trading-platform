package com.leapvelocity.service;

import com.leapvelocity.dto.response.PortfolioPerformanceDto;
import com.leapvelocity.dto.response.PortfolioPerformanceDto.InitialWeight;
import com.leapvelocity.dto.response.PortfolioPerformanceDto.Point;
import com.leapvelocity.dto.response.PortfolioPerformanceDto.SimulationStatus;
import com.leapvelocity.dto.response.PortfolioSummaryDto.ValuationStatus;
import com.leapvelocity.repository.HistoricalPriceRepository;
import com.leapvelocity.repository.InstrumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class PortfolioPerformanceService {
    private static final String BENCHMARK = "SPY";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final Set<String> PERIODS = Set.of("1M", "3M", "6M", "1Y", "2Y");
    private final PortfolioAnalyticsService analytics;
    private final HistoricalPriceRepository prices;
    private final InstrumentRepository instruments;

    public PortfolioPerformanceService(PortfolioAnalyticsService analytics, HistoricalPriceRepository prices,
                                       InstrumentRepository instruments) {
        this.analytics = analytics;
        this.prices = prices;
        this.instruments = instruments;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PortfolioPerformanceDto getPerformance(Long accountId, String period) {
        String selected = period == null ? "1Y" : period.trim().toUpperCase(Locale.ROOT);
        if (!PERIODS.contains(selected)) throw new IllegalArgumentException("period must be 1M, 3M, 6M, 1Y or 2Y");
        var summary = analytics.getSummary(accountId);
        LocalDate end = summary.asOf();
        LocalDate start = end == null ? null : switch (selected) {
            case "1M" -> end.minusMonths(1);
            case "3M" -> end.minusMonths(3);
            case "6M" -> end.minusMonths(6);
            case "1Y" -> end.minusYears(1);
            case "2Y" -> end.minusYears(2);
            default -> throw new IllegalStateException("Unsupported validated period: " + selected);
        };
        if (summary.valuationStatus() != ValuationStatus.READY) {
            return unavailable(accountId, selected,
                    summary.valuationStatus() == ValuationStatus.EMPTY ? SimulationStatus.EMPTY : SimulationStatus.UNAVAILABLE,
                    end, start, List.of(), summary.issues());
        }
        var weights = summary.holdings().stream().map(holding -> new InitialWeight(holding.symbol(),
                holding.marketValue().divide(summary.holdingsValue(), 12, RoundingMode.HALF_UP))).toList();
        if (instruments.findBySymbol(BENCHMARK).filter(instrument -> "USD".equals(instrument.getCurrency())).isEmpty()) {
            return unavailable(accountId, selected, SimulationStatus.UNAVAILABLE, end, start, weights,
                    List.of("USD SPY benchmark instrument is unavailable"));
        }
        List<String> symbols = new ArrayList<>(summary.holdings().stream().map(holding -> holding.symbol()).toList());
        if (!symbols.contains(BENCHMARK)) symbols.add(BENCHMARK);

        Map<LocalDate, Map<String, BigDecimal>> byDate = new TreeMap<>();
        for (var row : prices.findAdjustedPrices(symbols, start, end)) {
            if (row.adjustedClose() == null || row.adjustedClose().signum() <= 0) {
                return unavailable(accountId, selected, SimulationStatus.UNAVAILABLE, end, start, weights,
                        List.of("Invalid adjusted price for " + row.symbol() + " on " + row.date()));
            }
            byDate.computeIfAbsent(row.date(), ignored -> new HashMap<>()).put(row.symbol(), row.adjustedClose());
        }
        Set<String> available = new java.util.HashSet<>();
        byDate.values().forEach(values -> available.addAll(values.keySet()));
        var missing = symbols.stream().filter(symbol -> !available.contains(symbol)).toList();
        if (!missing.isEmpty()) {
            return unavailable(accountId, selected, SimulationStatus.UNAVAILABLE, end, start, weights,
                    List.of("Missing adjusted-price history for: " + String.join(", ", missing)));
        }
        var common = byDate.entrySet().stream().filter(entry -> entry.getValue().keySet().containsAll(symbols)).toList();
        if (common.size() < 2) {
            return unavailable(accountId, selected, SimulationStatus.UNAVAILABLE, end, start, weights,
                    List.of("At least two common valid trading dates are required for holdings and SPY"));
        }

        Map<String, BigDecimal> initialPrices = common.get(0).getValue();
        List<Point> points = new ArrayList<>();
        for (var day : common) {
            BigDecimal grownValue = BigDecimal.ZERO;
            for (var holding : summary.holdings()) {
                BigDecimal growth = day.getValue().get(holding.symbol()).divide(initialPrices.get(holding.symbol()), PRECISION);
                grownValue = grownValue.add(holding.marketValue().multiply(growth, PRECISION), PRECISION);
            }
            // Use unrounded market values rather than the summary's serialized
            // weight fractions, so the common starting observation is exactly 100.
            BigDecimal portfolioIndex = grownValue.divide(summary.holdingsValue(), PRECISION).multiply(HUNDRED, PRECISION);
            BigDecimal benchmarkIndex = day.getValue().get(BENCHMARK).divide(initialPrices.get(BENCHMARK), PRECISION)
                    .multiply(HUNDRED, PRECISION);
            points.add(new Point(day.getKey(), rounded(portfolioIndex), rounded(benchmarkIndex)));
        }
        Point last = points.get(points.size() - 1);
        return new PortfolioPerformanceDto(accountId, selected, BENCHMARK, SimulationStatus.READY,
                end, start, end, points.get(0).date(), last.date(),
                last.portfolioIndex().subtract(HUNDRED), last.benchmarkIndex().subtract(HUNDRED),
                weights, List.copyOf(points), List.of());
    }

    private BigDecimal rounded(BigDecimal value) { return value.setScale(8, RoundingMode.HALF_UP); }

    private PortfolioPerformanceDto unavailable(Long accountId, String period, SimulationStatus status,
                                                LocalDate end, LocalDate start, List<InitialWeight> weights,
                                                List<String> issues) {
        return new PortfolioPerformanceDto(accountId, period, BENCHMARK, status, end, start, end,
                null, null, null, null, weights, List.of(), List.copyOf(issues));
    }
}
