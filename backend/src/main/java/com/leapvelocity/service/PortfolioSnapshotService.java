package com.leapvelocity.service;

import com.leapvelocity.dto.response.PortfolioHoldingDto;
import com.leapvelocity.dto.response.PortfolioSummaryDto;
import com.leapvelocity.dto.response.PortfolioSummaryDto.ValuationStatus;
import com.leapvelocity.dto.request.RiskRequestDto;
import com.leapvelocity.dto.response.PortfolioRiskDto.Issue;
import com.leapvelocity.dto.response.PortfolioRiskDto.IssueCode;
import com.leapvelocity.dto.response.PortfolioRiskDto.Severity;
import com.leapvelocity.entities.Position;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.PositionRepository;
import com.leapvelocity.repository.HistoricalPriceRepository;
import com.leapvelocity.repository.InstrumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioSnapshotService {
    private final AccountRepository accounts;
    private final PositionRepository positions;
    private final HistoricalPriceRepository prices;
    private final InstrumentRepository instruments;
    public PortfolioSnapshotService(AccountRepository accounts, PositionRepository positions,
                                    HistoricalPriceRepository prices, InstrumentRepository instruments) {
        this.accounts = accounts;
        this.positions = positions;
        this.prices = prices;
        this.instruments = instruments;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PortfolioSummaryDto getSummary(Long accountId, LocalDate date) {
        return summarize(accountId, date, null);
    }

    public record ShockSnapshot(PortfolioSummaryDto valuation, List<Issue> issues) {
        public ShockSnapshot { issues = List.copyOf(issues); }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ShockSnapshot prepareShockInputs(Long accountId, LocalDate date) {
        List<Issue> issues = new ArrayList<>();
        var summary = summarize(accountId, date, issues);
        PortfolioReturnPreparation.validateValuation(summary, summary.holdings(), date, RiskRequestDto.defaults(), issues);
        // The price sensitivity model supports the project's stored cash equities/funds.
        for (var holding : positions.findByAccountIdOrderBySymbolAsc(accountId)) {
            if (holding.getQuantity() != null && holding.getQuantity().signum() == 0) continue;
            var instrument = holding.getInstrument();
            if (instrument != null && (instrument.getAssetClass() == null || !List.of("EQUITY", "FUND").contains(instrument.getAssetClass()))) {
                issues.add(PortfolioReturnPreparation.issue(IssueCode.UNSUPPORTED_POSITION, Severity.ERROR, "valuation",
                        holding.getSymbol(), null, "Uniform price shocks support long-only USD EQUITY and FUND holdings."));
            }
        }
        return new ShockSnapshot(summary, issues.stream().distinct().toList());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public PortfolioReturnPreparation.PreparedInputs prepareRiskInputs(Long accountId, RiskRequestDto options,
                                                                        LocalDate calculationDate) {
        if (options == null) options = RiskRequestDto.defaults();
        PortfolioReturnPreparation.lookbackStart(null, options.period());
        PortfolioReturnPreparation.minimumVarObservations(options.confidenceLevel());
        List<Issue> issues = new ArrayList<>();
        var summary = summarize(accountId, calculationDate, issues);
        String benchmarkSymbol = options.benchmark();
        var benchmark = instruments.findBySymbol(benchmarkSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Unknown benchmark instrument: " + benchmarkSymbol));
        if (!"USD".equals(benchmark.getCurrency()) || "CASH".equals(benchmark.getAssetClass())) {
            throw new IllegalArgumentException("Benchmark must be a USD non-CASH instrument");
        }
        var end = summary.asOf();
        var start = PortfolioReturnPreparation.lookbackStart(end, options.period());
        List<HistoricalPriceRepository.AdjustedPrice> history = List.of();
        if (summary.valuationStatus() == ValuationStatus.READY
                && PortfolioReturnPreparation.isFresh(end, calculationDate, options.maxPriceAgeDays())) {
            List<String> symbols = new ArrayList<>(summary.holdings().stream().map(PortfolioHoldingDto::symbol).toList());
            if (!symbols.contains(options.benchmark())) symbols.add(options.benchmark());
            history = prices.findAdjustedPrices(symbols, start, end);
        }
        return PortfolioReturnPreparation.prepare(summary, options, calculationDate, history, issues);
    }

    private PortfolioSummaryDto summarize(Long accountId, LocalDate before, List<Issue> riskIssues) {
        if (accountId == null || accountId <= 0) throw new IllegalArgumentException("A positive account id is required");
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        List<Position> held = positions.findByAccountIdOrderBySymbolAsc(accountId).stream()
                .filter(position -> position.getQuantity() == null || position.getQuantity().signum() != 0).toList();
        BigDecimal cash = account.getCashBalance();
        List<String> issues = new ArrayList<>();
        if (riskIssues != null && (cash == null || cash.signum() < 0)) {
            valuationIssue(issues, riskIssues, IssueCode.INVALID_POSITION, null, null, "Cash balance must be non-negative");
            return unavailable(accountId, cash, held, null, issues);
        }
        if (held.isEmpty()) {
            return new PortfolioSummaryDto(accountId, "USD", null, ValuationStatus.EMPTY,
                    BigDecimal.ZERO, cash, cash, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
        }

        for (Position holding : held) {
            if (holding.getQuantity() == null) valuationIssue(issues, riskIssues, IssueCode.INVALID_POSITION,
                    holding.getSymbol(), null, "Missing quantity for " + holding.getSymbol());
            else if (holding.getQuantity().signum() < 0) valuationIssue(issues, riskIssues, IssueCode.UNSUPPORTED_POSITION,
                    holding.getSymbol(), null, "Short position is not supported: " + holding.getSymbol());
            if (holding.getAverageCost() == null || holding.getAverageCost().signum() < 0) valuationIssue(issues, riskIssues,
                    IssueCode.INVALID_POSITION, holding.getSymbol(), null,
                    (holding.getAverageCost() == null ? "Missing" : "Negative") + " average cost for " + holding.getSymbol());
            if (holding.getInstrument() == null || !"USD".equals(holding.getInstrument().getCurrency())) {
                valuationIssue(issues, riskIssues, holding.getInstrument() == null ? IssueCode.MISSING_INSTRUMENT_METADATA : IssueCode.UNSUPPORTED_CURRENCY,
                        holding.getSymbol(), null, "USD instrument metadata is required for " + holding.getSymbol());
            } else if (riskIssues != null && "CASH".equals(holding.getInstrument().getAssetClass())) {
                valuationIssue(issues, riskIssues, IssueCode.UNSUPPORTED_POSITION, holding.getSymbol(), null,
                        "Cash must be represented by the account balance, not a CASH position");
            }
        }
        if (!issues.isEmpty()) return unavailable(accountId, cash, held, null, issues);

        BigDecimal costBasis = held.stream().map(this::costBasis).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> symbols = held.stream().map(Position::getSymbol).distinct().toList();
        LocalDate asOf = prices.findLatestCommonDate(symbols, before);
        if (asOf == null) {
            var available = prices.findSymbolsWithHistoryBefore(symbols, before);
            var missing = symbols.stream().filter(symbol -> !available.contains(symbol)).toList();
            if (missing.isEmpty()) {
                valuationIssue(issues, riskIssues, IssueCode.NO_COMMON_DATES, null, null,
                        "No common completed trading date for all holdings: " + String.join(", ", symbols));
            } else {
                issues.add("Missing completed price history for: " + String.join(", ", missing));
                if (riskIssues != null) {
                    for (String symbol : missing) riskIssues.add(PortfolioReturnPreparation.issue(IssueCode.MISSING_PRICE,
                            Severity.ERROR, "valuation", symbol, null, "Missing completed price history for: " + symbol));
                }
            }
            return unavailable(accountId, cash, held, costBasis, issues);
        }

        Map<String, BigDecimal> closes = prices.findClosesOnDate(symbols, asOf);
        for (String symbol : symbols) {
            if (closes.get(symbol) == null || closes.get(symbol).signum() <= 0) {
                valuationIssue(issues, riskIssues, IssueCode.INVALID_PRICE, symbol, asOf,
                        "Missing or invalid closing price for " + symbol + " on " + asOf);
            }
        }
        if (!issues.isEmpty()) return unavailable(accountId, cash, held, costBasis, issues);

        BigDecimal holdingsValue = held.stream().map(holding -> holding.getQuantity().multiply(closes.get(holding.getSymbol())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PortfolioHoldingDto> valued = held.stream().map(holding -> {
            BigDecimal price = closes.get(holding.getSymbol());
            BigDecimal value = holding.getQuantity().multiply(price);
            BigDecimal basis = costBasis(holding);
            return new PortfolioHoldingDto(holding.getSymbol(), "USD", holding.getQuantity(), holding.getAverageCost(),
                    price, value, basis, value.subtract(basis), value.divide(holdingsValue, 12, RoundingMode.HALF_UP));
        }).toList();
        return new PortfolioSummaryDto(accountId, "USD", asOf, ValuationStatus.READY, holdingsValue,
                cash, cash.add(holdingsValue), costBasis, holdingsValue.subtract(costBasis), valued, List.of());
    }

    private void valuationIssue(List<String> messages, List<Issue> riskIssues, IssueCode code,
                                 String symbol, LocalDate date, String message) {
        messages.add(message);
        if (riskIssues != null) riskIssues.add(PortfolioReturnPreparation.issue(code, Severity.ERROR, "valuation", symbol, date, message));
    }

    private BigDecimal costBasis(Position holding) {
        return holding.getQuantity().multiply(holding.getAverageCost());
    }

    private PortfolioSummaryDto unavailable(Long accountId, BigDecimal cash, List<Position> held,
                                             BigDecimal costBasis, List<String> issues) {
        List<PortfolioHoldingDto> unpriced = held.stream().map(holding -> new PortfolioHoldingDto(
                holding.getSymbol(), holding.getInstrument() == null ? null : holding.getInstrument().getCurrency(),
                holding.getQuantity(), holding.getAverageCost(), null, null,
                costBasis == null ? null : costBasis(holding), null, null)).toList();
        return new PortfolioSummaryDto(accountId, "USD", null, ValuationStatus.UNAVAILABLE,
                null, cash, null, costBasis, null, unpriced, List.copyOf(issues));
    }
}
