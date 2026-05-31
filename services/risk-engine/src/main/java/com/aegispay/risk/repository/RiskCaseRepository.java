package com.aegispay.risk.repository;

import com.aegispay.risk.domain.entity.RiskCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Extends {@link JpaSpecificationExecutor} so the back-office filter endpoint can build
 * optional predicates dynamically via the Criteria API — avoiding the known Hibernate issue
 * where JPQL {@code :param IS NULL} fails to resolve the SQL type when an enum-typed
 * parameter is passed as {@code null}.
 */
public interface RiskCaseRepository extends JpaRepository<RiskCase, UUID>,
        JpaSpecificationExecutor<RiskCase> {

    Optional<RiskCase> findByTransactionId(UUID transactionId);

    /** Back-office list — all cases ordered by most recent first (used as fallback). */
    Page<RiskCase> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Used by {@link com.aegispay.risk.rules.RepeatedPayeeFailureRuleEvaluator}:
     * returns true if this user has previously sent to payeeId and the decision
     * was one of the supplied values (typically ["REJECTED"]).
     */
    boolean existsByUserIdAndPayeeIdAndDecisionIn(UUID userId, UUID payeeId,
                                                  List<String> decisions);
}
