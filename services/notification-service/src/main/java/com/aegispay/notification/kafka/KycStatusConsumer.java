package com.aegispay.notification.kafka;

import com.aegispay.common.domain.enums.NotificationType;
import com.aegispay.common.domain.event.KycStatusChangedEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.notification.dispatcher.NotificationDispatcher;
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

    private final NotificationDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.KYC_STATUS_CHANGED, groupId = "notification-service-kyc")
    public void handle(ConsumerRecord<String, String> record) {
        try {
            KycStatusChangedEvent event = objectMapper.readValue(record.value(), KycStatusChangedEvent.class);
            dispatcher.dispatch(event.getUserId().toString(), NotificationType.KYC_STATUS_CHANGED,
                    "WEBSOCKET", null,
                    Map.of("newStatus", event.getNewStatus().name(),
                           "previousStatus", event.getPreviousStatus() != null ? event.getPreviousStatus().name() : ""));
        } catch (Exception e) {
            log.error("Error processing KYC status notification: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
