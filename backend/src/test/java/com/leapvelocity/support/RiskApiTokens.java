package com.leapvelocity.support;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** Real signed access tokens for the production resource-server filter in focused API tests. */
public final class RiskApiTokens {
    public static final String SECRET = "risk-api-test-secret-at-least-64-bytes-abcdefghijklmnopqrstuvwxyz-123456";
    public static final String ISSUER = "risk-api-test";

    private RiskApiTokens() { }

    public static JWTClaimsSet.Builder claims(long accountId, String role) {
        return new JWTClaimsSet.Builder().subject("42").issuer(ISSUER).claim("username", "risk-test")
                .claim("accountId", accountId).claim("roles", List.of(role))
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300)));
    }

    public static String bearer(long accountId, String role) throws Exception {
        return "Bearer " + sign(claims(accountId, role), SECRET, JWSAlgorithm.HS256);
    }

    public static String sign(JWTClaimsSet.Builder claims, String secret, JWSAlgorithm algorithm) throws Exception {
        var token = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build(), claims.build());
        token.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return token.serialize();
    }
}
