package com.aegispay.notification.dispatcher;

import com.aegispay.common.domain.enums.NotificationType;
import com.aegispay.notification.adapter.NotificationAdapter;
import com.aegispay.notification.domain.document.Notification;
import com.aegispay.notification.repository.NotificationRepository;
import com.aegispay.notification.template.NotificationTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationTemplateService templateService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;
    private final List<NotificationAdapter> adapters;

    /**
     * Dispatch a notification to a user (no idempotency — for non-transactional events).
     *
     * @param userId    target user UUID string
     * @param type      notification type (drives template selection)
     * @param channel   "WEBSOCKET", "EMAIL", or "SMS"
     * @param recipient email address or phone number (null for WEBSOCKET-only)
     * @param vars      template variable substitutions
     */
    public void dispatch(String userId, NotificationType type, String channel,
                         String recipient, Map<String, String> vars) {
        dispatch(userId, type, channel, recipient, vars, null);
    }

    /**
     * Dispatch a notification to a user with idempotency support.
     *
     * <p>When {@code transactionId} is non-null, an {@code eventKey} is derived as
     * {@code transactionId:type:channel}. A sparse unique MongoDB index on {@code eventKey}
     * ensures that if the same Kafka event is re-delivered (at-least-once), the second
     * attempt to save raises {@link DuplicateKeyException} which is caught and silently
     * skipped — preventing duplicate notifications.
     *
     * @param userId        target user UUID string
     * @param type          notification type (drives template selection)
     * @param channel       "WEBSOCKET", "EMAIL", or "SMS"
     * @param recipient     email address or phone number (null for WEBSOCKET-only)
     * @param vars          template variable substitutions
     * @param transactionId optional transaction UUID for idempotency (null = no dedup)
     */
    public void dispatch(String userId, NotificationType type, String channel,
                         String recipient, Map<String, String> vars, String transactionId) {

        NotificationTemplateService.RenderedNotification rendered =
                templateService.render(type, vars != null ? vars : Map.of(), "en");

        String eventKey = (transactionId != null && !transactionId.isBlank())
                ? transactionId + ":" + type.name() + ":" + channel
                : null;

        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .channel(channel)
                .status("PENDING")
                .title(rendered.title())
                .body(rendered.body())
                .metadata(vars)
                .createdAt(Instant.now())
                .eventKey(eventKey)
                .build();

        try {
            notification = notificationRepository.save(notification);
        } catch (DuplicateKeyException e) {
            log.info("Duplicate notification skipped (idempotency): eventKey={} userId={} type={} channel={}",
                    eventKey, userId, type, channel);
            return;
        }

        // Always push over WebSocket to the user's personal queue
        try {
            messagingTemplate.convertAndSendToUser(userId, "/queue/notifications",
                    Map.of("type", type.name(), "title", rendered.title(), "body", rendered.body()));
            log.debug("WebSocket push to userId={} type={}", userId, type);
        } catch (Exception e) {
            log.warn("WebSocket push failed for userId={}: {}", userId, e.getMessage());
        }

        // Deliver via external adapter when channel is not WEBSOCKET
        if (!"WEBSOCKET".equalsIgnoreCase(channel) && recipient != null) {
            NotificationAdapter adapter = resolveAdapter(channel);
            if (adapter != null) {
                try {
                    adapter.send(recipient, rendered.title(), rendered.body());
                    notification.setStatus("SENT");
                    notification.setSentAt(Instant.now());
                } catch (Exception e) {
                    log.error("Adapter send failed for userId={} channel={}: {}", userId, channel, e.getMessage(), e);
                    notification.setStatus("FAILED");
                    notification.setErrorMessage(e.getMessage());
                }
            }
        } else {
            notification.setStatus("SENT");
            notification.setSentAt(Instant.now());
        }

        notificationRepository.save(notification);
    }

    private NotificationAdapter resolveAdapter(String channel) {
        return adapters.stream()
                .filter(a -> a.channel().equalsIgnoreCase(channel))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("No adapter found for channel={}", channel);
                    return null;
                });
    }
}
