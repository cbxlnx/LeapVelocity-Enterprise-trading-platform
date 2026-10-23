package com.leapvelocity.controller;

import com.leapvelocity.dto.response.PortfolioSummaryDto;
import com.leapvelocity.dto.response.PortfolioSummaryDto.ValuationStatus;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.exceptions.GlobalExceptionHandler;
import com.leapvelocity.service.PortfolioAnalyticsService;
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
}
