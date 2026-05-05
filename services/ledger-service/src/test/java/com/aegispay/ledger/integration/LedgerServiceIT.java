package com.aegispay.ledger.integration;

import com.aegispay.common.domain.event.BalanceReserveRequestedEvent;
import com.aegispay.ledger.domain.entity.Account;
import com.aegispay.ledger.repository.AccountRepository;
import com.aegispay.ledger.repository.BalanceLockRepository;
import com.aegispay.ledger.repository.LedgerEntryRepository;
import com.aegispay.ledger.repository.OutboxEntryRepository;
import com.aegispay.ledger.service.LedgerService;
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
        "balance.reserve.requested",
        "balance.reserved",
        "balance.reserve.failed",
        "balance.commit.requested",
        "balance.committed",
        "balance.rollback.requested",
        "balance.rolled-back"
})
class LedgerServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("aegispay_ledger_test")
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
    }

    @Autowired LedgerService ledgerService;
    @Autowired AccountRepository accountRepository;
    @Autowired LedgerEntryRepository ledgerEntryRepository;
    @Autowired BalanceLockRepository balanceLockRepository;
    @Autowired OutboxEntryRepository outboxEntryRepository;

    UUID accountId;
    UUID txnId = UUID.randomUUID();
    UUID sagaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        outboxEntryRepository.deleteAll();
        balanceLockRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        accountRepository.deleteAll();

        Account account = Account.builder()
                .userId(UUID.randomUUID())
                .currency("USD")
                .availableBalance(new BigDecimal("500.00"))
                .reservedBalance(BigDecimal.ZERO)
                .build();
        accountId = accountRepository.save(account).getId();
    }

    @Test
    void reserveBalance_creates_ledger_entry_and_lock() {
        BalanceReserveRequestedEvent event = BalanceReserveRequestedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .transactionId(txnId)
                .sagaId(sagaId)
                .accountId(accountId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        ledgerService.reserveBalance(event);

        Account updated = accountRepository.findById(accountId).orElseThrow();
        assertThat(updated.getAvailableBalance()).isEqualByComparingTo("400.00");
        assertThat(updated.getReservedBalance()).isEqualByComparingTo("100.00");

        assertThat(ledgerEntryRepository.findByTransactionIdOrderByCreatedAtAsc(txnId)).hasSize(1);
        assertThat(balanceLockRepository.findByTransactionId(txnId)).isPresent();

        assertThat(outboxEntryRepository.findAll())
                .anyMatch(e -> e.getEventType().equals("BalanceReservedEvent"));
    }

    @Test
    void reserveBalance_insufficient_funds_writes_failed_event_no_ledger_entry() {
        BalanceReserveRequestedEvent event = BalanceReserveRequestedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .transactionId(txnId)
                .sagaId(sagaId)
                .accountId(accountId)
                .amount(new BigDecimal("9999.00"))
                .currency("USD")
                .build();

        ledgerService.reserveBalance(event);

        Account unchanged = accountRepository.findById(accountId).orElseThrow();
        assertThat(unchanged.getAvailableBalance()).isEqualByComparingTo("500.00");
        assertThat(ledgerEntryRepository.findByTransactionIdOrderByCreatedAtAsc(txnId)).isEmpty();
        assertThat(balanceLockRepository.findByTransactionId(txnId)).isEmpty();

        assertThat(outboxEntryRepository.findAll())
                .anyMatch(e -> e.getEventType().equals("BalanceReserveFailedEvent"));
    }
}
