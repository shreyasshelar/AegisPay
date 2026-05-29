package com.aegispay.common.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@SuperBuilder
public class TransactionFailedEvent extends BaseEvent {

    private UUID transactionId;
    private UUID userId;
    private String failureReason;
    private String failureCode;
    /**
     * Transaction amount — required so the data pipeline can record the correct
     * failed-transaction volume in ClickHouse. Previously absent, causing
     * {@code transaction_facts.amount = 0} for all failed rows.
     */
    private BigDecimal amount;
    /**
     * ISO-4217 currency code (e.g. "INR", "USD").
     * Required alongside {@link #amount}.
     */
    private String currency;
}
