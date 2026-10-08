package com.leapvelocity.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class AccountAccessAuthorizer {

    private AccountAccessAuthorizer() {
    }

    public static void requireOwnAccountOrAdmin(Long requestedAccountId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new AccessDeniedException("Account ownership verification requires authentication");
        }

        if (hasRole(authentication, "ROLE_ADMIN")) {
            return;
        }

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            throw new AccessDeniedException("Account ownership verification requires a JWT access token");
        }

        Jwt jwt = jwtAuthenticationToken.getToken();
        Long tokenAccountId = claimAccountId(jwt);
        if (tokenAccountId == null) {
            throw new AccessDeniedException("Account ownership verification requires an accountId claim");
        }

        if (requestedAccountId == null || !requestedAccountId.equals(tokenAccountId)) {
            throw new AccessDeniedException("Cannot access another account");
        }
    }

    private static boolean hasRole(Authentication authentication, String authority) {
        for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
            if (authority.equals(grantedAuthority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private static Long claimAccountId(Jwt jwt) {
        Object accountIdClaim = jwt.getClaims().get("accountId");
        if (accountIdClaim == null) {
            return null;
        }
        if (accountIdClaim instanceof Number number) {
            long value = number.longValue();
            return value > 0 ? value : null;
        }
        if (accountIdClaim instanceof String accountIdText) {
            try {
                long value = Long.parseLong(accountIdText);
                return value > 0 ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}