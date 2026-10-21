package com.leapvelocity.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.leapvelocity.dto.response.PortfolioRiskDto.ResultStatus;
import com.leapvelocity.service.HistoricalReplayCatalog.Event;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Event returns applied to current exposures; never actual historical account performance. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PortfolioReplayDto(String schemaVersion, Instant calculatedAt, LocalDate calculationDate,
                                 ResultStatus status, List<String> reasons, Event scenario, String methodology,
                                 String priceSource, String priceField, String identityReviewVersion, LocalDate identityReviewedOn,
                                 PortfolioSummaryDto valuation, int expectedHoldings, int validatedHoldings,
                                 BigDecimal totalPnL, BigDecimal impactPercent, BigDecimal postShockEquity,
                                 List<HoldingReplay> holdings, List<String> limitations) {
    public record HoldingReplay(String symbol, String historicalTicker, String providerSymbol, String securityIdentity,
                                List<String> identitySources, String identityNotes, ResultStatus status, List<String> reasons,
                                BigDecimal marketValue, BigDecimal fromAdjustedClose, BigDecimal toAdjustedClose,
                                Instant loadedAt, BigDecimal priceChangePercent, BigDecimal pnl, BigDecimal postShockValue) { }
}
