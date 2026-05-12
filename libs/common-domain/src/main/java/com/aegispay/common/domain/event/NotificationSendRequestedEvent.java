package com.aegispay.common.domain.event;

import com.aegispay.common.domain.enums.NotificationType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.UUID;

@Getter
@NoArgsConstructor
@SuperBuilder
public class NotificationSendRequestedEvent extends BaseEvent {

    private UUID userId;
    private NotificationType notificationType;
    private String channel;
    private String templateId;
    private Map<String, String> templateVariables;
    private String locale;
}
