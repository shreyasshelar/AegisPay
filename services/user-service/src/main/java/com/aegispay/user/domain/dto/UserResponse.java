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
    Instant updatedAt,
    /**
     * Human-readable explanation of why KYC was rejected, set by the AI pipeline.
     * Non-null only when {@code kycStatus == REJECTED}; null for all other statuses.
     * Shown directly in the profile KYC banner so the user knows what to fix
     * before re-uploading.
     */
    String rejectionReason
) {
    /**
     * Returns a copy of this response with the given rejection reason attached.
     * Used by {@link com.aegispay.user.service.UserService} to enrich the mapper
     * output (MapStruct cannot populate this field because it lives on
     * {@code KycDocument}, not on {@code User}).
     */
    public UserResponse withRejectionReason(String reason) {
        return new UserResponse(id, externalId, name, email, phone,
                firstName, lastName, role, tenantId, kycStatus,
                active, createdAt, updatedAt, reason);
    }
}
