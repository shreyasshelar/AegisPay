package com.aegispay.ai.repository;

import com.aegispay.ai.domain.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {
    List<KnowledgeDocument> findBySource(String source);
    void deleteBySource(String source);
}
