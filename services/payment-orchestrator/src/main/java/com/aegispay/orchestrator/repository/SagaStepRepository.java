package com.aegispay.orchestrator.repository;

import com.aegispay.orchestrator.domain.entity.SagaStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaStepRepository extends JpaRepository<SagaStep, UUID> {

    @Query("SELECT s FROM SagaStep s WHERE s.saga.id = :sagaId AND s.stepName = :stepName")
    Optional<SagaStep> findBySagaIdAndStepName(@Param("sagaId") UUID sagaId,
                                                @Param("stepName") String stepName);

    List<SagaStep> findBySagaIdOrderByCreatedAtAsc(UUID sagaId);
}
