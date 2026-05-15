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

    private Instant updatedAt;
}
