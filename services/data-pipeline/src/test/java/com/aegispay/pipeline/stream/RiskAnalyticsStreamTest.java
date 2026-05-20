package com.aegispay.pipeline.stream;

import com.aegispay.pipeline.sink.ClickHouseSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RiskAnalyticsStream} using {@link TopologyTestDriver}.
 */
@ExtendWith(MockitoExtension.class)
class RiskAnalyticsStreamTest {

    private static final String TOPIC_RISK_ASSESSED = "risk.assessed";

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> riskTopic;

    @Mock
    private ClickHouseSink clickHouseSink;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        StreamsBuilder builder = new StreamsBuilder();
        RiskAnalyticsStream stream = new RiskAnalyticsStream(builder, clickHouseSink);
        stream.buildTopology();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG,    "test-risk-pipeline");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
                Serdes.StringSerde.class.getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
                Serdes.StringSerde.class.getName());

        driver = new TopologyTestDriver(builder.build(), props);
        riskTopic = driver.createInputTopic(
                TOPIC_RISK_ASSESSED, Serdes.String().serializer(), Serdes.String().serializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    @DisplayName("Valid risk event → writeRiskAssessment called with correct fields")
    void validRiskEvent_writesRiskAssessment() throws Exception {
        UUID txId   = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", txId.toString());
        payload.put("userId",        userId.toString());
        payload.put("riskScore",     72);
        payload.put("decision",      "APPROVED");
        payload.put("ruleFlags",     List.of("HIGH_VELOCITY", "NEW_DEVICE"));

        riskTopic.pipeInput(txId.toString(), objectMapper.writeValueAsString(payload));

        ArgumentCaptor<ClickHouseSink.RiskAssessmentRecord> captor =
                ArgumentCaptor.forClass(ClickHouseSink.RiskAssessmentRecord.class);
        verify(clickHouseSink, times(1)).writeRiskAssessment(captor.capture());

        ClickHouseSink.RiskAssessmentRecord record = captor.getValue();
        assertThat(record.transactionId()).isEqualTo(txId);
        assertThat(record.userId()).isEqualTo(userId);
        assertThat(record.riskScore()).isEqualTo(72);
        assertThat(record.decision()).isEqualTo("APPROVED");
        assertThat(record.ruleFlags()).containsExactlyInAnyOrder("HIGH_VELOCITY", "NEW_DEVICE");
    }

    @Test
    @DisplayName("REJECTED decision preserved in sink record")
    void rejectedDecision_preserved() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", UUID.randomUUID().toString());
        payload.put("userId",        UUID.randomUUID().toString());
        payload.put("riskScore",     95);
        payload.put("decision",      "REJECTED");
        payload.put("ruleFlags",     List.of("SELF_TRANSFER"));

        riskTopic.pipeInput("k1", objectMapper.writeValueAsString(payload));

        ArgumentCaptor<ClickHouseSink.RiskAssessmentRecord> captor =
                ArgumentCaptor.forClass(ClickHouseSink.RiskAssessmentRecord.class);
        verify(clickHouseSink).writeRiskAssessment(captor.capture());
        assertThat(captor.getValue().decision()).isEqualTo("REJECTED");
        assertThat(captor.getValue().riskScore()).isEqualTo(95);
    }

    @Test
    @DisplayName("Empty ruleFlags list → sink called with empty list (not null)")
    void emptyRuleFlags_notNull() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", UUID.randomUUID().toString());
        payload.put("userId",        UUID.randomUUID().toString());
        payload.put("riskScore",     10);
        payload.put("decision",      "APPROVED");
        payload.put("ruleFlags",     Collections.emptyList());

        riskTopic.pipeInput("k2", objectMapper.writeValueAsString(payload));

        ArgumentCaptor<ClickHouseSink.RiskAssessmentRecord> captor =
                ArgumentCaptor.forClass(ClickHouseSink.RiskAssessmentRecord.class);
        verify(clickHouseSink).writeRiskAssessment(captor.capture());
        assertThat(captor.getValue().ruleFlags()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Malformed JSON → silently dropped, no sink interaction")
    void malformedJson_silentlyDropped() {
        riskTopic.pipeInput("bad", "NOT_JSON");
        verifyNoInteractions(clickHouseSink);
    }

    @Test
    @DisplayName("Missing decision field → defaults to UNKNOWN")
    void missingDecision_defaultsToUnknown() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", UUID.randomUUID().toString());
        payload.put("userId",        UUID.randomUUID().toString());
        payload.put("riskScore",     50);
        // decision intentionally absent

        riskTopic.pipeInput("k3", objectMapper.writeValueAsString(payload));

        ArgumentCaptor<ClickHouseSink.RiskAssessmentRecord> captor =
                ArgumentCaptor.forClass(ClickHouseSink.RiskAssessmentRecord.class);
        verify(clickHouseSink).writeRiskAssessment(captor.capture());
        assertThat(captor.getValue().decision()).isEqualTo("UNKNOWN");
    }
}
