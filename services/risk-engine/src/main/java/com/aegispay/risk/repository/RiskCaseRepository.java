package com.aegispay.risk.repository;

import com.aegispay.risk.domain.entity.RiskCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskCaseRepository extends JpaRepository<RiskCase, UUID> {
    Optional<RiskCase> findByTransactionId(UUID transactionId);

    /**
     * Used by {@link com.aegispay.risk.rules.RepeatedPayeeFailureRuleEvaluator}:
     * returns true if this user has previously sent to payeeId and the decision
     * was one of the supplied values (typically ["REJECTED"]).
     */
    boolean existsByUserIdAndPayeeIdAndDecisionIn(UUID userId, UUID payeeId,
                                                  List<String> decisions);
}
