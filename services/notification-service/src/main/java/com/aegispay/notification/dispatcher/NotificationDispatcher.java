package com.aegispay.notification.dispatcher;

import com.aegispay.common.domain.enums.NotificationType;
import com.aegispay.notification.adapter.NotificationAdapter;
import com.aegispay.notification.domain.document.Notification;
import com.aegispay.notification.repository.NotificationRepository;
import com.aegispay.notification.template.NotificationTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * Dispatch a notification to a user.
     * Always pushes over WebSocket; also sends via adapter if channel is EMAIL or SMS.
     *
     * @param userId    target user UUID string
     * @param type      notification type (drives template selection)
     * @param channel   "WEBSOCKET", "EMAIL", or "SMS"
     * @param recipient email address or phone number (null for WEBSOCKET-only)
     * @param vars      template variable substitutions
     */
    public void dispatch(String userId, NotificationType type, String channel,
                         String recipient, Map<String, String> vars) {

        NotificationTemplateService.RenderedNotification rendered =
                templateService.render(type, vars != null ? vars : Map.of(), "en");

        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .channel(channel)
                .status("PENDING")
                .title(rendered.title())
                .body(rendered.body())
                .metadata(vars)
                .createdAt(Instant.now())
                .build();

        notification = notificationRepository.save(notification);

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
