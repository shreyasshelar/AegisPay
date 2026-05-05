package com.aegispay.risk.controller;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.risk.domain.dto.BlacklistRequest;
import com.aegispay.risk.domain.dto.RiskCaseResponse;
import com.aegispay.risk.domain.entity.FraudBlacklist;
import com.aegispay.risk.domain.entity.RiskCase;
import com.aegispay.risk.exception.RiskCaseNotFoundException;
import com.aegispay.risk.repository.FraudBlacklistRepository;
import com.aegispay.risk.repository.RiskCaseRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskCaseRepository riskCaseRepository;
    private final FraudBlacklistRepository blacklistRepository;

    @GetMapping("/cases/{transactionId}")
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN', 'MERCHANT_OPS')")
    public ResponseEntity<ApiResponse<RiskCaseResponse>> getCase(@PathVariable UUID transactionId) {
        RiskCase rc = riskCaseRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RiskCaseNotFoundException(transactionId));

        RiskCaseResponse response = RiskCaseResponse.builder()
                .id(rc.getId())
                .transactionId(rc.getTransactionId())
                .userId(rc.getUserId())
                .riskScore(rc.getRiskScore())
                .decision(rc.getDecision().name())
                .ruleFlags(rc.getRuleFlags())
                .ragExplanation(rc.getRagExplanation())
                .createdAt(rc.getCreatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/blacklist")
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> addToBlacklist(
            @Valid @RequestBody BlacklistRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String addedBy = jwt.getSubject();
        FraudBlacklist entry = FraudBlacklist.builder()
                .entityType(request.getEntityType())
                .entityValue(request.getEntityValue())
                .reason(request.getReason())
                .addedBy(addedBy)
                .build();
        blacklistRepository.save(entry);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }
}
