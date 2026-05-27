package com.aegispay.transaction.client;

import com.aegispay.common.domain.exception.AegisPayException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

/**
 * Internal HTTP client used by {@link com.aegispay.transaction.service.TransactionService}
 * to validate the payer's identity and KYC status before creating a transaction, and to
 * verify the payee is a registered AegisPay user before the saga starts.
 *
 * <h3>Design rationale — why we call {@code /me} instead of {@code /{userId}/kyc-status}</h3>
 *
 * Two bugs existed in the previous design that caused "User not found" for real users:
 * <ol>
 *   <li><b>Wrong lookup direction on /{userId}/kyc-status</b>: when the caller's JWT
 *       carried {@code aegispay_user_id}, the user-service {@code getKycStatus} handler
 *       called {@code getByExternalId(userId)} — but {@code users.external_id} stores the
 *       Keycloak sub, not the AegisPay domain UUID.  The lookup always returned 404.</li>
 *   <li><b>Split-identity in transaction records</b>: for social users on first session
 *       (JWT has no {@code aegispay_user_id} yet), {@code TransactionController} fell back to
 *       {@code jwt.getSubject()} (Keycloak sub) as the {@code userId}.  Once Keycloak
 *       attribute write-back completed and the user received a new token with
 *       {@code aegispay_user_id}, subsequent transactions were stored with the AegisPay UUID —
 *       splitting the transaction history across two different IDs.</li>
 * </ol>
 *
 * <p>Using {@code GET /api/v1/users/me} fixes both:
 * <ul>
 *   <li>The user-service {@code /me} endpoint resolves correctly for all login types — it
 *       uses {@code getById} when {@code aegispay_user_id} is present, and
 *       {@code getByExternalId(sub)} as the fallback for first-session social users.</li>
 *   <li>The response always contains the AegisPay domain UUID ({@code id} field), so
 *       transactions are always stored with the stable internal identifier regardless of
 *       whether write-back has completed.</li>
 * </ul>
 */
@Slf4j
@Component
public class UserServiceClient {

    private final WebClient webClient;

