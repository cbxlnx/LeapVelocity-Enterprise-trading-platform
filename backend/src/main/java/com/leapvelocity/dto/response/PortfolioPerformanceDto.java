package com.leapvelocity.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Hypothetical buy-and-hold performance of current invested allocation. */
public record PortfolioPerformanceDto(
        Long accountId, String period, String benchmark, SimulationStatus simulationStatus,
        LocalDate weightingDate, LocalDate requestedFrom, LocalDate requestedTo,
        LocalDate from, LocalDate to,
        BigDecimal portfolioReturnPercent, BigDecimal benchmarkReturnPercent,
        List<InitialWeight> initialWeights, List<Point> points, List<String> issues
) {
    public enum SimulationStatus { READY, EMPTY, UNAVAILABLE }
    public record InitialWeight(String symbol, BigDecimal weight) { }
    public record Point(LocalDate date, BigDecimal portfolioIndex, BigDecimal benchmarkIndex) { }
}
