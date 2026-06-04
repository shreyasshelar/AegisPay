package com.aegispay.notification.domain.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.lang.Nullable;

import java.time.Instant;

@Document(collection = "user_contacts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContactDocument {

    @Id
    private String userId;

    @Nullable
    private String phoneNumber;

    /** Full deliverable email address — used for email notifications. */
    @Nullable
    private String email;

    /** Masked display-only version (e.g. j***@gmail.com) — shown in UI. */
    @Nullable
    private String maskedEmail;

    /**
     * Whether the user has opted in to SMS notifications.
     * Synced from user-service via {@code user.contact.updated} Kafka events.
     *
     * <p>Gate logic in {@link com.aegispay.notification.kafka.TransactionStatusConsumer}:
     * SMS is dispatched only when BOTH {@code smsNotificationsEnabled == true}
     * AND {@code phoneNumber} is non-null.
     */
    @Builder.Default
    private boolean smsNotificationsEnabled = false;

    private Instant updatedAt;
}
