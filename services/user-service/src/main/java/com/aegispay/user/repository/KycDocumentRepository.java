package com.aegispay.user.repository;

import com.aegispay.user.domain.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    List<KycDocument> findByUserId(UUID userId);

    List<KycDocument> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Returns the most recent KYC document for a user.
     * Used to retrieve the {@code rejectionReason} when {@code kycStatus == REJECTED}.
     */
    Optional<KycDocument> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
}
