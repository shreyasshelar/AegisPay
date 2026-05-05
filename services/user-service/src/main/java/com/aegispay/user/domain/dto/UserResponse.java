package com.aegispay.user.domain.dto;

import com.aegispay.common.domain.enums.KycStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record UserResponse(
    UUID id,
    String externalId,
    /** Full display name: "{firstName} {lastName}" — used by all three frontend platforms. */
    String name,
    String email,        // masked: j***@example.com
    String phone,        // masked: +91**8765
    String firstName,
    String lastName,
    String role,
    String tenantId,
    KycStatus kycStatus,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {}
