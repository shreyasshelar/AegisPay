package com.aegispay.notification.domain.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class NotificationResponse {
    String id;
    String userId;
    String type;
    String channel;
    String status;
    String title;
    String body;
    Instant createdAt;
    Instant sentAt;
}
