package com.aegispay.transaction.service;

import com.aegispay.common.domain.dto.PagedResponse;
import com.aegispay.common.domain.enums.TransactionStatus;
import com.aegispay.common.domain.exception.AegisPayException;
import com.aegispay.transaction.domain.dto.TransactionRequest;
import com.aegispay.transaction.domain.dto.TransactionResponse;
import com.aegispay.transaction.domain.dto.TransactionStatusResponse;
import com.aegispay.transaction.domain.entity.OutboxEntry;
import com.aegispay.transaction.domain.entity.Transaction;
import com.aegispay.transaction.domain.mapper.TransactionMapper;
import com.aegispay.transaction.idempotency.IdempotencyService;
import com.aegispay.transaction.kafka.TransactionEventProducer;
import com.aegispay.transaction.readmodel.TransactionView;
import com.aegispay.transaction.readmodel.TransactionViewRepository;
import com.aegispay.transaction.repository.OutboxEntryRepository;
import com.aegispay.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final OutboxEntryRepository outboxEntryRepository;
    private final TransactionViewRepository viewRepository;
    private final TransactionMapper transactionMapper;
    private final IdempotencyService idempotencyService;
    private final TransactionEventProducer eventProducer;
    private final MongoTemplate mongoTemplate;

    @Transactional
    public TransactionResponse create(TransactionRequest request,
                                      String idempotencyKey,
                                      UUID userId) {
        // Idempotent: return existing if idempotency key already used
        return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    log.debug("Returning existing transaction for idempotency key: {}", idempotencyKey);
                    return transactionMapper.toResponse(existing);
                })
                .orElseGet(() -> {
                    idempotencyService.claim(idempotencyKey);
                    return createNew(request, idempotencyKey, userId);
                });
    }

    private TransactionResponse createNew(TransactionRequest request,
                                          String idempotencyKey,
                                          UUID userId) {
        // Merge note into metadata so it is persisted and returned via the mapper
        java.util.Map<String, Object> meta = new java.util.HashMap<>();
        if (request.metadata() != null) meta.putAll(request.metadata());
        if (request.note() != null && !request.note().isBlank()) meta.put("note", request.note());

        Transaction txn = Transaction.builder()
                .userId(userId)
                .payerId(userId)           // the authenticated user is always the payer
                .payeeId(request.payeeId())
                .amount(request.amount())
                .currency(request.currency())
                .idempotencyKey(idempotencyKey)
                .status(TransactionStatus.INITIATED)
                .metadata(meta.isEmpty() ? null : meta)
                .build();

        transactionRepository.save(txn);

        // Write outbox entry atomically in the same transaction
        OutboxEntry outboxEntry = eventProducer.buildTransactionInitiatedEntry(txn);
        outboxEntryRepository.save(outboxEntry);

        // Seed the MongoDB read model immediately so /transactions list is populated
        viewRepository.save(TransactionView.builder()
                .id(txn.getId().toString())
                .userId(userId.toString())
                .payerId(userId.toString())
                .payeeId(request.payeeId().toString())
                .amount(request.amount())
                .currency(request.currency())
                .status(TransactionStatus.INITIATED.name())
                .lastEvent("TransactionInitiatedEvent")
                .initiatedAt(txn.getInitiatedAt())
                .updatedAt(txn.getInitiatedAt())
                .build());

        log.info("Transaction created: id={} userId={} amount={} {}",
                txn.getId(), userId, request.amount(), request.currency());

        return transactionMapper.toResponse(txn);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .map(transactionMapper::toResponse)
                .orElseThrow(() -> new AegisPayException(
                        "TRANSACTION_NOT_FOUND",
                        "Transaction not found: " + transactionId,
                        HttpStatus.NOT_FOUND));
    }

    public TransactionStatusResponse getStatus(UUID transactionId) {
        return viewRepository.findById(transactionId.toString())
                .map(transactionMapper::toStatusResponse)
                .orElseThrow(() -> new AegisPayException(
                        "TRANSACTION_NOT_FOUND",
                        "Transaction not found: " + transactionId,
                        HttpStatus.NOT_FOUND));
    }

    public PagedResponse<TransactionResponse> listForUser(
            UUID userId, int page, int size,
            String status, String fromDate, String toDate) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "initiatedAt"));

        // Build dynamic criteria — each condition added only when param is present
        java.util.List<Criteria> conditions = new java.util.ArrayList<>();
        conditions.add(Criteria.where("userId").is(userId.toString()));

        if (status != null && !status.isBlank()) {
            conditions.add(Criteria.where("status").is(status.toUpperCase()));
        }

        Instant from = (fromDate != null && !fromDate.isBlank()) ? Instant.parse(fromDate) : null;
        Instant to   = (toDate   != null && !toDate.isBlank())   ? Instant.parse(toDate)   : null;

        if (from != null && to != null) {
            conditions.add(Criteria.where("initiatedAt").gte(from).lte(to));
        } else if (from != null) {
            conditions.add(Criteria.where("initiatedAt").gte(from));
        } else if (to != null) {
            conditions.add(Criteria.where("initiatedAt").lte(to));
        }

        Criteria criteria = new Criteria().andOperator(conditions.toArray(new Criteria[0]));

        Query query = new Query(criteria).with(pageable);
        long total  = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), TransactionView.class);
        java.util.List<TransactionView> content = mongoTemplate.find(query, TransactionView.class);

        Page<TransactionView> views = PageableExecutionUtils.getPage(content, pageable, () -> total);

        return PagedResponse.<TransactionResponse>builder()
                .content(views.map(transactionMapper::toListItemResponse).getContent())
                .page(views.getNumber())
                .size(views.getSize())
                .totalElements(views.getTotalElements())
                .totalPages(views.getTotalPages())
                .first(views.isFirst())
                .last(views.isLast())
                .build();
    }
}
