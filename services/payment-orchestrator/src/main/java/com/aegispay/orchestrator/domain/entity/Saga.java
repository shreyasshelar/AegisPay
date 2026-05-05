package com.aegispay.orchestrator.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sagas")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Saga {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID transactionId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID payerId;

    @Column(nullable = false)
    private UUID payeeId;

    private UUID accountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 50)
    private String currentStep;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Instant startedAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    private Instant completedAt;

    @Column(nullable = false)
    private Instant timeoutAt;

    private String failureReason;
    private String externalReference;

    @OneToMany(mappedBy = "saga", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SagaStep> steps = new ArrayList<>();

    @PrePersist
    void prePersist() {
        startedAt = Instant.now();
    }
}
