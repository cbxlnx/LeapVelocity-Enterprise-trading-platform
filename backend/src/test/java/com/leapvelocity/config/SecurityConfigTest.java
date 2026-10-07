package com.leapvelocity.config;

import com.leapvelocity.controller.AccountController;
import com.leapvelocity.dto.response.AccountBalanceDto;
import com.leapvelocity.service.AccountService;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(SecurityConfigTest.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = {
        "app.security.jwt.secret=security-test-secret-at-least-64-bytes-abcdefghijklmnopqrstuvwxyz",
        "app.security.jwt.issuer=leapvelocity-auth"
})
class SecurityConfigTest {
    private static final String SECRET = "security-test-secret-at-least-64-bytes-abcdefghijklmnopqrstuvwxyz";
    @Autowired private WebApplicationContext context;
    @Autowired private AccountService accounts;
    private MockMvc mvc;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean AccountService accountService() { return mock(AccountService.class); }
        @Bean AccountController accountController(AccountService service) { return new AccountController(service); }
    }

    @BeforeEach
    void setUp() {
        reset(accounts);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test
    void missingOrMalformedTokenReturns401BeforeBusinessService() throws Exception {
        mvc.perform(get("/api/v1/accounts/1/balance")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accounts/1/balance").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/orders")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/orders/123")).andExpect(status().isUnauthorized());
        verifyNoInteractions(accounts);
    }

    @Test
    void validTokenAuthenticatesAndReachesExistingAccountController() throws Exception {
        when(accounts.getBalance(1L)).thenAnswer(invocation -> {
            assertEquals("42", SecurityContextHolder.getContext().getAuthentication().getName());
            return new AccountBalanceDto(1L, new BigDecimal("250000.00"));
        });
        mvc.perform(get("/api/v1/accounts/1/balance").header("Authorization", "Bearer " + sign(claims(), SECRET, JWSAlgorithm.HS256)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.cashBalance").value(250000.00));
        verify(accounts).getBalance(1L);
    }

    @Test
    void invalidSignatureIssuerAlgorithmExpiredFutureAndRefreshTokensReturn401() throws Exception {
        String[] tokens = {
                sign(claims(), "another-test-secret-at-least-32-bytes", JWSAlgorithm.HS256),
                sign(claims().issuer("wrong-issuer"), SECRET, JWSAlgorithm.HS256),
                sign(claims(), SECRET, JWSAlgorithm.HS384),
                sign(claims().expirationTime(Date.from(Instant.now().minusSeconds(1))), SECRET, JWSAlgorithm.HS256),
                sign(claims().notBeforeTime(Date.from(Instant.now().plusSeconds(60))), SECRET, JWSAlgorithm.HS256),
                sign(claims().claim("type", "refresh"), SECRET, JWSAlgorithm.HS256),
                sign(claims().expirationTime(null), SECRET, JWSAlgorithm.HS256),
                sign(claims().subject(" "), SECRET, JWSAlgorithm.HS256),
                sign(claims().claim("username", null), SECRET, JWSAlgorithm.HS256)
        };
        for (String token : tokens) {
            mvc.perform(get("/api/v1/accounts/1/balance").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void documentationIsPublic() throws Exception {
        // No documentation controller in this focused context; 404 proves security allowed the request.
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().subject("42").issuer("leapvelocity-auth")
                .claim("username", "testuser").issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(60)));
    }

    private String sign(JWTClaimsSet.Builder claims, String secret, JWSAlgorithm algorithm) throws Exception {
        SignedJWT token = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build(), claims.build());
        token.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return token.serialize();
    }
}
