package com.aegispay.ai.repository;

import com.aegispay.ai.domain.entity.AiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, UUID> {}
