package com.leapvelocity.service;

import com.leapvelocity.dto.request.RiskRequestDto;
import com.leapvelocity.dto.response.PortfolioRiskDto.*;
import com.leapvelocity.dto.response.PortfolioSummaryDto;
import com.leapvelocity.service.PortfolioReturnPreparation.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.leapvelocity.dto.response.PortfolioRiskDto.ResultStatus.*;

final class RiskParityFixtures {
    static PreparedInputs example(String confidence, int horizon) {
        var values = new ArrayList<BigDecimal>();
        for (int index = 0; index < 260; index++) {
            values.add(new BigDecimal("-.0115")); values.add(new BigDecimal(".0125"));
        }
        values.add(new BigDecimal(".0005"));
        return fixture(values, confidence, horizon);
    }

    static PreparedInputs fixture(List<BigDecimal> values, String confidence, int horizon) {
        var start = LocalDate.parse("2024-01-02");
        var returns = new ArrayList<ReturnPoint>();
        var wealth = new ArrayList<WealthPoint>();
        var paired = new ArrayList<PairedReturn>();
        BigDecimal level = BigDecimal.ONE;
        if (!values.isEmpty()) wealth.add(new WealthPoint(start, level));
        for (int index = 0; index < values.size(); index++) {
            var from = start.plusDays(index);
            var value = values.get(index);
            returns.add(new ReturnPoint(from, from.plusDays(1), value));
            level = level.multiply(BigDecimal.ONE.add(value), MathContext.DECIMAL128);
            wealth.add(new WealthPoint(from.plusDays(1), level));
            paired.add(new PairedReturn(from, from.plusDays(1), value, value.multiply(new BigDecimal(".5")).add(new BigDecimal(".0001"))));
        }
        var end = values.isEmpty() ? null : start.plusDays(values.size());
        var valuation = new PortfolioSummaryDto(1L, "USD", end,
                values.isEmpty() ? PortfolioSummaryDto.ValuationStatus.EMPTY : PortfolioSummaryDto.ValuationStatus.READY,
                new BigDecimal("100000"), new BigDecimal("50000"), new BigDecimal("150000"),
                new BigDecimal("100000"), BigDecimal.ZERO, List.of(), List.of());
        var core = availability(values.size(), 60, values.isEmpty());
        var eligibleVar = availability(Math.max(0, values.size() - horizon + 1),
                PortfolioReturnPreparation.minimumVarObservations(new BigDecimal(confidence)), values.isEmpty());
        var series = new Series(values.isEmpty() ? EMPTY : READY, wealth, returns,
                values.isEmpty() ? List.of(IssueCode.EMPTY_PORTFOLIO) : List.of());
        return new PreparedInputs(valuation, new RiskRequestDto(null, null, new BigDecimal(confidence), horizon, null, null),
                start.plusYears(3), UsEquityTradingCalendar.VERSION, values.isEmpty() ? EMPTY : READY,
                new History(start, end, start, end, wealth.size(), returns.size(), paired.size()),
                series, series, paired, core, core, eligibleVar, List.of());
    }

    static Availability availability(int count, int required, boolean empty) {
        return new Availability(empty ? EMPTY : count < required ? UNAVAILABLE : READY, count, required,
                empty ? List.of(IssueCode.EMPTY_PORTFOLIO) : count < required ? List.of(IssueCode.INSUFFICIENT_HISTORY) : List.of());
    }
}
