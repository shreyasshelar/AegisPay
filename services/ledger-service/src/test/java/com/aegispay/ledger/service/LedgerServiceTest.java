package com.aegispay.ledger.service;

import com.aegispay.common.domain.event.BalanceCommitRequestedEvent;
import com.aegispay.common.domain.event.BalanceReserveRequestedEvent;
import com.aegispay.common.domain.event.BalanceRollbackRequestedEvent;
import com.aegispay.ledger.config.LedgerServiceProperties;
import com.aegispay.ledger.domain.entity.Account;
import com.aegispay.ledger.domain.entity.BalanceLock;
import com.aegispay.ledger.domain.entity.LedgerEntry;
import com.aegispay.ledger.domain.entity.OutboxEntry;
import com.aegispay.ledger.domain.mapper.LedgerMapperImpl;
import com.aegispay.ledger.exception.AccountNotFoundException;
import com.aegispay.ledger.repository.AccountRepository;
import com.aegispay.ledger.repository.BalanceLockRepository;
import com.aegispay.ledger.repository.LedgerEntryRepository;
import com.aegispay.ledger.repository.OutboxEntryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock BalanceLockRepository balanceLockRepository;
    @Mock OutboxEntryRepository outboxEntryRepository;

    LedgerService ledgerService;

    UUID accountId = UUID.randomUUID();
    UUID txnId = UUID.randomUUID();
    UUID sagaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        LedgerServiceProperties props = new LedgerServiceProperties();
        props.setBalanceLockExpiryMinutes(30);

        ledgerService = new LedgerService(
                accountRepository, ledgerEntryRepository, balanceLockRepository,
                outboxEntryRepository, new LedgerMapperImpl(), objectMapper, props);
    }

    @Test
    void reserveBalance_happy_path() {
        Account account = accountWithBalance("100.00", "0.00");
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(balanceLockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.reserveBalance(reserveEvent(new BigDecimal("30.00")));

        assertThat(account.getAvailableBalance()).isEqualByComparingTo("70.00");
        assertThat(account.getReservedBalance()).isEqualByComparingTo("30.00");

        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getBalanceBefore()).isEqualByComparingTo("100.00");
        assertThat(entryCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("70.00");

        verify(outboxEntryRepository).save(argThat(e ->
                ((OutboxEntry) e).getEventType().equals("BalanceReservedEvent")));
    }

    @Test
    void reserveBalance_insufficient_funds_writes_failed_event() {
        Account account = accountWithBalance("10.00", "0.00");
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(outboxEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.reserveBalance(reserveEvent(new BigDecimal("50.00")));

        verify(ledgerEntryRepository, never()).save(any());
        verify(balanceLockRepository, never()).save(any());
        verify(outboxEntryRepository).save(argThat(e ->
                ((OutboxEntry) e).getEventType().equals("BalanceReserveFailedEvent")));
    }

    @Test
    void reserveBalance_account_not_found_throws() {
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.reserveBalance(reserveEvent(new BigDecimal("10.00"))))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void commitBalance_reduces_reserved_balance() {
        Account account = accountWithBalance("70.00", "30.00");
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(balanceLockRepository.findByTransactionId(txnId)).thenReturn(Optional.empty());
        when(outboxEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.commitBalance(commitEvent(new BigDecimal("30.00")));

        assertThat(account.getReservedBalance()).isEqualByComparingTo("0.00");
        verify(outboxEntryRepository).save(argThat(e ->
                ((OutboxEntry) e).getEventType().equals("BalanceCommittedEvent")));
    }

    @Test
    void rollbackBalance_restores_available_balance() {
        Account account = accountWithBalance("70.00", "30.00");
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(balanceLockRepository.findByTransactionId(txnId)).thenReturn(Optional.empty());
        when(outboxEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.rollbackBalance(rollbackEvent(new BigDecimal("30.00")));

        assertThat(account.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(account.getReservedBalance()).isEqualByComparingTo("0.00");
        verify(outboxEntryRepository).save(argThat(e ->
                ((OutboxEntry) e).getEventType().equals("BalanceRolledBackEvent")));
    }

    private Account accountWithBalance(String available, String reserved) {
        return Account.builder()
                .id(accountId)
                .userId(UUID.randomUUID())
                .currency("USD")
                .availableBalance(new BigDecimal(available))
                .reservedBalance(new BigDecimal(reserved))
                .version(0L)
                .build();
    }

    private BalanceReserveRequestedEvent reserveEvent(BigDecimal amount) {
        return BalanceReserveRequestedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .transactionId(txnId)
                .sagaId(sagaId)
                .accountId(accountId)
                .amount(amount)
                .currency("USD")
                .build();
    }

    private BalanceCommitRequestedEvent commitEvent(BigDecimal amount) {
        return BalanceCommitRequestedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .transactionId(txnId)
                .sagaId(sagaId)
                .accountId(accountId)
                .amount(amount)
                .build();
    }

    private BalanceRollbackRequestedEvent rollbackEvent(BigDecimal amount) {
        return BalanceRollbackRequestedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .transactionId(txnId)
                .sagaId(sagaId)
                .accountId(accountId)
                .amountToRelease(amount)
                .rollbackReason("test rollback")
                .build();
    }
}
