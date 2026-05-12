package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@NoArgsConstructor
@SuperBuilder
public class UserRegisteredEvent extends BaseEvent {

    private UUID userId;
    private String maskedEmail;
    private String phoneNumber;
    private String role;
    private String tenantId;
}
