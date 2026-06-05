package com.aegispay.notification.kafka;

import com.aegispay.common.domain.enums.NotificationType;
import com.aegispay.common.domain.event.UserRegisteredEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.notification.dispatcher.NotificationDispatcher;
import com.aegispay.notification.domain.document.UserContactDocument;
import com.aegispay.notification.repository.UserContactRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredConsumer {

    private final NotificationDispatcher dispatcher;
    private final UserContactRepository userContactRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.USER_REGISTERED, groupId = "notification-service-user")
    public void handle(ConsumerRecord<String, String> record) {
        try {
            UserRegisteredEvent event = objectMapper.readValue(record.value(), UserRegisteredEvent.class);
            String userId = event.getUserId().toString();

            // ── Upsert semantics — preserve OTP-verified phone on re-delivery ──────────
            // Kafka at-least-once delivery means user.registered can be re-delivered if the
            // consumer pod crashes after receiving but before committing the offset.
            //
            // Scenario that a full-replace save() breaks:
            //   1. User registers (phone=null for social login).
            //   2. UserRegisteredConsumer processes event → saves {phone=null, smsEnabled=false}.
            //   3. User adds + OTP-verifies phone → UserContactUpdatedConsumer sets
            //      {phone="+91...", smsEnabled=true}.
            //   4. Pod crashes mid-consumer, user.registered re-delivered.
            //   5. Full-replace save() overwrites → {phone=null, smsEnabled=false}. ← BUG
            //      All future SMS notifications silently dropped forever.
            //
            // Fix: find the existing document first; only set phone/smsEnabled from this
            // event if no phone is already on file.  Email fields are always safe to
            // overwrite — they come from the IdP and may legitimately change.
            UserContactDocument contact = userContactRepository.findById(userId)
                    .orElseGet(() -> UserContactDocument.builder()
                            .userId(userId)
                            .smsNotificationsEnabled(false)
                            .build());

            // Email is authoritative from the IdP — always update.
            contact.setEmail(event.getEmail());
            contact.setMaskedEmail(event.getMaskedEmail());

            // Phone: only set from the registration event if not already OTP-verified.
            // UserContactUpdatedConsumer is authoritative for phone (phone must be OTP-verified
            // before smsNotificationsEnabled is set to true). If a verified phone is already
            // on file, preserve it and leave smsNotificationsEnabled unchanged.
            if (contact.getPhoneNumber() == null || contact.getPhoneNumber().isBlank()) {
                contact.setPhoneNumber(event.getPhoneNumber());
                // smsNotificationsEnabled stays false at registration — phone is not yet
                // OTP-verified.  Only UserContactUpdatedConsumer may flip it to true.
            }

            contact.setUpdatedAt(Instant.now());
            userContactRepository.save(contact);

            dispatcher.dispatch(userId, NotificationType.USER_REGISTERED, "WEBSOCKET", null, Map.of());
        } catch (Exception e) {
            log.error("Error processing user registered notification: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
