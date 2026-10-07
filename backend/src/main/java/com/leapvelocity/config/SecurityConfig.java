package com.leapvelocity.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
@OpenAPIDefinition(security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/swagger-resources/**", "/webjars/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        NimbusJwtDecoder decoder;
        OAuth2TokenValidator<Jwt> validator;

        decoder = NimbusJwtDecoder.withSecretKey(secretKey(jwtProperties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Match NestJS expiry checks: expired tokens have no additional grace period.
        OAuth2TokenValidator<Jwt> accessClaims = jwt -> {
            Object username = jwt.getClaims().get("username");
            if (jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || !(username instanceof String name) || name.isBlank()
                    || jwt.getExpiresAt() == null || jwt.getClaims().containsKey("type")) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid access token claims", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO),
                new JwtIssuerValidator(jwtProperties.issuer()), accessClaims);
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter;
        JwtAuthenticationConverter converter;

        authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    private SecretKey secretKey(JwtProperties jwtProperties) {
        byte[] keyBytes;

        if (jwtProperties.secret() == null || jwtProperties.secret().isBlank()) {
            throw new IllegalArgumentException("JWT secret is required");
        }
        if (jwtProperties.issuer() == null || jwtProperties.issuer().isBlank()) {
            throw new IllegalArgumentException("JWT issuer is required");
        }
        keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes for HS256");
        }
        return new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }
}
