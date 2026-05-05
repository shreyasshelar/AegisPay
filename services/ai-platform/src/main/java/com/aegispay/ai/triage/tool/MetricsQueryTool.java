package com.aegispay.ai.triage.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MetricsQueryTool {

    @Tool(description = "Query current Prometheus metrics for a service. Returns p99 latency, error rate, throughput, and saturation signals.")
    public String queryMetrics(String serviceName) {
        log.info("MetricsQueryTool: querying metrics for service={}", serviceName);

        // In production: query Prometheus HTTP API with PromQL.
        return """
                Metrics snapshot for service '%s':
                http_server_requests_seconds{p99}       = 4.23s  (threshold: 2s)  ⚠ ABOVE THRESHOLD
                http_server_requests_error_rate         = 12.4%%  (threshold: 1%%) ⚠ ABOVE THRESHOLD
                http_server_requests_throughput_rps     = 287
                jvm_memory_used_bytes{heap}             = 1.82 GB / 2 GB (91%%)   ⚠ HIGH
                db_connection_pool_active               = 47 / 50                 ⚠ NEAR SATURATION
                kafka_consumer_lag{topic=payment.*}     = 1423                    ⚠ GROWING
                circuit_breaker_state{payment-gateway}  = OPEN
                """.formatted(serviceName);
    }
}
