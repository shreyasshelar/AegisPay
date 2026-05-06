package com.aegispay.orchestrator.saga;

import com.aegispay.common.domain.enums.RiskDecision;
import com.aegispay.common.domain.event.*;
import com.aegispay.orchestrator.client.StripePaymentGatewayClient;
import com.aegispay.orchestrator.config.OrchestratorProperties;
import com.aegispay.orchestrator.domain.entity.OutboxEntry;
import com.aegispay.orchestrator.domain.entity.Saga;
import com.aegispay.orchestrator.domain.entity.SagaStep;
import com.aegispay.orchestrator.repository.OutboxEntryRepository;
import com.aegispay.orchestrator.repository.SagaRepository;
import com.aegispay.orchestrator.repository.SagaStepRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentSagaOrchestratorTest {

    @Mock SagaRepository sagaRepository;
    @Mock SagaStepRepository stepRepository;
    @Mock OutboxEntryRepository outboxRepository;
    @Mock StripePaymentGatewayClient gatewayClient;

    PaymentSagaOrchestrator orchestrator;

    UUID txnId = UUID.randomUUID();
    UUID sagaId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID payerId = UUID.randomUUID();
    UUID payeeId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    BigDecimal amount = new BigDecimal("100.00");

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        OrchestratorProperties props = new OrchestratorProperties();
        props.setTimeoutMinutes(10);

        orchestrator = new PaymentSagaOrchestrator(
                sagaRepository, stepRepository, outboxRepository, gatewayClient, objectMapper, props);
    }

    @Test
    void startSaga_creates_saga_and_publishes_reserve_balance() {
        when(sagaRepository.findByTransactionId(txnId)).thenReturn(Optional.empty());
        when(sagaRepository.save(any())).thenAnswer(inv -> { Saga s = inv.getArgument(0); return s; });
        when(stepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.startSaga(initiatedEvent());

        ArgumentCaptor<OutboxEntry> outboxCaptor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("BalanceReserveRequestedEvent");
    }

    @Test
    void startSaga_idempotent_when_saga_already_exists() {
        when(sagaRepository.findByTransactionId(txnId)).thenReturn(Optional.of(saga()));

        orchestrator.startSaga(initiatedEvent());

        verify(sagaRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void onBalanceReserved_advances_to_risk_assessment() {
        Saga saga = saga();
        when(sagaRepository.findByTransactionIdForUpdate(txnId)).thenReturn(Optional.of(saga));
        when(stepRepository.findBySagaIdAndStepName(sagaId, SagaSteps.RESERVE_BALANCE))
                .thenReturn(Optional.of(step(SagaSteps.RESERVE_BALANCE, "IN_PROGRESS")));
        when(stepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.onBalanceReserved(reservedEvent());

        ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("RiskAssessmentRequestedEvent");
    }

    @Test
    void onRiskAssessed_rejected_triggers_rollback_and_fails() {
        Saga saga = saga();
        saga.setAccountId(accountId);
        when(sagaRepository.findByTransactionIdForUpdate(txnId)).thenReturn(Optional.of(saga));
        when(stepRepository.findBySagaIdAndStepName(sagaId, SagaSteps.ASSESS_RISK))
                .thenReturn(Optional.of(step(SagaSteps.ASSESS_RISK, "IN_PROGRESS")));
        when(stepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.onRiskAssessed(riskRejectedEvent());

        ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository, times(2)).save(captor.capture());
        var topics = captor.getAllValues().stream().map(OutboxEntry::getEventType).toList();
        assertThat(topics).contains("BalanceRollbackRequestedEvent", "TransactionFailedEvent");
    }

    @Test
    void onBalanceCommitted_completes_saga_and_publishes_completed() {
        Saga saga = saga();
        when(sagaRepository.findByTransactionIdForUpdate(txnId)).thenReturn(Optional.of(saga));
        when(stepRepository.findBySagaIdAndStepName(sagaId, SagaSteps.COMMIT_BALANCE))
                .thenReturn(Optional.of(step(SagaSteps.COMMIT_BALANCE, "IN_PROGRESS")));
        when(stepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sagaRepository.save(any())).thenAnswer(inv -> {
            Saga s = inv.getArgument(0); s = s; return s;
        });
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.onBalanceCommitted(committedEvent());

        ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("TransactionCompletedEvent");
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
    }

    // ---- factories ----

    private Saga saga() {
        Saga s = new Saga();
        s.setId(sagaId);
        s.setTransactionId(txnId);
        s.setUserId(userId);
        s.setPayerId(payerId);
        s.setPayeeId(payeeId);
        s.setAmount(amount);
        s.setCurrency("USD");
        s.setCurrentStep(SagaSteps.RESERVE_BALANCE);
        s.setStatus(SagaStatus.RUNNING);
        s.setTimeoutAt(Instant.now().plusSeconds(600));
        return s;
    }

    private SagaStep step(String name, String status) {
        SagaStep step = new SagaStep();
        step.setId(UUID.randomUUID());
        step.setStepName(name);
        step.setStepStatus(status);
        step.setAttemptCount(1);
        return step;
    }

    private TransactionInitiatedEvent initiatedEvent() {
        return TransactionInitiatedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(txnId).userId(userId).payerId(payerId).payeeId(payeeId)
                .amount(amount).currency("USD").idempotencyKey("key-1").build();
    }

    private BalanceReservedEvent reservedEvent() {
        return BalanceReservedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(txnId).sagaId(sagaId).accountId(accountId)
                .reservedAmount(amount).availableBalanceAfter(new BigDecimal("400.00")).build();
    }

    private RiskAssessedEvent riskRejectedEvent() {
        return RiskAssessedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(txnId).sagaId(sagaId).userId(userId)
                .riskScore(85).decision(RiskDecision.REJECTED).build();
    }

    private BalanceCommittedEvent committedEvent() {
        return BalanceCommittedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(txnId).sagaId(sagaId).accountId(accountId)
                .committedAmount(amount).availableBalanceAfter(BigDecimal.ZERO)
                .reservedBalanceAfter(BigDecimal.ZERO).build();
    }
}
