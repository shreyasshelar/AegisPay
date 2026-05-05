package com.aegispay.ai.error;

import com.aegispay.ai.rag.RagPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorResolutionService {

    private final RagPipelineService ragPipeline;

    private static final String SYSTEM_PROMPT = """
            You are a payment error resolution specialist for AegisPay.
            Use the following bank error code documentation to explain and resolve the error.

            CONTEXT FROM KNOWLEDGE BASE:
            {context}

            TASK:
            A payment failed with error code: {errorCode}
            Additional details: {question}

            Provide:
            1. A plain-English explanation of what this error means (1-2 sentences)
            2. The recommended action for the user (retry / contact bank / update payment details / etc.)
            3. Whether this error is typically transient or permanent

            Be concise and user-friendly. Avoid technical jargon.
            """;

    public ErrorResolutionResponse resolve(String errorCode, String errorMessage) {
        String question = "Error code: %s — %s".formatted(errorCode, errorMessage);
        String prompt = SYSTEM_PROMPT
                .replace("{errorCode}", errorCode)
                .replace("{question}", question);

        log.info("Resolving error: code={}", errorCode);
        String explanation = ragPipeline.query("ERROR_RESOLVE", question, prompt);
        return new ErrorResolutionResponse(errorCode, explanation);
    }

    public record ErrorResolutionResponse(String errorCode, String resolution) {}
}
