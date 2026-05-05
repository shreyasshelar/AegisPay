package com.aegispay.orchestrator.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SagaStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saga_id", nullable = false)
    private Saga saga;

    @Column(nullable = false, length = 50)
    private String stepName;

    @Column(nullable = false, length = 20)
    private String stepStatus;

    @Column(nullable = false)
    private int attemptCount;

    private Instant lastAttemptAt;
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (stepStatus == null) stepStatus = "PENDING";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
