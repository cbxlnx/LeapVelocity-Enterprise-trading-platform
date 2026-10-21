package com.leapvelocity.service;

import com.leapvelocity.dto.request.RiskRequestDto;
import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.AccountStatus;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.HistoricalPriceRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.leapvelocity.dto.response.PortfolioRiskDto.IssueCode.INCOMPLETE_HISTORY;
import static com.leapvelocity.dto.response.PortfolioRiskDto.IssueCode.ZERO_BENCHMARK_VARIANCE;
import static com.leapvelocity.dto.response.PortfolioRiskDto.ResultStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the real historical SQL and preparation together; account/position repositories are fixtures. */
class PortfolioRiskInputsRepositoryTest {
    private JdbcTemplate jdbc;
    private PortfolioAnalyticsService service;
    private final LocalDate end = LocalDate.parse("2026-10-16");
    private final RiskRequestDto options = new RiskRequestDto(null, "1M", null, null, null, null);

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "");
        // Each JdbcTemplate call opens a connection, so retain this test database for its lifetime.
        source.setUrl(source.getUrl() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA analytics");
        jdbc.execute("CREATE TABLE analytics.daily_prices (symbol VARCHAR(20), price_date DATE, close NUMERIC(20,6), "
                + "adjusted_close NUMERIC(20,6), PRIMARY KEY(symbol, price_date))");
        var days = UsEquityTradingCalendar.sessions(end.minusMonths(1), end).orElseThrow();
        for (int i = 0; i < days.size(); i++) {
            jdbc.update("INSERT INTO analytics.daily_prices VALUES (?, ?, ?, ?)", "AAPL", Date.valueOf(days.get(i)), 900, 100 + i);
            jdbc.update("INSERT INTO analytics.daily_prices VALUES (?, ?, ?, ?)", "SPY", Date.valueOf(days.get(i)), 800, 500 + i);
        }
        jdbc.update("INSERT INTO analytics.daily_prices VALUES ('AAPL', '2025-01-02', 100, 1)");
        var accounts = mock(AccountRepository.class);
        var positions = mock(PositionRepository.class);
        when(accounts.findById(1L)).thenReturn(Optional.of(new Account("EXAMPLE", "Test", new BigDecimal("500"), AccountStatus.ACTIVE)));
        var held = new Position(1L, "AAPL", BigDecimal.ONE, BigDecimal.TEN);
        held.setInstrument(new Instrument("AAPL", "Example", "EQUITY", "USD", true));
        when(positions.findByAccountIdOrderBySymbolAsc(1L)).thenReturn(List.of(held));
        var instruments = mock(InstrumentRepository.class);
        when(instruments.findBySymbol("SPY")).thenReturn(Optional.of(new Instrument("SPY", "Benchmark", "FUND", "USD", true)));
        service = new PortfolioAnalyticsService(new PortfolioSnapshotService(accounts, positions, new HistoricalPriceRepository(jdbc), instruments), PythonRiskServer.client(),
                Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void valuesWithRawClosesButPreparesReturnsFromBoundedAdjustedCloses() {
        var result = service.prepareRiskInputs(1L, options);
        assertEquals(0, new BigDecimal("900").compareTo(result.valuation().holdingsValue()));
        assertEquals(0, new BigDecimal("0.01").compareTo(result.portfolio().returns().get(0).value()));
        assertEquals(end.minusMonths(1), result.history().from());
        assertEquals(READY, result.portfolio().status());
        assertEquals(UNAVAILABLE, result.core().status()); // Complete 1M data, below the 60-return policy.
    }

    @Test
    void aStoredGapIsDetectedEvenWhenEverySymbolOmitsThatSession() {
        var gap = LocalDate.parse("2026-10-01");
        jdbc.update("DELETE FROM analytics.daily_prices WHERE price_date = ?", Date.valueOf(gap));
        var result = service.prepareRiskInputs(1L, options);
        assertEquals(UNAVAILABLE, result.portfolio().status());
        assertTrue(result.portfolio().returns().isEmpty());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == INCOMPLETE_HISTORY && gap.equals(issue.date())));
        assertEquals(READY, result.valuationStatus());
    }

    @Test
    void coreAssessmentCalculatesFromAdjustedSqlHistoryAndPreservesValuationContext() {
        jdbc.update("DELETE FROM analytics.daily_prices");
        var days = UsEquityTradingCalendar.sessions(end.minusMonths(3), end).orElseThrow();
        for (var day : days) {
            jdbc.update("INSERT INTO analytics.daily_prices VALUES (?, ?, ?, ?)", "AAPL", Date.valueOf(day), 900, 100);
            jdbc.update("INSERT INTO analytics.daily_prices VALUES (?, ?, ?, ?)", "SPY", Date.valueOf(day), 800, 500);
        }
        var threeMonths = new RiskRequestDto(null, "3M", null, null, null, null);
        var result = service.calculateCoreRisk(1L, threeMonths);
        assertEquals(0, new BigDecimal("900").compareTo(result.valuation().holdingsValue()));
        assertEquals(0, new BigDecimal("500").compareTo(result.valuation().cashBalance()));
        assertEquals(days.size() - 1, result.metrics().annualisedVolatility().observations());
        assertEquals(days.get(0), result.metrics().annualisedVolatility().from());
        assertEquals(end, result.metrics().annualisedVolatility().to());
        assertEquals(READY, result.metrics().annualisedVolatility().status());
        assertEquals(0, result.metrics().annualisedVolatility().value().signum());
        assertEquals(READY, result.metrics().maximumDrawdown().status());
        assertEquals(0, result.metrics().maximumDrawdown().value().signum());
        assertEquals(UNAVAILABLE, result.metrics().beta().status());
        assertNull(result.metrics().beta().value());
        assertEquals(List.of(ZERO_BENCHMARK_VARIANCE), result.metrics().beta().reasons());
    }
}
