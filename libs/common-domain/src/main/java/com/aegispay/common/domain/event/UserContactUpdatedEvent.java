package com.aegispay.common.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Published when a user adds or updates their phone number (or other contact detail).
 *
 * <p>Consumed by the notification-service to keep its {@code UserContactDocument}
 * read-model in sync.  This is the only supported mutation path — the notification
 * service never calls user-service directly; it relies solely on this event.
 *
 * <p>Primary use-case: SSO users (Google, GitHub, Apple, Microsoft) register without
 * a phone number because OAuth providers don't share them.  This event fires when the
 * user later adds their phone via {@code PATCH /api/v1/users/{userId}/phone}, enabling
 * SMS notifications that were silently skipped until then.
 */
@Getter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@SuperBuilder
public class UserContactUpdatedEvent extends BaseEvent {

    private UUID userId;

    /**
     * New phone number in E.164 / international format (e.g. {@code +919876543210}).
     * {@code null} when the phone is being removed.
     */
    private String phoneNumber;
}
