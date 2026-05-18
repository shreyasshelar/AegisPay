package com.aegispay.ai.kyc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycDocumentService {

    private final OcrExtractionService           ocrExtractionService;
    private final TamperingDetectionService       tamperingDetectionService;
    private final QualityScoreService             qualityScoreService;
    private final DocumentValidationService       documentValidationService;

    /**
     * Full pipeline: quality → tampering → document validation → OCR.
     *
     * @param registeredName Optional: user's registered full name for cross-validation.
     */
    public KycProcessingResult process(String base64ImageData, String mimeType, String registeredName) {
        log.info("Processing KYC document mimeType={} nameProvided={}", mimeType, registeredName != null);

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
                documentValidationService.validate(base64ImageData, mimeType, registeredName);

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
