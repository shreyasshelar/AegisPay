package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@SuperBuilder
public class UserRegisteredEvent extends BaseEvent {

    private final UUID userId;
    private final String maskedEmail;
    private final String phoneNumber;
    private final String role;
    private final String tenantId;
}
