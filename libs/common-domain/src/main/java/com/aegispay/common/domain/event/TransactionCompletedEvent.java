package com.aegispay.common.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@SuperBuilder
public class TransactionCompletedEvent extends BaseEvent {

    private UUID transactionId;
    private UUID userId;
    /**
     * Payee UUID — required by the notification service to deliver
     * a "You received money" alert to User B via WebSocket / Email / SMS.
     */
    private UUID payeeId;
    private BigDecimal amount;
    private String currency;
    private Instant completedAt;
    private String externalReference;
    /**
     * Timestamp when the saga was created (set by the orchestrator's {@code @PrePersist}).
     * Required by the data pipeline to compute real saga end-to-end latency:
     * {@code latency_ms = completedAt - sagaStartedAt}.
     * Previously absent, causing every saga latency row to store {@code 0}.
     */
    private Instant sagaStartedAt;
}
