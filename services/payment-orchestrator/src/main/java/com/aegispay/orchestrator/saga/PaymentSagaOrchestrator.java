package com.aegispay.orchestrator.saga;

import com.aegispay.common.domain.event.*;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.orchestrator.client.StripePaymentGatewayClient;
import com.aegispay.orchestrator.config.OrchestratorProperties;
import com.aegispay.orchestrator.domain.entity.OutboxEntry;
import com.aegispay.orchestrator.domain.entity.Saga;
import com.aegispay.orchestrator.domain.entity.SagaStep;
import com.aegispay.orchestrator.repository.OutboxEntryRepository;
import com.aegispay.orchestrator.repository.SagaRepository;
import com.aegispay.orchestrator.repository.SagaStepRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.aegispay.orchestrator.saga.SagaSteps.*;
import static com.aegispay.orchestrator.saga.SagaStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSagaOrchestrator {

    private final SagaRepository sagaRepository;
    private final SagaStepRepository stepRepository;
    private final OutboxEntryRepository outboxRepository;
    @SuppressWarnings("unused")   // injected for future direct-call use; primary path via Kafka consumer
    private final StripePaymentGatewayClient gatewayClient;
    private final ObjectMapper objectMapper;
    private final OrchestratorProperties properties;

    @Transactional
    public void startSaga(TransactionInitiatedEvent event) {
        if (sagaRepository.findByTransactionId(event.getTransactionId()).isPresent()) {
            log.warn("Saga already exists for txn={} — idempotent skip", event.getTransactionId());
            return;
        }

        Saga saga = Saga.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .payerId(event.getPayerId())
                .payeeId(event.getPayeeId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .currentStep(RESERVE_BALANCE)
                .status(RUNNING)
                .timeoutAt(Instant.now().plus(properties.getTimeoutMinutes(), ChronoUnit.MINUTES))
                .build();

        sagaRepository.save(saga);
        createStep(saga, RESERVE_BALANCE, "IN_PROGRESS");
        log.info("Saga started: sagaId={} txn={}", saga.getId(), saga.getTransactionId());

        publishReserveBalance(saga);
    }

    @Transactional
    public void onBalanceReserved(BalanceReservedEvent event) {
        Saga saga = requireSagaForUpdate(event.getTransactionId());
        if (!isStepIdempotent(saga, RESERVE_BALANCE, "IN_PROGRESS")) return;

        saga.setAccountId(event.getAccountId());
        completeStep(saga, RESERVE_BALANCE);

        saga.setCurrentStep(ASSESS_RISK);
        createStep(saga, ASSESS_RISK, "IN_PROGRESS");
        sagaRepository.save(saga);

        publishRiskAssessment(saga);
    }

    @Transactional
    public void onBalanceReserveFailed(BalanceReserveFailedEvent event) {
        Saga saga = requireSagaForUpdate(event.getTransactionId());
        if (!isStepIdempotent(saga, RESERVE_BALANCE, "IN_PROGRESS")) return;

        failStep(saga, RESERVE_BALANCE, event.getFailureReason());
        failSaga(saga, "Balance reserve failed: " + event.getFailureReason());
        publishTransactionFailed(saga, "INSUFFICIENT_FUNDS", saga.getFailureReason());
    }

    @Transactional
    public void onRiskAssessed(RiskAssessedEvent event) {
        Saga saga = requireSagaForUpdate(event.getTransactionId());
        if (!isStepIdempotent(saga, ASSESS_RISK, "IN_PROGRESS")) return;

        if (event.getDecision().name().equals("REJECTED")) {
            failStep(saga, ASSESS_RISK, "Risk rejected: score=" + event.getRiskScore());
            failSaga(saga, "Transaction rejected by risk engine: score=" + event.getRiskScore());
            // No publishTransactionFailed here — onBalanceRolledBack emits the single
            // terminal TRANSACTION_FAILED event after rollback completes.
            publishRollbackBalance(saga, saga.getFailureReason());
            return;
        }

        completeStep(saga, ASSESS_RISK);
        saga.setCurrentStep(PROCESS_PAYMENT);
        createStep(saga, PROCESS_PAYMENT, "IN_PROGRESS");
        sagaRepository.save(saga);

        publishProcessPayment(saga);
    }

    @Transactional
    public void onPaymentProcessed(PaymentProcessedEvent event) {
        Saga saga = requireSagaForUpdate(event.getTransactionId());
        if (!isStepIdempotent(saga, PROCESS_PAYMENT, "IN_PROGRESS")) return;

        if (!event.isSuccess()) {
            failStep(saga, PROCESS_PAYMENT, event.getFailureMessage());
            failSaga(saga, "Payment gateway failed: " + event.getFailureCode());
            // No publishTransactionFailed here — onBalanceRolledBack emits the single
            // terminal TRANSACTION_FAILED event after rollback completes.
            publishRollbackBalance(saga, saga.getFailureReason());
            return;
        }

        saga.setExternalReference(event.getExternalReference());
        completeStep(saga, PROCESS_PAYMENT);
        saga.setCurrentStep(COMMIT_BALANCE);
        createStep(saga, COMMIT_BALANCE, "IN_PROGRESS");
        sagaRepository.save(saga);

        publishCommitBalance(saga);
    }

    @Transactional
    public void onBalanceCommitted(BalanceCommittedEvent event) {
        Saga saga = requireSagaForUpdate(event.getTransactionId());
        if (!isStepIdempotent(saga, COMMIT_BALANCE, "IN_PROGRESS")) return;

        completeStep(saga, COMMIT_BALANCE);
        saga.setStatus(COMPLETED);
        saga.setCurrentStep("COMPLETED");
        saga.setCompletedAt(Instant.now());
        sagaRepository.save(saga);

        publishTransactionCompleted(saga);
        log.info("Saga COMPLETED: sagaId={} txn={}", saga.getId(), saga.getTransactionId());
    }

    @Transactional
    public void onBalanceRolledBack(BalanceRolledBackEvent event) {
        Saga saga = requireSagaForUpdate(event.getTransactionId());
        // Saga internally marks as ROLLED_BACK for audit trail
        saga.setStatus(ROLLED_BACK);
        saga.setCompletedAt(Instant.now());
        sagaRepository.save(saga);

        // Single terminal event to transaction-service — maps to FAILED on the
        // transaction (user-facing).  No separate TRANSACTION_ROLLED_BACK event
        // so there is no race and the UI always shows one clean failure state.
        String failureCode = deriveFailureCode(saga.getFailureReason());
        publishTransactionFailed(saga, failureCode, saga.getFailureReason());
        log.info("Saga ROLLED BACK: sagaId={} txn={}", saga.getId(), saga.getTransactionId());
    }

    /** Extracts a short failure code from the human-readable failure reason. */
    private String deriveFailureCode(String reason) {
        if (reason == null) return "UNKNOWN";
        if (reason.contains("INSUFFICIENT_FUNDS")) return "INSUFFICIENT_FUNDS";
        if (reason.contains("ACCOUNT_NOT_FOUND"))  return "ACCOUNT_NOT_FOUND";
        if (reason.contains("risk engine"))         return "RISK_REJECTED";
        if (reason.contains("Payment gateway"))     return "PAYMENT_GATEWAY_FAILED";
        if (reason.contains("timed out"))           return "SAGA_TIMEOUT";
        return "TRANSACTION_FAILED";
    }

    /** Called by SagaTimeoutScheduler for sagas past their deadline. */
    @Transactional
    public void timeoutSaga(Saga saga) {
        saga = sagaRepository.findByTransactionIdForUpdate(saga.getTransactionId()).orElse(saga);
        if (COMPLETED.equals(saga.getStatus()) || FAILED.equals(saga.getStatus())) return;

        log.warn("Saga timeout: sagaId={} txn={} step={}", saga.getId(), saga.getTransactionId(), saga.getCurrentStep());
        saga.setStatus(COMPENSATING);
        failSaga(saga, "Saga timed out at step: " + saga.getCurrentStep());

        if (!RESERVE_BALANCE.equals(saga.getCurrentStep())) {
            publishRollbackBalance(saga, "Saga timeout");
        }
        publishTransactionFailed(saga, "SAGA_TIMEOUT", saga.getFailureReason());
    }

    /**
     * Called by {@link com.aegispay.orchestrator.controller.StripeWebhookController}
     * when Stripe delivers a {@code payment_intent.succeeded} webhook asynchronously.
     * This handles 3DS/redirect flows where the payment confirmation arrives after
     * the initial API call.
     */
    @Transactional
    public void onStripePaymentSucceeded(UUID transactionId, String paymentIntentId) {
        Saga saga = sagaRepository.findByTransactionIdForUpdate(transactionId).orElse(null);
        if (saga == null) {
            log.warn("onStripePaymentSucceeded: no saga for txn={}", transactionId);
            return;
        }
        if (!RUNNING.equals(saga.getStatus()) || !PROCESS_PAYMENT.equals(saga.getCurrentStep())) {
            log.info("onStripePaymentSucceeded: saga already past PROCESS_PAYMENT for txn={} — skip", transactionId);
            return;
        }
        log.info("Stripe async payment succeeded: pi={} txn={}", paymentIntentId, transactionId);
        saga.setExternalReference(paymentIntentId);
        completeStep(saga, PROCESS_PAYMENT);
        saga.setCurrentStep(COMMIT_BALANCE);
        createStep(saga, COMMIT_BALANCE, "IN_PROGRESS");
        sagaRepository.save(saga);
        publishCommitBalance(saga);
    }

    /**
     * Called by {@link com.aegispay.orchestrator.controller.StripeWebhookController}
     * when Stripe delivers a {@code payment_intent.payment_failed} or
     * {@code payment_intent.canceled} webhook.
     */
    @Transactional
    public void onStripePaymentFailed(UUID transactionId, String paymentIntentId,
                                      String failureCode, String failureMessage) {
        Saga saga = sagaRepository.findByTransactionIdForUpdate(transactionId).orElse(null);
        if (saga == null) {
            log.warn("onStripePaymentFailed: no saga for txn={}", transactionId);
            return;
        }
        if (!RUNNING.equals(saga.getStatus()) || !PROCESS_PAYMENT.equals(saga.getCurrentStep())) {
            log.info("onStripePaymentFailed: saga already past PROCESS_PAYMENT for txn={} — skip", transactionId);
            return;
        }
        log.warn("Stripe async payment failed: pi={} txn={} code={}", paymentIntentId, transactionId, failureCode);
        saga.setExternalReference(paymentIntentId);
        failStep(saga, PROCESS_PAYMENT, failureMessage);
        failSaga(saga, "Payment gateway failed: " + failureCode);
        publishRollbackBalance(saga, saga.getFailureReason());
        publishTransactionFailed(saga, failureCode, saga.getFailureReason());
    }

    // ---- helpers ----

    private Saga requireSagaForUpdate(UUID transactionId) {
        return sagaRepository.findByTransactionIdForUpdate(transactionId)
                .orElseThrow(() -> new IllegalStateException("No saga found for txn=" + transactionId));
    }

    private boolean isStepIdempotent(Saga saga, String stepName, String expectedStatus) {
        return stepRepository.findBySagaIdAndStepName(saga.getId(), stepName)
                .map(s -> expectedStatus.equals(s.getStepStatus()))
                .orElse(false);
    }

    private void createStep(Saga saga, String stepName, String status) {
        stepRepository.save(SagaStep.builder()
                .saga(saga)
                .stepName(stepName)
                .stepStatus(status)
                .attemptCount(1)
                .lastAttemptAt(Instant.now())
                .build());
    }

    private void completeStep(Saga saga, String stepName) {
        stepRepository.findBySagaIdAndStepName(saga.getId(), stepName).ifPresent(step -> {
            step.setStepStatus("COMPLETED");
            stepRepository.save(step);
        });
    }

    private void failStep(Saga saga, String stepName, String reason) {
        stepRepository.findBySagaIdAndStepName(saga.getId(), stepName).ifPresent(step -> {
            step.setStepStatus("FAILED");
            step.setErrorMessage(reason);
            stepRepository.save(step);
        });
    }

    private void failSaga(Saga saga, String reason) {
        saga.setStatus(COMPENSATING);
        saga.setFailureReason(reason);
        sagaRepository.save(saga);
    }

    // ---- outbox event builders ----

    private void publishReserveBalance(Saga saga) {
        BalanceReserveRequestedEvent event = BalanceReserveRequestedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(saga.getTransactionId()).sagaId(saga.getId())
                .userId(saga.getPayerId())
                .amount(saga.getAmount()).currency(saga.getCurrency())
                .build();
        writeOutbox(saga, "BalanceReserveRequestedEvent", KafkaTopics.BALANCE_RESERVE_REQUESTED, event);
    }

    private void publishRiskAssessment(Saga saga) {
        RiskAssessmentRequestedEvent event = RiskAssessmentRequestedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(saga.getTransactionId()).sagaId(saga.getId())
                .userId(saga.getUserId()).amount(saga.getAmount()).currency(saga.getCurrency())
                .build();
        writeOutbox(saga, "RiskAssessmentRequestedEvent", KafkaTopics.RISK_ASSESSMENT_REQUESTED, event);
    }

    private void publishProcessPayment(Saga saga) {
        PaymentProcessRequestedEvent event = PaymentProcessRequestedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(saga.getTransactionId()).sagaId(saga.getId())
                .payerId(saga.getPayerId()).payeeId(saga.getPayeeId())
                .amount(saga.getAmount()).currency(saga.getCurrency())
                .build();
        writeOutbox(saga, "PaymentProcessRequestedEvent", KafkaTopics.PAYMENT_PROCESS_REQUESTED, event);
    }

    private void publishCommitBalance(Saga saga) {
        BalanceCommitRequestedEvent event = BalanceCommitRequestedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(saga.getTransactionId()).sagaId(saga.getId())
                .accountId(saga.getAccountId()).amount(saga.getAmount())
                .build();
        writeOutbox(saga, "BalanceCommitRequestedEvent", KafkaTopics.BALANCE_COMMIT_REQUESTED, event);
    }

    private void publishRollbackBalance(Saga saga, String reason) {
        BalanceRollbackRequestedEvent event = BalanceRollbackRequestedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(saga.getTransactionId()).sagaId(saga.getId())
                .accountId(saga.getAccountId()).amountToRelease(saga.getAmount())
                .rollbackReason(reason)
                .build();
        writeOutbox(saga, "BalanceRollbackRequestedEvent", KafkaTopics.BALANCE_ROLLBACK_REQUESTED, event);
    }

    private void publishTransactionCompleted(Saga saga) {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(saga.getTransactionId()).userId(saga.getUserId())
                .amount(saga.getAmount()).currency(saga.getCurrency())
                .completedAt(saga.getCompletedAt()).externalReference(saga.getExternalReference())
                .build();
        writeOutbox(saga, "TransactionCompletedEvent", KafkaTopics.TRANSACTION_COMPLETED, event);
    }

    private void publishTransactionFailed(Saga saga, String failureCode, String reason) {
        TransactionFailedEvent event = TransactionFailedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(saga.getTransactionId()).userId(saga.getUserId())
                .failureReason(reason).failureCode(failureCode)
                .build();
        writeOutbox(saga, "TransactionFailedEvent", KafkaTopics.TRANSACTION_FAILED, event);
    }

    private void publishTransactionRolledBack(Saga saga, String reason) {
        TransactionRolledBackEvent event = TransactionRolledBackEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(saga.getTransactionId()).userId(saga.getUserId())
                .rollbackReason(reason).sagaId(saga.getId().toString())
                .build();
        writeOutbox(saga, "TransactionRolledBackEvent", KafkaTopics.TRANSACTION_ROLLED_BACK, event);
    }

    private void writeOutbox(Saga saga, String eventType, String topic, Object event) {
        try {
            outboxRepository.save(OutboxEntry.builder()
                    .aggregateId(saga.getTransactionId().toString())
                    .aggregateType("Saga")
                    .eventType(eventType)
                    .topic(topic)
                    .messageKey(saga.getTransactionId().toString())
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize " + eventType, e);
        }
    }
}
