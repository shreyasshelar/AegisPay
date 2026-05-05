package com.aegispay.e2e;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Generates self-signed JWTs for E2E tests.
 * Services must be started with OAUTH2_ISSUER_URI set to a stub so they
 * accept these tokens without contacting a real IdP.
 */
class TestTokenFactory {

    private static final String SECRET =
            "aegispay-e2e-test-secret-key-minimum-256-bits-required";

    static String customerToken() {
        return buildToken(UUID.randomUUID().toString(), List.of("ROLE_CUSTOMER"));
    }

    static String backOfficeToken() {
        return buildToken(UUID.randomUUID().toString(), List.of("ROLE_BACK_OFFICE"));
    }

    private static String buildToken(String userId, List<String> roles) {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId)
                .claim("aegispay_user_id", userId)
                .claim("roles", roles)
                .issuer("http://localhost:9999")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }
}
