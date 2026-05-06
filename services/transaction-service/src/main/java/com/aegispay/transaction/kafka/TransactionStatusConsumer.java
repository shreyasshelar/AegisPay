package com.aegispay.transaction.kafka;

import com.aegispay.common.domain.enums.TransactionStatus;
import com.aegispay.common.domain.event.TransactionCompletedEvent;
import com.aegispay.common.domain.event.TransactionFailedEvent;
import com.aegispay.common.domain.event.TransactionRolledBackEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.transaction.domain.dto.TransactionStatusResponse;
import com.aegispay.transaction.domain.entity.Transaction;
import com.aegispay.transaction.readmodel.TransactionView;
import com.aegispay.transaction.readmodel.TransactionViewRepository;
import com.aegispay.transaction.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumes terminal transaction events from the payment-orchestrator and:
 *  1. Updates the PostgreSQL write model (transaction status + completion fields)
 *  2. Upserts the MongoDB read model (TransactionView)
 *  3. Pushes a real-time status update over STOMP/WebSocket
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionStatusConsumer {

    private final TransactionRepository transactionRepository;
    private final TransactionViewRepository viewRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {
            KafkaTopics.TRANSACTION_COMPLETED,
            KafkaTopics.TRANSACTION_FAILED,
            KafkaTopics.TRANSACTION_ROLLED_BACK
        },
        groupId = "transaction-service-status-consumer"
    )
    @Transactional
    public void handle(ConsumerRecord<String, String> record) throws Exception {
        String topic = record.topic();
        String payload = record.value();

        log.debug("Received status event: topic={} key={}", topic, record.key());

        try {
            switch (topic) {
                case KafkaTopics.TRANSACTION_COMPLETED  -> handleCompleted(payload);
                case KafkaTopics.TRANSACTION_FAILED     -> handleFailed(payload);
                case KafkaTopics.TRANSACTION_ROLLED_BACK -> handleRolledBack(payload);
                default -> log.warn("Unhandled topic: {}", topic);
            }
        } catch (Exception e) {
            log.error("Error processing status event: topic={} error={}", topic, e.getMessage(), e);
            throw e;   // let the error handler retry / DLQ
        }
    }

    private void handleCompleted(String payload) throws Exception {
        TransactionCompletedEvent event = objectMapper.readValue(payload, TransactionCompletedEvent.class);
        UUID txnId = event.getTransactionId();

        transactionRepository.findById(txnId).ifPresent(txn -> {
            txn.setStatus(TransactionStatus.COMPLETED);
            txn.setCompletedAt(event.getCompletedAt());
            txn.setExternalReference(event.getExternalReference());
            transactionRepository.save(txn);
            upsertView(txn, "TransactionCompletedEvent");
            pushWebSocket(txnId, TransactionStatus.COMPLETED, "TransactionCompletedEvent");
        });
    }

    private void handleFailed(String payload) throws Exception {
        TransactionFailedEvent event = objectMapper.readValue(payload, TransactionFailedEvent.class);
        UUID txnId = event.getTransactionId();

        transactionRepository.findById(txnId).ifPresent(txn -> {
            txn.setStatus(TransactionStatus.FAILED);
            txn.setFailureReason(event.getFailureReason());
            txn.setCompletedAt(Instant.now());
            transactionRepository.save(txn);
            upsertView(txn, "TransactionFailedEvent");
            pushWebSocket(txnId, TransactionStatus.FAILED, "TransactionFailedEvent");
        });
    }

    private void handleRolledBack(String payload) throws Exception {
        TransactionRolledBackEvent event = objectMapper.readValue(payload, TransactionRolledBackEvent.class);
        UUID txnId = event.getTransactionId();

        transactionRepository.findById(txnId).ifPresent(txn -> {
            txn.setStatus(TransactionStatus.ROLLED_BACK);
            txn.setFailureReason(event.getRollbackReason());
            txn.setCompletedAt(Instant.now());
            transactionRepository.save(txn);
            upsertView(txn, "TransactionRolledBackEvent");
            pushWebSocket(txnId, TransactionStatus.ROLLED_BACK, "TransactionRolledBackEvent");
        });
    }

    private void upsertView(Transaction txn, String lastEvent) {
        TransactionView view = viewRepository.findById(txn.getId().toString())
                .orElseGet(() -> TransactionView.builder()
                        .id(txn.getId().toString())
                        .userId(txn.getUserId().toString())
                        .payerId(txn.getPayerId().toString())
                        .payeeId(txn.getPayeeId().toString())
                        .amount(txn.getAmount())
                        .currency(txn.getCurrency())
                        .initiatedAt(txn.getInitiatedAt())
                        .build());

        view.setStatus(txn.getStatus().name());
        view.setLastEvent(lastEvent);
        view.setUpdatedAt(Instant.now());
        view.setCompletedAt(txn.getCompletedAt());
        view.setFailureReason(txn.getFailureReason());
        view.setExternalReference(txn.getExternalReference());

        viewRepository.save(view);
    }

    private void pushWebSocket(UUID transactionId, TransactionStatus status, String lastEvent) {
        TransactionStatusResponse response = TransactionStatusResponse.builder()
                .transactionId(transactionId)
                .status(status.name())
                .lastEvent(lastEvent)
                .updatedAt(Instant.now())
                .build();

        messagingTemplate.convertAndSend(
                "/topic/transactions/" + transactionId + "/status",
                response);

        log.debug("WebSocket push: txnId={} status={}", transactionId, status);
    }
}
