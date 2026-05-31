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

    /**
     * Idempotency key for Kafka at-least-once deduplication.
     * Format: {@code transactionId:type:channel} — e.g.
     * {@code a749ced1-...:TRANSACTION_COMPLETED:WEBSOCKET}.
     *
     * <p>Null for non-transaction notifications (KYC, user-registered, etc.).
     * The sparse unique index means null values are not indexed and thus do
     * not conflict — only duplicate transaction events are rejected.
     */
    @Indexed(unique = true, sparse = true)
    private String eventKey;
}
