package com.aegispay.common.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@SuperBuilder
public class UserRegisteredEvent extends BaseEvent {

    private UUID userId;
    /** Full deliverable email address — stored by notification-service for email delivery. */
    private String email;
    /** Display-only masked version (e.g. j***@gmail.com) — never used for delivery. */
    private String maskedEmail;
    private String phoneNumber;
    private String role;
    private String tenantId;
}
