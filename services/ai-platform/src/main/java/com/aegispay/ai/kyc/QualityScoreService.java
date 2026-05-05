package com.aegispay.ai.kyc;

import com.aegispay.ai.audit.AiAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualityScoreService {

    private static final String SYSTEM_PROMPT = """
            You are a document image quality assessor for KYC. Evaluate the image on:
            - sharpness: is the text readable without blurring? (0.0–1.0)
            - brightness: is the document well-lit, not overexposed or underexposed? (0.0–1.0)
            - crop: is the full document visible with margins, no important edges cut off? (0.0–1.0)
            - glare: are there reflective spots obscuring text? (0.0=severe glare, 1.0=no glare)

            Respond ONLY with a JSON object:
            {
              "overallScore": 0.0-1.0,
              "sharpness": 0.0-1.0,
              "brightness": 0.0-1.0,
              "crop": 0.0-1.0,
              "glare": 0.0-1.0,
              "acceptable": true|false,
              "rejectionReason": "string or null"
            }
            The document is acceptable if overallScore >= 0.6 and no individual dimension < 0.4.
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final AiAuditService auditService;

    public QualityResult score(String base64ImageData, String mimeType) {
        long start = System.currentTimeMillis();
        String output = null;
        String error = null;

        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64ImageData);
            Media imageMedia = new Media(MimeType.valueOf(mimeType), imageBytes);

            UserMessage userMessage = new UserMessage(SYSTEM_PROMPT, imageMedia);

            output = chatClientBuilder.build()
                    .prompt()
                    .messages(userMessage)
                    .call()
                    .content();

            return parseResult(output);

        } catch (Exception e) {
            error = e.getMessage();
            log.error("Quality scoring failed: {}", e.getMessage(), e);
            throw new RuntimeException("Quality scoring failed: " + e.getMessage(), e);
        } finally {
            long latencyMs = System.currentTimeMillis() - start;
            auditService.log("QUALITY_SCORE", "document image (" + mimeType + ")",
                    output, "claude-sonnet-4-6", latencyMs, error);
        }
    }

    private QualityResult parseResult(String json) {
        String cleaned = json.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(cleaned, QualityResult.class);
        } catch (Exception e) {
            log.warn("Could not parse quality JSON: {}", e.getMessage());
            return new QualityResult(0.0, 0.0, 0.0, 0.0, 0.0, false, "parse-error");
        }
    }

    public record QualityResult(
            double overallScore,
            double sharpness,
            double brightness,
            double crop,
            double glare,
            boolean acceptable,
            String rejectionReason
    ) {}
}
