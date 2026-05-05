package com.aegispay.transaction.statemachine;

import com.aegispay.common.domain.enums.TransactionStatus;
import com.aegispay.common.domain.exception.AegisPayException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enforces valid transaction state transitions.
 *
 * The transaction-service itself only drives INITIATED → (terminal state via Kafka consumer).
 * Intermediate transitions (RESERVED, RISK_CLEARED, PROCESSING) are driven by the
 * payment-orchestrator saga in Phase 6.
 *
 * All possible transitions are declared here so the state machine is complete and
 * can be used by the orchestrator's status-update consumer when Phase 6 lands.
 */
@Component
public class TransactionStateMachine {

    private static final Map<TransactionStatus, Set<TransactionStatus>> VALID_TRANSITIONS = Map.of(
        TransactionStatus.INITIATED,    EnumSet.of(
            TransactionStatus.RESERVED,
            TransactionStatus.FAILED,
            TransactionStatus.ROLLED_BACK
        ),
        TransactionStatus.RESERVED,     EnumSet.of(
            TransactionStatus.RISK_CLEARED,
            TransactionStatus.FAILED,
            TransactionStatus.ROLLED_BACK
        ),
        TransactionStatus.RISK_CLEARED, EnumSet.of(
            TransactionStatus.PROCESSING,
            TransactionStatus.FAILED,
            TransactionStatus.ROLLED_BACK
        ),
        TransactionStatus.PROCESSING,   EnumSet.of(
            TransactionStatus.COMPLETED,
            TransactionStatus.FAILED,
            TransactionStatus.ROLLED_BACK
        )
    );

    public void assertValidTransition(TransactionStatus current, TransactionStatus next) {
        Set<TransactionStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(next)) {
            throw new AegisPayException(
                "INVALID_TRANSACTION_TRANSITION",
                "Transition from " + current + " to " + next + " is not allowed.",
                HttpStatus.CONFLICT
            );
        }
    }

    public boolean isTerminal(TransactionStatus status) {
        return status == TransactionStatus.COMPLETED
            || status == TransactionStatus.FAILED
            || status == TransactionStatus.ROLLED_BACK;
    }
}
