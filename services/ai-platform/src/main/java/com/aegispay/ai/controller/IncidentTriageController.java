package com.aegispay.ai.controller;

import com.aegispay.ai.triage.IncidentTriageAgent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/incidents")
@RequiredArgsConstructor
public class IncidentTriageController {

    private final IncidentTriageAgent incidentTriageAgent;

    @PostMapping("/triage")
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'ADMIN')")
    public ResponseEntity<IncidentTriageAgent.TriageReport> triage(
            @Valid @RequestBody TriageRequest request) {
        IncidentTriageAgent.TriageReport report =
                incidentTriageAgent.triage(request.serviceName(), request.incidentDescription());
        return ResponseEntity.ok(report);
    }

    public record TriageRequest(
            @NotBlank String serviceName,
            @NotBlank String incidentDescription
    ) {}
}
