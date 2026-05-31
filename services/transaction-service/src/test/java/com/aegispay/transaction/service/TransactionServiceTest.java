package com.aegispay.transaction.service;

import com.aegispay.common.domain.enums.TransactionStatus;
import com.aegispay.common.domain.exception.AegisPayException;
import com.aegispay.common.domain.exception.DuplicateIdempotencyKeyException;
import com.aegispay.transaction.domain.dto.TransactionRequest;
import com.aegispay.transaction.domain.dto.TransactionResponse;
import com.aegispay.transaction.domain.entity.OutboxEntry;
import com.aegispay.transaction.domain.entity.Transaction;
import com.aegispay.transaction.domain.mapper.TransactionMapperImpl;
import com.aegispay.transaction.idempotency.IdempotencyService;
import com.aegispay.transaction.client.UserServiceClient;
import com.aegispay.transaction.kafka.TransactionEventProducer;
import com.aegispay.transaction.readmodel.TransactionView;
import com.aegispay.transaction.readmodel.TransactionViewRepository;
import com.aegispay.transaction.repository.OutboxEntryRepository;
import com.aegispay.transaction.repository.TransactionRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private OutboxEntryRepository outboxEntryRepository;
    @Mock private TransactionViewRepository viewRepository;
    @Spy  private TransactionMapperImpl transactionMapper;
    @Mock private IdempotencyService idempotencyService;
    @Mock private TransactionEventProducer eventProducer;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private UserServiceClient userServiceClient;

    @InjectMocks
    private TransactionService transactionService;

    private static final UUID USER_ID  = UUID.randomUUID();
    private static final UUID PAYER_ID = UUID.randomUUID();
    private static final UUID PAYEE_ID = UUID.randomUUID();

    @Test
    void createBuildsTransactionAndOutboxEntry() {
        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        doNothing().when(userServiceClient).assertPayeeExists(any(UUID.class));

        Transaction saved = buildTransaction();
        when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);
        when(outboxEntryRepository.save(any())).thenReturn(null);
        when(viewRepository.save(any())).thenReturn(null);
        when(eventProducer.buildTransactionInitiatedEntry(any()))
                .thenReturn(OutboxEntry.builder().aggregateId(saved.getId().toString())
                        .aggregateType("Transaction").eventType("TransactionInitiatedEvent")
                        .topic("transaction.initiated").messageKey(saved.getId().toString())
                        .payload("{}").build());

        TransactionResponse response = transactionService.create(buildRequest(), "key-1", USER_ID);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TransactionStatus.INITIATED);
        verify(idempotencyService).claim("key-1");
        verify(transactionRepository).save(any(Transaction.class));
        verify(outboxEntryRepository).save(any());
        verify(viewRepository).save(any(TransactionView.class));
    }

    @Test
    void createIsIdempotentForExistingKey() {
        Transaction existing = buildTransaction();
        when(transactionRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.of(existing));

        TransactionResponse response = transactionService.create(buildRequest(), "key-2", USER_ID);

        assertThat(response).isNotNull();
        verifyNoInteractions(idempotencyService);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void getByIdThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getById(id))
                .isInstanceOf(AegisPayException.class)
                .hasMessageContaining("not found");
    }

    private TransactionRequest buildRequest() {
        return new TransactionRequest(PAYEE_ID, new BigDecimal("500.00"), "INR", null, null);
    }

    private Transaction buildTransaction() {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .payerId(PAYER_ID)
                .payeeId(PAYEE_ID)
                .amount(new BigDecimal("500.00"))
                .currency("INR")
                .idempotencyKey("key-1")
                .status(TransactionStatus.INITIATED)
                .initiatedAt(Instant.now())
                .build();
    }
}
