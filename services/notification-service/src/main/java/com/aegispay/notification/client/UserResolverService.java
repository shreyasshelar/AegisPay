package com.aegispay.notification.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Resolves the AegisPay domain UUID for the currently authenticated user.
 *
 * <p>Problem: social login users (Google, GitHub, etc.) may not have the
 * {@code aegispay_user_id} claim in their JWT yet. This happens when
 * {@link com.aegispay.user.service.KeycloakAdminService#writeUserAttributes} ran
 * asynchronously but the user hasn't re-logged-in to get a refreshed token carrying
 * the new claim.
 *
 * <p>In that case, falling back to {@code jwt.getSubject()} (the Keycloak internal UUID)
 * would query for the wrong userId — because notifications are stored against the
 * AegisPay domain UUID (from Kafka events), not the Keycloak sub.
 *
 * <p>Solution: call {@code GET /api/v1/users/me} on the User Service, forwarding the
 * caller's Bearer token.  The User Service's {@code /me} endpoint performs an
 * {@code getByExternalId(sub)} lookup as a fallback, always returning the correct
 * AegisPay UUID regardless of whether the JWT claim has been set yet.
 */
@Slf4j
@Service
public class UserResolverService {

    private final RestClient restClient;

    public UserResolverService(
            @Value("${aegispay.user-service.base-url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    // ── Response envelope types ───────────────────────────────────────────────

    private record UserData(String id, String kycStatus) {}
    private record MeEnvelope(boolean success, UserData data) {}

    // ── Core method ───────────────────────────────────────────────────────────

    /**
     * Returns the AegisPay domain userId for the given JWT.
     *
     * <ol>
     *   <li><b>Fast path</b>: if {@code aegispay_user_id} claim is present and valid → return it
     *       immediately (no HTTP call).</li>
     *   <li><b>Slow path</b>: JWT claim absent (first-session social user) → call
     *       {@code GET /api/v1/users/me} forwarding the Bearer token; User Service
     *       resolves by Keycloak sub → returns AegisPay UUID.</li>
     *   <li><b>Last resort</b>: if User Service is also unavailable → fall back to
     *       {@code jwt.getSubject()}.  The query will return 0 results but at least
     *       the service remains available.</li>
     * </ol>
     *
     * @param jwt the caller's JWT (never null)
     * @return AegisPay domain userId string
     */
    public String resolveUserId(Jwt jwt) {
        // ── Fast path ─────────────────────────────────────────────────────────
        String claimId = jwt.getClaimAsString("aegispay_user_id");
        if (claimId != null && !claimId.isBlank()) {
            return claimId;
        }

        // ── Slow path — call /me ──────────────────────────────────────────────
        try {
            MeEnvelope envelope = restClient.get()
                    .uri("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue())
                    .retrieve()
                    .body(MeEnvelope.class);

            if (envelope != null && envelope.success()
                    && envelope.data() != null
                    && envelope.data().id() != null
                    && !envelope.data().id().isBlank()) {
                log.debug("resolveUserId: resolved via /me: sub={} aegisId={}",
                        jwt.getSubject(), envelope.data().id());
                return envelope.data().id();
            }

            log.warn("resolveUserId: /me returned empty envelope for sub={}", jwt.getSubject());
        } catch (RestClientException e) {
            log.warn("resolveUserId: user-service /me unavailable for sub={}: {}",
                    jwt.getSubject(), e.getMessage());
        }

        // ── Last resort ───────────────────────────────────────────────────────
        log.warn("resolveUserId: falling back to JWT subject for sub={} — "
                + "notification query may return 0 results until Keycloak attribute is set",
                jwt.getSubject());
        return jwt.getSubject();
    }
}
