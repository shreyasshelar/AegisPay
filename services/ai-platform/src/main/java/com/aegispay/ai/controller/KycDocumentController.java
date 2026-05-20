package com.aegispay.ai.controller;

import com.aegispay.ai.kyc.KycDocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/ai/kyc")
@RequiredArgsConstructor
public class KycDocumentController {

    private final KycDocumentService kycDocumentService;

    /**
     * Process a KYC document image and return quality, tampering, validation, and OCR results.
     * <p>
     * {@code registeredName} is optional but strongly recommended: when provided, the AI will
     * cross-check the name printed on the document against the registered account name.
     */
    @PostMapping("/process")
    public ResponseEntity<KycDocumentService.KycProcessingResult> process(
            @Valid @RequestBody ProcessRequest request) {
        KycDocumentService.KycProcessingResult result = kycDocumentService.process(
                request.base64ImageData(),
                request.mimeType(),
                request.registeredName());
        return ResponseEntity.ok(result);
    }

    public record ProcessRequest(
            @NotBlank String base64ImageData,
            @NotBlank String mimeType,
            /** Optional: registered full name for name cross-validation. */
            String registeredName
    ) {}
}
