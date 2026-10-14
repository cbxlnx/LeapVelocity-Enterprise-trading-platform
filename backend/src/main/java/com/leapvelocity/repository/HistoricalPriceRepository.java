package com.leapvelocity.repository;

import com.leapvelocity.dto.response.HistoricalPricePointDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/** Read-only access to the table maintained by the Python importer. */
@Repository
public class HistoricalPriceRepository {
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
}