    public UserServiceClient(
            @Value("${aegispay.user-service.base-url:http://user-service:8081}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    // ── Data types ─────────────────────────────────────────────────────────────

    /**
     * Resolved identity + KYC status for the currently authenticated user.
     * Returned from {@link #resolveCurrentUser()}; both values come from a single
     * {@code /me} call so no second round-trip is needed.
     *
     * @param id        the AegisPay domain UUID (primary key); {@code null} only when the
     *                  circuit breaker is open (user-service unavailable)
     * @param kycStatus KYC status string; {@code "UNKNOWN"} when unavailable
     */
    public record UserMeInfo(UUID id, String kycStatus) {
        /** Sentinel used by the circuit-breaker fallback when user-service is down. */
        public static final UserMeInfo UNKNOWN = new UserMeInfo(null, "UNKNOWN");
    }

    /** Subset of the {@code ApiResponse<UserResponse>} envelope we need from {@code /me}. */
    private record MeUserData(UUID id, String kycStatus) {}
    private record MeEnvelope(boolean success, MeUserData data) {}

    /** Subset of the {@code ApiResponse<T>} envelope for the payee existence check. */
    private record ExistEnvelope(boolean success) {}

    // ── Core resolution ────────────────────────────────────────────────────────

    /**
     * Resolves the calling user's AegisPay UUID and KYC status via
     * {@code GET /api/v1/users/me} (forwarding the user's Bearer token).
     *
     * <p>Wrapped in a circuit breaker: if user-service is unavailable, returns
     * {@link UserMeInfo#UNKNOWN} so a temporary outage does not block all payments.
     * KYC status {@code "UNKNOWN"} is treated as allowed by {@link #assertKycStatus}.
     */
    @CircuitBreaker(name = "user-service-kyc", fallbackMethod = "resolveCurrentUserFallback")
    public UserMeInfo resolveCurrentUser() {
        String bearer = resolveBearer();
        if (bearer == null) {
            log.warn("resolveCurrentUser: no Bearer token in SecurityContext — returning UNKNOWN");
            return UserMeInfo.UNKNOWN;
        }

        MeEnvelope envelope = webClient.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .retrieve()
                .bodyToMono(MeEnvelope.class)
                .block();

        if (envelope != null && envelope.success() && envelope.data() != null) {
            log.debug("resolveCurrentUser: id={} kycStatus={}",
                    envelope.data().id(), envelope.data().kycStatus());
            return new UserMeInfo(envelope.data().id(), envelope.data().kycStatus());
        }

        log.warn("resolveCurrentUser: /me returned empty or error envelope — returning UNKNOWN");
        return UserMeInfo.UNKNOWN;
    }

    @SuppressWarnings("unused")
    UserMeInfo resolveCurrentUserFallback(Throwable cause) {
        log.warn("resolveCurrentUser circuit-breaker fallback: {} — allowing through with UNKNOWN status",
                cause.getMessage());
        return UserMeInfo.UNKNOWN;
    }

    /**
     * Derives the payer's stable AegisPay domain UUID using a two-tier strategy:
     *
     * <ol>
     *   <li><b>Fast path</b> (no network call): if {@code aegispay_user_id} is already in the JWT
     *       (write-back completed), parse and return it directly.</li>
     *   <li><b>Slow path</b> (one {@code /me} call): social users on first session whose JWT
     *       doesn't have {@code aegispay_user_id} yet → call {@link #resolveCurrentUser()}
     *       to get the UUID assigned during registration.</li>
     *   <li><b>Last resort</b>: if user-service is also unreachable, fall back to the Keycloak
     *       sub. This <em>may</em> cause a transaction list split if write-back later completes,
     *       but it is preferable to rejecting the payment entirely during an outage.</li>
     * </ol>
     */
    public UUID resolveUserId(Jwt jwt) {
        String aeId = jwt.getClaimAsString("aegispay_user_id");
        if (aeId != null && !aeId.isBlank()) {
            try {
                return UUID.fromString(aeId);
            } catch (IllegalArgumentException e) {
                log.warn("resolveUserId: malformed aegispay_user_id claim '{}' — falling to /me", aeId);
            }
        }

        // No valid aegispay_user_id in JWT: call /me to resolve.
        UserMeInfo me = resolveCurrentUser();
        if (me.id() != null) {
            return me.id();
        }

        // user-service unavailable AND no JWT claim: last resort.
        log.warn("resolveUserId: user-service unavailable and aegispay_user_id absent from JWT; "
                + "falling back to JWT subject={} — transaction userId may split if write-back completes later",
                jwt.getSubject());
        return UUID.fromString(jwt.getSubject());
    }

    // ── KYC gate ───────────────────────────────────────────────────────────────

    /**
     * Validates KYC status and throws {@link AegisPayException} if the user is not allowed
     * to send money.
     *
     * <ul>
     *   <li>{@code APPROVED}     → allowed</li>
     *   <li>{@code MANUAL_REVIEW} → allowed (pending agent review; risk engine applies secondary check)</li>
     *   <li>{@code UNKNOWN}      → allowed (user-service fallback; risk engine applies secondary check)</li>
     *   <li>{@code REJECTED}     → blocked with {@code KYC_REJECTED} / HTTP 403</li>
     *   <li>Any other status     → blocked with {@code KYC_INCOMPLETE} / HTTP 403</li>
     * </ul>
     */
    public void assertKycStatus(String kycStatus) {
        switch (kycStatus) {
            case "APPROVED", "MANUAL_REVIEW", "UNKNOWN" -> { /* allowed */ }
            case "REJECTED" -> throw new AegisPayException(
                    "KYC_REJECTED",
                    "Your identity verification was rejected. You cannot send money. "
                    + "Please contact support.",
                    HttpStatus.FORBIDDEN);
            default -> throw new AegisPayException(
                    "KYC_INCOMPLETE",
                    "Please complete identity verification (KYC) before sending money. "
                    + "Current status: " + kycStatus,
                    HttpStatus.FORBIDDEN);
        }
    }

    // ── Payee existence gate ───────────────────────────────────────────────────

    /**
     * Verifies the payee is a registered AegisPay user (by internal UUID / primary key).
     *
     * <p>Throws {@link AegisPayException} with code {@code PAYEE_NOT_FOUND} (HTTP 422)
     * if the payeeId does not correspond to any registered user.  This prevents the saga from
     * starting — and Stripe from being charged — when the recipient has no ledger account.
     *
     * <p>Falls back silently (allows through) when user-service is unavailable so a temporary
     * outage does not block all outgoing payments; the ledger service provides a second-layer
     * defence-in-depth check for non-existent payee accounts.
     */
    @CircuitBreaker(name = "user-service-kyc", fallbackMethod = "assertPayeeExistsFallback")
    public void assertPayeeExists(UUID payeeId) {
        String bearer = resolveBearer();
        try {
            webClient.get()
                    .uri("/api/v1/users/{id}/exists", payeeId)
                    .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, bearer); })
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            // 2xx → user exists, nothing to do
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new AegisPayException(
                        "PAYEE_NOT_FOUND",
                        "Recipient not found. Please verify the payee ID and try again.",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            throw e; // re-throw so the circuit breaker counts it
        }
    }

    @SuppressWarnings("unused")
    void assertPayeeExistsFallback(UUID payeeId, Throwable cause) {
        log.warn("assertPayeeExists circuit-breaker fallback for payeeId={}: {} — allowing through",
                payeeId, cause.getMessage());
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private String resolveBearer() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return "Bearer " + jwt.getTokenValue();
        }
        return null;
    }
}
