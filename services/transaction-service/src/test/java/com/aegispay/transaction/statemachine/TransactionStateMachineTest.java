package com.aegispay.transaction.statemachine;

import com.aegispay.common.domain.enums.TransactionStatus;
import com.aegispay.common.domain.exception.AegisPayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionStateMachineTest {

    private final TransactionStateMachine sm = new TransactionStateMachine();

    @Test void initiatedToReserved()   { sm.assertValidTransition(TransactionStatus.INITIATED, TransactionStatus.RESERVED); }
    @Test void reservedToRiskCleared() { sm.assertValidTransition(TransactionStatus.RESERVED, TransactionStatus.RISK_CLEARED); }
    @Test void riskClearedToProcessing(){ sm.assertValidTransition(TransactionStatus.RISK_CLEARED, TransactionStatus.PROCESSING); }
    @Test void processingToCompleted() { sm.assertValidTransition(TransactionStatus.PROCESSING, TransactionStatus.COMPLETED); }

    @Test void anyActiveStateToFailed() {
        sm.assertValidTransition(TransactionStatus.INITIATED, TransactionStatus.FAILED);
        sm.assertValidTransition(TransactionStatus.RESERVED, TransactionStatus.FAILED);
        sm.assertValidTransition(TransactionStatus.RISK_CLEARED, TransactionStatus.FAILED);
        sm.assertValidTransition(TransactionStatus.PROCESSING, TransactionStatus.FAILED);
    }

    @Test void anyActiveStateToRolledBack() {
        sm.assertValidTransition(TransactionStatus.INITIATED, TransactionStatus.ROLLED_BACK);
        sm.assertValidTransition(TransactionStatus.PROCESSING, TransactionStatus.ROLLED_BACK);
    }

    @Test void rejectsSkippingStates() {
        assertThatThrownBy(() ->
                sm.assertValidTransition(TransactionStatus.INITIATED, TransactionStatus.COMPLETED))
                .isInstanceOf(AegisPayException.class);
    }

    @Test void rejectsTransitionFromTerminalState() {
        assertThatThrownBy(() ->
                sm.assertValidTransition(TransactionStatus.COMPLETED, TransactionStatus.FAILED))
                .isInstanceOf(AegisPayException.class);
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = {"COMPLETED", "FAILED", "ROLLED_BACK"})
    void terminalStatesAreTerminal(TransactionStatus status) {
        assertThat(sm.isTerminal(status)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = {"INITIATED", "RESERVED", "RISK_CLEARED", "PROCESSING"})
    void activeStatesAreNotTerminal(TransactionStatus status) {
        assertThat(sm.isTerminal(status)).isFalse();
    }
}
