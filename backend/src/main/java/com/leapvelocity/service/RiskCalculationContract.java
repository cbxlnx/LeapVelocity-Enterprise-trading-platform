package com.leapvelocity.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.leapvelocity.dto.response.PortfolioRiskDto.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Internal v1 wire records. Decimal strings avoid binary floating point in transport. */
public final class RiskCalculationContract {
    private RiskCalculationContract() { }
    public record Parameters(String confidenceLevel, int holdingPeriodDays) { }
    public record ReturnPoint(@JsonProperty("from") LocalDate from, LocalDate to, String value) { }
    public record WealthPoint(LocalDate date, String value) { }
    public record PairedReturn(@JsonProperty("from") LocalDate from, LocalDate to, String portfolio, String benchmark) { }
    public record Series(List<ReturnPoint> returns, List<WealthPoint> wealth) { }
    public record Request(String schemaVersion, String requestId, String holdingsValue, Parameters parameters,
                          Series portfolio, List<PairedReturn> pairedReturns,
                          PortfolioReturnPreparation.Availability core, PortfolioReturnPreparation.Availability beta,
                          PortfolioReturnPreparation.Availability historicalVar) {
        public static Request from(PortfolioReturnPreparation.PreparedInputs inputs, String id) {
            return new Request("1.0", id, decimal(inputs.valuation().holdingsValue()),
                    new Parameters(decimal(inputs.parameters().confidenceLevel()), inputs.parameters().holdingPeriodDays()),
                    new Series(inputs.portfolio().returns().stream().map(p -> new ReturnPoint(p.from(), p.to(), decimal(p.value()))).toList(),
                            inputs.portfolio().wealth().stream().map(p -> new WealthPoint(p.date(), decimal(p.value()))).toList()),
                    inputs.pairedReturns().stream().map(p -> new PairedReturn(p.from(), p.to(), decimal(p.portfolio()), decimal(p.benchmark()))).toList(),
                    inputs.core(), inputs.beta(), inputs.historicalVar());
        }
    }
    public record Response(String schemaVersion, String requestId, CoreMetrics metrics,
                           List<VarResult> valueAtRisk, List<Issue> issues, MonteCarloDistribution monteCarlo) { }
    private static String decimal(BigDecimal value) { return value == null ? null : value.toString(); }
}
