package com.aegispay.ai.kyc;

import com.aegispay.ai.audit.AiAuditService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;
import java.util.List;

import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class TamperingDetectionService {

    private static final String SYSTEM_PROMPT = """
            You are a document tampering detection specialist. Analyze the identity document image for signs of forgery or manipulation:
            - Font inconsistencies (mixed fonts, irregular character spacing)
            - Pixelation or blurring around edited areas
            - Colour band anomalies (different background hues suggesting paste/overlay)
            - Misalignment of security features (holograms, watermarks, borders)
            - Copy-paste artifacts or duplicate texture regions

            Respond ONLY with a JSON object:
            {
              "tampered": true|false,
              "confidence": 0.0-1.0,
              "indicators": ["list of observed indicators, empty if none"]
            }
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final AiAuditService auditService;

    /**
     * Detect signs of document forgery/tampering in a KYC image.
     *
     * <p>Resilience decorators:
     * <ul>
     *   <li>{@code @CircuitBreaker} — fallback returns {@code tampered=false, confidence=0.0} with
     *       an "ai-unavailable" indicator. A false-negative is safer than blocking legitimate users
     *       during an outage; the {@code DocumentValidationService} provides an independent layer.
     *   <li>{@code @Retry} — 2 retries with exponential backoff.
     * </ul>
     */
    @CircuitBreaker(name = "kyc-ai", fallbackMethod = "detectFallback")
    @Retry(name = "kyc-ai")
    public TamperingResult detect(String base64ImageData, String mimeType) {
        long start = System.currentTimeMillis();
        String output = null;
        String error = null;

        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64ImageData);
            Media imageMedia = new Media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes));

            UserMessage userMessage = UserMessage.builder().text(SYSTEM_PROMPT).media(List.of(imageMedia)).build();

            output = chatClientBuilder.build()
                    .prompt()
                    .messages(userMessage)
                    .call()
                    .content();

            return parseResult(output);

        } catch (Exception e) {
            error = e.getMessage();
            log.error("Tampering detection failed: {}", e.getMessage(), e);
            // Re-throw so @Retry can attempt again and @CircuitBreaker can track the failure.
            throw new RuntimeException("Tampering detection failed: " + e.getMessage(), e);
        } finally {
            long latencyMs = System.currentTimeMillis() - start;
            auditService.log("TAMPERING_DETECT", "document image (" + mimeType + ")",
                    output, "claude-sonnet-4-6", latencyMs, error);
        }
    }

    @SuppressWarnings("unused")   // called reflectively by Resilience4j
    TamperingResult detectFallback(String base64ImageData, String mimeType, Throwable cause) {
        log.warn("Tampering detection fallback triggered (circuit open or retries exhausted): {}",
                cause != null ? cause.getMessage() : "unknown");
        return new TamperingResult(false, 0.0,
                java.util.List.of("ai-unavailable: tampering check bypassed"));
    }

    private TamperingResult parseResult(String json) {
        String cleaned = json.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(cleaned, TamperingResult.class);
        } catch (Exception e) {
            log.warn("Could not parse tampering JSON, defaulting safe: {}", e.getMessage());
            return new TamperingResult(false, 0.0, java.util.List.of("parse-error: " + e.getMessage()));
        }
    }

    public record TamperingResult(boolean tampered, double confidence, java.util.List<String> indicators) {}
}
