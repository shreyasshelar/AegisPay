package com.aegispay.user.service;

import com.aegispay.user.domain.entity.KycDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * HTTP client for the AI Platform service.
 *
 * The KYC analysis call is intentionally asynchronous — it submits the job and returns
 * immediately. The AI platform calls back via PATCH /api/v1/users/{id}/kyc/status when
 * analysis is complete, which then drives the KYC state machine forward.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiPlatformClient {

    private final RestClient aiPlatformRestClient;

    /**
     * Submits a KYC document for AI OCR + analysis.
     * Fires-and-forgets — the AI platform will call back asynchronously.
     */
    @Async
    public void submitKycDocument(KycDocument document, String userId) {
        try {
            Map<String, String> request = Map.of(
                "documentId",   document.getId().toString(),
                "userId",       userId,
                "documentType", document.getDocumentType(),
                "documentRef",  document.getDocumentRef()
            );

            aiPlatformRestClient.post()
                    .uri("/api/v1/ai/kyc/process")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("KYC document submitted to AI platform: documentId={} userId={}",
                    document.getId(), userId);

        } catch (RestClientException e) {
            // Fire-and-forget — log the failure; the document stays in PROCESSING state
            // and can be resubmitted if the AI platform doesn't callback within a deadline.
            log.error("Failed to submit KYC document to AI platform: documentId={} error={}",
                    document.getId(), e.getMessage());
        }
    }
}
