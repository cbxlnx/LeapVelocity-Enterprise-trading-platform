package com.leapvelocity.service;

import com.leapvelocity.dto.response.PortfolioHoldingDto;
import com.leapvelocity.dto.response.PortfolioSummaryDto;
import com.leapvelocity.dto.response.PortfolioSummaryDto.ValuationStatus;
import com.leapvelocity.entities.Position;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.PositionRepository;
import com.leapvelocity.repository.HistoricalPriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioAnalyticsService {
    private final AccountRepository accounts;
    private final PositionRepository positions;
    private final HistoricalPriceRepository prices;

    public PortfolioAnalyticsService(AccountRepository accounts, PositionRepository positions,
                                     HistoricalPriceRepository prices) {
        this.accounts = accounts;
        this.positions = positions;
        this.prices = prices;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PortfolioSummaryDto getSummary(Long accountId) {
        if (accountId == null || accountId <= 0) throw new IllegalArgumentException("A positive account id is required");
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        List<Position> held = positions.findByAccountIdOrderBySymbolAsc(accountId).stream()
                .filter(position -> position.getQuantity().signum() != 0).toList();
        BigDecimal cash = account.getCashBalance();
        if (held.isEmpty()) {
            return new PortfolioSummaryDto(accountId, "USD", null, ValuationStatus.EMPTY,
                    BigDecimal.ZERO, cash, cash, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
        }

        List<String> issues = new ArrayList<>();
        for (Position holding : held) {
            if (holding.getQuantity().signum() < 0) issues.add("Short position is not supported: " + holding.getSymbol());
            if (holding.getAverageCost().signum() < 0) issues.add("Negative average cost for " + holding.getSymbol());
            if (holding.getInstrument() == null || !"USD".equals(holding.getInstrument().getCurrency())) {
                issues.add("USD instrument metadata is required for " + holding.getSymbol());
            }
        }
        if (!issues.isEmpty()) return unavailable(accountId, cash, held, null, issues);

        BigDecimal costBasis = held.stream().map(this::costBasis).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> symbols = held.stream().map(Position::getSymbol).distinct().toList();
        LocalDate before = LocalDate.now(ZoneId.of("America/New_York"));
        LocalDate asOf = prices.findLatestCommonDate(symbols, before);
        if (asOf == null) {
            var available = prices.findSymbolsWithHistoryBefore(symbols, before);
            var missing = symbols.stream().filter(symbol -> !available.contains(symbol)).toList();
            issues.add(missing.isEmpty()
                    ? "No common completed trading date for all holdings: " + String.join(", ", symbols)
                    : "Missing completed price history for: " + String.join(", ", missing));
            return unavailable(accountId, cash, held, costBasis, issues);
        }

        Map<String, BigDecimal> closes = prices.findClosesOnDate(symbols, asOf);
        for (String symbol : symbols) {
            if (closes.get(symbol) == null || closes.get(symbol).signum() <= 0) {
                issues.add("Missing or invalid closing price for " + symbol + " on " + asOf);
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
