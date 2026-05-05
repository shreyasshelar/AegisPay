package com.aegispay.user.repository;

import com.aegispay.user.domain.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    List<KycDocument> findByUserId(UUID userId);

    List<KycDocument> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
