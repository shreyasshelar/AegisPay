package com.aegispay.orchestrator.kafka;

import com.aegispay.common.domain.event.PaymentProcessRequestedEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.orchestrator.client.StripePaymentGatewayClient;
import com.aegispay.orchestrator.domain.entity.OutboxEntry;
import com.aegispay.orchestrator.domain.entity.Saga;
import com.aegispay.orchestrator.repository.OutboxEntryRepository;
import com.aegispay.orchestrator.repository.SagaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Listens to {@code payment.process.requested}, calls Stripe, then publishes
 * {@code payment.processed} (success) or immediately compensates (failure).
 *
 * <p>For most Stripe PaymentIntents created with {@code confirm: true} and automatic
 * payment methods, the result is synchronous. For 3DS/redirect flows, the Stripe
 * webhook will call {@link com.aegispay.orchestrator.controller.StripeWebhookController}
 * which advances the saga asynchronously.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessRequestedConsumer {

    private final StripePaymentGatewayClient stripeClient;
    private final SagaRepository sagaRepository;
    private final OutboxEntryRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = KafkaTopics.PAYMENT_PROCESS_REQUESTED,
                   groupId = "payment-orchestrator-gateway")
    public void handle(ConsumerRecord<String, String> record) {
        PaymentProcessRequestedEvent event;
        try {
            event = objectMapper.readValue(record.value(), PaymentProcessRequestedEvent.class);
        } catch (Exception e) {
            log.error("Failed to parse PaymentProcessRequestedEvent: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }

        log.info("Processing payment via Stripe: txn={} amount={} {}",
                event.getTransactionId(), event.getAmount(), event.getCurrency());

        // Guard: if saga already advanced past this step, skip (idempotent)
        Saga saga = sagaRepository.findByTransactionId(event.getTransactionId()).orElse(null);
        if (saga == null) {
            log.warn("No saga found for txn={} — skipping Stripe call", event.getTransactionId());
            return;
        }

        StripePaymentGatewayClient.PaymentRequest paymentRequest =
                new StripePaymentGatewayClient.PaymentRequest(
                        event.getTransactionId(),
                        event.getPayerId(),
                        event.getPayeeId(),
                        event.getAmount(),
                        event.getCurrency());

        StripePaymentGatewayClient.PaymentResult result = stripeClient.processPayment(paymentRequest);

        // Publish PaymentProcessedEvent to outbox so SagaReplyConsumer picks it up
        publishPaymentProcessed(saga, result);
    }

    private void publishPaymentProcessed(Saga saga, StripePaymentGatewayClient.PaymentResult result) {
        com.aegispay.common.domain.event.PaymentProcessedEvent event =
                com.aegispay.common.domain.event.PaymentProcessedEvent.builder()
                        .eventId(UUID.randomUUID())
                        .occurredAt(Instant.now())
                        .schemaVersion(1)
                        .transactionId(saga.getTransactionId())
                        .sagaId(saga.getId())
                        .success(result.success())
                        .externalReference(result.externalReference())
                        .failureCode(result.failureCode())
                        .failureMessage(result.failureMessage())
                        .build();

        try {
            outboxRepository.save(OutboxEntry.builder()
                    .aggregateId(saga.getTransactionId().toString())
                    .aggregateType("Saga")
                    .eventType("PaymentProcessedEvent")
                    .topic(KafkaTopics.PAYMENT_PROCESSED)
                    .messageKey(saga.getTransactionId().toString())
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize PaymentProcessedEvent", e);
        }
    }
}
