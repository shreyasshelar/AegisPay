package com.aegispay.notification.domain.document;

import com.aegispay.common.domain.enums.NotificationType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    private String id;

    @Indexed
    private String userId;

    private NotificationType type;

    /** EMAIL, SMS, WEBSOCKET */
    private String channel;

    /** PENDING, SENT, FAILED */
    private String status;

    private String title;
    private String body;
    private Map<String, String> metadata;

    private String errorMessage;

    private Instant createdAt;
    private Instant sentAt;
}
