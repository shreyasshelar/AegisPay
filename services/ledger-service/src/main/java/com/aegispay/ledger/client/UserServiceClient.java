package com.aegispay.ledger.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Lightweight HTTP client used by {@link com.aegispay.ledger.controller.LedgerController}
 * to resolve the calling user's stable AegisPay domain UUID before querying the ledger.
 *
 * <h3>Why this is necessary</h3>
 * The {@code accounts} table is keyed by {@code userId} — the AegisPay domain UUID assigned
 * at registration.  The JWT {@code sub} claim is the Keycloak internal UUID, which is different
 * from the AegisPay UUID.  For first-session users (social login or Keycloak-native users whose
 * {@code aegispay_user_id} attribute hasn't been written back to Keycloak yet), the JWT does not
 * carry {@code aegispay_user_id}, so the ledger controller must resolve it by calling the user
 * service's {@code /me} endpoint.
 *
 * <h3>Resolution strategy</h3>
 * <ol>
 *   <li><b>Fast path</b>: {@code aegispay_user_id} claim present in JWT → parse and return (no network call).</li>
 *   <li><b>Slow path</b>: claim absent → {@code GET /api/v1/users/me} with the user's Bearer token.</li>
 *   <li><b>Last resort</b>: user-service unreachable → fall back to {@code jwt.sub} and log a warning.
 *       The ledger will return an empty account list for this session; the user will see zero balance
 *       but no exception will propagate.  Correct UUID is used on the next login after write-back.</li>
 * </ol>
 */
@Slf4j
@Component
public class UserServiceClient {

    private final RestClient restClient;

    public UserServiceClient(
            @Value("${aegispay.user-service.base-url:http://user-service:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    // ── Internal response types ────────────────────────────────────────────────

    /** Subset of {@code UserResponse} we need from the {@code /me} envelope. */
    private record MeUserData(UUID id) {}

    /** {@code ApiResponse<UserResponse>} envelope shape. */
    private record MeEnvelope(boolean success, MeUserData data) {}

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Resolves the stable AegisPay domain UUID for the authenticated user.
     *
     * @param jwt the JWT of the calling user (forwarded as Bearer in the /me call)
     * @return the AegisPay domain UUID; falls back to {@code UUID.fromString(jwt.getSubject())}
     *         only when user-service is unreachable (last resort, may return zero balance for
     *         first-session users until write-back completes)
     */
    public UUID resolveUserId(Jwt jwt) {
        // ── Fast path ────────────────────────────────────────────────────────
        String aeId = jwt.getClaimAsString("aegispay_user_id");
        if (aeId != null && !aeId.isBlank()) {
            try {
                return UUID.fromString(aeId);
            } catch (IllegalArgumentException e) {
                log.warn("resolveUserId: malformed aegispay_user_id='{}' in JWT sub={} — calling /me",
                        aeId, jwt.getSubject());
            }
        }

        // ── Slow path: first-session user (no aegispay_user_id claim yet) ───
        try {
            MeEnvelope envelope = restClient.get()
                    .uri("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue())
                    .retrieve()
                    .body(MeEnvelope.class);

            if (envelope != null && envelope.success()
                    && envelope.data() != null
                    && envelope.data().id() != null) {
                log.debug("resolveUserId: /me resolved userId={} for sub={}",
                        envelope.data().id(), jwt.getSubject());
                return envelope.data().id();
            }

            log.warn("resolveUserId: /me returned empty envelope for sub={} — using JWT subject as fallback",
                    jwt.getSubject());

        } catch (RestClientException e) {
            // Non-fatal: user-service temporarily unavailable.  The last-resort fallback
            // means the ledger returns an empty account list rather than throwing 500.
            log.warn("resolveUserId: /me call failed for sub={} ({}); using JWT subject as last-resort fallback",
                    jwt.getSubject(), e.getMessage());
        }

        // ── Last resort ──────────────────────────────────────────────────────
        // jwt.subject is the Keycloak sub, NOT the AegisPay UUID.  Accounts are keyed by
        // AegisPay UUID, so this will return an empty list — better than throwing a 500.
        // The user will see zero balance until they log out and back in (or until
        // user-service recovers and write-back completes).
        return UUID.fromString(jwt.getSubject());
    }
}
