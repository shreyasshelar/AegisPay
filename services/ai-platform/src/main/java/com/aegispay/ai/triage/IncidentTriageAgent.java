package com.aegispay.ai.triage;

import com.aegispay.ai.audit.AiAuditService;
import com.aegispay.ai.triage.tool.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class IncidentTriageAgent {

    private static final String SYSTEM_PROMPT = """
            You are an expert SRE and autonomous incident response agent for AegisPay, a production
            fintech payment platform. An incident has been reported for service '{serviceName}':
            {incidentDescription}

            ━━━ INVESTIGATION PHASE ━━━
            You MUST call ALL THREE investigation tools before drawing any conclusions.
            The tools return REAL live data from production — actual pod logs, live actuator metrics,
            real ClickHouse analytics, and GitHub commit history. Do NOT invent or assume facts.

            Investigation order (mandatory):
            1. readLogs('{serviceName}', 100) — pod logs + actuator health
            2. queryMetrics('{serviceName}') — HTTP metrics, JVM, DB pool, circuit breakers, ClickHouse
            3. getDeploymentHistory('{serviceName}') — recent commits that may have caused this

            ━━━ ACTION PHASE ━━━
            After investigation, you MAY take the following autonomous actions if the evidence
            clearly justifies them:

            • restartDeployment(serviceName, reason)
              Use when: pod is in CrashLoopBackOff, OOMKilled, or logs show unrecoverable state.
              Do NOT use for: configuration errors, DB migration failures (restart won't help).
              Always state your reason explicitly — it will be annotated on the pod.

            • checkAndResetCircuitBreaker(serviceName, cbName)
              Use when: circuit breaker is OPEN due to a transient dependency outage that is now
              resolved, or when the underlying issue (e.g. DB restart) has been fixed.
              Do NOT reset a breaker that is protecting from an ongoing outage.

            ━━━ REPORT FORMAT ━━━
            After gathering all tool results (and taking any justified actions), produce:

            1. **Root Cause** — based ONLY on what the tools returned. Quote specific log lines.
            2. **Contributing Factors** — metric values, recent commits, circuit breaker states.
            3. **Actions Taken** — list any restartDeployment or circuitBreaker resets you triggered,
               with the exact reason you provided. If no actions were taken, say why.
            4. **Immediate Mitigation** — specific steps for the on-call engineer if actions were
               insufficient or if action is outside the agent's scope.
            5. **Long-term Remediation** — prevent recurrence.

            If tools returned no useful data for a section, say "No data available" — never guess.
            Quote specific log lines, metric numbers, and commit SHAs you actually observed.
            """;

    private final ChatClient chatClient;
    private final AiAuditService auditService;

    public IncidentTriageAgent(ChatClient.Builder chatClientBuilder,
                               AiAuditService auditService,
                               LogReaderTool logReaderTool,
                               MetricsQueryTool metricsQueryTool,
                               DeploymentHistoryTool deploymentHistoryTool,
                               PodRestartTool podRestartTool,
                               CircuitBreakerResetTool circuitBreakerResetTool) {
        this.chatClient = chatClientBuilder
                .defaultTools(logReaderTool, metricsQueryTool, deploymentHistoryTool,
                               podRestartTool, circuitBreakerResetTool)
                .build();
        this.auditService = auditService;
    }

    /**
     * Run AI-assisted incident triage. The agent will:
     * 1. Investigate using read-only tools (logs, metrics, history)
     * 2. Optionally take autonomous remediation actions (pod restart, CB reset)
     * 3. Return a structured report with all findings and actions taken
     *
     * <p>Resilience:
     * <ul>
     *   <li>{@code @CircuitBreaker} — opens after 50% failure rate; fallback returns manual steps
     *   <li>{@code @Retry} — 2 retries with exponential backoff for transient failures
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
            throw new RuntimeException("Triage agent failed: " + e.getMessage(), e);
        } finally {
            long latencyMs = System.currentTimeMillis() - start;
            auditService.log("INCIDENT_TRIAGE",
                    serviceName + ": " + incidentDescription,
                    output, "triage-agent", latencyMs, error);
        }
    }

    @SuppressWarnings("unused")
    TriageReport triageFallback(String serviceName, String incidentDescription, Throwable cause) {
        log.warn("IncidentTriageAgent fallback triggered for service={}: {}",
                serviceName, cause != null ? cause.getMessage() : "circuit open");
        String fallback = """
                ⚠ Triage agent is temporarily unavailable (AI service error or circuit open).

                **Immediate manual steps:**
                1. Check service logs: `kubectl logs -l app.kubernetes.io/name=%s -n aegispay --tail=100`
                2. Check pod status: `kubectl get pods -n aegispay -l app.kubernetes.io/name=%s`
                3. Check recent commits: `git log --oneline -10`
                4. Check Kafka consumer lag in Kafka UI
                5. Check circuit breakers: `kubectl exec -n aegispay deploy/%s -- curl -s localhost:%d/actuator/circuitbreakers`
                6. Inspect Grafana → AegisPay SLA & Latency dashboard

                **Error details:** %s
                """.formatted(serviceName, serviceName, serviceName, 8080,
                cause != null && cause.getMessage() != null ? cause.getMessage() : "Unknown error");
        return new TriageReport(serviceName, incidentDescription, fallback, true);
    }

    public record TriageReport(String serviceName, String incidentDescription,
                               String analysis, boolean degraded) {}
}