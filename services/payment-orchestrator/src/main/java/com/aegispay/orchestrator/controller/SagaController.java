package com.aegispay.orchestrator.controller;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.orchestrator.domain.dto.SagaStatusResponse;
import com.aegispay.orchestrator.domain.dto.SagaStepDto;
import com.aegispay.orchestrator.domain.entity.Saga;
import com.aegispay.orchestrator.exception.SagaNotFoundException;
import com.aegispay.orchestrator.repository.SagaRepository;
import com.aegispay.orchestrator.repository.SagaStepRepository;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/api/v1/sagas")
@RequiredArgsConstructor
public class SagaController {

    private final SagaRepository sagaRepository;
    private final SagaStepRepository stepRepository;

    @GetMapping("/by-transaction/{transactionId}")
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<SagaStatusResponse>> getByTransactionId(
            @PathVariable UUID transactionId) {

        Saga saga = sagaRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new SagaNotFoundException(transactionId));

        var steps = stepRepository.findBySagaIdOrderByCreatedAtAsc(saga.getId()).stream()
                .map(s -> SagaStepDto.builder()
                        .id(s.getId())
                        .stepName(s.getStepName())
                        .stepStatus(s.getStepStatus())
                        .attemptCount(s.getAttemptCount())
                        .lastAttemptAt(s.getLastAttemptAt())
                        .errorMessage(s.getErrorMessage())
                        .build())
                .collect(Collectors.toList());

        SagaStatusResponse response = SagaStatusResponse.builder()
                .sagaId(saga.getId())
                .transactionId(saga.getTransactionId())
                .currentStep(saga.getCurrentStep())
                .status(saga.getStatus())
                .amount(saga.getAmount())
                .currency(saga.getCurrency())
                .startedAt(saga.getStartedAt())
                .updatedAt(saga.getUpdatedAt())
                .completedAt(saga.getCompletedAt())
                .failureReason(saga.getFailureReason())
                .externalReference(saga.getExternalReference())
                .steps(steps)
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
