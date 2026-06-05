package com.aegispay.notification.kafka;

import com.aegispay.common.domain.enums.NotificationType;
import com.aegispay.common.domain.event.KycStatusChangedEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.notification.client.UserServiceFallbackClient;
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
public class KycStatusConsumer {

    private final NotificationDispatcher    dispatcher;
    private final UserContactRepository     userContactRepository;
    private final UserServiceFallbackClient userServiceFallbackClient;
    private final ObjectMapper              objectMapper;

    @KafkaListener(topics = KafkaTopics.KYC_STATUS_CHANGED, groupId = "notification-service-kyc")
    public void handle(ConsumerRecord<String, String> record) {
        try {
            KycStatusChangedEvent event = objectMapper.readValue(record.value(), KycStatusChangedEvent.class);
            String userId = event.getUserId().toString();
            Map<String, String> vars = Map.of(
                "newStatus",      event.getNewStatus().name(),
                "previousStatus", event.getPreviousStatus() != null ? event.getPreviousStatus().name() : "");

            dispatcher.dispatch(userId, NotificationType.KYC_STATUS_CHANGED, "WEBSOCKET", null, vars);

            // SMS only for final KYC decisions (APPROVED / REJECTED / MANUAL_REVIEW).
            // Transitional states (DOCUMENT_SUBMITTED, AI_PROCESSING) only need the
            // WebSocket push so the UI can update — no need to text the user for every step.
            String newStatus = event.getNewStatus() != null ? event.getNewStatus().name() : "";
            boolean isFinalDecision = newStatus.equals("APPROVED")
                    || newStatus.equals("REJECTED")
                    || newStatus.equals("MANUAL_REVIEW");
            if (isFinalDecision) {
                String phone = resolvePhone(userId);
                if (phone != null) {
                    dispatcher.dispatch(userId, NotificationType.KYC_STATUS_CHANGED, "SMS", phone, vars);
                }
            }
        } catch (Exception e) {
            log.error("Error processing KYC status notification: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private String resolvePhone(String userId) {
        try {
            var contact = userContactRepository.findById(userId);
            if (contact.isEmpty()) {
                // Defense-in-depth: lazily provision the contact document if missing.
                // This covers the edge case where KYC completes before user.registered
                // has been consumed by this consumer group (e.g. during a pod restart).
                var provisioned = userServiceFallbackClient.fetchAndProvision(userId);
                if (provisioned == null) return null;
                // Only return phone if SMS is enabled and number is on file
                return provisioned.isSmsNotificationsEnabled()
                        && provisioned.getPhoneNumber() != null
                        && !provisioned.getPhoneNumber().isBlank()
                        ? provisioned.getPhoneNumber()
                        : null;
            }
            String phone = contact.get().getPhoneNumber();
            return (phone != null && !phone.isBlank()) ? phone : null;
        } catch (Exception ex) {
            log.warn("Phone lookup failed for userId={}: {}", userId, ex.getMessage());
            return null;
        }
    }
}
