package com.aegispay.ledger.service;

import com.aegispay.common.domain.enums.LedgerEntryType;
import com.aegispay.common.domain.event.*;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.ledger.config.LedgerServiceProperties;
import com.aegispay.ledger.domain.dto.AccountResponse;
import com.aegispay.ledger.domain.dto.LedgerEntryResponse;
import com.aegispay.ledger.domain.entity.Account;
import com.aegispay.ledger.domain.entity.BalanceLock;
import com.aegispay.ledger.domain.entity.LedgerEntry;
import com.aegispay.ledger.domain.entity.OutboxEntry;
import com.aegispay.ledger.domain.mapper.LedgerMapper;
import com.aegispay.ledger.exception.AccountNotFoundException;
import com.aegispay.ledger.exception.InsufficientFundsException;
import com.aegispay.ledger.repository.AccountRepository;
import com.aegispay.ledger.repository.BalanceLockRepository;
import com.aegispay.ledger.repository.LedgerEntryRepository;
import com.aegispay.ledger.repository.OutboxEntryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final BalanceLockRepository balanceLockRepository;
    private final OutboxEntryRepository outboxEntryRepository;
    private final LedgerMapper ledgerMapper;
    private final ObjectMapper objectMapper;
    private final LedgerServiceProperties properties;

    @Transactional
    public void reserveBalance(BalanceReserveRequestedEvent event) {
        Account account = accountRepository.findByUserIdAndCurrencyForUpdate(event.getUserId(), event.getCurrency())
                .orElseThrow(() -> new AccountNotFoundException(event.getUserId()));

        BigDecimal amount = event.getAmount();

        if (account.getAvailableBalance().compareTo(amount) < 0) {
            log.warn("Insufficient funds for txn={}: available={} requested={}",
                    event.getTransactionId(), account.getAvailableBalance(), amount);
            writeFailedEvent(event, account.getId(), account.getAvailableBalance(), "INSUFFICIENT_FUNDS");
            return;
        }

        BigDecimal balanceBefore = account.getAvailableBalance();
        account.setAvailableBalance(account.getAvailableBalance().subtract(amount));
        account.setReservedBalance(account.getReservedBalance().add(amount));
        accountRepository.save(account);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .accountId(account.getId())
                .transactionId(event.getTransactionId())
                .entryType(LedgerEntryType.RESERVE)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(account.getAvailableBalance())
                .description("Reserve for transaction " + event.getTransactionId())
                .build());

        balanceLockRepository.save(BalanceLock.builder()
                .transactionId(event.getTransactionId())
                .accountId(account.getId())
                .reservedAmount(amount)
                .expiresAt(Instant.now().plus(properties.getBalanceLockExpiryMinutes(), ChronoUnit.MINUTES))
                .build());

        BalanceReservedEvent reply = BalanceReservedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .transactionId(event.getTransactionId())
                .sagaId(event.getSagaId())
                .accountId(account.getId())
                .reservedAmount(amount)
                .availableBalanceAfter(account.getAvailableBalance())
                .build();

        writeOutbox(event.getTransactionId().toString(), "BalanceReservedEvent",
                KafkaTopics.BALANCE_RESERVED, reply);
        log.info("Balance reserved: txn={} amount={}", event.getTransactionId(), amount);
    }

    @Transactional
    public void commitBalance(BalanceCommitRequestedEvent event) {
        Account account = accountRepository.findByIdForUpdate(event.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(event.getAccountId()));

        BigDecimal amount = event.getAmount();
        BigDecimal balanceBefore = account.getReservedBalance();

        account.setReservedBalance(account.getReservedBalance().subtract(amount));
        accountRepository.save(account);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .accountId(account.getId())
                .transactionId(event.getTransactionId())
                .entryType(LedgerEntryType.COMMIT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(account.getReservedBalance())
                .description("Commit for transaction " + event.getTransactionId())
                .build());

        balanceLockRepository.findByTransactionId(event.getTransactionId())
                .ifPresent(balanceLockRepository::delete);

        BalanceCommittedEvent reply = BalanceCommittedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .transactionId(event.getTransactionId())
                .sagaId(event.getSagaId())
                .accountId(account.getId())
                .committedAmount(amount)
                .availableBalanceAfter(account.getAvailableBalance())
                .reservedBalanceAfter(account.getReservedBalance())
                .build();

        writeOutbox(event.getTransactionId().toString(), "BalanceCommittedEvent",
                KafkaTopics.BALANCE_COMMITTED, reply);
        log.info("Balance committed: txn={} amount={}", event.getTransactionId(), amount);
    }

    @Transactional
    public void rollbackBalance(BalanceRollbackRequestedEvent event) {
        Account account = accountRepository.findByIdForUpdate(event.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(event.getAccountId()));

        BigDecimal amount = event.getAmountToRelease();
        BigDecimal balanceBefore = account.getAvailableBalance();

        account.setAvailableBalance(account.getAvailableBalance().add(amount));
        account.setReservedBalance(account.getReservedBalance().subtract(amount));
        accountRepository.save(account);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .accountId(account.getId())
                .transactionId(event.getTransactionId())
                .entryType(LedgerEntryType.RELEASE)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(account.getAvailableBalance())
                .description("Rollback for transaction " + event.getTransactionId()
                        + ": " + event.getRollbackReason())
                .build());

        balanceLockRepository.findByTransactionId(event.getTransactionId())
                .ifPresent(balanceLockRepository::delete);

        BalanceRolledBackEvent reply = BalanceRolledBackEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .transactionId(event.getTransactionId())
                .sagaId(event.getSagaId())
                .accountId(account.getId())
                .releasedAmount(amount)
                .availableBalanceAfter(account.getAvailableBalance())
                .build();

        writeOutbox(event.getTransactionId().toString(), "BalanceRolledBackEvent",
                KafkaTopics.BALANCE_ROLLED_BACK, reply);
        log.info("Balance rolled back: txn={} amount={}", event.getTransactionId(), amount);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsForUser(UUID userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(ledgerMapper::toAccountResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getEntriesForTransaction(UUID transactionId) {
        return ledgerEntryRepository.findByTransactionIdOrderByCreatedAtAsc(transactionId).stream()
                .map(ledgerMapper::toLedgerEntryResponse)
                .toList();
    }

    /** Called by ExpiredLockCleanupScheduler to release a lock past its expiry. */
    @Transactional
    public void releaseExpiredLock(BalanceLock lock) {
        accountRepository.findByIdForUpdate(lock.getAccountId()).ifPresent(account -> {
            BigDecimal amount = lock.getReservedAmount();
            BigDecimal balanceBefore = account.getAvailableBalance();

            account.setAvailableBalance(account.getAvailableBalance().add(amount));
            account.setReservedBalance(account.getReservedBalance().subtract(amount));
            accountRepository.save(account);

            ledgerEntryRepository.save(LedgerEntry.builder()
                    .accountId(account.getId())
                    .transactionId(lock.getTransactionId())
                    .entryType(LedgerEntryType.RELEASE)
                    .amount(amount)
                    .balanceBefore(balanceBefore)
                    .balanceAfter(account.getAvailableBalance())
                    .description("Expired lock release for transaction " + lock.getTransactionId())
                    .build());

            balanceLockRepository.delete(lock);
            log.warn("Released expired balance lock: txn={} amount={}", lock.getTransactionId(), amount);
        });
    }

    private void writeFailedEvent(BalanceReserveRequestedEvent event, UUID accountId,
                                  BigDecimal availableBalance, String reason) {
        BalanceReserveFailedEvent reply = BalanceReserveFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .transactionId(event.getTransactionId())
                .sagaId(event.getSagaId())
                .accountId(accountId)
                .requestedAmount(event.getAmount())
                .availableBalance(availableBalance)
                .failureReason(reason)
                .build();

        writeOutbox(event.getTransactionId().toString(), "BalanceReserveFailedEvent",
                KafkaTopics.BALANCE_RESERVE_FAILED, reply);
    }

    private void writeOutbox(String aggregateId, String eventType, String topic, Object event) {
        try {
            outboxEntryRepository.save(OutboxEntry.builder()
                    .aggregateId(aggregateId)
                    .aggregateType("Account")
                    .eventType(eventType)
                    .topic(topic)
                    .messageKey(aggregateId)
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize " + eventType, e);
        }
    }
}
