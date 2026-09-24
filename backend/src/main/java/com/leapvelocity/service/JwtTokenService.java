package com.leapvelocity.service;

import com.leapvelocity.config.JwtProperties;
import com.leapvelocity.dto.response.AuthResponseDto;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class JwtTokenService {

    private static final String TOKEN_TYPE = "Bearer";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public AuthResponseDto issueToken(String username) {
        Instant issuedAt;
        Instant expiresAt;
        String role;
        JwtClaimsSet claims;
        JwsHeader headers;
        String token;

        issuedAt = Instant.now();
        expiresAt = issuedAt.plusSeconds(jwtProperties.expirationMinutes() * 60);
        role = resolveRole(username);

        claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(username)
                .claim("role", role)
                .build();

        headers = JwsHeader.with(MacAlgorithm.HS256).build();
        token = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
        return new AuthResponseDto(TOKEN_TYPE, token, expiresAt.getEpochSecond() - issuedAt.getEpochSecond());
    }

    private String resolveRole(String username) {
        if ("admin".equalsIgnoreCase(username)) {
            return "ADMIN";
        }
        return "TRADER";
    }
}
