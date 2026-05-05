package com.aegispay.notification.kafka;

import com.aegispay.common.domain.event.NotificationSendRequestedEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.notification.dispatcher.NotificationDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSendRequestedConsumer {

    private final NotificationDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_SEND_REQUESTED, groupId = "notification-service-generic")
    public void handle(ConsumerRecord<String, String> record) {
        try {
            NotificationSendRequestedEvent event =
                    objectMapper.readValue(record.value(), NotificationSendRequestedEvent.class);

            dispatcher.dispatch(
                    event.getUserId().toString(),
                    event.getNotificationType(),
                    event.getChannel() != null ? event.getChannel() : "WEBSOCKET",
                    null,
                    event.getTemplateVariables()
            );
        } catch (Exception e) {
            log.error("Error processing notification.send.requested: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
