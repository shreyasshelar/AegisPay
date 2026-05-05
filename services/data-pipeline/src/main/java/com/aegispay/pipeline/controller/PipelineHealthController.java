package com.aegispay.pipeline.controller;

import com.aegispay.pipeline.sink.ClickHouseSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KafkaStreams;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight operational REST controller for the data-pipeline service.
 *
 * <p>Exposes {@code GET /api/v1/pipeline/status} with a JSON body describing:
 * <ul>
 *   <li>Overall service status</li>
 *   <li>Kafka Streams state (RUNNING, REBALANCING, ERROR, …)</li>
 *   <li>ClickHouse connectivity (SELECT 1 probe)</li>
 *   <li>Total records flushed to ClickHouse since startup</li>
 * </ul>
 *
 * <p>This endpoint is intentionally lightweight and does not block on any
 * external call beyond the quick ClickHouse ping.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/pipeline")
@RequiredArgsConstructor
public class PipelineHealthController {

    /** Factory bean that owns the Kafka Streams instance. */
    private final StreamsBuilderFactoryBean streamsBuilderFactoryBean;

    /** Named ClickHouse JdbcTemplate — see {@code ClickHouseConfig}. */
    private final JdbcTemplate clickHouseJdbcTemplate;

    /** Sink for reporting total-flushed counter. */
    private final ClickHouseSink clickHouseSink;

    /**
     * Returns a JSON snapshot of the pipeline's runtime state.
     *
     * <p>HTTP 200 is always returned (even when degraded) so that load-balancer
     * health checks remain stable. Use {@code /actuator/health} for a
     * comprehensive liveness/readiness check.
     *
     * @return JSON map with keys: {@code status}, {@code kafkaStreamsState},
     *         {@code clickhouseConnected}, {@code totalFlushedRecords}
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        String kafkaState     = resolveKafkaStreamsState();
        boolean clickHouseOk  = probeClickHouse();
        String overallStatus  = deriveOverallStatus(kafkaState, clickHouseOk);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", overallStatus);
        body.put("kafkaStreamsState", kafkaState);
        body.put("clickhouseConnected", clickHouseOk);
        body.put("totalFlushedRecords", clickHouseSink.getTotalFlushed());

        log.debug("Pipeline status: {}", body);
        return ResponseEntity.ok(body);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String resolveKafkaStreamsState() {
        try {
            KafkaStreams streams = streamsBuilderFactoryBean.getKafkaStreams();
            if (streams == null) {
                return "NOT_STARTED";
            }
            return streams.state().name();
        } catch (Exception ex) {
            log.warn("Could not retrieve Kafka Streams state: {}", ex.getMessage());
            return "UNKNOWN";
        }
    }

    private boolean probeClickHouse() {
        try {
            Integer result = clickHouseJdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(result);
        } catch (Exception ex) {
            log.warn("ClickHouse connectivity probe failed: {}", ex.getMessage());
            return false;
        }
    }

    private String deriveOverallStatus(String kafkaState, boolean clickHouseOk) {
        boolean kafkaRunning = "RUNNING".equalsIgnoreCase(kafkaState)
                || "REBALANCING".equalsIgnoreCase(kafkaState);

        if (kafkaRunning && clickHouseOk) {
            return "RUNNING";
        }
        if (kafkaRunning) {
            return "DEGRADED_CLICKHOUSE_UNAVAILABLE";
        }
        if (clickHouseOk) {
            return "DEGRADED_KAFKA_STREAMS_NOT_RUNNING";
        }
        return "DEGRADED";
    }
}
