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
 * Lightweight HTTP client used by {@link com.aegispay.transaction.service.TransactionService}
 * to verify the payer's KYC status before initiating a transaction.
 *
 * <p>Called synchronously (blocking) in the create transaction path. The circuit breaker
 * opens after repeated failures so a user-service outage doesn't block all payments —
 * the fallback allows the transaction through but logs a warning.
 *
 * <p>The call goes service-to-service (not via the API Gateway) to avoid the gateway's
 * rate-limiting applying to internal calls.
 */
@Slf4j
@Component
public class UserServiceClient {

    private final WebClient webClient;

    public UserServiceClient(
            @Value("${aegispay.user-service.base-url:http://user-service:8081}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    /** Minimal projection — only fields needed for the KYC gate. */
    public record UserKycProjection(String kycStatus) {}

    /** Wrapper matching ApiResponse<UserKycProjection> envelope. */
    record KycApiResponse(boolean success, UserKycProjection data) {}

    /**
     * Returns the KYC status string for the given userId.
     * Falls back to {@code "UNKNOWN"} if user-service is unavailable (circuit open).
     */
    @CircuitBreaker(name = "user-service-kyc", fallbackMethod = "kycStatusFallback")
    public String getKycStatus(UUID userId) {
        String bearer = resolveBearer();
        KycApiResponse response = webClient.get()
                .uri("/api/v1/users/{id}/kyc-status", userId)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, bearer); })
                .retrieve()
                .bodyToMono(KycApiResponse.class)
                .block();
        return (response != null && response.data() != null) ? response.data().kycStatus() : "UNKNOWN";
    }

    private String resolveBearer() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return "Bearer " + jwt.getTokenValue();
        }
        return null;
    }

    @SuppressWarnings("unused")
    String kycStatusFallback(UUID userId, Throwable cause) {
        log.warn("UserServiceClient KYC check fallback for userId={}: {} — allowing transaction through",
                userId, cause.getMessage());
        return "UNKNOWN";   // unknown → allow through; risk engine applies secondary check
    }

    /**
     * Checks KYC status and throws if the user is not allowed to send money.
     * <ul>
     *   <li>APPROVED    → allowed
     *   <li>MANUAL_REVIEW → allowed (pending agent review, amount gated by risk engine)
     *   <li>UNKNOWN     → allowed (user-service fallback; risk engine secondary check applies)
     *   <li>PENDING / DOCUMENT_SUBMITTED / AI_PROCESSING → blocked
     *   <li>REJECTED    → blocked
     * </ul>
     */
    public void assertKycAllowsTransaction(UUID userId) {
        String status = getKycStatus(userId);
        switch (status) {
            case "APPROVED", "MANUAL_REVIEW", "UNKNOWN" -> { /* allowed */ }
            case "REJECTED" -> throw new AegisPayException(
                    "KYC_REJECTED",
                    "Your identity verification was rejected. You cannot send money. Please contact support.",
                    HttpStatus.FORBIDDEN);
            default -> throw new AegisPayException(
                    "KYC_INCOMPLETE",
                    "Please complete identity verification (KYC) before sending money. Current status: " + status,
                    HttpStatus.FORBIDDEN);
        }
    }
}
