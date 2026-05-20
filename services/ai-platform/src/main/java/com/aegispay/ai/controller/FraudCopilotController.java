package com.aegispay.ai.controller;

import com.aegispay.ai.fraud.FraudCopilotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/ai/fraud")
@RequiredArgsConstructor
public class FraudCopilotController {

    private final FraudCopilotService fraudCopilotService;

    @PostMapping("/explain")
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN', 'MERCHANT_OPS')")
    public ResponseEntity<ExplainResponse> explain(@Valid @RequestBody ExplainRequest request) {
        String explanation = fraudCopilotService.explain(
                request.transactionId(), request.riskScore(), request.flaggedRules());
        return ResponseEntity.ok(new ExplainResponse(request.transactionId(), explanation));
    }

    public record ExplainRequest(
            @NotNull UUID transactionId,
            @Min(0) @Max(100) int riskScore,
            @NotEmpty List<String> flaggedRules
    ) {}

    public record ExplainResponse(UUID transactionId, String explanation) {}
}
