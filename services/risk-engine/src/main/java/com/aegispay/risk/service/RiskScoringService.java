package com.aegispay.risk.service;

import com.aegispay.common.domain.enums.RiskDecision;
import com.aegispay.common.domain.event.RiskAssessedEvent;
import com.aegispay.common.domain.event.RiskAssessmentRequestedEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.risk.client.FraudCopilotClient;
import com.aegispay.risk.config.RiskProperties;
import com.aegispay.risk.domain.entity.OutboxEntry;
import com.aegispay.risk.domain.entity.RiskCase;
import com.aegispay.risk.repository.OutboxEntryRepository;
import com.aegispay.risk.repository.RiskCaseRepository;
import com.aegispay.risk.rules.RuleResult;
import com.aegispay.risk.rules.RulesEngine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskScoringService {

    private final RulesEngine rulesEngine;
    private final FraudCopilotClient fraudCopilotClient;
    private final RiskCaseRepository riskCaseRepository;
    private final OutboxEntryRepository outboxEntryRepository;
    private final ObjectMapper objectMapper;
    private final RiskProperties props;

    @Transactional
    public void assess(RiskAssessmentRequestedEvent event) {
        if (riskCaseRepository.findByTransactionId(event.getTransactionId()).isPresent()) {
            log.warn("Risk case already exists for txn={} — idempotent skip", event.getTransactionId());
            return;
        }

        RuleResult ruleResult;
        try {
            ruleResult = rulesEngine.evaluate(event);
        } catch (Exception e) {
            // Rules engine failure must never stall the saga — approve conservatively and log.
            log.error("RulesEngine threw unexpected exception for txn={}: {} — defaulting to APPROVED",
                    event.getTransactionId(), e.getMessage(), e);
            ruleResult = new RuleResult(0, java.util.List.of("RULES_ENGINE_ERROR"));
        }

        RiskDecision decision = resolveDecision(ruleResult.getTotalScore());

        String ragExplanation = null;
        if (!ruleResult.getFlaggedRules().isEmpty()) {
            // FraudCopilotClient already has a @CircuitBreaker fallback — this call is safe.
            ragExplanation = fraudCopilotClient.explain(
                    event.getTransactionId(), ruleResult.getTotalScore(), ruleResult.getFlaggedRules());
        }

        RiskCase riskCase = RiskCase.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .payeeId(event.getPayeeId())
                .riskScore(ruleResult.getTotalScore())
                .decision(decision)
                .ruleFlags(ruleResult.getFlaggedRules())
                .ragExplanation(ragExplanation)
                .build();
        riskCaseRepository.save(riskCase);

        RiskAssessedEvent reply = RiskAssessedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .transactionId(event.getTransactionId())
                .sagaId(event.getSagaId())
                .userId(event.getUserId())
                .riskScore(ruleResult.getTotalScore())
                .decision(decision)
                .flaggedRules(ruleResult.getFlaggedRules())
                .ragExplanation(ragExplanation)
                .build();

        writeOutbox(event.getTransactionId().toString(), reply);
        log.info("Risk assessment: txn={} score={} decision={}", event.getTransactionId(),
                ruleResult.getTotalScore(), decision);
    }

    private RiskDecision resolveDecision(int score) {
        if (score >= props.getScoreThresholdReject()) return RiskDecision.REJECTED;
        if (score >= props.getScoreThresholdApprove()) return RiskDecision.REVIEW;
        return RiskDecision.APPROVED;
    }

    private void writeOutbox(String transactionId, RiskAssessedEvent event) {
        try {
            outboxEntryRepository.save(OutboxEntry.builder()
                    .aggregateId(transactionId)
                    .aggregateType("RiskCase")
                    .eventType("RiskAssessedEvent")
                    .topic(KafkaTopics.RISK_ASSESSED)
                    .messageKey(transactionId)
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize RiskAssessedEvent", e);
        }
    }
}
