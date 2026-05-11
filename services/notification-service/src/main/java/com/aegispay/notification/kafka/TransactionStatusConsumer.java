package com.aegispay.notification.kafka;

import com.aegispay.common.domain.enums.NotificationType;
import com.aegispay.common.domain.event.TransactionCompletedEvent;
import com.aegispay.common.domain.event.TransactionFailedEvent;
import com.aegispay.common.domain.event.TransactionRolledBackEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.notification.dispatcher.NotificationDispatcher;
import com.aegispay.notification.repository.UserContactRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionStatusConsumer {

    private final NotificationDispatcher dispatcher;
    private final UserContactRepository userContactRepository;
    private final ObjectMapper objectMapper;

    /** Returns the user's phone number, or null if not found or blank — never throws. */
    private String resolvePhone(String userId) {
        try {
            var opt = userContactRepository.findById(userId);
            if (opt.isEmpty()) return null;
            String phone = opt.get().getPhoneNumber();
            return (phone != null && !phone.isBlank()) ? phone : null;
        } catch (Exception ex) {
            log.warn("Phone lookup failed for userId={}: {}", userId, ex.getMessage());
            return null;
        }
    }

    @KafkaListener(
        topics = {
            KafkaTopics.TRANSACTION_COMPLETED,
            KafkaTopics.TRANSACTION_FAILED,
            KafkaTopics.TRANSACTION_ROLLED_BACK
        },
        groupId = "notification-service-transactions"
    )
    public void handle(ConsumerRecord<String, String> record) {
        log.debug("Received transaction event: topic={} key={}", record.topic(), record.key());
        try {
            switch (record.topic()) {
                case KafkaTopics.TRANSACTION_COMPLETED -> {
                    TransactionCompletedEvent e = objectMapper.readValue(record.value(), TransactionCompletedEvent.class);
                    Map<String, String> vars = Map.of(
                        "amount", e.getAmount().toPlainString(),
                        "currency", e.getCurrency(),
                        "externalReference", e.getExternalReference() != null ? e.getExternalReference() : "");
                    // WebSocket only for success — SMS would be too noisy
                    dispatcher.dispatch(e.getUserId().toString(), NotificationType.TRANSACTION_COMPLETED,
                            "WEBSOCKET", null, vars);
                }
                case KafkaTopics.TRANSACTION_FAILED -> {
                    TransactionFailedEvent e = objectMapper.readValue(record.value(), TransactionFailedEvent.class);
                    String userId = e.getUserId().toString();
                    Map<String, String> vars = Map.of(
                        "failureReason", e.getFailureReason() != null ? e.getFailureReason() : "Unknown",
                        "failureCode",   e.getFailureCode()   != null ? e.getFailureCode()   : "");
                    dispatcher.dispatch(userId, NotificationType.TRANSACTION_FAILED, "WEBSOCKET", null, vars);
                    String phone = resolvePhone(userId);
                    if (phone != null) {
                        dispatcher.dispatch(userId, NotificationType.TRANSACTION_FAILED, "SMS", phone, vars);
                    }
                }
                case KafkaTopics.TRANSACTION_ROLLED_BACK -> {
                    TransactionRolledBackEvent e = objectMapper.readValue(record.value(), TransactionRolledBackEvent.class);
                    String userId = e.getUserId().toString();
                    Map<String, String> vars = Map.of(
                        "rollbackReason", e.getRollbackReason() != null ? e.getRollbackReason() : "");
                    dispatcher.dispatch(userId, NotificationType.TRANSACTION_ROLLED_BACK, "WEBSOCKET", null, vars);
                    String phone = resolvePhone(userId);
                    if (phone != null) {
                        dispatcher.dispatch(userId, NotificationType.TRANSACTION_ROLLED_BACK, "SMS", phone, vars);
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
