package com.leapvelocity.controller;

import com.leapvelocity.dto.response.PortfolioPerformanceDto;
import com.leapvelocity.dto.response.PortfolioPerformanceDto.SimulationStatus;
import com.leapvelocity.exceptions.GlobalExceptionHandler;
import com.leapvelocity.service.PortfolioPerformanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PortfolioPerformanceControllerTest {
    private PortfolioPerformanceService performance;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        performance = mock(PortfolioPerformanceService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PortfolioPerformanceController(performance))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        authenticate("ROLE_TRADER");
    }

    private void authenticate(String role) {
        var jwt = Jwt.withTokenValue("test").header("alg", "HS256").claim("accountId", 1L).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role))));
    }

    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }

    private PortfolioPerformanceDto result(long accountId) {
        return result(accountId, "1Y");
    }

    private PortfolioPerformanceDto result(long accountId, String period) {
        LocalDate date = LocalDate.parse("2026-10-13");
        LocalDate from = date.minusYears("2Y".equals(period) ? 2 : 1);
        return new PortfolioPerformanceDto(accountId, period, "SPY", SimulationStatus.READY, date, from, date,
                date.minusDays(1), date, new BigDecimal("2"), new BigDecimal("5"), List.of(),
                List.of(new PortfolioPerformanceDto.Point(date, new BigDecimal("102"), new BigDecimal("105"))), List.of());
    }

    @Test
    void defaultsToOneYearAndReturnsIndicesAndPercentageReturns() throws Exception {
        when(performance.getPerformance(1L, "1Y")).thenReturn(result(1L));
        mvc.perform(get("/api/v1/accounts/1/analytics/performance"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.benchmark").value("SPY"))
                .andExpect(jsonPath("$.points[0].date").value("2026-10-13"))
                .andExpect(jsonPath("$.points[0].portfolioIndex").value(102))
                .andExpect(jsonPath("$.portfolioReturnPercent").value(2));
    }

    @Test
    void acceptsTwoYearPeriodForOwnAccount() throws Exception {
        when(performance.getPerformance(1L, "2Y")).thenReturn(result(1L, "2Y"));
        mvc.perform(get("/api/v1/accounts/1/analytics/performance").param("period", "2Y"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.period").value("2Y"))
                .andExpect(jsonPath("$.requestedFrom").value("2024-10-13"));
        verify(performance).getPerformance(1L, "2Y");
    }

    @Test
    void deniesAnotherAccountBeforeCalculating() throws Exception {
        mvc.perform(get("/api/v1/accounts/2/analytics/performance").param("period", "2Y"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(performance);
    }

    @Test
    void adminCanRequestAnotherAccountsTwoYearHistory() throws Exception {
        authenticate("ROLE_ADMIN");
        when(performance.getPerformance(2L, "2Y")).thenReturn(result(2L, "2Y"));
        mvc.perform(get("/api/v1/accounts/2/analytics/performance").param("period", "2Y"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(2));
        verify(performance).getPerformance(2L, "2Y");
    }

    @Test
    void unsupportedPeriodUsesExistingValidationResponse() throws Exception {
        when(performance.getPerformance(1L, "ALL")).thenThrow(new IllegalArgumentException("Unsupported period"));
        mvc.perform(get("/api/v1/accounts/1/analytics/performance").param("period", "ALL"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VAL-422"));
    }
}
