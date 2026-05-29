package com.aegispay.user.kyc;

import com.aegispay.common.domain.enums.KycStatus;
import com.aegispay.common.domain.exception.AegisPayException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enforces valid KYC state transitions.
 *
 * <p>APPROVED is the only truly terminal state — no further transitions are allowed.
 * REJECTED allows re-submission (REJECTED → DOCUMENT_SUBMITTED) so users can
 * upload a corrected document after a failed verification attempt.
 */
@Component
public class KycStateMachine {

    private static final Map<KycStatus, Set<KycStatus>> VALID_TRANSITIONS = Map.of(
        KycStatus.PENDING,            EnumSet.of(KycStatus.DOCUMENT_SUBMITTED),
        KycStatus.DOCUMENT_SUBMITTED, EnumSet.of(KycStatus.AI_PROCESSING),
        KycStatus.AI_PROCESSING,      EnumSet.of(KycStatus.APPROVED, KycStatus.REJECTED, KycStatus.MANUAL_REVIEW),
        KycStatus.MANUAL_REVIEW,      EnumSet.of(KycStatus.APPROVED, KycStatus.REJECTED),
        // Users may re-upload after rejection — REJECTED is not a dead end
        KycStatus.REJECTED,           EnumSet.of(KycStatus.DOCUMENT_SUBMITTED)
    );

    public void assertValidTransition(KycStatus current, KycStatus next) {
        Set<KycStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(next)) {
            throw new AegisPayException(
                "INVALID_KYC_TRANSITION",
                "KYC transition from " + current + " to " + next + " is not allowed.",
                HttpStatus.CONFLICT
            );
        }
    }

    /** Only APPROVED is terminal. REJECTED allows re-upload via REJECTED → DOCUMENT_SUBMITTED. */
    public boolean isTerminal(KycStatus status) {
        return status == KycStatus.APPROVED;
    }
}
