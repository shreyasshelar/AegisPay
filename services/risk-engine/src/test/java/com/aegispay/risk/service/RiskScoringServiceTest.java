package com.aegispay.risk.service;

import com.aegispay.common.domain.enums.RiskDecision;
import com.aegispay.common.domain.event.RiskAssessmentRequestedEvent;
import com.aegispay.risk.client.FraudCopilotClient;
import com.aegispay.risk.config.RiskProperties;
import com.aegispay.risk.domain.entity.OutboxEntry;
import com.aegispay.risk.domain.entity.RiskCase;
import com.aegispay.risk.repository.OutboxEntryRepository;
import com.aegispay.risk.repository.RiskCaseRepository;
import com.aegispay.risk.rules.RuleResult;
import com.aegispay.risk.rules.RulesEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskScoringServiceTest {

    @Mock RulesEngine rulesEngine;
    @Mock FraudCopilotClient fraudCopilotClient;
    @Mock RiskCaseRepository riskCaseRepository;
    @Mock OutboxEntryRepository outboxEntryRepository;

    RiskScoringService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new RiskScoringService(rulesEngine, fraudCopilotClient, riskCaseRepository,
                outboxEntryRepository, objectMapper, new RiskProperties());
    }

    @Test
    void assess_approved_no_flags_no_rag_call() {
        UUID txnId = UUID.randomUUID();
        when(riskCaseRepository.findByTransactionId(txnId)).thenReturn(Optional.empty());
        when(rulesEngine.evaluate(any())).thenReturn(new RuleResult(10, List.of()));
        when(riskCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.assess(event(txnId));

        verifyNoInteractions(fraudCopilotClient);

        ArgumentCaptor<RiskCase> caseCaptor = ArgumentCaptor.forClass(RiskCase.class);
        verify(riskCaseRepository).save(caseCaptor.capture());
        assertThat(caseCaptor.getValue().getDecision()).isEqualTo(RiskDecision.APPROVED);

        ArgumentCaptor<OutboxEntry> outboxCaptor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxEntryRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("RiskAssessedEvent");
    }

    @Test
    void assess_rejected_calls_rag_and_persists_explanation() {
        UUID txnId = UUID.randomUUID();
        when(riskCaseRepository.findByTransactionId(txnId)).thenReturn(Optional.empty());
        when(rulesEngine.evaluate(any())).thenReturn(new RuleResult(85, List.of("VELOCITY_EXCEEDED")));
        when(fraudCopilotClient.explain(any(), anyInt(), any())).thenReturn("High velocity detected.");
        when(riskCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.assess(event(txnId));

        ArgumentCaptor<RiskCase> caseCaptor = ArgumentCaptor.forClass(RiskCase.class);
        verify(riskCaseRepository).save(caseCaptor.capture());
        assertThat(caseCaptor.getValue().getDecision()).isEqualTo(RiskDecision.REJECTED);
        assertThat(caseCaptor.getValue().getRagExplanation()).isEqualTo("High velocity detected.");
    }

    @Test
    void assess_idempotent_when_case_already_exists() {
        UUID txnId = UUID.randomUUID();
        when(riskCaseRepository.findByTransactionId(txnId)).thenReturn(Optional.of(new RiskCase()));

        service.assess(event(txnId));

        verifyNoInteractions(rulesEngine, outboxEntryRepository);
    }

    private RiskAssessmentRequestedEvent event(UUID txnId) {
        return RiskAssessmentRequestedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(txnId).sagaId(UUID.randomUUID()).userId(UUID.randomUUID())
                .amount(new BigDecimal("500")).currency("USD").build();
    }
}
