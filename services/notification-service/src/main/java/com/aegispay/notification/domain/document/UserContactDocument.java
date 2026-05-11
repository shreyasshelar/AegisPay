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

    @Nullable
    private String maskedEmail;

    private Instant updatedAt;
}
