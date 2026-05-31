package com.aegispay.ai.kyc;

import com.aegispay.ai.config.AiPlatformProperties;
import com.aegispay.ai.controller.KycDocumentController.ProcessRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycDocumentService {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final OcrExtractionService           ocrExtractionService;
    private final TamperingDetectionService       tamperingDetectionService;
    private final QualityScoreService             qualityScoreService;
    private final DocumentValidationService       documentValidationService;
    private final AiPlatformProperties            properties;
    private final ObjectMapper                    objectMapper;

    // The only RestClient bean in this application context — no @Qualifier needed.
    private final RestClient                      userServiceRestClient;

    // ── Synchronous pipeline (kept for backward-compatibility / testing) ──────

    /**
     * Full pipeline without a declared document type hint (backward-compat / testing).
     */
    public KycProcessingResult process(String base64ImageData, String mimeType, String registeredName) {
        return process(base64ImageData, mimeType, registeredName, null);
    }

    /**
     * Full pipeline: quality → tampering → document validation → OCR.
     *
     * @param registeredName       Optional: user's registered full name for cross-validation.
     * @param declaredDocumentType Optional: the document type the user selected in the UI.
     *                             Passed to the validation service as a hint so the AI can
     *                             cross-check the user's claim against the image content.
     */
    public KycProcessingResult process(String base64ImageData, String mimeType,
                                       String registeredName, String declaredDocumentType) {
        log.info("Processing KYC document mimeType={} nameProvided={} declaredType={}",
                mimeType, registeredName != null, declaredDocumentType);

        // ── 1. Image quality ─────────────────────────────────────────────────────
        QualityScoreService.QualityResult quality = qualityScoreService.score(base64ImageData, mimeType);
        if (!quality.acceptable()) {
            log.info("Document rejected on quality: {}", quality.rejectionReason());
            return KycProcessingResult.rejected("QUALITY_REJECTED", quality.rejectionReason(),
                    quality, null, null, null);
        }

        // ── 2. Tampering detection ────────────────────────────────────────────────
        TamperingDetectionService.TamperingResult tamper =
                tamperingDetectionService.detect(base64ImageData, mimeType);
        if (tamper.tampered() && tamper.confidence() > 0.7) {
            log.warn("Document suspected tampered: confidence={}", tamper.confidence());
            return KycProcessingResult.rejected("TAMPERING_DETECTED",
                    "Document appears tampered (confidence=" + tamper.confidence() + ")",
                    quality, tamper, null, null);
        }

        // ── 3. Document validation ────────────────────────────────────────────────
        DocumentValidationService.DocumentValidationResult validation =
                documentValidationService.validate(base64ImageData, mimeType,
                        registeredName, declaredDocumentType);

        if (!validation.overallValid()) {
            String reasons = String.join("; ", validation.failureReasons());
            log.info("Document failed validation: {}", reasons);
            return KycProcessingResult.rejected("DOCUMENT_INVALID", reasons,
                    quality, tamper, null, validation);
        }

        // ── 4. OCR extraction ─────────────────────────────────────────────────────
        OcrExtractionService.ExtractedDocumentData extracted =
                ocrExtractionService.extract(base64ImageData, mimeType);

        // Low-confidence tampering or name mismatch → manual review
        boolean manualReview = tamper.tampered() ||
                Boolean.FALSE.equals(validation.nameMatch());
        String overallStatus = manualReview ? "MANUAL_REVIEW" : "APPROVED";

        return KycProcessingResult.success(overallStatus, quality, tamper, extracted, validation);
    }

    // ── Preliminary callback (synchronous) ───────────────────────────────────

    /**
     * Immediately transitions the user's KYC status to {@code DOCUMENT_SUBMITTED} in
     * User Service before the async pipeline begins.
     *
     * <p>Called synchronously from the controller so that by the time the 202 response
     * reaches the browser, User Service has already set the status — the frontend
     * can refetch and immediately see the "Document received" banner and hide the
     * upload form, preventing duplicate submissions.
     *
     * @param userId       AegisPay domain UUID of the submitting user.
     * @param documentType The document type declared by the user (NATIONAL_ID, PASSPORT, …).
     */
    public void markDocumentSubmitted(UUID userId, String documentType) {
        if (userId == null) {
            log.warn("markDocumentSubmitted skipped — userId is null (no aegispay_user_id JWT claim)");
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId",      null);
        payload.put("documentType",    documentType);
        payload.put("newStatus",       "DOCUMENT_SUBMITTED");
        payload.put("rejectionReason", null);
        payload.put("tamperedFlag",    false);
        payload.put("qualityScore",    null);
        payload.put("extractedData",   null);
        log.info("Sending preliminary DOCUMENT_SUBMITTED callback: userId={}", userId);
        postToUserService(userId, payload);
    }

    // ── Async pipeline ────────────────────────────────────────────────────────

    /**
     * Runs the full KYC pipeline in a Spring-managed async task and calls back
     * User Service when done.
     *
     * <p>This method returns immediately — the caller (controller) sends 202 Accepted.
     * On completion (success or failure), the pipeline calls
     * {@code PATCH /api/v1/users/{userId}/kyc/status} on User Service using
     * the shared {@code X-Internal-Api-Key} header for service-to-service auth.
     * User Service then publishes a {@code KycStatusChangedEvent} → Kafka →
     * Notification Service → WebSocket push to the user.
     *
     * @param userId  AegisPay domain UUID of the submitting user.
     * @param request The document upload request containing base64 image + metadata.
     */
    @Async
    public void processAsync(UUID userId, ProcessRequest request) {
        log.info("KYC async pipeline started: userId={} mimeType={} declaredType={}",
                userId, request.mimeType(), request.documentType());
        try {
            KycProcessingResult result = process(
                    request.base64ImageData(),
                    request.mimeType(),
                    request.registeredName(),
                    request.documentType());

            sendCallback(userId, request.documentType(), result);
            log.info("KYC async pipeline completed: userId={} status={}", userId, result.status());

        } catch (Exception e) {
            log.error("KYC async pipeline failed: userId={} error={}", userId, e.getMessage(), e);
            // Notify the user that processing failed so they can re-upload,
            // rather than leaving them in PENDING with no indication of what happened.
            sendFailureCallback(userId, request.documentType(),
                    "Document processing encountered an error. Please try re-uploading.");
        }
    }

    // ── User Service callback ─────────────────────────────────────────────────

    private void sendCallback(UUID userId, String documentType, KycProcessingResult result) {
        Map<String, Object> payload = buildCallbackPayload(documentType, result);
        postToUserService(userId, payload);
    }

    private void sendFailureCallback(UUID userId, String documentType, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId",    null);
        payload.put("documentType",  documentType);
        payload.put("newStatus",     "REJECTED");
        payload.put("rejectionReason", reason);
        payload.put("tamperedFlag",  false);
        payload.put("qualityScore",  null);
        payload.put("extractedData", null);
        postToUserService(userId, payload);
    }

    private void postToUserService(UUID userId, Map<String, Object> payload) {
        if (userId == null) {
            log.warn("KYC callback skipped — no userId available (user had no aegispay_user_id claim)");
            return;
        }
        try {
            userServiceRestClient.patch()
                    .uri("/api/v1/users/{userId}/kyc/status", userId)
                    .header(INTERNAL_KEY_HEADER, properties.getUserService().getInternalApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("KYC callback sent to user-service: userId={}", userId);
        } catch (RestClientException e) {
            // The user stays in PENDING; they can re-upload.
            // A monitoring alert on user-service error logs will surface this.
            log.error("KYC callback to user-service failed: userId={} error={}", userId, e.getMessage(), e);
        }
    }

    /**
     * Converts a {@link KycProcessingResult} into the flat map that matches the
     * {@code KycStatusUpdateRequest} record on the User Service side.
     * {@code documentId} is deliberately {@code null} because in the async direct-upload
     * flow no {@code KycDocument} row was pre-created by User Service — it creates one
     * inline inside {@code processAiCallback}.
     *
     * <p>The stored {@code documentType} is resolved in this priority order:
     * <ol>
     *   <li>AI-detected type from {@code DocumentValidationResult.documentTypeDetected}
     *       (mapped to User Service naming: AADHAAR→NATIONAL_ID, PAN→PAN_CARD)
     *   <li>User's declared type from the upload request (fallback when AI returns UNKNOWN)
     *   <li>"UNKNOWN" if neither is available
     * </ol>
     * This prevents the user's dropdown selection from overriding the actual document type
     * the AI observed in the image.
     */
    private Map<String, Object> buildCallbackPayload(String declaredDocumentType, KycProcessingResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId",   null);
        payload.put("documentType", resolveDocumentType(declaredDocumentType, result));
        payload.put("newStatus",    result.status());
        payload.put("rejectionReason", result.rejectionReason());
        payload.put("tamperedFlag",
                result.tampering() != null && result.tampering().tampered());
        payload.put("qualityScore",
                result.quality() != null
                        ? BigDecimal.valueOf(result.quality().overallScore())
                        : null);
        // Serialize ExtractedDocumentData → Map<String,Object> for the JSONB column
        payload.put("extractedData", extractedDataToMap(result.extractedData()));
        return payload;
    }

    /**
     * Picks the best document type to store in User Service.
     *
     * <p>Priority: AI-detected (mapped to User Service naming) → user's declared type → "UNKNOWN".
     * The AI-detected type is authoritative because users can mis-select the dropdown.
     */
    private String resolveDocumentType(String declaredDocumentType, KycProcessingResult result) {
        // 1. Try AI-detected type from DocumentValidationResult
        if (result.validation() != null) {
            String detected = result.validation().documentTypeDetected();
            if (detected != null && !detected.isBlank() && !"UNKNOWN".equalsIgnoreCase(detected)) {
                // Map AI naming convention → User Service naming convention
                String mapped = switch (detected.toUpperCase()) {
                    case "AADHAAR" -> "NATIONAL_ID";
                    case "PAN"     -> "PAN_CARD";
                    default        -> detected.toUpperCase(); // PASSPORT, DRIVING_LICENSE — same name
                };
                log.debug("Document type resolved from AI detection: detected={} mapped={}",
                        detected, mapped);
                return mapped;
            }
        }
        // 2. Fall back to user's declared type
        if (declaredDocumentType != null && !declaredDocumentType.isBlank()) {
            log.debug("Document type resolved from user declaration: {}", declaredDocumentType);
            return declaredDocumentType;
        }
        return "UNKNOWN";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractedDataToMap(OcrExtractionService.ExtractedDocumentData data) {
        if (data == null) return null;
        try {
            return objectMapper.convertValue(data, Map.class);
        } catch (Exception e) {
            log.warn("Could not convert extractedData to map: {}", e.getMessage());
            return null;
        }
    }

    // ── Result record ─────────────────────────────────────────────────────────

    public record KycProcessingResult(
            String status,
            String rejectionCode,
            String rejectionReason,
            QualityScoreService.QualityResult quality,
            TamperingDetectionService.TamperingResult tampering,
            OcrExtractionService.ExtractedDocumentData extractedData,
            DocumentValidationService.DocumentValidationResult validation
    ) {
        static KycProcessingResult success(
                String status,
                QualityScoreService.QualityResult quality,
                TamperingDetectionService.TamperingResult tamper,
                OcrExtractionService.ExtractedDocumentData extracted,
                DocumentValidationService.DocumentValidationResult validation) {
            return new KycProcessingResult(status, null, null, quality, tamper, extracted, validation);
        }

        static KycProcessingResult rejected(
                String code, String reason,
                QualityScoreService.QualityResult quality,
                TamperingDetectionService.TamperingResult tamper,
                OcrExtractionService.ExtractedDocumentData extracted,
                DocumentValidationService.DocumentValidationResult validation) {
            return new KycProcessingResult("REJECTED", code, reason, quality, tamper, extracted, validation);
        }
    }
}
