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

            // Persist email, phone + masked email so EMAIL/SMS can be sent on future events
            UserContactDocument contact = UserContactDocument.builder()
                    .userId(userId)
                    .email(event.getEmail())
                    .phoneNumber(event.getPhoneNumber())
                    .maskedEmail(event.getMaskedEmail())
                    .updatedAt(Instant.now())
                    .build();
            userContactRepository.save(contact);

            dispatcher.dispatch(userId, NotificationType.USER_REGISTERED, "WEBSOCKET", null, Map.of());
        } catch (Exception e) {
            log.error("Error processing user registered notification: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
