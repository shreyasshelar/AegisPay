package com.aegispay.risk.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FraudCopilotClient {

    private final WebClient aiPlatformWebClient;

    public record FraudExplainRequest(UUID transactionId, int riskScore, List<String> flaggedRules) {}
    public record FraudExplainResponse(String explanation) {}

    @CircuitBreaker(name = "ai-platform", fallbackMethod = "fallbackExplanation")
    public String explain(UUID transactionId, int score, List<String> flags) {
        FraudExplainRequest request = new FraudExplainRequest(transactionId, score, flags);
        FraudExplainResponse response = aiPlatformWebClient.post()
                .uri("/ai/fraud/explain")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FraudExplainResponse.class)
                .block();
        return response != null ? response.explanation() : null;
    }

    String fallbackExplanation(UUID transactionId, int score, List<String> flags, Throwable t) {
        log.warn("Fraud copilot unavailable for txn={}: {}", transactionId, t.getMessage());
        return "AI explanation unavailable. Flagged rules: " + flags;
    }
}
