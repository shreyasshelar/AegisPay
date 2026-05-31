package com.aegispay.ai.triage.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads REAL log output for the named service.
 *
 * Strategy (in priority order):
 *  1. Read the last N lines of logs/{serviceName}.log from the project root.
 *     (Spring Boot file appender writes here when the bat-script redirect is active.)
 *  2. If the file is empty or absent, call the service's Spring Boot Actuator
 *     /actuator/health endpoint so the agent has live health status.
 *  3. Annotate what was read so the LLM knows whether it is real or partial data.
 */
@Slf4j
@Component
public class LogReaderTool {

    /** Mapping from service name → management/actuator port. */
    private static final Map<String, Integer> SERVICE_PORTS = Map.of(
            "api-gateway",            8190,
            "user-service",           8081,
            "transaction-service",    8082,
            "ledger-service",         8083,
            "payment-orchestrator",   8084,
            "risk-engine",            8085,
            "notification-service",   8086,
            "reconciliation-service", 8087,
            "data-pipeline",          8089,
            "ai-platform",            8091
    );

    private final Path logsDir;

    public LogReaderTool(
            @Value("${aegispay.triage.logs-dir:logs}") String logsDirPath) {
        this.logsDir = Paths.get(logsDirPath).toAbsolutePath();
    }

    @Tool(description =
            "Read REAL recent logs for an AegisPay service. " +
            "Queries the actual log file on disk and the service's live actuator health endpoint. " +
            "serviceName must be one of: api-gateway, user-service, transaction-service, " +
            "ledger-service, payment-orchestrator, risk-engine, notification-service, " +
            "reconciliation-service, data-pipeline, ai-platform. " +
            "windowMinutes is ignored — returns the last 200 log lines or actuator health.")
    public String readLogs(String serviceName, int windowMinutes) {
        log.info("LogReaderTool.readLogs: service={} window={}m", serviceName, windowMinutes);
        StringBuilder sb = new StringBuilder();

        // ── 1. Log file ──────────────────────────────────────────────────────────
        String logContent = readLogFile(serviceName, 200);
        if (logContent != null && !logContent.isBlank()) {
            sb.append("=== LOG FILE (last 200 lines of logs/").append(serviceName).append(".log) ===\n");
            sb.append(logContent).append("\n");
        } else {
            sb.append("=== LOG FILE: empty or not found for ").append(serviceName).append(" ===\n");
            sb.append("(Spring Boot is logging to the console window — file appender may not be active)\n\n");
        }

        // ── 2. Live actuator health ──────────────────────────────────────────────
        Integer port = SERVICE_PORTS.get(serviceName.toLowerCase());
        if (port != null) {
            String health = fetchActuatorHealth(serviceName, port);
            sb.append("=== ACTUATOR HEALTH (live: http://localhost:").append(port).append("/actuator/health) ===\n");
            sb.append(health).append("\n");
        } else {
            sb.append("=== ACTUATOR HEALTH: unknown service '").append(serviceName).append("' — no port mapping ===\n");
        }

        return sb.toString();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private String readLogFile(String serviceName, int tailLines) {
        Path logFile = logsDir.resolve(serviceName + ".log");
        if (!Files.exists(logFile)) return null;
        try {
            List<String> lines;
            try (Stream<String> stream = Files.lines(logFile)) {
                lines = stream.collect(Collectors.toList());
            }
            if (lines.isEmpty()) return null;
            int start = Math.max(0, lines.size() - tailLines);
            return lines.subList(start, lines.size()).stream()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            log.warn("LogReaderTool: failed to read {}: {}", logFile, e.getMessage());
            return null;
        }
    }

    private String fetchActuatorHealth(String serviceName, int port) {
        try {
            WebClient client = WebClient.builder()
                    .baseUrl("http://localhost:" + port)
                    .build();
            String body = client.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(java.time.Duration.ofSeconds(5));
            return body != null ? body : "(empty response)";
        } catch (WebClientResponseException e) {
            return "HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString();
        } catch (Exception e) {
            return "Unreachable (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
        }
    }
}
