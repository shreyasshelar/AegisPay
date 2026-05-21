package com.aegispay.ai.kyc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KycDocumentServiceTest {

    private OcrExtractionService ocrService;
    private TamperingDetectionService tamperService;
    private QualityScoreService qualityService;
    private DocumentValidationService validationService;
    private KycDocumentService kycService;

    private static final String FAKE_B64 = "aGVsbG8="; // "hello" base64
    private static final String MIME = "image/jpeg";

    private static final DocumentValidationService.DocumentValidationResult VALID_DOC =
            new DocumentValidationService.DocumentValidationResult(
                    "PASSPORT", true, "format ok", true, true,
                    true, java.util.List.of(), null, null,
                    true, true, "ABC123456", "2030-01-15", "1990-01-15", "John Doe",
                    true, java.util.List.of());

    @BeforeEach
    void setup() {
        ocrService = mock(OcrExtractionService.class);
        tamperService = mock(TamperingDetectionService.class);
        qualityService = mock(QualityScoreService.class);
        validationService = mock(DocumentValidationService.class);
        kycService = new KycDocumentService(ocrService, tamperService, qualityService, validationService);
    }

    @Test
    void process_returns_approved_on_clean_document() {
        when(qualityService.score(anyString(), anyString()))
                .thenReturn(new QualityScoreService.QualityResult(0.85, 0.9, 0.8, 0.85, 0.9, true, null));
        when(tamperService.detect(anyString(), anyString()))
                .thenReturn(new TamperingDetectionService.TamperingResult(false, 0.05, List.of()));
        when(validationService.validate(anyString(), anyString(), any()))
                .thenReturn(VALID_DOC);
        when(ocrService.extract(anyString(), anyString()))
                .thenReturn(new OcrExtractionService.ExtractedDocumentData(
                        "John Doe", "1990-01-15", "ABC123456", "PASSPORT", "2030-01-15", null, null));

        KycDocumentService.KycProcessingResult result = kycService.process(FAKE_B64, MIME, null);

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.extractedData().fullName()).isEqualTo("John Doe");
        assertThat(result.rejectionCode()).isNull();
    }

    @Test
    void process_returns_rejected_when_quality_too_low() {
        when(qualityService.score(anyString(), anyString()))
                .thenReturn(new QualityScoreService.QualityResult(0.4, 0.3, 0.5, 0.4, 0.6, false, "image too blurry"));

        KycDocumentService.KycProcessingResult result = kycService.process(FAKE_B64, MIME, null);

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.rejectionCode()).isEqualTo("QUALITY_REJECTED");
        verifyNoInteractions(tamperService, validationService, ocrService);
    }

    @Test
    void process_returns_rejected_on_high_confidence_tampering() {
        when(qualityService.score(anyString(), anyString()))
                .thenReturn(new QualityScoreService.QualityResult(0.9, 0.9, 0.9, 0.9, 0.9, true, null));
        when(tamperService.detect(anyString(), anyString()))
                .thenReturn(new TamperingDetectionService.TamperingResult(true, 0.92, List.of("font mismatch")));

        KycDocumentService.KycProcessingResult result = kycService.process(FAKE_B64, MIME, null);

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.rejectionCode()).isEqualTo("TAMPERING_DETECTED");
        verifyNoInteractions(validationService, ocrService);
    }

    @Test
    void process_routes_to_manual_review_on_low_confidence_tampering() {
        when(qualityService.score(anyString(), anyString()))
                .thenReturn(new QualityScoreService.QualityResult(0.85, 0.9, 0.8, 0.85, 0.9, true, null));
        when(tamperService.detect(anyString(), anyString()))
                .thenReturn(new TamperingDetectionService.TamperingResult(true, 0.5, List.of("minor colour band")));
        when(validationService.validate(anyString(), anyString(), any()))
                .thenReturn(VALID_DOC);
        when(ocrService.extract(anyString(), anyString()))
                .thenReturn(new OcrExtractionService.ExtractedDocumentData(
                        "Jane Smith", "1985-03-22", "XY9876543", "AADHAAR", null, null, null));

        KycDocumentService.KycProcessingResult result = kycService.process(FAKE_B64, MIME, null);

        assertThat(result.status()).isEqualTo("MANUAL_REVIEW");
    }
}
