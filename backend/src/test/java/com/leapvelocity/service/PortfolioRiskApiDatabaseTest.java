package com.leapvelocity.service;

import com.leapvelocity.config.SecurityConfig;
import com.leapvelocity.controller.PortfolioAnalyticsController;
import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.AccountStatus;
import com.leapvelocity.exceptions.GlobalExceptionHandler;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.HistoricalPriceRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.repository.PositionRepository;
import com.leapvelocity.support.RiskApiTokens;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;

import static com.leapvelocity.support.RiskApiTokens.bearer;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC, production JWT filters, service transaction proxy, JPA repositories and historical JDBC on H2. */
@SpringJUnitConfig(PortfolioRiskApiDatabaseTest.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = {"app.security.jwt.secret=" + RiskApiTokens.SECRET,
        "app.security.jwt.issuer=" + RiskApiTokens.ISSUER})
class PortfolioRiskApiDatabaseTest {
    private static final LocalDate END = LocalDate.parse("2026-10-16");

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = AccountRepository.class)
    @Import({SecurityConfig.class, GlobalExceptionHandler.class})
    static class Config {
        static boolean snapshotCommitted;
        static int calculationCalls;
        static RiskCalculationExceptionFixture failure;
        enum RiskCalculationExceptionFixture { TIMEOUT, UNAVAILABLE, INVALID_RESPONSE }
        @Bean DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.leapvelocity.entities");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean HistoricalPriceRepository historicalPrices(JdbcTemplate jdbc) {
            return new HistoricalPriceRepository(jdbc) {
                @Override public List<AdjustedPrice> findAdjustedPrices(List<String> symbols, LocalDate from, LocalDate to) {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                    assertEquals(java.sql.Connection.TRANSACTION_REPEATABLE_READ,
                            TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
                    snapshotCommitted = false;
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCompletion(int status) { snapshotCommitted = status == STATUS_COMMITTED; }
                    });
                    return super.findAdjustedPrices(symbols, from, to);
                }
            };
        }
        @Bean PortfolioSnapshotService snapshots(AccountRepository accounts, PositionRepository positions,
                HistoricalPriceRepository prices, InstrumentRepository instruments) {
            return new PortfolioSnapshotService(accounts, positions, prices, instruments);
        }
        @Bean PortfolioAnalyticsService analytics(PortfolioSnapshotService snapshots) {
            var realClient = PythonRiskServer.client();
            var guarded = new RiskCalculationClient("http://unused.invalid", "test-token", 1, 1) {
                @Override public RiskCalculationContract.Response calculate(PortfolioReturnPreparation.PreparedInputs input) {
                    calculationCalls++;
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    if (!input.portfolio().returns().isEmpty()) assertTrue(snapshotCommitted);
                    if (failure != null) throw new com.leapvelocity.exceptions.RiskCalculationException(
                            com.leapvelocity.exceptions.RiskCalculationException.Kind.valueOf(failure.name()), null);
                    return realClient.calculate(input);
                }
            };
            return new PortfolioAnalyticsService(snapshots, guarded,
                    Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC));
        }
        @Bean PortfolioAnalyticsController controller(PortfolioAnalyticsService service) { return new PortfolioAnalyticsController(service); }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private AccountRepository accounts;
    @Autowired private PositionRepository positions;
    @Autowired private InstrumentRepository instruments;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PortfolioAnalyticsService analytics;
    @Autowired private PortfolioSnapshotService snapshots;
    @Autowired private JpaTransactionManager transactionManager;
    private MockMvc mvc;
    private long accountId;

    @BeforeEach
    void seed() {
        Config.failure = null;
        Config.calculationCalls = 0;
        positions.deleteAllInBatch();
        accounts.deleteAllInBatch();
        instruments.deleteAllInBatch();
        instruments.saveAllAndFlush(List.of(new Instrument("AAPL", "Example", "EQUITY", "USD", true),
                new Instrument("SPY", "Benchmark", "FUND", "USD", true),
                new Instrument("EURO", "Other currency", "EQUITY", "EUR", true),
                new Instrument("CASHUSD", "Cash", "CASH", "USD", true)));
        var account = accounts.saveAndFlush(new Account("RISK-EXAMPLE", "Test", new BigDecimal("500"), AccountStatus.ACTIVE));
        accountId = account.getId();
        positions.saveAndFlush(new Position(accountId, "AAPL", BigDecimal.ONE, BigDecimal.TEN));
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS analytics");
        jdbc.execute("CREATE TABLE IF NOT EXISTS analytics.daily_prices (symbol VARCHAR(20), price_date DATE, "
                + "close NUMERIC(20,6), adjusted_close NUMERIC(20,6), PRIMARY KEY(symbol, price_date))");
        jdbc.update("DELETE FROM analytics.daily_prices");
        var days = UsEquityTradingCalendar.sessions(END.minusYears(1), END).orElseThrow();
        for (int i = 0; i < days.size(); i++) {
            jdbc.update("INSERT INTO analytics.daily_prices VALUES (?, ?, ?, ?)", "AAPL", Date.valueOf(days.get(i)), 900, 100 + i);
            jdbc.update("INSERT INTO analytics.daily_prices VALUES (?, ?, ?, ?)", "SPY", Date.valueOf(days.get(i)), 800, 500 + i);
        }
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    private String path() { return "/api/v1/accounts/" + accountId + "/analytics/risk"; }
    private String owner() throws Exception { return bearer(accountId, "TRADER"); }

    @Test
    void existingSummaryStillParticipatesInItsCallersTransaction() {
        new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.update("UPDATE analytics.daily_prices SET close = 123 WHERE symbol = 'AAPL' AND price_date = ?",
                    Date.valueOf(END));
            assertEquals(0, new BigDecimal("123").compareTo(analytics.getSummary(accountId).holdingsValue()),
                    "Historical simulation callers must keep summary valuation in their existing transaction");
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            status.setRollbackOnly();
            return null;
        });
        assertEquals(0, new BigDecimal("900").compareTo(analytics.getSummary(accountId).holdingsValue()));
        assertEquals(0, Config.calculationCalls);
    }

    @Test
    void snapshotProxyCommitsBeforeHttpEvenWhenCallerHasATransaction() {
        assertTrue(AopUtils.isAopProxy(snapshots));
        new TransactionTemplate(transactionManager).execute(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(com.leapvelocity.dto.response.PortfolioRiskDto.RiskStatus.READY, analytics.getRisk(accountId, null).riskStatus());
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "Caller transaction resumes after orchestration");
            return null;
        });
        assertEquals(1, Config.calculationCalls);
        assertTrue(Config.snapshotCommitted);
    }

    @Test
    void calculationFailuresPreserveExistingPublic500EnvelopeAndAuthorization() throws Exception {
        for (var failure : Config.RiskCalculationExceptionFixture.values()) {
            Config.failure = failure;
            mvc.perform(get(path()).header("Authorization", owner())).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("ERR-999"))
                    .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
        }
        int previous = Config.calculationCalls;
        mvc.perform(get(path()).header("Authorization", bearer(accountId + 1000, "TRADER"))).andExpect(status().isForbidden());
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        assertEquals(previous, Config.calculationCalls, "Unauthorized requests must never call Python");
    }

    @Test
    void completeMvpMatchesTheSharedImportedHistoryAndIndependentGoldenValues() throws Exception {
        loadMvpFixture();
        var json = JsonMapper.builder().build();
        var expected = json.readTree(getClass().getResourceAsStream("/risk/mvp-expected.json"));
        mvc.perform(get(path()).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.riskStatus").value("READY"))
                .andExpect(jsonPath("$.valuation.holdingsValue").value(1000))
                .andExpect(jsonPath("$.valuation.cashBalance").value(500))
                .andExpect(jsonPath("$.valuation.asOf").value(expected.get("asOf").asString()))
                .andExpect(jsonPath("$.history.returnObservations").value(expected.get("returnObservations").asInt()))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.value").value(Double.valueOf(expected.get("volatility").asString())))
                .andExpect(jsonPath("$.metrics.maximumDrawdown.value").value(Double.valueOf(expected.get("drawdown").asString())))
                .andExpect(jsonPath("$.metrics.beta.value").value(Double.valueOf(expected.get("beta").asString())))
                .andExpect(jsonPath("$.valueAtRisk[0].lossPercent").value(Double.valueOf(expected.get("varPercent").asString())))
                .andExpect(jsonPath("$.valueAtRisk[0].lossAmount").value(Double.valueOf(expected.get("varAmount").asString())))
                .andExpect(jsonPath("$.valueAtRisk[1].status").value("READY"))
                .andExpect(jsonPath("$.valueAtRisk[2].status").value("READY"));
    }

    @Test
    void normalVarMatchesIndependentFixtureMathAndSharesConfiguredConfidenceAndHorizon() throws Exception {
        loadMvpFixture();
        // Independent Python statistics.NormalDist calculation on the two-state wealth fixture:
        // 1 session: mean=.0019303916409832147, s=.058417910562519375, z95=1.6448536269514722.
        // 3 sessions: mean=.0019322573312116167, s=.05841884154749218, z90=1.2815515655446004.
        mvc.perform(get(path()).header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valueAtRisk[1].lossPercent").value(9.41585204))
                .andExpect(jsonPath("$.valueAtRisk[1].lossAmount").value(94.16))
                .andExpect(jsonPath("$.valueAtRisk[2].lossAmount", greaterThan(91.0)))
                .andExpect(jsonPath("$.valueAtRisk[2].lossAmount", lessThan(97.0)));
        mvc.perform(get(path()).param("confidenceLevel", "0.90").param("holdingPeriodDays", "3").header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parameters.confidenceLevel").value(.90))
                .andExpect(jsonPath("$.parameters.holdingPeriodDays").value(3))
                .andExpect(jsonPath("$.valueAtRisk[1].lossPercent").value(7.29345005))
                .andExpect(jsonPath("$.valueAtRisk[1].lossAmount").value(72.93))
                .andExpect(jsonPath("$.valueAtRisk[2].status").value("READY"))
                .andExpect(jsonPath("$.valueAtRisk[2].observations").value(249));
        mvc.perform(get(path()).param("confidenceLevel", "0.99").header("Authorization", owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valueAtRisk[1].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.valueAtRisk[1].lossAmount").value(nullValue()))
                .andExpect(jsonPath("$.valueAtRisk[1].requiredObservations").value(500))
                .andExpect(jsonPath("$.valueAtRisk[2].reasons", hasItem("INSUFFICIENT_HISTORY")));
    }

    @Test
    void sharedFixtureDistinguishesMissingBenchmarkFromAGapInHeldHistory() throws Exception {
        loadMvpFixture();
        jdbc.update("DELETE FROM analytics.daily_prices WHERE symbol='SPY'");
        mvc.perform(get(path()).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.riskStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.metrics.beta.reasons", hasItem("MISSING_BENCHMARK")))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.status").value("READY"))
                .andExpect(jsonPath("$.valueAtRisk[0].status").value("READY"))
                .andExpect(jsonPath("$.valueAtRisk[1].status").value("READY"))
                .andExpect(jsonPath("$.valueAtRisk[2].status").value("READY"));
        jdbc.update("DELETE FROM analytics.daily_prices WHERE symbol='AAPL' AND price_date='2026-10-01'");
        mvc.perform(get(path()).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.riskStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.reasons", hasItem("INCOMPLETE_HISTORY")))
                .andExpect(jsonPath("$.valueAtRisk[0].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.valueAtRisk[1].lossAmount").value(nullValue()))
                .andExpect(jsonPath("$.valueAtRisk[2].reasons", hasItem("INCOMPLETE_HISTORY")));
    }

    private void loadMvpFixture() throws Exception {
        jdbc.update("DELETE FROM analytics.daily_prices");
        positions.deleteAllInBatch();
        instruments.saveAndFlush(new Instrument("MSFT", "Synthetic fixture", "EQUITY", "USD", true));
        positions.saveAllAndFlush(List.of(new Position(accountId, "AAPL", new BigDecimal("6"), BigDecimal.TEN),
                new Position(accountId, "MSFT", new BigDecimal("2"), BigDecimal.TEN)));
        try (var reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/risk/mvp-history.csv"), StandardCharsets.UTF_8))) {
            var rows = reader.lines().skip(1).map(line -> line.split(",")).toList();
            assertEquals(UsEquityTradingCalendar.sessions(END.minusYears(1), END).orElseThrow(),
                    rows.stream().map(row -> LocalDate.parse(row[0])).toList());
            for (var row : rows) {
                for (int index = 0; index < 3; index++) {
                    jdbc.update("INSERT INTO analytics.daily_prices VALUES (?, ?, ?, ?)",
                            List.of("AAPL", "MSFT", "SPY").get(index), Date.valueOf(row[0]),
                            List.of(100, 200, 500).get(index), new BigDecimal(row[index + 1]));
                }
            }
        }
    }

    @Test
    void ownerReceivesFullShapeWithRealCalculatedCoreAndExplicitPendingComponents() throws Exception {
        assertTrue(AopUtils.isAopProxy(analytics), "Service transaction advice must be active");
        var first = mvc.perform(get(path()).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.schemaVersion").value("1.0"))
                .andExpect(jsonPath("$.riskStatus").value("READY"))
                .andExpect(jsonPath("$.valuation.accountId").value(accountId))
                .andExpect(jsonPath("$.valuation.holdingsValue").value(900))
                .andExpect(jsonPath("$.valuation.cashBalance").value(500))
                .andExpect(jsonPath("$.calculatedAt").value("2026-10-19T12:00:00Z"))
                .andExpect(jsonPath("$.history.returnObservations").value(251))
                .andExpect(jsonPath("$.series.status").value("READY"))
                .andExpect(jsonPath("$.series.wealth", hasSize(252)))
                .andExpect(jsonPath("$.series.wealth[0].value").value(1))
                .andExpect(jsonPath("$.series.returns", hasSize(251)))
                .andExpect(jsonPath("$.series.reasons", hasSize(0)))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.status").value("READY"))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.unit").value("PERCENT"))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.value", greaterThan(0.0)))
                .andExpect(jsonPath("$.metrics.maximumDrawdown.value").value(0))
                .andExpect(jsonPath("$.metrics.beta.status").value("READY"))
                .andExpect(jsonPath("$.valueAtRisk", hasSize(3)))
                .andExpect(jsonPath("$.valueAtRisk[0].status").value("READY"))
                .andExpect(jsonPath("$.valueAtRisk[0].lossAmount").value(0))
                .andExpect(jsonPath("$.valueAtRisk[0].observations").value(251))
                .andExpect(jsonPath("$.valueAtRisk[0].estimate").value(true))
                .andExpect(jsonPath("$.valueAtRisk[1].status").value("READY"))
                .andExpect(jsonPath("$.valueAtRisk[2].status").value("READY"))
                .andExpect(jsonPath("$.assumptions.normalVarDistribution").value("NORMAL"))
                .andExpect(jsonPath("$.assumptions.normalVarHorizonMethod").value("FIT_TO_OVERLAPPING_COMPOUNDED_RETURNS"))
                .andExpect(jsonPath("$.assumptions.monteCarloSimulations").value(100000))
                .andExpect(jsonPath("$.assumptions.monteCarloSeed").value(20261020))
                .andReturn().getResponse().getContentAsString();
        var second = mvc.perform(get(path()).header("Authorization", owner())).andReturn().getResponse().getContentAsString();
        assertEquals(first, second, "Fixed snapshot and clock must be repeatable through the API");
    }

    @Test
    void securityDeniesNonOwnerButAllowsAdminAndReturns404OnlyForAuthorizedMissingAccounts() throws Exception {
        mvc.perform(get(path()).header("Authorization", bearer(accountId + 1000, "TRADER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH-403"));
        mvc.perform(get(path()).header("Authorization", bearer(accountId + 1000, "ADMIN")))
                .andExpect(status().isOk());
        var absent = "/api/v1/accounts/999999/analytics/risk";
        mvc.perform(get(absent).header("Authorization", owner())).andExpect(status().isForbidden());
        mvc.perform(get(absent).header("Authorization", bearer(999999, "TRADER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACC-404"));
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
    }

    @Test
    void knownBenchmarkWithNoHistoryDisablesOnlyBeta() throws Exception {
        jdbc.update("DELETE FROM analytics.daily_prices WHERE symbol = 'SPY'");
        mvc.perform(get(path()).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.metrics.beta.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.metrics.beta.reasons[0]").value("MISSING_BENCHMARK"))
                .andExpect(jsonPath("$.metrics.beta.value").value(nullValue()))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.status").value("READY"))
                .andExpect(jsonPath("$.series.status").value("READY"))
                .andExpect(jsonPath("$.valueAtRisk[0].status").value("READY"));
    }

    @Test
    void historicalVarUsesConfiguredConfidenceAndOverlappingHorizonWithRealStoredPrices() throws Exception {
        // Adjusted AAPL goes 300,299,...,49. 251 one-session losses are 1/300,...,1/50.
        jdbc.update("UPDATE analytics.daily_prices SET adjusted_close = 400 - adjusted_close WHERE symbol = 'AAPL'");
        mvc.perform(get(path()).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valueAtRisk[0].method").value("HISTORICAL"))
                .andExpect(jsonPath("$.valueAtRisk[0].lossPercent").value(1.61290323))
                .andExpect(jsonPath("$.valueAtRisk[0].lossAmount").value(14.52))
                .andExpect(jsonPath("$.valueAtRisk[0].requiredObservations").value(100));
        mvc.perform(get(path()).param("holdingPeriodDays", "5").header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parameters.holdingPeriodDays").value(5))
                .andExpect(jsonPath("$.valueAtRisk[0].observations").value(247))
                .andExpect(jsonPath("$.valueAtRisk[0].lossPercent").value(7.57575758))
                .andExpect(jsonPath("$.valueAtRisk[0].lossAmount").value(68.18));
        mvc.perform(get(path()).param("confidenceLevel", "0.99").param("holdingPeriodDays", "5").header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parameters.confidenceLevel").value(0.99))
                .andExpect(jsonPath("$.valueAtRisk[0].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.valueAtRisk[0].lossAmount").value(nullValue()))
                .andExpect(jsonPath("$.valueAtRisk[0].observations").value(247))
                .andExpect(jsonPath("$.valueAtRisk[0].requiredObservations").value(500))
                .andExpect(jsonPath("$.valueAtRisk[0].reasons[0]").value("INSUFFICIENT_HISTORY"))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.status").value("READY"));
    }

    @Test
    void unknownNonUsdAndCashBenchmarksReturnExistingValidationEnvelope() throws Exception {
        for (var symbol : List.of("UNKNOWN", "EURO", "CASHUSD")) mvc.perform(get(path())
                        .param("benchmark", symbol).header("Authorization", owner()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VAL-422"));
    }

    @Test
    void emptyHoldingsPreserveCashAndSerializeNullRiskValues() throws Exception {
        positions.deleteAllInBatch();
        mvc.perform(get(path()).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.riskStatus").value("EMPTY"))
                .andExpect(jsonPath("$.valuation.cashBalance").value(500))
                .andExpect(jsonPath("$.valuation.asOf").value(nullValue()))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.status").value("EMPTY"))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.value").value(nullValue()))
                .andExpect(jsonPath("$.valueAtRisk[0].status").value("EMPTY"));
    }

    @Test
    void missingHoldingHistoryReturnsUnavailableRatherThan500() throws Exception {
        jdbc.update("DELETE FROM analytics.daily_prices WHERE symbol = 'AAPL'");
        mvc.perform(get(path()).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.riskStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.value").value(nullValue()))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.reasons", hasItem("MISSING_PRICE")))
                .andExpect(jsonPath("$.series.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.series.wealth", hasSize(0)))
                .andExpect(jsonPath("$.series.returns", hasSize(0)))
                .andExpect(jsonPath("$.valuation.holdingsValue").value(nullValue()));
    }

    @Test
    void staleHistoryReturnsExplicitFreshnessFailure() throws Exception {
        jdbc.update("DELETE FROM analytics.daily_prices WHERE price_date > ?", Date.valueOf(END.minusDays(30)));
        mvc.perform(get(path()).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.riskStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.reasons", hasItem("STALE_PRICE")))
                .andExpect(jsonPath("$.series.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.series.wealth", hasSize(0)));
    }

    @Test
    void missingSessionAndInvalidAdjustedPricesReturnStructuredUnavailability() throws Exception {
        jdbc.update("DELETE FROM analytics.daily_prices WHERE price_date = '2026-10-01'");
        mvc.perform(get(path()).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.metrics.maximumDrawdown.reasons", hasItem("INCOMPLETE_HISTORY")))
                .andExpect(jsonPath("$.series.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.series.wealth", hasSize(0)));
        jdbc.update("UPDATE analytics.daily_prices SET adjusted_close = 0 WHERE symbol = 'AAPL' AND price_date = '2026-10-02'");
        mvc.perform(get(path()).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.metrics.maximumDrawdown.reasons", hasItem("INVALID_PRICE")));
    }

    @Test
    void shortWindowAndZeroBenchmarkVariancePreserveDatesCountsAndReasons() throws Exception {
        mvc.perform(get(path()).param("period", "1M").header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.metrics.beta.reasons[0]").value("INSUFFICIENT_HISTORY"))
                .andExpect(jsonPath("$.metrics.beta.requiredObservations").value(60))
                .andExpect(jsonPath("$.metrics.beta.from").value("2026-09-16"))
                .andExpect(jsonPath("$.metrics.beta.to").value("2026-10-16"));
        jdbc.update("UPDATE analytics.daily_prices SET adjusted_close = 500 WHERE symbol = 'SPY'");
        mvc.perform(get(path()).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.metrics.beta.reasons[0]").value("ZERO_BENCHMARK_VARIANCE"))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.status").value("READY"));
    }

    @Test
    void infrastructureFailureRemainsASanitized500RatherThanMissingMarketData() throws Exception {
        jdbc.execute("DROP TABLE analytics.daily_prices");
        mvc.perform(get(path()).header("Authorization", owner()))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value(path()));
    }
}
