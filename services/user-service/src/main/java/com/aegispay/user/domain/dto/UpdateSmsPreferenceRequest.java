package com.aegispay.user.domain.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for PATCH /api/v1/users/{userId}/notifications/sms.
 *
 * <p>Enables or disables SMS notifications for the user.
 * The backend enforces that {@code enabled = true} is only accepted when a
 * verified phone number is already on file — the client should gate the
 * control accordingly, but the server is the authoritative guard.
 */
public record UpdateSmsPreferenceRequest(
    @NotNull(message = "enabled is required")
    Boolean enabled
) {}
