package com.aegispay.orchestrator.integration;

import com.aegispay.common.domain.event.TransactionInitiatedEvent;
import com.aegispay.orchestrator.domain.entity.Saga;
import com.aegispay.orchestrator.repository.OutboxEntryRepository;
import com.aegispay.orchestrator.repository.SagaRepository;
import com.aegispay.orchestrator.saga.PaymentSagaOrchestrator;
import com.aegispay.orchestrator.saga.SagaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
        "transaction.initiated", "balance.reserve.requested", "balance.reserved",
        "balance.reserve.failed", "risk.assessment.requested", "risk.assessed",
        "payment.process.requested", "payment.processed",
        "balance.commit.requested", "balance.committed",
        "balance.rollback.requested", "balance.rolled-back",
        "transaction.completed", "transaction.failed", "transaction.rolled-back"
})
class SagaIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("aegispay_sagas_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers",
                () -> "${spring.embedded.kafka.brokers}");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "https://test-issuer.aegispay.io");
        registry.add("aegispay.external-payment-gateway.base-url",
                () -> "http://mock-gateway:9090");
    }

    @Autowired PaymentSagaOrchestrator orchestrator;
    @Autowired SagaRepository sagaRepository;
    @Autowired OutboxEntryRepository outboxRepository;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        sagaRepository.deleteAll();
    }

    @Test
    void startSaga_persists_saga_and_queues_reserve_balance_outbox_entry() {
        UUID txnId = UUID.randomUUID();
        TransactionInitiatedEvent event = TransactionInitiatedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(txnId).userId(UUID.randomUUID())
                .payerId(UUID.randomUUID()).payeeId(UUID.randomUUID())
                .amount(new BigDecimal("250.00")).currency("USD")
                .idempotencyKey("it-key-1").build();

        orchestrator.startSaga(event);

        Saga saga = sagaRepository.findByTransactionId(txnId).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.RUNNING);
        assertThat(saga.getCurrentStep()).isEqualTo("RESERVE_BALANCE");

        assertThat(outboxRepository.findAll())
                .anyMatch(e -> e.getEventType().equals("BalanceReserveRequestedEvent"));
    }

    @Test
    void startSaga_idempotent_on_duplicate_call() {
        UUID txnId = UUID.randomUUID();
        TransactionInitiatedEvent event = TransactionInitiatedEvent.builder()
                .eventId(UUID.randomUUID()).occurredAt(Instant.now()).schemaVersion(1)
                .transactionId(txnId).userId(UUID.randomUUID())
                .payerId(UUID.randomUUID()).payeeId(UUID.randomUUID())
                .amount(new BigDecimal("100.00")).currency("USD")
                .idempotencyKey("it-key-2").build();

        orchestrator.startSaga(event);
        orchestrator.startSaga(event);

        assertThat(sagaRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }
}
