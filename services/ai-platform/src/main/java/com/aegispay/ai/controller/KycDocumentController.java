package com.aegispay.ai.controller;

import com.aegispay.ai.kyc.KycDocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/kyc")
@RequiredArgsConstructor
public class KycDocumentController {

    private final KycDocumentService kycDocumentService;

    /**
     * Process a KYC document image (base64-encoded) and return quality score,
     * tampering assessment, and extracted data.
     */
    @PostMapping("/process")
    public ResponseEntity<KycDocumentService.KycProcessingResult> process(
            @Valid @RequestBody ProcessRequest request) {
        KycDocumentService.KycProcessingResult result =
                kycDocumentService.process(request.base64ImageData(), request.mimeType());
        return ResponseEntity.ok(result);
    }

    public record ProcessRequest(
            @NotBlank String base64ImageData,
            @NotBlank String mimeType
    ) {}
}
