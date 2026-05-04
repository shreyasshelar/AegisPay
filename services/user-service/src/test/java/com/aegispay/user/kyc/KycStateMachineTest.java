package com.aegispay.user.kyc;

import com.aegispay.common.domain.enums.KycStatus;
import com.aegispay.common.domain.exception.AegisPayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KycStateMachineTest {

    private final KycStateMachine stateMachine = new KycStateMachine();

    @Test
    void allowsPendingToDocumentSubmitted() {
        stateMachine.assertValidTransition(KycStatus.PENDING, KycStatus.DOCUMENT_SUBMITTED);
    }

    @Test
    void allowsDocumentSubmittedToAiProcessing() {
        stateMachine.assertValidTransition(KycStatus.DOCUMENT_SUBMITTED, KycStatus.AI_PROCESSING);
    }

    @Test
    void allowsAiProcessingToApproved() {
        stateMachine.assertValidTransition(KycStatus.AI_PROCESSING, KycStatus.APPROVED);
    }

    @Test
    void allowsAiProcessingToRejected() {
        stateMachine.assertValidTransition(KycStatus.AI_PROCESSING, KycStatus.REJECTED);
    }

    @Test
    void allowsAiProcessingToManualReview() {
        stateMachine.assertValidTransition(KycStatus.AI_PROCESSING, KycStatus.MANUAL_REVIEW);
    }

    @Test
    void allowsManualReviewToApproved() {
        stateMachine.assertValidTransition(KycStatus.MANUAL_REVIEW, KycStatus.APPROVED);
    }

    @Test
    void rejectsPendingDirectlyToApproved() {
        assertThatThrownBy(() ->
                stateMachine.assertValidTransition(KycStatus.PENDING, KycStatus.APPROVED))
                .isInstanceOf(AegisPayException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("APPROVED");
    }

    @Test
    void rejectsApprovedToAnyStatus() {
        assertThatThrownBy(() ->
                stateMachine.assertValidTransition(KycStatus.APPROVED, KycStatus.REJECTED))
                .isInstanceOf(AegisPayException.class);
    }

    @Test
    void approvedIsTerminal() {
        assertThat(stateMachine.isTerminal(KycStatus.APPROVED)).isTrue();
    }

    @Test
    void rejectedIsTerminal() {
        assertThat(stateMachine.isTerminal(KycStatus.REJECTED)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = KycStatus.class, names = {"PENDING", "DOCUMENT_SUBMITTED", "AI_PROCESSING", "MANUAL_REVIEW"})
    void nonTerminalStatusesAreNotTerminal(KycStatus status) {
        assertThat(stateMachine.isTerminal(status)).isFalse();
    }
}
