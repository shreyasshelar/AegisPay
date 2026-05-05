package com.aegispay.common.domain.event;

import com.aegispay.common.domain.enums.NotificationType;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.UUID;

@Getter
@SuperBuilder
public class NotificationSendRequestedEvent extends BaseEvent {

    private final UUID userId;
    private final NotificationType notificationType;
    private final String channel;
    private final String templateId;
    private final Map<String, String> templateVariables;
    private final String locale;
}
