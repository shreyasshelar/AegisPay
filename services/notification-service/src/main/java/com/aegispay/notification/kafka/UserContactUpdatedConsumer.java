package com.aegispay.notification.kafka;

import com.aegispay.common.domain.event.UserContactUpdatedEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.notification.domain.document.UserContactDocument;
import com.aegispay.notification.repository.UserContactRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Keeps the notification-service {@link UserContactDocument} read-model in sync
 * when a user adds or changes their phone number.
 *
 * <p>This is critical for SSO users (Google, GitHub, Apple, Microsoft) who register
 * without a phone number because OAuth providers don't supply one.  Without this
 * consumer the contact document's {@code phoneNumber} field stays {@code null} forever
 * and SMS notifications are never delivered regardless of user preference.
 *
 * <p>Uses upsert semantics ({@code findById} + set fields + save) rather than a
 * replace, so {@code email} and {@code maskedEmail} populated at registration are
 * preserved and not overwritten.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserContactUpdatedConsumer {

    private final UserContactRepository userContactRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = KafkaTopics.USER_CONTACT_UPDATED,
        groupId = "notification-service-contact"
    )
    public void handle(ConsumerRecord<String, String> record) {
        try {
            UserContactUpdatedEvent event =
                    objectMapper.readValue(record.value(), UserContactUpdatedEvent.class);

            String userId = event.getUserId().toString();

            // Upsert: preserve existing email fields set at registration.
            UserContactDocument contact = userContactRepository.findById(userId)
                    .orElseGet(() -> UserContactDocument.builder()
                            .userId(userId)
                            .build());

            contact.setPhoneNumber(event.getPhoneNumber());
            contact.setUpdatedAt(Instant.now());
            userContactRepository.save(contact);

            log.info("UserContactDocument updated: userId={} hasPhone={}",
                    userId, event.getPhoneNumber() != null);

        } catch (Exception e) {
            log.error("Error processing user.contact.updated: key={} error={}",
                    record.key(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
