package com.leapvelocity.repository;

import com.leapvelocity.dto.response.HistoricalPricePointDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-only access to the table maintained by the Python importer. */
@Repository
public class HistoricalPriceRepository {
    public record AdjustedPrice(String symbol, LocalDate date, BigDecimal adjustedClose) { }

    private final JdbcTemplate jdbcTemplate;

    public HistoricalPriceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<HistoricalPricePointDto> findPrices(String symbol, LocalDate from, LocalDate to) {
        return jdbcTemplate.query("""
                SELECT price_date, close, adjusted_close, volume, source
                FROM analytics.daily_prices
                WHERE symbol = ? AND price_date >= ? AND price_date <= ?
                ORDER BY price_date ASC
                """,
                (row, index) -> new HistoricalPricePointDto(
                        row.getDate("price_date").toLocalDate(),
                        row.getBigDecimal("close"),
                        row.getBigDecimal("adjusted_close"),
                        row.getLong("volume"),
                        row.getString("source")),
                symbol, Date.valueOf(from), Date.valueOf(to));
    }

    public LocalDate findLatestCommonDate(List<String> symbols, LocalDate before) {
        if (symbols.isEmpty()) return null;
        String placeholders = String.join(",", Collections.nCopies(symbols.size(), "?"));
        List<Object> parameters = new ArrayList<>(symbols);
        parameters.add(Date.valueOf(before));
        parameters.add(symbols.size());
        return jdbcTemplate.queryForObject("""
                SELECT MAX(price_date) AS as_of FROM (
                    SELECT price_date FROM analytics.daily_prices
                    WHERE symbol IN (%s) AND price_date < ?
                    GROUP BY price_date HAVING COUNT(DISTINCT symbol) = ?
                ) common_dates
                """.formatted(placeholders), (row, index) -> {
                    Date day = row.getDate("as_of");
                    return day == null ? null : day.toLocalDate();
                }, parameters.toArray());
    }

    /** A null from selects all stored history through the inclusive end date. */
    public List<AdjustedPrice> findAdjustedPrices(List<String> symbols, LocalDate from, LocalDate to) {
        if (symbols.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(symbols.size(), "?"));
        List<Object> parameters = new ArrayList<>(symbols);
        String lowerBound = "";
        if (from != null) {
            lowerBound = " AND price_date >= ?";
            parameters.add(Date.valueOf(from));
        }
        parameters.add(Date.valueOf(to));
        return jdbcTemplate.query("SELECT symbol, price_date, adjusted_close FROM analytics.daily_prices "
                        + "WHERE symbol IN (" + placeholders + ")" + lowerBound
                        + " AND price_date <= ? ORDER BY price_date ASC, symbol ASC",
                (row, index) -> new AdjustedPrice(row.getString("symbol"),
                        row.getDate("price_date").toLocalDate(), row.getBigDecimal("adjusted_close")),
                parameters.toArray());
    }

    public Map<String, BigDecimal> findClosesOnDate(List<String> symbols, LocalDate date) {
        if (symbols.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(symbols.size(), "?"));
        List<Object> parameters = new ArrayList<>(symbols);
        parameters.add(Date.valueOf(date));
        return jdbcTemplate.query("SELECT symbol, close FROM analytics.daily_prices "
                        + "WHERE symbol IN (" + placeholders + ") AND price_date = ?",
                (row, index) -> Map.entry(row.getString("symbol"), row.getBigDecimal("close")),
                parameters.toArray()).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Set<String> findSymbolsWithHistoryBefore(List<String> symbols, LocalDate before) {
        if (symbols.isEmpty()) return Set.of();
        String placeholders = String.join(",", Collections.nCopies(symbols.size(), "?"));
        List<Object> parameters = new ArrayList<>(symbols);
        parameters.add(Date.valueOf(before));
        return Set.copyOf(jdbcTemplate.query("SELECT DISTINCT symbol FROM analytics.daily_prices "
                        + "WHERE symbol IN (" + placeholders + ") AND price_date < ?",
                (row, index) -> row.getString("symbol"), parameters.toArray()));
    }
}
