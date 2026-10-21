package com.leapvelocity.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.leapvelocity.dto.response.PortfolioRiskDto.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Read-only hypothetical price sensitivity. P&L is positive for gains, negative for losses. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PortfolioShockDto(String schemaVersion, Instant calculatedAt, LocalDate calculationDate,
                                ResultStatus status, List<IssueCode> reasons, BigDecimal priceChangePercent,
                                PortfolioSummaryDto valuation, BigDecimal totalPnL, BigDecimal impactPercent,
                                BigDecimal postShockEquity, List<HoldingShock> holdings, List<Issue> issues) {
    public record HoldingShock(String symbol, BigDecimal marketValue, BigDecimal priceChangePercent,
                               BigDecimal pnl, BigDecimal postShockValue) { }
}
