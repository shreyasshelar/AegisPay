package com.aegispay.notification.kafka;

import com.aegispay.common.domain.enums.NotificationType;
import com.aegispay.common.domain.event.TransactionCompletedEvent;
import com.aegispay.common.domain.event.TransactionFailedEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.notification.dispatcher.NotificationDispatcher;
import com.aegispay.notification.domain.document.UserContactDocument;
import com.aegispay.notification.repository.UserContactRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionStatusConsumer {

    private final NotificationDispatcher dispatcher;
    private final UserContactRepository userContactRepository;
    private final ObjectMapper objectMapper;

    /** Ops Slack webhook — receives FAILED alerts regardless of individual user settings. */
    @Value("${aegispay.notification.slack.webhook-url:}")
    private String slackWebhookUrl;

    // ── Contact resolution helpers ────────────────────────────────────────────

    private Optional<UserContactDocument> resolveContact(String userId) {
        try {
            return userContactRepository.findById(userId);
        } catch (Exception ex) {
            log.warn("Contact lookup failed for userId={}: {}", userId, ex.getMessage());
            return Optional.empty();
        }
    }

    private String resolvePhone(String userId) {
        return resolveContact(userId)
                .map(UserContactDocument::getPhoneNumber)
                .filter(p -> p != null && !p.isBlank())
                .orElse(null);
    }

    private String resolveEmail(String userId) {
        return resolveContact(userId)
                .map(UserContactDocument::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .orElse(null);
    }

    // ── Kafka listener ────────────────────────────────────────────────────────

    @KafkaListener(
        topics = {
            KafkaTopics.TRANSACTION_COMPLETED,
            KafkaTopics.TRANSACTION_FAILED
            // TRANSACTION_ROLLED_BACK removed — orchestrator now maps rollback → TRANSACTION_FAILED
        },
        groupId = "notification-service-transactions"
    )
    public void handle(ConsumerRecord<String, String> record) {
        log.debug("Received transaction event: topic={} key={}", record.topic(), record.key());
        try {
            switch (record.topic()) {

                case KafkaTopics.TRANSACTION_COMPLETED -> {
                    TransactionCompletedEvent e = objectMapper.readValue(record.value(), TransactionCompletedEvent.class);
                    String userId = e.getUserId().toString();
                    String txId   = e.getTransactionId().toString();
                    Map<String, String> vars = Map.of(
                        "amount",            e.getAmount().toPlainString(),
                        "currency",          e.getCurrency(),
                        "externalReference", e.getExternalReference() != null ? e.getExternalReference() : "");

                    // ── Payer notifications ───────────────────────────────────
                    // WebSocket — real-time in-app
                    dispatcher.dispatch(userId, NotificationType.TRANSACTION_COMPLETED, "WEBSOCKET", null, vars, txId);

                    // Email — "Your payment was successful" receipt
                    String email = resolveEmail(userId);
                    if (email != null) {
                        dispatcher.dispatch(userId, NotificationType.TRANSACTION_COMPLETED, "EMAIL", email, vars, txId);
                    }

                    // ── Payee notifications ───────────────────────────────────
                    // Only fired when the saga actually completes (money arrived).
                    // Failed/in-flight transactions never reach this branch, so the
                    // payee is never notified about money that didn't land.
                    if (e.getPayeeId() != null) {
                        String payeeUserId = e.getPayeeId().toString();
                        Map<String, String> receivedVars = Map.of(
                            "amount",   e.getAmount().toPlainString(),
                            "currency", e.getCurrency());

                        // WebSocket — real-time balance nudge
                        dispatcher.dispatch(payeeUserId, NotificationType.MONEY_RECEIVED, "WEBSOCKET", null, receivedVars, txId);

                        // Email — "You received X" confirmation
                        String payeeEmail = resolveEmail(payeeUserId);
                        if (payeeEmail != null) {
                            dispatcher.dispatch(payeeUserId, NotificationType.MONEY_RECEIVED, "EMAIL", payeeEmail, receivedVars, txId);
                        }

                        // SMS — immediate alert (only if phone on file)
                        String payeePhone = resolvePhone(payeeUserId);
                        if (payeePhone != null) {
                            dispatcher.dispatch(payeeUserId, NotificationType.MONEY_RECEIVED, "SMS", payeePhone, receivedVars, txId);
                        }
                    }
                }

                case KafkaTopics.TRANSACTION_FAILED -> {
                    TransactionFailedEvent e = objectMapper.readValue(record.value(), TransactionFailedEvent.class);
                    String userId = e.getUserId().toString();
                    String txId   = e.getTransactionId().toString();
                    Map<String, String> vars = Map.of(
                        "failureReason", e.getFailureReason() != null ? e.getFailureReason() : "Unknown",
                        "failureCode",   e.getFailureCode()   != null ? e.getFailureCode()   : "");

                    // WebSocket — real-time in-app
                    dispatcher.dispatch(userId, NotificationType.TRANSACTION_FAILED, "WEBSOCKET", null, vars, txId);

                    // Email — failure notice to user
                    String email = resolveEmail(userId);
                    if (email != null) {
                        dispatcher.dispatch(userId, NotificationType.TRANSACTION_FAILED, "EMAIL", email, vars, txId);
                    }

                    // SMS — urgent alert (only if phone on file)
                    String phone = resolvePhone(userId);
                    if (phone != null) {
                        dispatcher.dispatch(userId, NotificationType.TRANSACTION_FAILED, "SMS", phone, vars, txId);
                    }

                    // Slack — ops/internal alert (always fired if webhook configured)
                    if (!slackWebhookUrl.isBlank()) {
                        dispatcher.dispatch(userId, NotificationType.TRANSACTION_FAILED, "SLACK",
                                slackWebhookUrl, vars, txId);
                    }
                }

                default -> log.warn("Unhandled topic: {}", record.topic());
            }
        } catch (Exception e) {
            log.error("Error processing transaction notification: topic={} error={}", record.topic(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
