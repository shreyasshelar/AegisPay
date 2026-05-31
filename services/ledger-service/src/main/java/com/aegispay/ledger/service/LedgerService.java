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
    private final ExchangeRateService exchangeRateService;

    @Transactional
    public void reserveBalance(BalanceReserveRequestedEvent event) {
        // Find the user's primary account (any currency — we convert to it).
        // Using findByUserId and taking the first account means a single INR
        // account covers all transaction currencies; no per-currency account needed.
        java.util.List<Account> accounts =
                accountRepository.findByUserId(event.getUserId());
        if (accounts.isEmpty()) {
            log.warn("No account found for userId={} txn={}",
                    event.getUserId(), event.getTransactionId());
            writeFailedEvent(event, null, java.math.BigDecimal.ZERO,
                    "ACCOUNT_NOT_FOUND: no account for user " + event.getUserId());
            return;
        }

        // Acquire a pessimistic write lock on the chosen account.
        Account account = accountRepository.findByIdForUpdate(accounts.get(0).getId())
                .orElseThrow();

        // Convert the transaction amount into the account's base currency.
        // e.g. $5.30 USD → ₹442.55 INR at the current mid-market rate.
        BigDecimal convertedAmount = exchangeRateService.convert(
                event.getAmount(), event.getCurrency(), account.getCurrency());

        if (account.getAvailableBalance().compareTo(convertedAmount) < 0) {
            log.warn("Insufficient funds for txn={}: available={} {} requested={} {} (= {} {})",
                    event.getTransactionId(),
                    account.getAvailableBalance(), account.getCurrency(),
                    event.getAmount(), event.getCurrency(),
                    convertedAmount, account.getCurrency());
            writeFailedEvent(event, account.getId(), account.getAvailableBalance(),
                    "INSUFFICIENT_FUNDS");
            return;
        }

        BigDecimal balanceBefore = account.getAvailableBalance();
        account.setAvailableBalance(account.getAvailableBalance().subtract(convertedAmount));
        account.setReservedBalance(account.getReservedBalance().add(convertedAmount));
        accountRepository.save(account);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .accountId(account.getId())
                .transactionId(event.getTransactionId())
                .entryType(LedgerEntryType.RESERVE)
                .amount(convertedAmount)
                .balanceBefore(balanceBefore)
                .balanceAfter(account.getAvailableBalance())
                .description(String.format("Reserve for txn %s: %s %s → %s %s",
                        event.getTransactionId(),
                        event.getAmount(), event.getCurrency(),
                        convertedAmount, account.getCurrency()))
                .build());

        // Lock stores the BASE-CURRENCY amount so commit/rollback always
        // use the exact figure that was deducted — no re-conversion needed.
        balanceLockRepository.save(BalanceLock.builder()
                .transactionId(event.getTransactionId())
                .accountId(account.getId())
                .reservedAmount(convertedAmount)
                .expiresAt(Instant.now().plus(properties.getBalanceLockExpiryMinutes(), ChronoUnit.MINUTES))
                .build());

        BalanceReservedEvent reply = BalanceReservedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .transactionId(event.getTransactionId())
                .sagaId(event.getSagaId())
                .accountId(account.getId())
                .reservedAmount(convertedAmount)
                .availableBalanceAfter(account.getAvailableBalance())
                .build();

        writeOutbox(event.getTransactionId().toString(), "BalanceReservedEvent",
                KafkaTopics.BALANCE_RESERVED, reply);
        log.info("Balance reserved: txn={} {} {} → {} {}",
                event.getTransactionId(),
                event.getAmount(), event.getCurrency(),
                convertedAmount, account.getCurrency());
    }

    @Transactional
    public void commitBalance(BalanceCommitRequestedEvent event) {
        // ── Debit sender: remove from reserved balance ──────────────────────
        Account senderAccount = accountRepository.findByIdForUpdate(event.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(event.getAccountId()));

        // Use the lock's base-currency amount, not event.getAmount() which carries
        // the raw transaction amount in the transaction's (possibly foreign) currency.
        BigDecimal amount = balanceLockRepository
                .findByTransactionId(event.getTransactionId())
                .map(BalanceLock::getReservedAmount)
                .orElse(event.getAmount());   // fallback if lock already cleaned up

        BigDecimal senderBalanceBefore = senderAccount.getReservedBalance();
        senderAccount.setReservedBalance(senderAccount.getReservedBalance().subtract(amount));
        accountRepository.save(senderAccount);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .accountId(senderAccount.getId())
                .transactionId(event.getTransactionId())
                .entryType(LedgerEntryType.COMMIT)
                .amount(amount)
                .balanceBefore(senderBalanceBefore)
                .balanceAfter(senderAccount.getReservedBalance())
                .description("Commit for transaction " + event.getTransactionId())
                .build());

        balanceLockRepository.findByTransactionId(event.getTransactionId())
                .ifPresent(balanceLockRepository::delete);

        // ── Credit receiver: add to available balance ────────────────────────
        if (event.getPayeeId() != null) {
            List<Account> receiverAccounts = accountRepository.findByUserId(event.getPayeeId());
            if (receiverAccounts.isEmpty()) {
                // Defense-in-depth: transaction-service should have rejected this at creation
                // time via the payee-existence gate.  If we still arrive here (e.g. a race
                // condition where the user registered after the check but before the saga), we
                // MUST NOT silently skip the credit — that would charge the sender and deliver
                // nothing to the receiver (permanent money loss).  Throw so the Kafka consumer
                // retries; after max retries the event goes to the DLQ for manual investigation.
                throw new AccountNotFoundException(
                        "commitBalance: no ledger account for payeeId=" + event.getPayeeId()
                        + " txn=" + event.getTransactionId()
                        + " — refusing to skip credit; failing for Kafka retry/DLQ");
            } else {
                Account receiverAccount = accountRepository.findByIdForUpdate(receiverAccounts.get(0).getId())
                        .orElseThrow();
                // Convert from sender's base currency to receiver's base currency if they differ.
                BigDecimal creditAmount = exchangeRateService.convert(
                        amount, senderAccount.getCurrency(), receiverAccount.getCurrency());
                BigDecimal receiverBalanceBefore = receiverAccount.getAvailableBalance();
                receiverAccount.setAvailableBalance(receiverAccount.getAvailableBalance().add(creditAmount));
                accountRepository.save(receiverAccount);

                ledgerEntryRepository.save(LedgerEntry.builder()
                        .accountId(receiverAccount.getId())
                        .transactionId(event.getTransactionId())
                        .entryType(LedgerEntryType.CREDIT)
                        .amount(creditAmount)
                        .balanceBefore(receiverBalanceBefore)
                        .balanceAfter(receiverAccount.getAvailableBalance())
                        .description("Credit from transaction " + event.getTransactionId())
                        .build());
                log.info("Receiver credited: txn={} payeeId={} amount={} {}",
                        event.getTransactionId(), event.getPayeeId(), creditAmount, receiverAccount.getCurrency());
            }
        } else {
            log.warn("commitBalance: payeeId is null for txn={} — receiver credit skipped",
                    event.getTransactionId());
        }

        BalanceCommittedEvent reply = BalanceCommittedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .transactionId(event.getTransactionId())
                .sagaId(event.getSagaId())
                .accountId(senderAccount.getId())
                .committedAmount(amount)
                .availableBalanceAfter(senderAccount.getAvailableBalance())
                .reservedBalanceAfter(senderAccount.getReservedBalance())
                .build();

        writeOutbox(event.getTransactionId().toString(), "BalanceCommittedEvent",
                KafkaTopics.BALANCE_COMMITTED, reply);
        log.info("Balance committed: txn={} amount={}", event.getTransactionId(), amount);
    }

    @Transactional
    public void rollbackBalance(BalanceRollbackRequestedEvent event) {
        Account account = accountRepository.findByIdForUpdate(event.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(event.getAccountId()));

        // Use the lock's base-currency amount so we release exactly what was deducted.
        BigDecimal amount = balanceLockRepository
                .findByTransactionId(event.getTransactionId())
                .map(BalanceLock::getReservedAmount)
                .orElse(event.getAmountToRelease());  // fallback
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

    private void writeFailedEvent(BalanceReserveRequestedEvent event, @SuppressWarnings("SameParameterValue") UUID accountId,
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
