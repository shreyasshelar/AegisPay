package com.aegispay.ai.triage.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Queries REAL live metrics for an AegisPay service.
 *
 * <b>K8s mode</b> (detected via KUBERNETES_SERVICE_HOST env var):
 *   Uses K8s service DNS: http://{serviceName}.{namespace}.svc.cluster.local:{port}/actuator/metrics
 *   ClickHouse: jdbc:clickhouse://clickhouse.{infraNamespace}.svc.cluster.local:8123
 *
 * <b>Local mode:</b>
 *   Uses localhost:{port}/actuator/metrics + localhost ClickHouse
 */
@Slf4j
@Component
public class MetricsQueryTool {

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

    private static final List<String> ACTUATOR_METRICS = List.of(
            "http.server.requests",
            "jvm.memory.used",
            "jvm.threads.live",
            "hikaricp.connections.active",
            "hikaricp.connections.max",
            "kafka.consumer.fetch.manager.records.lag"
    );

    private final String namespace;
    private final String infraNamespace;
    private final boolean k8sMode;

    public MetricsQueryTool(
            @Value("${K8S_NAMESPACE:aegispay}") String namespace,
            @Value("${INFRA_NAMESPACE:aegispay-infra}") String infraNamespace) {
        this.namespace = namespace;
        this.infraNamespace = infraNamespace;
        this.k8sMode = System.getenv("KUBERNETES_SERVICE_HOST") != null;
        log.info("MetricsQueryTool: k8s-mode={} ns={} infraNs={}", k8sMode, namespace, infraNamespace);
    }

    @Tool(description =
            "Query REAL live metrics for an AegisPay service. " +
            "In K8s: reads from the service's actuator endpoint via K8s service DNS. " +
            "Also queries ClickHouse for transaction success rate, saga latency p50/p99, " +
            "and risk assessment breakdown over the last hour. " +
            "serviceName must match one of the running services.")
    public String queryMetrics(String serviceName) {
        log.info("MetricsQueryTool.queryMetrics: service={} k8s={}", serviceName, k8sMode);
        StringBuilder sb = new StringBuilder();

        Integer port = SERVICE_PORTS.get(serviceName.toLowerCase());
        if (port == null) {
            sb.append("Unknown service '").append(serviceName).append("'. Valid: ").append(SERVICE_PORTS.keySet()).append("\n");
        } else {
            String baseUrl = k8sMode
                    ? "http://" + serviceName + "." + namespace + ".svc.cluster.local:" + port
                    : "http://localhost:" + port;

            sb.append("=== ACTUATOR METRICS (").append(baseUrl).append(") ===\n");
            for (String metric : ACTUATOR_METRICS) {
                String value = fetchMetric(baseUrl, metric);
                if (value != null) {
                    sb.append(metric).append(": ").append(value).append("\n");
                }
            }

            // Circuit breaker state
            sb.append("\n=== CIRCUIT BREAKERS ===\n");
            sb.append(fetchHttp(baseUrl, "/actuator/circuitbreakers")).append("\n");
        }

        // ── ClickHouse analytics ─────────────────────────────────────────────
        String chHost = k8sMode
                ? "clickhouse." + infraNamespace + ".svc.cluster.local"
                : "localhost";
        sb.append("\n=== CLICKHOUSE ANALYTICS (last 1h) from ").append(chHost).append(" ===\n");
        sb.append(queryClickHouse(chHost));

        return sb.toString();
    }

    private String fetchMetric(String baseUrl, String metricName) {
        try {
            WebClient client = WebClient.builder().baseUrl(baseUrl).build();
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = client.get()
                    .uri("/actuator/metrics/{name}", metricName)
                    .retrieve().bodyToMono(Map.class)
                    .block(Duration.ofSeconds(5));
            if (resp == null) return null;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> measurements = (List<Map<String, Object>>) resp.get("measurements");
            if (measurements == null || measurements.isEmpty()) return "(no measurements)";
            StringBuilder vals = new StringBuilder();
            for (Map<String, Object> m : measurements) {
                if (vals.length() > 0) vals.append(", ");
                vals.append(m.get("statistic")).append("=").append(m.get("value"));
            }
            return vals.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String fetchHttp(String baseUrl, String path) {
        try {
            String body = WebClient.builder().baseUrl(baseUrl).build()
                    .get().uri(path).retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(5));
            return body != null ? body : "(empty)";
        } catch (Exception e) {
            return "(unreachable: " + e.getMessage() + ")";
        }
    }

    private String queryClickHouse(String host) {
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
            ds.setUrl("jdbc:clickhouse://" + host + ":8123/aegispay_analytics");
            JdbcTemplate ch = new JdbcTemplate(ds);
            StringBuilder result = new StringBuilder();

            try {
                List<Map<String, Object>> txStats = ch.queryForList(
                        "SELECT status, count() AS cnt FROM transaction_facts " +
                        "WHERE event_time >= now() - INTERVAL 1 HOUR GROUP BY status ORDER BY cnt DESC");
                result.append("transaction_facts (last 1h): ");
                if (txStats.isEmpty()) result.append("no data\n");
                else { txStats.forEach(r -> result.append(r.get("status")).append("=").append(r.get("cnt")).append(" ")); result.append("\n"); }
            } catch (Exception e) { result.append("transaction_facts: ").append(e.getMessage()).append("\n"); }

            try {
                Map<String, Object> lat = ch.queryForMap(
                        "SELECT quantile(0.5)(latency_ms) AS p50_ms, quantile(0.99)(latency_ms) AS p99_ms, " +
                        "count() AS total FROM saga_latencies WHERE completed_at >= now() - INTERVAL 1 HOUR");
                result.append("saga_latencies (last 1h): p50=").append(lat.get("p50_ms"))
                        .append("ms p99=").append(lat.get("p99_ms")).append("ms total=").append(lat.get("total")).append("\n");
            } catch (Exception e) { result.append("saga_latencies: ").append(e.getMessage()).append("\n"); }

            try {
                List<Map<String, Object>> risk = ch.queryForList(
                        "SELECT decision, count() AS cnt FROM risk_assessments " +
                        "WHERE assessed_at >= now() - INTERVAL 1 HOUR GROUP BY decision ORDER BY cnt DESC");
                result.append("risk_assessments (last 1h): ");
                if (risk.isEmpty()) result.append("no data\n");
                else { risk.forEach(r -> result.append(r.get("decision")).append("=").append(r.get("cnt")).append(" ")); result.append("\n"); }
            } catch (Exception e) { result.append("risk_assessments: ").append(e.getMessage()).append("\n"); }

            return result.length() > 0 ? result.toString() : "(no ClickHouse data)\n";
        } catch (Exception e) {
            return "ClickHouse unreachable: " + e.getMessage() + "\n";
        }
    }
}