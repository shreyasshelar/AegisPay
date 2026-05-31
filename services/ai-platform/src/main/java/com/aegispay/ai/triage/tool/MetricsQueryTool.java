package com.aegispay.ai.triage.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Queries REAL live metrics from two sources:
 *  1. Spring Boot Actuator /actuator/metrics endpoint on each service.
 *  2. ClickHouse aegispay_analytics tables for payment-level SLA and saga data.
 *
 * No Prometheus dependency required — every service already exposes
 * metrics via the Spring Boot Actuator HTTP API.
 */
@Slf4j
@Component
public class MetricsQueryTool {

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

    /** Actuator metric names we pull for each service. */
    private static final List<String> ACTUATOR_METRICS = List.of(
            "http.server.requests",
            "jvm.memory.used",
            "jvm.threads.live",
            "hikaricp.connections.active",
            "hikaricp.connections.max",
            "kafka.consumer.fetch.manager.records.lag"
    );

    @Tool(description =
            "Query REAL live metrics for an AegisPay service. " +
            "Reads from the service's Spring Boot Actuator /actuator/metrics endpoint " +
            "and from ClickHouse for saga latency and transaction success rate data. " +
            "serviceName must match one of the running services.")
    public String queryMetrics(String serviceName) {
        log.info("MetricsQueryTool.queryMetrics: service={}", serviceName);
        StringBuilder sb = new StringBuilder();

        // ── Actuator metrics ─────────────────────────────────────────────────────
        Integer port = SERVICE_PORTS.get(serviceName.toLowerCase());
        if (port != null) {
            sb.append("=== ACTUATOR METRICS (live: http://localhost:").append(port).append(") ===\n");
            for (String metric : ACTUATOR_METRICS) {
                String value = fetchMetric(port, metric);
                if (value != null) {
                    sb.append(metric).append(": ").append(value).append("\n");
                }
            }
        } else {
            sb.append("Unknown service '").append(serviceName).append("' — no port mapping.\n");
        }

        // ── ClickHouse analytics (payment-level data) ────────────────────────────
        sb.append("\n=== CLICKHOUSE ANALYTICS (last 1 hour) ===\n");
        sb.append(queryClickHouse());

        return sb.toString();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private String fetchMetric(int port, String metricName) {
        try {
            WebClient client = WebClient.builder()
                    .baseUrl("http://localhost:" + port)
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = client.get()
                    .uri("/actuator/metrics/{name}", metricName)
                    .retrieve()
                    .bodyToMono(Map.class)
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
            return null; // metric not exposed or service down
        }
    }

    private String queryClickHouse() {
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
            ds.setUrl("jdbc:clickhouse://localhost:8123/aegispay_analytics");
            JdbcTemplate ch = new JdbcTemplate(ds);

            StringBuilder result = new StringBuilder();

            // Transaction success rate (last hour)
            try {
                List<Map<String, Object>> txStats = ch.queryForList(
                        "SELECT status, count() AS cnt " +
                        "FROM transaction_facts " +
                        "WHERE event_time >= now() - INTERVAL 1 HOUR " +
                        "GROUP BY status ORDER BY cnt DESC");
                result.append("transaction_facts (last 1h): ");
                if (txStats.isEmpty()) {
                    result.append("no data\n");
                } else {
                    for (Map<String, Object> row : txStats) {
                        result.append(row.get("status")).append("=").append(row.get("cnt")).append(" ");
                    }
                    result.append("\n");
                }
            } catch (Exception e) {
                result.append("transaction_facts: ").append(e.getMessage()).append("\n");
            }

            // Saga latency p50/p99 (last hour)
            try {
                Map<String, Object> latency = ch.queryForMap(
                        "SELECT quantile(0.5)(latency_ms) AS p50_ms, " +
                        "       quantile(0.99)(latency_ms) AS p99_ms, " +
                        "       count() AS total " +
                        "FROM saga_latencies " +
                        "WHERE completed_at >= now() - INTERVAL 1 HOUR");
                result.append("saga_latencies (last 1h): p50=")
                        .append(latency.get("p50_ms")).append("ms p99=")
                        .append(latency.get("p99_ms")).append("ms total=")
                        .append(latency.get("total")).append("\n");
            } catch (Exception e) {
                result.append("saga_latencies: ").append(e.getMessage()).append("\n");
            }

            // Risk assessments breakdown (last hour)
            try {
                List<Map<String, Object>> riskStats = ch.queryForList(
                        "SELECT decision, count() AS cnt " +
                        "FROM risk_assessments " +
                        "WHERE assessed_at >= now() - INTERVAL 1 HOUR " +
                        "GROUP BY decision ORDER BY cnt DESC");
                result.append("risk_assessments (last 1h): ");
                if (riskStats.isEmpty()) {
                    result.append("no data\n");
                } else {
                    for (Map<String, Object> row : riskStats) {
                        result.append(row.get("decision")).append("=").append(row.get("cnt")).append(" ");
                    }
                    result.append("\n");
                }
            } catch (Exception e) {
                result.append("risk_assessments: ").append(e.getMessage()).append("\n");
            }

            return result.length() > 0 ? result.toString() : "(no ClickHouse data)\n";
        } catch (Exception e) {
            return "ClickHouse unreachable: " + e.getMessage() + "\n";
        }
    }
}
