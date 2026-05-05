package com.aegispay.ai.triage;

import com.aegispay.ai.audit.AiAuditService;
import com.aegispay.ai.triage.tool.DeploymentHistoryTool;
import com.aegispay.ai.triage.tool.LogReaderTool;
import com.aegispay.ai.triage.tool.MetricsQueryTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class IncidentTriageAgent {

    private static final String SYSTEM_PROMPT = """
            You are an expert SRE incident triage assistant for AegisPay, a fintech payment platform.
            An incident has been reported for service '{serviceName}': {incidentDescription}

            Investigate by calling the available tools (readLogs, queryMetrics, getDeploymentHistory)
            in a logical order. After gathering evidence, produce a structured root cause analysis:
            1. Root Cause
            2. Contributing Factors
            3. Immediate Mitigation Steps
            4. Long-term Remediation

            Reference specific log lines, metric values, and deployment entries you observed.
            """;

    private final ChatClient chatClient;
    private final AiAuditService auditService;

    public IncidentTriageAgent(ChatClient.Builder chatClientBuilder,
                               AiAuditService auditService,
                               LogReaderTool logReaderTool,
                               MetricsQueryTool metricsQueryTool,
                               DeploymentHistoryTool deploymentHistoryTool) {
        this.chatClient = chatClientBuilder
                .defaultTools(logReaderTool, metricsQueryTool, deploymentHistoryTool)
                .build();
        this.auditService = auditService;
    }

    public TriageReport triage(String serviceName, String incidentDescription) {
        long start = System.currentTimeMillis();
        String output = null;
        String error = null;

        try {
            String prompt = SYSTEM_PROMPT
                    .replace("{serviceName}", serviceName)
                    .replace("{incidentDescription}", incidentDescription);

            output = chatClient.prompt(prompt).call().content();
            return new TriageReport(serviceName, incidentDescription, output);

        } catch (Exception e) {
            error = e.getMessage();
            log.error("IncidentTriageAgent failed for service={}: {}", serviceName, e.getMessage(), e);
            throw new RuntimeException("Incident triage failed: " + e.getMessage(), e);
        } finally {
            long latencyMs = System.currentTimeMillis() - start;
            auditService.log("INCIDENT_TRIAGE",
                    serviceName + ": " + incidentDescription,
                    output, "claude-sonnet-4-6", latencyMs, error);
        }
    }

    public record TriageReport(String serviceName, String incidentDescription, String analysis) {}
}
