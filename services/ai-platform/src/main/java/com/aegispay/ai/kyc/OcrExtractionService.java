package com.aegispay.ai.kyc;

import com.aegispay.ai.audit.AiAuditService;
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
public class OcrExtractionService {

    private static final String SYSTEM_PROMPT = """
            You are a KYC document extraction assistant. Analyze the provided identity document image and extract:
            - fullName: the person's full legal name
            - dateOfBirth: in ISO format YYYY-MM-DD
            - documentNumber: ID/passport/Aadhaar/PAN number
            - documentType: AADHAAR, PAN, PASSPORT, DRIVING_LICENSE, or UNKNOWN
            - expiryDate: in ISO format YYYY-MM-DD if present, else null
            - address: full address if visible, else null

            Respond ONLY with a valid JSON object using these exact keys. Do not include any prose.
            If you cannot read a field clearly, set it to null rather than guessing.
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final AiAuditService auditService;

    public ExtractedDocumentData extract(String base64ImageData, String mimeType) {
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

            return parseExtraction(output);

        } catch (Exception e) {
            error = e.getMessage();
            log.error("OCR extraction failed: {}", e.getMessage(), e);
            throw new RuntimeException("OCR extraction failed: " + e.getMessage(), e);
        } finally {
            long latencyMs = System.currentTimeMillis() - start;
            auditService.log("OCR_EXTRACT", "document image (" + mimeType + ")",
                    output, "claude-sonnet-4-6", latencyMs, error);
        }
    }

    private ExtractedDocumentData parseExtraction(String json) {
        // Strip markdown fences if present
        String cleaned = json.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(cleaned, ExtractedDocumentData.class);
        } catch (Exception e) {
            log.warn("Could not parse OCR JSON response, returning raw: {}", e.getMessage());
            return new ExtractedDocumentData(null, null, null, "UNKNOWN", null, null, cleaned);
        }
    }

    public record ExtractedDocumentData(
            String fullName,
            String dateOfBirth,
            String documentNumber,
            String documentType,
            String expiryDate,
            String address,
            String rawResponse
    ) {}
}
