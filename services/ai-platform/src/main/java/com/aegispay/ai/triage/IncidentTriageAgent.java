package com.aegispay.ai.triage;

import com.aegispay.ai.audit.AiAuditService;
import com.aegispay.ai.triage.tool.DeploymentHistoryTool;
import com.aegispay.ai.triage.tool.LogReaderTool;
import com.aegispay.ai.triage.tool.MetricsQueryTool;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class IncidentTriageAgent {

    private static final String SYSTEM_PROMPT = """
            You are an expert SRE incident triage assistant for AegisPay, a fintech payment platform.
            An incident has been reported for service '{serviceName}': {incidentDescription}

            IMPORTANT: You MUST call the available tools before drawing any conclusions.
            The tools return REAL live data — actual log files, live actuator health endpoints,
            real ClickHouse metrics, and real git history. Do NOT invent or assume any facts.
            If a tool returns empty data, say so explicitly — do not fill gaps with guesses.

            Investigate in this order:
            1. Call readLogs('{serviceName}', 30) — read actual log output and health status
            2. Call queryMetrics('{serviceName}') — read live HTTP metrics, JVM, DB pool, ClickHouse analytics
            3. Call getDeploymentHistory('{serviceName}') — read real git commits for recent changes

            After gathering all tool results, produce a structured root cause analysis:
            1. Root Cause — based ONLY on what the tools returned
            2. Contributing Factors — from actual log lines / metric values / commits you observed
            3. Immediate Mitigation Steps — specific to what you found
            4. Long-term Remediation

            If the tools returned no useful data for a section, say "No data available" rather than guessing.
            Quote specific log lines, metric numbers, and commit hashes you actually observed.
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

    /**
     * Run AI-assisted incident triage for the given service.
     *
     * <p>Resilience decorators:
     * <ul>
     *   <li>{@code @CircuitBreaker} — opens after 50% failure rate on a 5-call window; fallback
     *       returns a structured degraded report with manual remediation steps so the admin is
     *       never left with a raw 500.
     *   <li>{@code @Retry} — 2 retries with 1 s exponential backoff on transient failures
     *       (rate-limit bursts, transient network blips).
     * </ul>
     */
    @CircuitBreaker(name = "triage-agent", fallbackMethod = "triageFallback")
    @Retry(name = "triage-agent")
    public TriageReport triage(String serviceName, String incidentDescription) {
        long start = System.currentTimeMillis();
        String output = null;
        String error = null;

        try {
            String prompt = SYSTEM_PROMPT
                    .replace("{serviceName}", serviceName)
                    .replace("{incidentDescription}", incidentDescription);

            output = chatClient.prompt(prompt).call().content();
            return new TriageReport(serviceName, incidentDescription, output, false);

        } catch (Exception e) {
            error = e.getMessage();
            log.error("IncidentTriageAgent failed for service={}: {}", serviceName, e.getMessage(), e);
            // Re-throw so @Retry can attempt again and @CircuitBreaker can track the failure.
            throw new RuntimeException("Triage agent failed: " + e.getMessage(), e);
        } finally {
            long latencyMs = System.currentTimeMillis() - start;
            auditService.log("INCIDENT_TRIAGE",
                    serviceName + ": " + incidentDescription,
                    output, "claude-sonnet-4-6", latencyMs, error);
        }
    }

    @SuppressWarnings("unused")   // called reflectively by Resilience4j
    TriageReport triageFallback(String serviceName, String incidentDescription, Throwable cause) {
        log.warn("IncidentTriageAgent fallback triggered for service={}: {}",
                serviceName, cause != null ? cause.getMessage() : "circuit open");
        String fallback = """
                ⚠ Triage agent is temporarily unavailable (AI service error or circuit open).

                **Immediate manual steps:**
                1. Check service logs: `kubectl logs -l app=%s --tail=200`
                2. Check recent deployments: `kubectl rollout history deployment/%s`
                3. Query Prometheus: `http_server_requests_seconds_count{service="%s",status="5xx"}`
                4. Check Kafka consumer lag in Kafka UI (http://localhost:8090)
                5. Inspect Grafana → AegisPay SLA & Latency dashboard

                **Error details:** %s
                """.formatted(serviceName, serviceName, serviceName,
                cause != null && cause.getMessage() != null ? cause.getMessage() : "Unknown error");
        return new TriageReport(serviceName, incidentDescription, fallback, true);
    }

    public record TriageReport(String serviceName, String incidentDescription, String analysis,
                               boolean degraded) {}
}
