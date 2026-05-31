package com.aegispay.pipeline.stream;

import com.aegispay.pipeline.sink.ClickHouseSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TransactionMetricsStream} using Kafka Streams'
 * {@link TopologyTestDriver} — no broker, no Spring context required.
 *
 * <p>The topology is built by calling {@code buildTopology()} directly on a
 * real {@link StreamsBuilder}, then the resulting {@link Topology} is passed
 * to {@link TopologyTestDriver} for in-process execution.
 */
@ExtendWith(MockitoExtension.class)
class TransactionMetricsStreamTest {

    private static final String TOPIC_COMPLETED   = "transaction.completed";
    private static final String TOPIC_FAILED      = "transaction.failed";

    private TopologyTestDriver        driver;
    private TestInputTopic<String, String>  completedTopic;
    private TestInputTopic<String, String>  failedTopic;

    @Mock
    private ClickHouseSink clickHouseSink;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        StreamsBuilder builder = new StreamsBuilder();
        TransactionMetricsStream stream = new TransactionMetricsStream(builder, clickHouseSink);
        stream.buildTopology();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG,    "test-pipeline");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
                Serdes.StringSerde.class.getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
                Serdes.StringSerde.class.getName());

        driver = new TopologyTestDriver(builder.build(), props);

        completedTopic = driver.createInputTopic(
                TOPIC_COMPLETED, Serdes.String().serializer(), Serdes.String().serializer());
        failedTopic = driver.createInputTopic(
                TOPIC_FAILED, Serdes.String().serializer(), Serdes.String().serializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    // ── COMPLETED event ───────────────────────────────────────────────────────

    @Test
    @DisplayName("COMPLETED event → ClickHouseSink.writeTransactionFact called once with correct fields")
    void completedEvent_writesTransactionFact() throws Exception {
        UUID txId   = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", txId.toString());
        payload.put("userId",        userId.toString());
        payload.put("amount",        "500.00");
        payload.put("currency",      "INR");
        payload.put("completedAt",   Instant.now().toString());

        completedTopic.pipeInput(txId.toString(), objectMapper.writeValueAsString(payload));

        ArgumentCaptor<ClickHouseSink.TransactionFactRecord> captor =
                ArgumentCaptor.forClass(ClickHouseSink.TransactionFactRecord.class);
        verify(clickHouseSink, times(1)).writeTransactionFact(captor.capture());

        ClickHouseSink.TransactionFactRecord record = captor.getValue();
        assertThat(record.transactionId()).isEqualTo(txId);
        assertThat(record.userId()).isEqualTo(userId);
        assertThat(record.amount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(record.currency()).isEqualTo("INR");
        assertThat(record.status()).isEqualTo("COMPLETED");
        assertThat(record.failureCode()).isNull();
    }

    @Test
    @DisplayName("COMPLETED event with missing amount → defaults gracefully, no exception")
    void completedEvent_missingAmount_noException() throws Exception {
        UUID txId   = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", txId.toString());
        payload.put("userId",        userId.toString());
        // amount intentionally missing
        payload.put("currency",      "USD");
        payload.put("completedAt",   Instant.now().toString());

        completedTopic.pipeInput(txId.toString(), objectMapper.writeValueAsString(payload));

        // Should still call writeTransactionFact (with null/zero amount) — no crash
        verify(clickHouseSink, atLeastOnce()).writeTransactionFact(any());
    }

    // ── FAILED event ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("FAILED event → writeTransactionFact with FAILED status and failureCode")
    void failedEvent_writesTransactionFactWithFailureCode() throws Exception {
        UUID txId   = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", txId.toString());
        payload.put("userId",        userId.toString());
        payload.put("failureCode",   "INSUFFICIENT_FUNDS");

        failedTopic.pipeInput(txId.toString(), objectMapper.writeValueAsString(payload));

        ArgumentCaptor<ClickHouseSink.TransactionFactRecord> captor =
                ArgumentCaptor.forClass(ClickHouseSink.TransactionFactRecord.class);
        verify(clickHouseSink, times(1)).writeTransactionFact(captor.capture());

        ClickHouseSink.TransactionFactRecord record = captor.getValue();
        assertThat(record.transactionId()).isEqualTo(txId);
        assertThat(record.status()).isEqualTo("FAILED");
        assertThat(record.failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("Malformed JSON is silently dropped — no exception, no sink call")
    void malformedJson_isDropped() {
        completedTopic.pipeInput("bad-key", "{ this is not json {{{{");
        verifyNoInteractions(clickHouseSink);
    }

    @Test
    @DisplayName("Null/empty value is silently dropped")
    void nullValue_isDropped() {
        completedTopic.pipeInput("key", (String) null);
        verifyNoInteractions(clickHouseSink);
    }

    @Test
    @DisplayName("Multiple events processed sequentially — sink called once per event")
    void multipleEvents_sinkCalledForEach() throws Exception {
        for (int i = 0; i < 5; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("transactionId", UUID.randomUUID().toString());
            p.put("userId",        UUID.randomUUID().toString());
            p.put("amount",        "100.00");
            p.put("currency",      "INR");
            p.put("completedAt",   Instant.now().toString());
            completedTopic.pipeInput("k" + i, objectMapper.writeValueAsString(p));
        }
        verify(clickHouseSink, times(5)).writeTransactionFact(any());
    }

    @Test
    @DisplayName("COMPLETED and FAILED events from same transaction → each written once")
    void mixedTopics_eachWrittenOnce() throws Exception {
        UUID txId   = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Map<String, Object> completed = Map.of(
                "transactionId", txId.toString(),
                "userId",        userId.toString(),
                "amount",        "250.00",
                "currency",      "INR",
                "completedAt",   Instant.now().toString()
        );
        Map<String, Object> failed = Map.of(
                "transactionId", UUID.randomUUID().toString(),
                "userId",        userId.toString(),
                "failureCode",   "PAYEE_NOT_FOUND"
        );

        completedTopic.pipeInput(txId.toString(), objectMapper.writeValueAsString(completed));
        failedTopic.pipeInput("f1", objectMapper.writeValueAsString(failed));

        verify(clickHouseSink, times(2)).writeTransactionFact(any());
    }
}
