package com.aegispay.ai.triage.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads REAL log output and health status for the named service.
 *
 * <b>K8s mode</b> (detected via KUBERNETES_SERVICE_HOST env var):
 *   1. Calls the service's actuator health endpoint via K8s service DNS
 *      ({serviceName}.{namespace}.svc.cluster.local:{port}/actuator/health)
 *   2. Fetches recent pod logs via the K8s API server using the pod's
 *      ServiceAccount token (mounted at the standard path).
 *
 * <b>Local mode</b> (no KUBERNETES_SERVICE_HOST):
 *   1. Reads logs/{serviceName}.log from disk.
 *   2. Falls back to localhost:{port}/actuator/health.
 */
@Slf4j
@Component
public class LogReaderTool {

    private static final Map<String, Integer> SERVICE_PORTS = Map.of(
            "api-gateway",            8080,
            "user-service",           8081,
            "transaction-service",    8082,
            "ledger-service",         8083,
            "payment-orchestrator",   8084,
            "risk-engine",            8085,
            "notification-service",   8086,
            "reconciliation-service", 8087,
            "data-pipeline",          8089,
            "ai-platform",            8088
    );

    private final Path logsDir;
    private final String namespace;
    private final boolean k8sMode;
    private final String k8sApiBase;

    public LogReaderTool(
            @Value("${aegispay.triage.logs-dir:logs}") String logsDirPath,
            @Value("${K8S_NAMESPACE:aegispay}") String namespace) {
        this.logsDir = Paths.get(logsDirPath).toAbsolutePath();
        this.namespace = namespace;
        this.k8sMode = System.getenv("KUBERNETES_SERVICE_HOST") != null;
        String k8sHost = System.getenv("KUBERNETES_SERVICE_HOST");
        String k8sPort = System.getenv("KUBERNETES_SERVICE_PORT");
        this.k8sApiBase = (k8sHost != null && k8sPort != null)
                ? "https://" + k8sHost + ":" + k8sPort
                : "https://kubernetes.default.svc";
        log.info("LogReaderTool: k8s-mode={} namespace={}", k8sMode, namespace);
    }

    @Tool(description =
            "Read REAL recent logs and health status for an AegisPay service. " +
            "In K8s: queries the live actuator health endpoint via service DNS and fetches " +
            "pod logs from the K8s API. In local mode: reads log files and calls localhost actuator. " +
            "serviceName must be one of: api-gateway, user-service, transaction-service, " +
            "ledger-service, payment-orchestrator, risk-engine, notification-service, " +
            "reconciliation-service, data-pipeline, ai-platform. " +
            "tailLines controls how many log lines to return (default 100).")
    public String readLogs(String serviceName, int tailLines) {
        if (tailLines <= 0) tailLines = 100;
        log.info("LogReaderTool.readLogs: service={} tail={} k8s={}", serviceName, tailLines, k8sMode);
        StringBuilder sb = new StringBuilder();

        Integer port = SERVICE_PORTS.get(serviceName.toLowerCase());
        if (port == null) {
            return "Unknown service '" + serviceName + "'. Valid: " + SERVICE_PORTS.keySet();
        }

        if (k8sMode) {
            // ── K8s: actuator health via service DNS ─────────────────────────
            String healthUrl = "http://" + serviceName + "." + namespace + ".svc.cluster.local:" + port;
            sb.append("=== ACTUATOR HEALTH (").append(healthUrl).append(") ===\n");
            sb.append(fetchHttp(healthUrl, "/actuator/health", null)).append("\n\n");

            // ── K8s: pod logs via K8s API ─────────────────────────────────────
            sb.append("=== POD LOGS (K8s API, last ").append(tailLines).append(" lines) ===\n");
            sb.append(fetchK8sLogs(serviceName, tailLines)).append("\n");
        } else {
            // ── Local: log file ───────────────────────────────────────────────
            String logContent = readLogFile(serviceName, tailLines);
            if (logContent != null && !logContent.isBlank()) {
                sb.append("=== LOG FILE (last ").append(tailLines).append(" lines) ===\n");
                sb.append(logContent).append("\n\n");
            } else {
                sb.append("=== LOG FILE: not found for ").append(serviceName).append(" ===\n\n");
            }
            // ── Local: actuator health via localhost ──────────────────────────
            sb.append("=== ACTUATOR HEALTH (localhost:").append(port).append(") ===\n");
            sb.append(fetchHttp("http://localhost:" + port, "/actuator/health", null)).append("\n");
        }
        return sb.toString();
    }

    // ── K8s helpers ───────────────────────────────────────────────────────────

    private String fetchK8sLogs(String serviceName, int tailLines) {
        try {
            String token = readServiceAccountToken();
            if (token == null) return "(no service account token — RBAC not configured)";

            // Step 1: find running pod for service
            String podListUrl = k8sApiBase + "/api/v1/namespaces/" + namespace
                    + "/pods?labelSelector=app.kubernetes.io%2Fname%3D" + serviceName
                    + "&fieldSelector=status.phase%3DRunning&limit=1";

            WebClient client = WebClient.builder()
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024))
                    .build();

            @SuppressWarnings("unchecked")
            Map<String, Object> podList = client.get().uri(podListUrl)
                    .retrieve().bodyToMono(Map.class).block(Duration.ofSeconds(10));

            if (podList == null) return "(no response from K8s API)";
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) podList.get("items");
            if (items == null || items.isEmpty()) return "(no running pod found for " + serviceName + ")";

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) items.get(0).get("metadata");
            String podName = (String) metadata.get("name");

            // Step 2: fetch pod logs
            String logUrl = k8sApiBase + "/api/v1/namespaces/" + namespace
                    + "/pods/" + podName + "/log?tailLines=" + tailLines + "&timestamps=true";
            String logs = client.get().uri(logUrl)
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(15));

            return "Pod: " + podName + "\n" + (logs != null ? logs : "(empty)");
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 403) {
                return "(403 Forbidden — grant ai-platform ServiceAccount get/list on pods and pods/log)";
            }
            return "(K8s API error " + e.getStatusCode() + ": " + e.getResponseBodyAsString().substring(0, Math.min(200, e.getResponseBodyAsString().length())) + ")";
        } catch (Exception e) {
            log.warn("LogReaderTool K8s logs failed: {}", e.getMessage());
            return "(K8s log fetch failed: " + e.getMessage() + ")";
        }
    }

    private String readServiceAccountToken() {
        try {
            Path tokenPath = Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/token");
            if (!Files.exists(tokenPath)) return null;
            return Files.readString(tokenPath).trim();
        } catch (IOException e) {
            return null;
        }
    }

    // ── Local helpers ─────────────────────────────────────────────────────────

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

    private String fetchHttp(String baseUrl, String path, String token) {
        try {
            WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);
            if (token != null) builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            String body = builder.build().get().uri(path)
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(5));
            return body != null ? body : "(empty response)";
        } catch (WebClientResponseException e) {
            return "HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString();
        } catch (Exception e) {
            return "Unreachable (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
        }
    }
}