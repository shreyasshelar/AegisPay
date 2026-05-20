package com.aegispay.ai.kyc;

import com.aegispay.ai.audit.AiAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.util.Base64;
import java.util.List;

/**
 * Bank-grade document validation using Claude multimodal vision.
 *
 * Checks performed:
 *  - Document type detection
 *  - Format/number validation per document type (Aadhaar 12-digit, PAN ABCDE1234F, Passport, DL)
 *  - Expiry date check (reject expired)
 *  - Age verification (18+ from DOB)
 *  - Security feature presence (seal, hologram, issuing authority, photo)
 *  - Name cross-match against registered user name (if provided)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentValidationService {

    private static final String SYSTEM_PROMPT = """
            You are a bank-grade KYC document validator. Today's date is 2026-05-18.
            Analyze the provided government identity document image and perform these checks:

            DOCUMENT TYPES AND FORMAT RULES:
            - AADHAAR: 12-digit numeric number (may appear as XXXX XXXX XXXX). Issuer: UIDAI.
            - PAN: Exactly 10 chars: 5 uppercase letters + 4 digits + 1 uppercase letter (e.g. ABCDE1234F). Issuer: Income Tax Dept, India.
            - PASSPORT: 8 alphanumeric characters starting with a letter (Indian). Must have visible MRZ (machine-readable zone) at bottom with 2 lines of 44 chars. Issuer: Ministry of External Affairs.
            - DRIVING_LICENSE: State code (2 uppercase letters) + RTO code (2 digits) + year (2-4 digits) + 7-digit sequence. Issuer: RTO / Transport Authority.

            Respond ONLY with a valid JSON object using these exact keys (no prose, no markdown fences):
            {
              "documentTypeDetected": "AADHAAR|PAN|PASSPORT|DRIVING_LICENSE|UNKNOWN",
              "formatValid": true|false,
              "formatDetails": "brief explanation of why format is valid or not",
              "notExpired": true|false|null,
              "ageVerified": true|false|null,
              "securityFeaturesPresent": true|false,
              "missingSecurityFeatures": ["list of missing features, empty if all present"],
              "nameMatch": true|false|null,
              "nameMatchDetails": "brief note on match or mismatch",
              "issuingAuthorityVisible": true|false,
              "photoPresent": true|false,
              "extractedDocumentNumber": "document number as printed or null",
              "extractedExpiry": "YYYY-MM-DD or null",
              "extractedDob": "YYYY-MM-DD or null",
              "extractedName": "full name as printed or null",
              "overallValid": true|false,
              "failureReasons": ["list of rejection reasons, empty if overallValid is true"]
            }

            Rules for overallValid=false:
            - formatValid=false
            - notExpired=false (document is expired)
            - ageVerified=false (person is under 18)
            - photoPresent=false (no photo on document)
            - issuingAuthorityVisible=false

            nameMatch=false alone → overallValid=false (name mismatch is critical for bank KYC)
            securityFeaturesPresent=false → overallValid=false

            Set nameMatch=null if no registeredName was provided.
            Set notExpired=null if expiry date is not applicable or not visible.
            Set ageVerified=null if date of birth is not visible on document.
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final AiAuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * Validate a KYC document image against bank-grade checks.
     *
     * <p>Resilience decorators:
     * <ul>
     *   <li>{@code @CircuitBreaker} — fallback returns {@code safeFail()} so the KYC pipeline
     *       routes to MANUAL_REVIEW rather than crashing with a 500.
     *   <li>{@code @Retry} — 2 retries with exponential backoff before opening circuit.
     * </ul>
     */
    @CircuitBreaker(name = "kyc-ai", fallbackMethod = "validateFallback")
    @Retry(name = "kyc-ai")
    public DocumentValidationResult validate(
            String base64ImageData,
            String mimeType,
            String registeredName) {

        long start = System.currentTimeMillis();
        String output = null;
        String error = null;

        String prompt = buildPrompt(registeredName);

        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64ImageData);
            Media imageMedia = new Media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes));

            UserMessage userMessage = UserMessage.builder()
                    .text(prompt)
                    .media(List.of(imageMedia))
                    .build();

            output = chatClientBuilder.build()
                    .prompt()
                    .messages(userMessage)
                    .call()
                    .content();

            return parseResult(output);

        } catch (Exception e) {
            error = e.getMessage();
            log.error("Document validation failed: {}", e.getMessage(), e);
            // Re-throw so @Retry can attempt again and @CircuitBreaker can track the failure.
            throw new RuntimeException("Document validation failed: " + e.getMessage(), e);
        } finally {
            long latencyMs = System.currentTimeMillis() - start;
            auditService.log("DOC_VALIDATE", "document image (" + mimeType + ")",
                    output, "claude-sonnet-4-6", latencyMs, error);
        }
    }

    @SuppressWarnings("unused")   // called reflectively by Resilience4j
    DocumentValidationResult validateFallback(String base64ImageData, String mimeType,
                                              String registeredName, Throwable cause) {
        log.warn("Document validation fallback triggered (circuit open or retries exhausted): {}",
                cause != null ? cause.getMessage() : "unknown");
        return DocumentValidationResult.safeFail(
                "AI validation service temporarily unavailable — document routed to manual review");
    }

    private String buildPrompt(String registeredName) {
        if (registeredName != null && !registeredName.isBlank()) {
            return SYSTEM_PROMPT + "\n\nRegistered name to cross-check: \"" + registeredName + "\"";
        }
        return SYSTEM_PROMPT + "\n\nNo registeredName provided — set nameMatch to null.";
    }

    private DocumentValidationResult parseResult(String json) {
        String cleaned = json == null ? "{}" : json.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
        }
        try {
            return objectMapper.readValue(cleaned, DocumentValidationResult.class);
        } catch (Exception e) {
            log.warn("Could not parse validation JSON, defaulting to safe-fail: {}", e.getMessage());
            return DocumentValidationResult.safeFail("parse-error: " + e.getMessage());
        }
    }

    public record DocumentValidationResult(
            String documentTypeDetected,
            boolean formatValid,
            String formatDetails,
            Boolean notExpired,
            Boolean ageVerified,
            boolean securityFeaturesPresent,
            List<String> missingSecurityFeatures,
            Boolean nameMatch,
            String nameMatchDetails,
            boolean issuingAuthorityVisible,
            boolean photoPresent,
            String extractedDocumentNumber,
            String extractedExpiry,
            String extractedDob,
            String extractedName,
            boolean overallValid,
            List<String> failureReasons
    ) {
        static DocumentValidationResult safeFail(String reason) {
            return new DocumentValidationResult(
                    "UNKNOWN", false, reason, null, null,
                    false, List.of(), null, null,
                    false, false, null, null, null, null,
                    false, List.of(reason)
            );
        }
    }
}
