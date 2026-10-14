package com.leapvelocity.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalPriceRepositoryTest {
    private HistoricalPriceRepository repository;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA analytics");
        jdbc.execute("""
                CREATE TABLE analytics.daily_prices (
                    symbol VARCHAR(20), price_date DATE, close NUMERIC(20,6),
                    adjusted_close NUMERIC(20,6), volume BIGINT, source TEXT
                )
                """);
        jdbc.execute("""
                INSERT INTO analytics.daily_prices VALUES
                ('AAPL', '2025-10-03', 253.123456, 250.654321, 300, 'yfinance'),
                ('MSFT', '2025-10-02', 500, 499, 200, 'yfinance'),
                ('AAPL', '2025-10-01', 251.123456, 248.654321, 100, 'yfinance'),
                ('AAPL', '2025-10-02', 252.123456, 249.654321, 200, 'yfinance')
                """);
        repository = new HistoricalPriceRepository(jdbc);
    }

    @Test
    void filtersSymbolIncludesBothBoundsAndSortsByTradingDate() {
        var rows = repository.findPrices("AAPL", LocalDate.parse("2025-10-01"), LocalDate.parse("2025-10-02"));
        assertEquals(List.of(LocalDate.parse("2025-10-01"), LocalDate.parse("2025-10-02")),
                rows.stream().map(row -> row.date()).toList());
        assertEquals("251.123456", rows.get(0).close().toPlainString());
        assertEquals("248.654321", rows.get(0).adjustedClose().toPlainString());
        assertEquals(100, rows.get(0).volume());
    }

    @Test
    void allowsASingleDateAndEmptyRange() {
        assertEquals(1, repository.findPrices("AAPL", LocalDate.parse("2025-10-02"), LocalDate.parse("2025-10-02")).size());
        assertTrue(repository.findPrices("AAPL", LocalDate.parse("2000-01-01"), LocalDate.parse("2000-12-31")).isEmpty());
    }

    @Test
    void bindsSymbolAsAParameter() {
        assertTrue(repository.findPrices("AAPL' OR 1=1 --", LocalDate.parse("2025-10-01"), LocalDate.parse("2025-10-03")).isEmpty());
    }
}
