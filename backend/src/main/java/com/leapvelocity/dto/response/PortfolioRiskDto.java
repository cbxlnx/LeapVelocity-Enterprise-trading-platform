package com.leapvelocity.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.leapvelocity.dto.request.RiskRequestDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Authenticated risk API response and shared core calculation records. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Portfolio risk response. Unavailable results are explicit; null results never imply zero.")
public record PortfolioRiskDto(
        String schemaVersion,
        Instant calculatedAt,
        LocalDate calculationDate,
        RiskRequestDto parameters,
        RiskStatus riskStatus,
        PortfolioSummaryDto valuation,
        History history,
        Assumptions assumptions,
        CoreMetrics metrics,
        List<VarResult> valueAtRisk,
        List<Issue> issues,
        ChartSeries series,
        MonteCarloDistribution monteCarlo,
        StressCapabilities stressCapabilities
) {
    public enum RiskStatus { READY, PARTIAL, EMPTY, UNAVAILABLE }
    public enum ResultStatus { READY, EMPTY, UNAVAILABLE, NOT_IMPLEMENTED }
    public enum Unit { PERCENT, DIMENSIONLESS }
    public enum VarMethod { HISTORICAL, PARAMETRIC, MONTE_CARLO }
    public enum Severity { INFO, WARNING, ERROR }

    public record HistogramBin(BigDecimal lowerBound, BigDecimal upperBound, int count) { }
    public record MonteCarloDistribution(ResultStatus status, List<IssueCode> reasons, int simulations,
                                         BigDecimal confidenceLevel, int holdingPeriodDays, String unit,
                                         String tailConvention, List<HistogramBin> bins, BigDecimal varAmount,
                                         BigDecimal expectedShortfallAmount, BigDecimal expectedShortfallPercent) { }
    public record ScenarioAvailability(String id, String title, ResultStatus status, List<String> reasons) { }
    public record StressCapabilities(List<ScenarioAvailability> historical, ScenarioAvailability sectorIncrements) {
        public static StressCapabilities current() {
            var reasons = List.of("Validated adjusted event-period prices and issuer/ticker continuity mappings are not supplied.",
                    "The bundled trading calendar covers 2024–2028, outside these event periods; no proxy shocks are assumed.");
            return new StressCapabilities(List.of(
                    new ScenarioAvailability("CRISIS_2008", "2008 financial crisis", ResultStatus.NOT_IMPLEMENTED, reasons),
                    new ScenarioAvailability("MARCH_2020", "March 2020", ResultStatus.NOT_IMPLEMENTED, reasons)),
                    new ScenarioAvailability("SECTOR_INCREMENTS", "Sector increments", ResultStatus.UNAVAILABLE,
                            List.of("Instrument metadata has no validated sector classification; the existing importer stores prices only.")));
        }
    }

    public enum IssueCode {
        EMPTY_PORTFOLIO, UNSUPPORTED_POSITION, UNSUPPORTED_CURRENCY, INVALID_POSITION,
        MISSING_INSTRUMENT_METADATA, MISSING_PRICE, INVALID_PRICE, NO_COMMON_DATES,
        STALE_PRICE, INCOMPLETE_HISTORY, INSUFFICIENT_HISTORY, MISSING_BENCHMARK,
        ZERO_BENCHMARK_VARIANCE, TRADING_CALENDAR_UNAVAILABLE, METHOD_NOT_IMPLEMENTED, NUMERICAL_ERROR
    }

    @Schema(description = "Inclusive requested/effective price ranges. Counts are observations, not elapsed calendar days.")
    public record History(LocalDate requestedFrom, LocalDate requestedTo, LocalDate from, LocalDate to,
                          int priceObservations, int returnObservations, int benchmarkReturnObservations) { }

    @Schema(description = "Validated current-allocation buy-and-hold observations for charts; excludes cash. "
            + "Wealth starts at 1 and returns are fractions. This is not actual historical account performance.")
    public record ChartSeries(ResultStatus status, List<WealthObservation> wealth,
                              List<ReturnObservation> returns, List<IssueCode> reasons) { }
    public record WealthObservation(LocalDate date, BigDecimal value) { }
    public record ReturnObservation(LocalDate from, LocalDate to, BigDecimal value) { }

    @Schema(description = "Versioned methodology; fixed MVP policies are echoed rather than selectable query options")
    public record Assumptions(String returnModel, String cashTreatment, String missingPricePolicy,
                              String tradingCalendar, int annualisationFactor, int minimumCoreReturns,
                              int minimumVarObservations, int minimumVarTailObservations,
                              String quantileMethod, String varHorizonMethod,
                              String normalVarDistribution, String normalVarHorizonMethod,
                              int monteCarloSimulations, long monteCarloSeed) {
        public static Assumptions mvp() {
            return new Assumptions("CURRENT_ALLOCATION_BUY_AND_HOLD", "EXCLUDED", "REJECT_INCOMPLETE_WINDOW",
                    "US_EQUITY_REGULAR_SESSIONS", 252, 60, 100, 5, "NEAREST_RANK",
                    "OVERLAPPING_COMPOUNDED_RETURNS", "NORMAL", "FIT_TO_OVERLAPPING_COMPOUNDED_RETURNS",
                    100_000, 20261020L);
        }
    }

    public record CoreMetrics(ScalarMetric annualisedVolatility, ScalarMetric maximumDrawdown, ScalarMetric beta) { }

    /** Internal core calculation bundle, reused by the full API response. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "Internal core calculation bundle with valuation, history and availability")
    public record CoreAssessment(String schemaVersion, Instant calculatedAt, LocalDate calculationDate,
                                 String calendarVersion, RiskRequestDto parameters, PortfolioSummaryDto valuation,
                                 History history, Assumptions assumptions, CoreMetrics metrics, List<Issue> issues) {
        public CoreAssessment { issues = List.copyOf(issues); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "READY requires a non-null value and no reasons; other states require null value and reasons")
    public record ScalarMetric(ResultStatus status, BigDecimal value, Unit unit,
                               int observations, int requiredObservations, LocalDate from, LocalDate to,
                               List<IssueCode> reasons) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "VaR is a non-negative loss estimate in percentage points and USD invested holdings value. "
            + "Confidence and session horizon are echoed in parameters; observations counts complete historical horizon windows, not simulations. "
            + "Parametric and Monte Carlo fit a normal distribution to compounded horizon returns; simulation settings are in assumptions. "
            + "USD and percentage round independently from the unrounded loss fraction. Null means unavailable, never zero.")
    public record VarResult(VarMethod method, ResultStatus status, boolean estimate,
                            BigDecimal lossPercent, BigDecimal lossAmount,
                            int observations, int requiredObservations, LocalDate from, LocalDate to,
                            List<IssueCode> reasons) { }

    @Schema(description = "Stable code plus human-readable context; scope identifies the affected response field")
    public record Issue(IssueCode code, Severity severity, String scope, String symbol,
                        LocalDate date, String message) { }
}
