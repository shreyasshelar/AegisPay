package com.aegispay.ledger.kafka;

import com.aegispay.common.domain.event.UserRegisteredEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.ledger.domain.entity.Account;
import com.aegispay.ledger.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Creates a default INR account for every newly registered user.
 *
 * <p>Triggered by the {@code user.registered} Kafka event published by user-service
 * after {@code POST /api/v1/users/register}.
 *
 * <p>Idempotent — {@code findByUserIdAndCurrency} check prevents duplicate accounts
 * if the event is replayed (e.g. consumer restart, DLQ reprocessing).
 *
 * <p>Initial balance is ₹0. Top-up or on-boarding seed balance is applied
 * separately via the ledger top-up flow once KYC is approved.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredConsumer {

    private static final String DEFAULT_CURRENCY = "INR";

    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = KafkaTopics.USER_REGISTERED, groupId = "ledger-service-user")
    public void handle(ConsumerRecord<String, String> record) {
        try {
            UserRegisteredEvent event = objectMapper.readValue(record.value(), UserRegisteredEvent.class);

            // Idempotency guard
            boolean exists = accountRepository
                    .findByUserIdAndCurrency(event.getUserId(), DEFAULT_CURRENCY)
                    .isPresent();

            if (exists) {
                log.debug("Account already exists for userId={} — skipping", event.getUserId());
                return;
            }

            Account account = Account.builder()
                    .userId(event.getUserId())
                    .currency(DEFAULT_CURRENCY)
                    .availableBalance(BigDecimal.ZERO)
                    .reservedBalance(BigDecimal.ZERO)
                    .tenantId(event.getTenantId() != null ? event.getTenantId() : "default")
                    .build();

            accountRepository.save(account);

            log.info("Default INR account created: userId={} accountId={}",
                    event.getUserId(), account.getId());

        } catch (Exception e) {
            log.error("Failed to create account for UserRegisteredEvent key={}: {}",
                    record.key(), e.getMessage(), e);
            throw new RuntimeException(e); // re-throw so Kafka retries / sends to DLQ
        }
    }
}
