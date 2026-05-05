package com.aegispay.ai.fraud;

import com.aegispay.ai.rag.RagPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudCopilotService {

    private final RagPipelineService ragPipeline;

    private static final String SYSTEM_PROMPT = """
            You are a fraud analyst assistant for AegisPay, a payment platform.
            Use the following historical fraud case context to explain a risk decision.

            CONTEXT FROM KNOWLEDGE BASE:
            {context}

            TASK:
            A transaction has been flagged with a risk score of {riskScore}/100.
            Flagged rules: {flaggedRules}
            Transaction ID: {transactionId}

            Provide a concise, clear explanation (2-4 sentences) of why this transaction was flagged,
            referencing patterns from the context where relevant. Focus on actionable insight.
            Do NOT repeat the flagged rule names verbatim — explain what they mean.
            """;

    public String explain(UUID transactionId, int riskScore, List<String> flaggedRules) {
        String question = "Explain risk score %d with flags: %s".formatted(riskScore, flaggedRules);

        String prompt = SYSTEM_PROMPT
                .replace("{riskScore}", String.valueOf(riskScore))
                .replace("{flaggedRules}", flaggedRules.toString())
                .replace("{transactionId}", transactionId.toString())
                .replace("{question}", question);

        log.info("Fraud copilot: txn={} score={} flags={}", transactionId, riskScore, flaggedRules);
        return ragPipeline.query("FRAUD_EXPLAIN", question, prompt);
    }
}
