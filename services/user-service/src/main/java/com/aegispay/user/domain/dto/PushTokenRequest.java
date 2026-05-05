package com.aegispay.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

@Builder
public record PushTokenRequest(
    /** FCM registration token (Android) or APNs device token (iOS). */
    @NotBlank String token,

    /** "ios" or "android" */
    @NotBlank @Pattern(regexp = "^(ios|android)$", message = "platform must be 'ios' or 'android'")
    String platform
) {}
