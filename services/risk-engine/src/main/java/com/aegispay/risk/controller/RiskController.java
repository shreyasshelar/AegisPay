package com.aegispay.risk.controller;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.common.domain.dto.PagedResponse;
import com.aegispay.common.domain.enums.RiskDecision;
import com.aegispay.risk.domain.dto.BlacklistRequest;
import com.aegispay.risk.domain.dto.RiskCaseResponse;
import com.aegispay.risk.domain.entity.FraudBlacklist;
import com.aegispay.risk.domain.entity.RiskCase;
import com.aegispay.risk.exception.RiskCaseNotFoundException;
import com.aegispay.risk.repository.FraudBlacklistRepository;
import com.aegispay.risk.repository.RiskCaseRepository;
import jakarta.validation.Valid;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskCaseRepository riskCaseRepository;
    private final FraudBlacklistRepository blacklistRepository;

    // ── List all risk cases ────────────────────────────────────────────────────

    /**
     * Lists risk cases with optional server-side filtering.
     *
     * <p>All filter params are optional.  Combining them works as AND:
     * <ul>
     *   <li>{@code decision}  — APPROVED | REVIEW | REJECTED</li>
     *   <li>{@code minScore}  — inclusive lower bound (0-100)</li>
     *   <li>{@code maxScore}  — inclusive upper bound (0-100)</li>
     *   <li>{@code fromDate}  — ISO-8601 datetime, e.g. {@code 2026-05-01T00:00:00Z}</li>
     *   <li>{@code toDate}    — ISO-8601 datetime, e.g. {@code 2026-05-31T23:59:59Z}</li>
     * </ul>
     */
    @GetMapping("/cases")
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN', 'MERCHANT_OPS')")
    public ResponseEntity<ApiResponse<PagedResponse<RiskCaseResponse>>> listCases(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String decision,
            @RequestParam(required = false)    Integer minScore,
            @RequestParam(required = false)    Integer maxScore,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate) {

        // ── Build Specification dynamically ──────────────────────────────────
        // Using JpaSpecificationExecutor avoids the Hibernate JPQL null-enum issue:
        // JPQL ":param IS NULL" fails when the param is an enum type because Hibernate
        // can't infer the SQL type for a null binding.  Specifications skip the predicate
        // entirely when the filter value is absent — no null is ever bound.
        Specification<RiskCase> spec = Specification.where(null);

        if (decision != null && !decision.isBlank()) {
            try {
                RiskDecision dec = RiskDecision.valueOf(decision.toUpperCase());
                spec = spec.and((root, q, cb) -> cb.equal(root.get("decision"), dec));
            } catch (IllegalArgumentException ignored) {
                // unknown decision value → no filter applied
            }
        }
        if (minScore != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("riskScore"), minScore));
        }
        if (maxScore != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("riskScore"), maxScore));
        }
        if (fromDate != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
        }
        if (toDate != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
        }

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RiskCase> resultPage = riskCaseRepository.findAll(spec, pageable);

        Page<RiskCaseResponse> mapped = resultPage.map(rc -> RiskCaseResponse.builder()
                .id(rc.getId())
                .transactionId(rc.getTransactionId())
                .userId(rc.getUserId())
                .riskScore(rc.getRiskScore())
                .decision(rc.getDecision().name())
                .ruleFlags(rc.getRuleFlags())
                .ragExplanation(rc.getRagExplanation())
                .createdAt(rc.getCreatedAt())
                .build());

        PagedResponse<RiskCaseResponse> response = PagedResponse.<RiskCaseResponse>builder()
                .content(mapped.getContent())
                .page(mapped.getNumber())
                .size(mapped.getSize())
                .totalElements(mapped.getTotalElements())
                .totalPages(mapped.getTotalPages())
                .first(mapped.isFirst())
                .last(mapped.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ── Single case by transaction ID ─────────────────────────────────────────

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
