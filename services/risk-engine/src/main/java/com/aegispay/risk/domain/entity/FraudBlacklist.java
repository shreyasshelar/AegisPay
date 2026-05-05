package com.aegispay.risk.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_blacklist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String entityType;   // USER, IBAN, IP

    @Column(nullable = false)
    private String entityValue;

    private String reason;

    @Column(nullable = false)
    private Instant addedAt;

    private String addedBy;

    @PrePersist
    void prePersist() {
        addedAt = Instant.now();
    }
}
