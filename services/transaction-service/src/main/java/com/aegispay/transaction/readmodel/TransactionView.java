package com.aegispay.transaction.readmodel;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * CQRS read model — denormalized view of a transaction stored in MongoDB.
 * Updated by the Kafka consumer whenever a terminal event (completed/failed/rolled-back)
 * arrives. Intermediate saga state updates (RESERVED, RISK_CLEARED, PROCESSING)
 * will be added in Phase 6 (payment-orchestrator).
 */
@Document(collection = "transaction_views")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionView {

    @Id
    private String id;   // transaction UUID as String (MongoDB convention)

    @Indexed
    private String userId;

    private String payerId;
    private String payeeId;

    private BigDecimal amount;
    private String currency;

    private String status;       // TransactionStatus name
    private String lastEvent;    // event type that caused the last status change

    private Instant initiatedAt;
    private Instant updatedAt;
    private Instant completedAt;

    private String failureReason;
    private String failureCode;
    private String externalReference;

    /** AI-generated ETA or delay explanation (populated asynchronously). */
    private String aiExplanation;
}
