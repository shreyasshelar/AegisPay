package com.aegispay.user.domain.entity;

import com.aegispay.common.domain.enums.KycStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The IdP subject claim (sub) — unique identifier from Keycloak / Entra / Okta. */
    @Column(name = "external_id", nullable = false, unique = true, length = 255)
    private String externalId;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String role = "CUSTOMER";

    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 50)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** FCM registration token (Android) or APNs device token (iOS). Nullable — set on first login. */
    @Column(name = "push_token", length = 512)
    private String pushToken;

    /** "ios" or "android" */
    @Column(name = "push_token_platform", length = 10)
    private String pushTokenPlatform;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
