package com.aegispay.risk.repository;

import com.aegispay.risk.domain.entity.FraudBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FraudBlacklistRepository extends JpaRepository<FraudBlacklist, UUID> {
    Optional<FraudBlacklist> findByEntityTypeAndEntityValue(String entityType, String entityValue);
    boolean existsByEntityTypeAndEntityValue(String entityType, String entityValue);
}
