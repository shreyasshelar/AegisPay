package com.aegispay.ai.controller;

import com.aegispay.ai.kyc.KycDocumentService;
import com.aegispay.ai.service.UserLookupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/ai/kyc")
@RequiredArgsConstructor
public class KycDocumentController {

    private final KycDocumentService kycDocumentService;
    private final UserLookupService  userLookupService;

    /**
     * Accepts a KYC document image and starts the AI analysis pipeline in the background.
     *
     * <p>Returns {@code 202 Accepted} immediately — the 4-step vision pipeline
     * (quality → tampering → validation → OCR) can take up to 6 minutes on free-tier
     * providers.  When the pipeline finishes, the AI Platform calls back the User Service
     * ({@code PATCH /api/v1/users/{userId}/kyc/status}) which transitions the KYC state
     * and publishes a {@code KycStatusChangedEvent}.  The Notification Service then
     * delivers a WebSocket push so the frontend updates without polling.
     *
     * <h3>userId resolution</h3>
     * <p>On the first session after a social SSO login (Google, Microsoft), Keycloak issues
     * the access token before {@code auth.ts} has written the {@code aegispay_user_id}
     * attribute back to Keycloak.  In that case this controller resolves the domain UUID by
     * calling {@code GET /api/v1/users/me} on User Service with the caller's own Bearer
     * token.  User Service handles the bootstrap path by looking up by Keycloak {@code sub}.
     * If the lookup fails (User Service unreachable, user not provisioned), the request is
     * rejected with {@code 503 Service Unavailable} rather than silently discarded.
     *
     * <p>{@code registeredName} is optional but recommended: when provided, the AI will
     * cross-check the name printed on the document against the registered account name.
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> process(
            @Valid @RequestBody ProcessRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String aeId = jwt != null ? jwt.getClaimAsString("aegispay_user_id") : null;
        UUID userId = (aeId != null && !aeId.isBlank()) ? UUID.fromString(aeId) : null;

        if (userId == null) {
            // First-session social login: aegispay_user_id not yet written back to Keycloak.
            // Resolve the domain UUID by asking User Service /me with the caller's own Bearer
            // token — User Service handles the bootstrap path transparently.
            log.info("aegispay_user_id absent from JWT; resolving userId via User Service /me "
                    + "(sub={})", jwt != null ? jwt.getSubject() : "null");

            userId = (jwt != null)
                    ? userLookupService.resolveUserId(jwt.getTokenValue()).orElse(null)
                    : null;

            if (userId == null) {
                log.error("KYC submission rejected: userId could not be resolved "
                        + "(User Service unreachable or user not yet provisioned). sub={}",
                        jwt != null ? jwt.getSubject() : "null");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message",
                                "Unable to verify your identity. "
                                + "Please wait a moment and try again."));
            }
            log.info("Resolved userId={} via /me for sub={}", userId,
                    jwt != null ? jwt.getSubject() : "null");
        }

        log.info("KYC document received for async processing: userId={} mimeType={}",
                userId, request.mimeType());

        // Synchronously mark the user as DOCUMENT_SUBMITTED in User Service before
        // returning 202 so the frontend's immediate refetch sees the updated status.
        kycDocumentService.markDocumentSubmitted(userId, request.documentType());

        // Fire-and-forget — Spring's @Async task executor picks this up immediately.
        kycDocumentService.processAsync(userId, request);

        return ResponseEntity.accepted().body(Map.of(
                "message", "Document submitted for KYC processing. "
                        + "You will receive a notification when analysis is complete."
        ));
    }

    public record ProcessRequest(
            @NotBlank String base64ImageData,
            @NotBlank String mimeType,
            /** Document type (NATIONAL_ID, PASSPORT, DRIVING_LICENSE, PAN_CARD). */
            String documentType,
            /** Optional: registered full name for name cross-validation. */
            String registeredName
    ) {}
}
