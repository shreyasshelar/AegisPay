package com.aegispay.risk.integration;

import com.aegispay.common.domain.enums.RiskDecision;
import com.aegispay.common.domain.event.RiskAssessmentRequestedEvent;
import com.aegispay.risk.repository.OutboxEntryRepository;
import com.aegispay.risk.repository.RiskCaseRepository;
import com.aegispay.risk.service.RiskScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        "risk.assessment.requested", "risk.assessed", "kyc.status.changed"
})
class RiskScoringIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("aegispay_risk_test")
            .withUsername("test")
            .withPassword("test");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers",
                () -> "${spring.embedded.kafka.brokers}");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "https://test-issuer.aegispay.io");
        registry.add("aegispay.ai-platform.base-url", () -> "http://ai-stub:9999");
    }

    @Autowired RiskScoringService riskScoringService;
    @Autowired RiskCaseRepository riskCaseRepository;
    @Autowired OutboxEntryRepository outboxEntryRepository;

    @BeforeEach
    void setUp() {
        outboxEntryRepository.deleteAll();
        riskCaseRepository.deleteAll();
    }

    @Test
    void assess_low_risk_transaction_is_approved() {
        UUID txnId = UUID.randomUUID();
        RiskAssessmentRequestedEvent event = RiskAssessmentRequestedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(txnId).sagaId(UUID.randomUUID()).userId(UUID.randomUUID())
                .amount(new BigDecimal("50")).currency("USD").payeeCountry("IN")
                .build();

        riskScoringService.assess(event);

        var riskCase = riskCaseRepository.findByTransactionId(txnId).orElseThrow();
        assertThat(riskCase.getDecision()).isEqualTo(RiskDecision.APPROVED);

        assertThat(outboxEntryRepository.findAll())
                .anyMatch(e -> e.getEventType().equals("RiskAssessedEvent"));
    }

    @Test
    void assess_is_idempotent_on_duplicate() {
        UUID txnId = UUID.randomUUID();
        RiskAssessmentRequestedEvent event = RiskAssessmentRequestedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(txnId).sagaId(UUID.randomUUID()).userId(UUID.randomUUID())
                .amount(new BigDecimal("50")).currency("USD").build();

        riskScoringService.assess(event);
        riskScoringService.assess(event);

        assertThat(riskCaseRepository.count()).isEqualTo(1);
        assertThat(outboxEntryRepository.count()).isEqualTo(1);
    }
}
