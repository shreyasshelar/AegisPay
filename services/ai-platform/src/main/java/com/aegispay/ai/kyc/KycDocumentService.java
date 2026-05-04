package com.aegispay.ai.kyc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycDocumentService {

    private final OcrExtractionService ocrExtractionService;
    private final TamperingDetectionService tamperingDetectionService;
    private final QualityScoreService qualityScoreService;

    public KycProcessingResult process(String base64ImageData, String mimeType) {
        log.info("Processing KYC document mimeType={}", mimeType);

        QualityScoreService.QualityResult quality = qualityScoreService.score(base64ImageData, mimeType);

        if (!quality.acceptable()) {
            log.info("Document rejected on quality: {}", quality.rejectionReason());
            return KycProcessingResult.rejected("QUALITY_REJECTED", quality.rejectionReason(), quality, null, null);
        }

        TamperingDetectionService.TamperingResult tamper = tamperingDetectionService.detect(base64ImageData, mimeType);

        if (tamper.tampered() && tamper.confidence() > 0.7) {
            log.warn("Document suspected tampered: confidence={}", tamper.confidence());
            return KycProcessingResult.rejected("TAMPERING_DETECTED",
                    "Document appears tampered (confidence=" + tamper.confidence() + ")", quality, tamper, null);
        }

        OcrExtractionService.ExtractedDocumentData extracted = ocrExtractionService.extract(base64ImageData, mimeType);

        String overallStatus = tamper.tampered() ? "MANUAL_REVIEW" : "APPROVED";
        return KycProcessingResult.success(overallStatus, quality, tamper, extracted);
    }

    public record KycProcessingResult(
            String status,
            String rejectionCode,
            String rejectionReason,
            QualityScoreService.QualityResult quality,
            TamperingDetectionService.TamperingResult tampering,
            OcrExtractionService.ExtractedDocumentData extractedData
    ) {
        static KycProcessingResult success(String status,
                                           QualityScoreService.QualityResult quality,
                                           TamperingDetectionService.TamperingResult tamper,
                                           OcrExtractionService.ExtractedDocumentData extracted) {
            return new KycProcessingResult(status, null, null, quality, tamper, extracted);
        }

        static KycProcessingResult rejected(String code, String reason,
                                            QualityScoreService.QualityResult quality,
                                            TamperingDetectionService.TamperingResult tamper,
                                            OcrExtractionService.ExtractedDocumentData extracted) {
            return new KycProcessingResult("REJECTED", code, reason, quality, tamper, extracted);
        }
    }
}
