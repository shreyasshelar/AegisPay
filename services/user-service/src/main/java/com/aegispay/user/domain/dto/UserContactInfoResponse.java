package com.aegispay.user.domain.dto;

import java.util.UUID;

/**
 * Unmasked contact details returned by the internal service-to-service endpoint
 * {@code GET /api/v1/users/{userId}/internal/contact}.
 *
 * <p><b>Never expose this DTO externally.</b> The {@code email} and {@code phoneNumber}
 * fields are full, deliverable values — not the masked versions returned by the public
 * {@link UserResponse} API. This record exists solely so the notification-service can
 * lazily provision a {@code UserContactDocument} when the {@code user.registered} Kafka
 * event was delayed or missed during a deployment restart.
 *
 * <p>Access is restricted to callers presenting a valid {@code X-Internal-Api-Key} header,
 * which the {@code InternalApiKeyFilter} converts to {@code ROLE_ADMIN} in the
 * {@code SecurityContextHolder} — allowing the {@code @PreAuthorize("hasRole('ADMIN')")}
 * guard on the controller endpoint to pass.
 */
public record UserContactInfoResponse(
    UUID    id,
    /** Full deliverable email address (unmasked) — for outbound email delivery. */
    String  email,
    /** Full phone number (unmasked) — for SMS delivery; null if not on file. */
    String  phoneNumber,
    /** Whether the user has opted in to SMS notifications. */
    boolean smsNotificationsEnabled
) {}
