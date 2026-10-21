package com.leapvelocity.controller;

import com.leapvelocity.dto.request.RiskRequestDto;
import com.leapvelocity.dto.response.PortfolioRiskDto;
import com.leapvelocity.dto.response.PortfolioRiskDto.*;
import com.leapvelocity.dto.response.PortfolioSummaryDto;
import com.leapvelocity.dto.response.PortfolioSummaryDto.ValuationStatus;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.exceptions.GlobalExceptionHandler;
import com.leapvelocity.service.PortfolioAnalyticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PortfolioAnalyticsControllerTest {
    private PortfolioAnalyticsService analytics;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        analytics = mock(PortfolioAnalyticsService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PortfolioAnalyticsController(analytics))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        authenticate("ROLE_TRADER");
    }

    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }

    private void authenticate(String role) {
        var jwt = Jwt.withTokenValue("test").header("alg", "HS256").claim("accountId", 1L).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role))));
    }

    private PortfolioSummaryDto summary(long accountId) {
        return new PortfolioSummaryDto(accountId, "USD", LocalDate.parse("2025-10-01"), ValuationStatus.READY,
                new BigDecimal("24"), new BigDecimal("5"), new BigDecimal("29"), new BigDecimal("20"),
                new BigDecimal("4"), List.of(), List.of());
    }

    @Test
    void ownerGetsValuationWithAsOf() throws Exception {
        when(analytics.getSummary(1L)).thenReturn(summary(1L));
        mvc.perform(get("/api/v1/accounts/1/analytics/summary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.asOf").value("2025-10-01"))
                .andExpect(jsonPath("$.totalEquity").value(29))
                .andExpect(jsonPath("$.valuationStatus").value("READY"));
    }

    @Test
    void otherAccountIsDeniedBeforeReadingItsData() throws Exception {
        mvc.perform(get("/api/v1/accounts/2/analytics/summary"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH-403"));
        verifyNoInteractions(analytics);
    }

    @Test
    void adminCanReadAnotherAccount() throws Exception {
        authenticate("ROLE_ADMIN");
        when(analytics.getSummary(2L)).thenReturn(summary(2L));
        mvc.perform(get("/api/v1/accounts/2/analytics/summary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(2));
    }

    @Test
    void authorizedMissingAccountReturns404() throws Exception {
        when(analytics.getSummary(1L)).thenThrow(new AccountNotFoundException(1L));
        mvc.perform(get("/api/v1/accounts/1/analytics/summary"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACC-404"));
    }

    @Test
    void unavailableValuationSerializesNullInsteadOfZero() throws Exception {
        when(analytics.getSummary(1L)).thenReturn(new PortfolioSummaryDto(1L, "USD", null,
                ValuationStatus.UNAVAILABLE, null, new BigDecimal("5"), null, new BigDecimal("20"), null,
                List.of(), List.of("Missing completed price history for: AAPL")));
        mvc.perform(get("/api/v1/accounts/1/analytics/summary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.holdingsValue").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.issues[0]").value("Missing completed price history for: AAPL"));
    }

    private PortfolioRiskDto core(long accountId, RiskRequestDto options) {
        var empty = new ScalarMetric(ResultStatus.EMPTY, null, Unit.PERCENT, 0, 60, null, null, List.of(IssueCode.EMPTY_PORTFOLIO));
        var beta = new ScalarMetric(ResultStatus.EMPTY, null, Unit.DIMENSIONLESS, 0, 60, null, null, List.of(IssueCode.EMPTY_PORTFOLIO));
        return new PortfolioRiskDto("1.0", Instant.parse("2026-10-19T12:00:00Z"), LocalDate.parse("2026-10-19"), options, RiskStatus.EMPTY, summary(accountId), new History(null, null, null, null, 0, 0, 0), Assumptions.mvp(), new CoreMetrics(empty, empty, beta), List.of(), List.of(), new ChartSeries(ResultStatus.EMPTY, List.of(), List.of(), List.of(IssueCode.EMPTY_PORTFOLIO)), null, StressCapabilities.current());
    }

    @Test
    void ownerGetsCoreRiskWithDefaultsAndExplicitUnavailableValues() throws Exception {
        when(analytics.getRisk(1L, RiskRequestDto.defaults())).thenReturn(core(1L, RiskRequestDto.defaults()));
        mvc.perform(get("/api/v1/accounts/1/analytics/risk"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parameters.period").value("1Y"))
                .andExpect(jsonPath("$.metrics.annualisedVolatility.unit").value("PERCENT"))
                .andExpect(jsonPath("$.metrics.beta.unit").value("DIMENSIONLESS"))
                .andExpect(jsonPath("$.metrics.beta.status").value("EMPTY"))
                .andExpect(jsonPath("$.metrics.beta.value").value(org.hamcrest.Matchers.nullValue()));
        verify(analytics).getRisk(1L, RiskRequestDto.defaults());
    }

    @Test
    void allOptionsBindNormalizeAndReachTheRiskService() throws Exception {
        var options = new RiskRequestDto("USD", "3M", new BigDecimal("0.99"), 10, "QQQ", 30);
        when(analytics.getRisk(1L, options)).thenReturn(core(1L, options));
        mvc.perform(get("/api/v1/accounts/1/analytics/risk").param("currency", " usd ").param("period", " 3m ")
                        .param("confidenceLevel", "0.99").param("holdingPeriodDays", "10").param("benchmark", " qqq ")
                        .param("maxPriceAgeDays", "30"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parameters.benchmark").value("QQQ"));
        verify(analytics).getRisk(1L, options);
    }

    @Test
    void otherAccountIsDeniedBeforeRiskCalculationAndOptionMetadataReads() throws Exception {
        mvc.perform(get("/api/v1/accounts/2/analytics/risk"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH-403"));
        verifyNoInteractions(analytics);
    }

    @Test
    void adminCanCalculateRiskForAnotherAccount() throws Exception {
        authenticate("ROLE_ADMIN");
        when(analytics.getRisk(2L, RiskRequestDto.defaults())).thenReturn(core(2L, RiskRequestDto.defaults()));
        mvc.perform(get("/api/v1/accounts/2/analytics/risk"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valuation.accountId").value(2));
        verify(analytics).getRisk(2L, RiskRequestDto.defaults());
    }

    @Test
    void authorizedMissingRiskAccountReturnsTheExisting404Envelope() throws Exception {
        when(analytics.getRisk(1L, RiskRequestDto.defaults())).thenThrow(new AccountNotFoundException(1L));
        mvc.perform(get("/api/v1/accounts/1/analytics/risk"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACC-404"))
                .andExpect(jsonPath("$.path").value("/api/v1/accounts/1/analytics/risk"));
    }

    @ParameterizedTest
    @MethodSource("invalidRiskQueries")
    void invalidRiskOptionsUseExistingValidationBeforeCalculation(String key, String value) throws Exception {
        mvc.perform(get("/api/v1/accounts/1/analytics/risk").param(key, value))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VAL-422"));
        verifyNoInteractions(analytics);
    }

    static Stream<Arguments> invalidRiskQueries() {
        return Stream.of(Arguments.of("currency", "EUR"), Arguments.of("period", "ALL"),
                Arguments.of("confidenceLevel", "NaN"), Arguments.of("confidenceLevel", "0.999"),
                Arguments.of("holdingPeriodDays", "1.5"), Arguments.of("holdingPeriodDays", "0"),
                Arguments.of("benchmark", "A/B"), Arguments.of("concentrationThreshold", "0"),
                Arguments.of("shocks", "-0.1,-0.10"), Arguments.of("shocks", "-0.1,,0"),
                Arguments.of("shocks", "0.1"), Arguments.of("maxPriceAgeDays", "31"),
                Arguments.of("peroid", "1M"), Arguments.of("shocks[0]", "-0.1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"currency", "period", "confidenceLevel", "holdingPeriodDays", "benchmark",
            "concentrationThreshold", "shocks", "maxPriceAgeDays"})
    void explicitlyBlankOptionsCannotSilentlyBecomeDefaults(String key) throws Exception {
        mvc.perform(get("/api/v1/accounts/1/analytics/risk").param(key, " "))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VAL-422"));
        verifyNoInteractions(analytics);
    }

    @Test
    void duplicateQueryOptionsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/accounts/1/analytics/risk").param("period", "1Y", "1M"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VAL-422"));
        verifyNoInteractions(analytics);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "99999999999999999999999999999"})
    void invalidAccountIdsReturnValidationRatherThan500(String id) throws Exception {
        mvc.perform(get("/api/v1/accounts/" + id + "/analytics/risk"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VAL-422"));
        verifyNoInteractions(analytics);
    }
}
