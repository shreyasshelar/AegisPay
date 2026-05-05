package com.aegispay.orchestrator.kafka;

import com.aegispay.common.domain.event.*;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.orchestrator.saga.PaymentSagaOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Single listener for all saga reply events — routes each to the orchestrator.
 * All reply topics share the same group so they are processed in order per partition.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaReplyConsumer {

    private final PaymentSagaOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {
            KafkaTopics.BALANCE_RESERVED,
            KafkaTopics.BALANCE_RESERVE_FAILED,
            KafkaTopics.RISK_ASSESSED,
            KafkaTopics.PAYMENT_PROCESSED,
            KafkaTopics.BALANCE_COMMITTED,
            KafkaTopics.BALANCE_ROLLED_BACK
        },
        groupId = "payment-orchestrator-replies"
    )
    public void handle(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        log.debug("Received saga reply: topic={} key={}", topic, record.key());
        try {
            switch (topic) {
                case KafkaTopics.BALANCE_RESERVED ->
                    orchestrator.onBalanceReserved(parse(record.value(), BalanceReservedEvent.class));
                case KafkaTopics.BALANCE_RESERVE_FAILED ->
                    orchestrator.onBalanceReserveFailed(parse(record.value(), BalanceReserveFailedEvent.class));
                case KafkaTopics.RISK_ASSESSED ->
                    orchestrator.onRiskAssessed(parse(record.value(), RiskAssessedEvent.class));
                case KafkaTopics.PAYMENT_PROCESSED ->
                    orchestrator.onPaymentProcessed(parse(record.value(), PaymentProcessedEvent.class));
                case KafkaTopics.BALANCE_COMMITTED ->
                    orchestrator.onBalanceCommitted(parse(record.value(), BalanceCommittedEvent.class));
                case KafkaTopics.BALANCE_ROLLED_BACK ->
                    orchestrator.onBalanceRolledBack(parse(record.value(), BalanceRolledBackEvent.class));
                default -> log.warn("Unhandled saga reply topic: {}", topic);
            }
        } catch (Exception e) {
            log.error("Error handling saga reply: topic={} key={} error={}", topic, record.key(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private <T> T parse(String json, Class<T> type) throws Exception {
        return objectMapper.readValue(json, type);
    }
}
