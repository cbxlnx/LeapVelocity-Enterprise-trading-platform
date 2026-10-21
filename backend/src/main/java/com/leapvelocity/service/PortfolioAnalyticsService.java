package com.leapvelocity.service;

import com.leapvelocity.dto.request.RiskRequestDto;
import com.leapvelocity.dto.request.PriceShockRequestDto;
import com.leapvelocity.dto.response.PortfolioShockDto;
import com.leapvelocity.dto.response.PortfolioReplayDto;
import com.leapvelocity.dto.response.PortfolioRiskDto;
import com.leapvelocity.dto.response.PortfolioRiskDto.*;
import com.leapvelocity.dto.response.PortfolioSummaryDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

/** Orchestrates an immutable database snapshot followed by a transaction-free calculation request. */
@Service
public class PortfolioAnalyticsService {
    private final PortfolioSnapshotService snapshots;
    private final RiskCalculationClient calculator;
    private final Clock clock;

    @Autowired
    public PortfolioAnalyticsService(PortfolioSnapshotService snapshots, RiskCalculationClient calculator) {
        this(snapshots, calculator, Clock.systemUTC());
    }

    PortfolioAnalyticsService(PortfolioSnapshotService snapshots, RiskCalculationClient calculator, Clock clock) {
        this.snapshots = snapshots;
        this.calculator = calculator;
        this.clock = clock;
    }

    public PortfolioSummaryDto getSummary(Long accountId) {
        return snapshots.getSummary(accountId, LocalDate.now(clock.withZone(ZoneId.of("America/New_York"))));
    }

    public PortfolioReturnPreparation.PreparedInputs prepareRiskInputs(Long accountId, RiskRequestDto options) {
        return snapshots.prepareRiskInputs(accountId, options, LocalDate.now(clock.withZone(ZoneId.of("America/New_York"))));
    }

    public PortfolioShockDto calculateShock(Long accountId, PriceShockRequestDto request) {
        var change = request == null ? null : request.priceChangePercent();
        if (change == null || change.compareTo(new BigDecimal("-50")) < 0 || change.compareTo(new BigDecimal("50")) > 0) {
            throw new IllegalArgumentException("priceChangePercent must be between -50 and 50 inclusive");
        }
        var at = clock.instant();
        var date = at.atZone(ZoneId.of("America/New_York")).toLocalDate();
        var snapshot = snapshots.prepareShockInputs(accountId, date);
        var valuation = snapshot.valuation();
        var reasons = snapshot.issues().stream().filter(issue -> issue.severity() == Severity.ERROR)
                .map(Issue::code).distinct().toList();
        if (!reasons.isEmpty() || valuation.holdings().isEmpty()) {
            var status = reasons.isEmpty() ? ResultStatus.EMPTY : ResultStatus.UNAVAILABLE;
            return new PortfolioShockDto("1.0", at, date, status,
                    reasons.isEmpty() ? List.of(IssueCode.EMPTY_PORTFOLIO) : reasons, change, valuation,
                    null, null, null, List.of(), snapshot.issues());
        }
        var holdings = valuation.holdings().stream().map(holding -> {
            var pnl = PriceSensitivity.pnl(holding.marketValue(), change.movePointLeft(2));
            return new PortfolioShockDto.HoldingShock(holding.symbol(), holding.marketValue(), change, pnl,
                    holding.marketValue().add(pnl));
        }).toList();
        var pnl = holdings.stream().map(PortfolioShockDto.HoldingShock::pnl)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        var impact = PriceSensitivity.impact(pnl, valuation.totalEquity());
        return new PortfolioShockDto("1.0", at, date, ResultStatus.READY, List.of(), change, valuation, pnl, impact,
                valuation.totalEquity().add(pnl), holdings, snapshot.issues());
    }

    public PortfolioReplayDto calculateReplay(Long accountId, String scenarioId) {
        var event = HistoricalReplayCatalog.event(scenarioId);
        var at = clock.instant();
        var date = at.atZone(ZoneId.of("America/New_York")).toLocalDate();
        return HistoricalReplayCalculator.calculate(snapshots.prepareReplayInputs(accountId, event, date), event, at, date);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CoreAssessment calculateCoreRisk(Long accountId, RiskRequestDto options) {
        var risk = getRisk(accountId, options);
        return new CoreAssessment(risk.schemaVersion(), risk.calculatedAt(), risk.calculationDate(),
                UsEquityTradingCalendar.VERSION, risk.parameters(), risk.valuation(), risk.history(),
                risk.assumptions(), risk.metrics(), risk.issues());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PortfolioRiskDto getRisk(Long accountId, RiskRequestDto options) {
        var at = clock.instant();
        var date = at.atZone(ZoneId.of("America/New_York")).toLocalDate();
        // A separate Spring proxy completes REQUIRES_NEW before HTTP. NOT_SUPPORTED
        // also suspends an ambient caller transaction for the whole orchestration.
        var prepared = snapshots.prepareRiskInputs(accountId, options, date);
        var result = calculator.calculate(prepared);
        var metrics = result.metrics();
        var states = new ArrayList<>(List.of(metrics.annualisedVolatility().status(),
                metrics.maximumDrawdown().status(), metrics.beta().status()));
        states.addAll(result.valueAtRisk().stream().map(VarResult::status).toList());
        var status = metrics.annualisedVolatility().status() == ResultStatus.EMPTY ? RiskStatus.EMPTY
                : states.stream().allMatch(value -> value == ResultStatus.READY) ? RiskStatus.READY
                : states.contains(ResultStatus.READY) ? RiskStatus.PARTIAL : RiskStatus.UNAVAILABLE;
        var issues = new ArrayList<>(prepared.issues());
        issues.addAll(result.issues());
        return new PortfolioRiskDto("1.0", at, date, prepared.parameters(), status, prepared.valuation(),
                prepared.history(), Assumptions.mvp(), metrics, result.valueAtRisk(), issues.stream().distinct().toList(),
                new ChartSeries(prepared.portfolio().status(),
                        prepared.portfolio().wealth().stream().map(p -> new WealthObservation(p.date(), p.value())).toList(),
                        prepared.portfolio().returns().stream().map(p -> new ReturnObservation(p.from(), p.to(), p.value())).toList(),
                        prepared.portfolio().reasons()), result.monteCarlo(), StressCapabilities.current());
    }
}
