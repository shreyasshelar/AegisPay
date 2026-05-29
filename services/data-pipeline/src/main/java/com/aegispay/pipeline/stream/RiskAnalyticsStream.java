package com.aegispay.pipeline.stream;

import com.aegispay.pipeline.sink.ClickHouseSink;
import com.aegispay.pipeline.sink.ClickHouseSink.RiskAssessmentRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Sentinel: all-zeros nil UUID used when a required field is absent from the payload.
// Rows with this userId are trivially queryable and can be quarantined / backfilled.
// Using UUID.randomUUID() instead (the old pattern) creates untraceable orphaned rows.

/**
 * Kafka Streams topology for risk-assessment events.
 *
 * <p>Consumes the {@code risk.assessed} topic and converts each message into a
 * {@link RiskAssessmentRecord} that is enqueued in the {@link ClickHouseSink}
 * for periodic flush to ClickHouse.
 *
 * <p>The {@code RiskAssessedEvent} payload (as JSON) has the shape:
 * <pre>
 * {
 *   "transactionId": "...",
 *   "userId": "...",
 *   "riskScore": 72,
 *   "decision": "APPROVED" | "REJECTED" | "REVIEW",
 *   "ruleFlags": ["HIGH_VELOCITY", "NEW_DEVICE"]
 * }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskAnalyticsStream {

    private static final String TOPIC_RISK_ASSESSED = "risk.assessed";

    private final StreamsBuilder streamsBuilder;
    private final ClickHouseSink clickHouseSink;

    private final ObjectMapper objectMapper = buildObjectMapper();

    @PostConstruct
    public void buildTopology() {
        Consumed<String, String> stringConsumed =
                Consumed.with(Serdes.String(), Serdes.String());

        KStream<String, String> riskStream =
                streamsBuilder.stream(TOPIC_RISK_ASSESSED, stringConsumed);

        riskStream.foreach((key, value) -> {
            try {
                Map<String, Object> payload = parseJson(value);
                if (payload == null) return;

                UUID   transactionId = uuidFrom(payload, "transactionId");
                UUID   userId        = uuidFrom(payload, "userId");
                int    riskScore     = intFrom(payload, "riskScore");
                String decision      = stringFrom(payload, "decision", "UNKNOWN");
                List<String> ruleFlags = ruleFlagsFrom(payload);
                Instant eventTime    = Instant.now(); // risk events typically don't carry their own timestamp

                RiskAssessmentRecord record = new RiskAssessmentRecord(
                        transactionId,
                        userId,
                        riskScore,
                        decision,
                        ruleFlags,
                        eventTime
                );

                clickHouseSink.writeRiskAssessment(record);

                if (log.isDebugEnabled()) {
                    log.debug("Processed risk.assessed: transactionId={}, decision={}, riskScore={}, flags={}",
                            transactionId, decision, riskScore, ruleFlags);
                }

            } catch (Exception ex) {
                log.error("Error processing risk.assessed message key={}: {}", key, ex.getMessage(), ex);
            }
        });

        log.info("RiskAnalyticsStream topology built: topic={}", TOPIC_RISK_ASSESSED);
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            log.warn("Received null/blank message on risk.assessed — skipping");
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            log.error("Failed to parse risk.assessed JSON: {} — value={}", ex.getMessage(), json);
            return null;
        }
    }

    private static final UUID NIL_UUID = new UUID(0, 0);

    private UUID uuidFrom(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) {
            log.warn("Missing required UUID field '{}' in risk.assessed payload — storing nil UUID sentinel", key);
            return NIL_UUID;
        }
        try {
            return val instanceof UUID u ? u : UUID.fromString(val.toString());
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid UUID value for field '{}': '{}' — storing nil UUID sentinel", key, val);
            return NIL_UUID;
        }
    }

    private int intFrom(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return 0;
        return val instanceof Number n ? n.intValue() : Integer.parseInt(val.toString());
    }

    private String stringFrom(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> ruleFlagsFrom(Map<String, Object> map) {
        Object val = map.get("ruleFlags");
        if (val == null) return Collections.emptyList();
        if (val instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .toList();
        }
        // Handle the unlikely case where it arrives as a comma-separated string
        return List.of(val.toString().split(","));
    }

    private static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
