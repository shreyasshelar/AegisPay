package com.aegispay.risk.repository;

import com.aegispay.risk.domain.entity.RiskCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RiskCaseRepository extends JpaRepository<RiskCase, UUID> {
    Optional<RiskCase> findByTransactionId(UUID transactionId);
}
