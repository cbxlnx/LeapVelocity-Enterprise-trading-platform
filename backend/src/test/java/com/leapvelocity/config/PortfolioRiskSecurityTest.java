package com.leapvelocity.config;

import com.leapvelocity.controller.PortfolioAnalyticsController;
import com.leapvelocity.dto.request.RiskRequestDto;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.exceptions.GlobalExceptionHandler;
import com.leapvelocity.service.PortfolioAnalyticsService;
import com.leapvelocity.support.RiskApiTokens;
import com.nimbusds.jose.JWSAlgorithm;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.Date;

import static com.leapvelocity.support.RiskApiTokens.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(PortfolioRiskSecurityTest.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = {"app.security.jwt.secret=" + RiskApiTokens.SECRET,
        "app.security.jwt.issuer=" + RiskApiTokens.ISSUER})
class PortfolioRiskSecurityTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean PortfolioAnalyticsService analytics() { return mock(PortfolioAnalyticsService.class); }
        @Bean PortfolioAnalyticsController controller(PortfolioAnalyticsService service) { return new PortfolioAnalyticsController(service); }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private PortfolioAnalyticsService analytics;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(analytics);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test
    void missingAndMalformedTokensAreRejectedBeforeBindingOrCalculation() throws Exception {
        mvc.perform(get("/api/v1/accounts/1/analytics/risk").param("period", "invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accounts/1/analytics/risk").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(analytics);
    }

    @Test
    void invalidSignatureIssuerAlgorithmExpiryFutureRefreshAndClaimsAreRejectedByActualDecoder() throws Exception {
        String[] tokens = {
                sign(claims(1, "TRADER"), "another-test-secret-at-least-64-bytes-abcdefghijklmnopqrstuvwxyz-12345", JWSAlgorithm.HS256),
                sign(claims(1, "TRADER").issuer("wrong"), SECRET, JWSAlgorithm.HS256),
                sign(claims(1, "TRADER"), SECRET, JWSAlgorithm.HS384),
                sign(claims(1, "TRADER").expirationTime(Date.from(Instant.now().minusSeconds(1))), SECRET, JWSAlgorithm.HS256),
                sign(claims(1, "TRADER").notBeforeTime(Date.from(Instant.now().plusSeconds(300))), SECRET, JWSAlgorithm.HS256),
                sign(claims(1, "TRADER").claim("type", "refresh"), SECRET, JWSAlgorithm.HS256),
                sign(claims(1, "TRADER").expirationTime(null), SECRET, JWSAlgorithm.HS256),
                sign(claims(1, "TRADER").claim("username", null), SECRET, JWSAlgorithm.HS256),
                sign(claims(1, "TRADER").subject(" "), SECRET, JWSAlgorithm.HS256)
        };
        for (var token : tokens) mvc.perform(get("/api/v1/accounts/1/analytics/risk").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(analytics);
    }

    @Test
    void signedOwnerAndAdminReachCalculationAndAuthorizedMissingAccountsReturn404() throws Exception {
        when(analytics.getRisk(1L, RiskRequestDto.defaults())).thenThrow(new AccountNotFoundException(1L));
        when(analytics.getRisk(2L, RiskRequestDto.defaults())).thenThrow(new AccountNotFoundException(2L));
        mvc.perform(get("/api/v1/accounts/1/analytics/risk").header("Authorization", bearer(1, "TRADER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACC-404"));
        mvc.perform(get("/api/v1/accounts/2/analytics/risk").header("Authorization", bearer(1, "ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACC-404"));
        verify(analytics).getRisk(1L, RiskRequestDto.defaults());
        verify(analytics).getRisk(2L, RiskRequestDto.defaults());
    }

    @Test
    void signedNonOwnerAndAbsentAccountClaimReturn403BeforeAnyServiceInteraction() throws Exception {
        mvc.perform(get("/api/v1/accounts/2/analytics/risk").header("Authorization", bearer(1, "TRADER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH-403"));
        var noAccount = sign(claims(1, "TRADER").claim("accountId", null), SECRET, JWSAlgorithm.HS256);
        mvc.perform(get("/api/v1/accounts/1/analytics/risk").header("Authorization", "Bearer " + noAccount))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH-403"));
        verifyNoInteractions(analytics);
    }

    @Test
    void authenticatedInvalidQueryUsesExistingValidationEnvelope() throws Exception {
        mvc.perform(get("/api/v1/accounts/1/analytics/risk").param("holdingPeriodDays", "NaN")
                        .header("Authorization", bearer(1, "TRADER")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VAL-422"));
        verifyNoInteractions(analytics);
    }
}
