package com.aegispay.user.kafka;

import com.aegispay.common.domain.enums.KycStatus;
import com.aegispay.common.domain.event.KycStatusChangedEvent;
import com.aegispay.common.domain.event.UserRegisteredEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.user.domain.entity.User;
import com.aegispay.user.outbox.OutboxEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds outbox entries for user events.
 * All Kafka publishing goes through the transactional outbox — never direct.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventProducer {

    private final ObjectMapper objectMapper;

    public OutboxEntry buildUserRegisteredEntry(User user) {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .eventId(UUID.randomUUID())
                .correlationId(null)    // filled by MDC in scheduler
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .userId(user.getId())
                .email(user.getEmail())              // full email for notification delivery
                .maskedEmail(maskEmail(user.getEmail())) // display-only masked version
                .phoneNumber(user.getPhone())
                .role(user.getRole())
                .tenantId(user.getTenantId())
                .build();

        return OutboxEntry.builder()
                .aggregateId(user.getId().toString())
                .aggregateType("User")
                .eventType("UserRegisteredEvent")
                .topic(KafkaTopics.USER_REGISTERED)
                .messageKey(user.getId().toString())
                .payload(serialize(event))
                .build();
    }

    public OutboxEntry buildKycStatusChangedEntry(User user,
                                                   KycStatus previousStatus,
                                                   String documentType,
                                                   String rejectionReason) {
        KycStatusChangedEvent event = KycStatusChangedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .userId(user.getId())
                .previousStatus(previousStatus)
                .newStatus(user.getKycStatus())
                .documentType(documentType)
                .rejectionReason(rejectionReason)
                .build();

        return OutboxEntry.builder()
                .aggregateId(user.getId().toString())
                .aggregateType("User")
                .eventType("KycStatusChangedEvent")
                .topic(KafkaTopics.KYC_STATUS_CHANGED)
                .messageKey(user.getId().toString())
                .payload(serialize(event))
                .build();
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event: " + event.getClass().getSimpleName(), e);
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String local = email.substring(0, email.indexOf('@'));
        String domain = email.substring(email.indexOf('@'));
        return local.charAt(0) + "***" + domain;
    }
}
