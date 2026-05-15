# AegisPay — Idempotency Pattern

**Idempotency**: applying an operation multiple times has the same effect as applying it once.

This is critical in payments. If a network error causes the client to retry `POST /api/v1/transactions`, we must not create two payments.

---

## Three-Layer Idempotency

### Layer 1 — API Gateway (HTTP)

Every mutating request must include `Idempotency-Key: <UUID>` header.

```
Gateway receives: POST /api/v1/transactions + Idempotency-Key: abc-123

Redis: SET idempotency:abc-123 "pending" NX PX 86400000
  → returns OK (first request) → forward to service
  → returns nil (duplicate) → return cached 200 response immediately
```

The response is cached in Redis for 24 hours. Any retry within 24 hours gets the exact same response — even if Transaction Service is down.

### Layer 2 — Database (Postgres)

Transaction table has a `UNIQUE` constraint on `external_idempotency_key`:

```sql
CREATE UNIQUE INDEX idx_transactions_idempotency 
ON transactions (external_idempotency_key) 
WHERE external_idempotency_key IS NOT NULL;
```

If the API Gateway cache miss occurs (different server, Redis evicted), the DB constraint is the final safety net.

### Layer 3 — Kafka Consumers

Every Kafka consumer checks if it has already processed the event before applying side effects:

```java
// Ledger Service consumer
public void consume(BalanceReservationRequest event) {
    if (accountRepository.existsReservation(event.transactionId())) {
        log.info("Already processed transactionId={}, skipping", event.transactionId());
        return;
    }
    // process...
}
```

MongoDB upserts use `_id = transactionId` so `$set` operations are naturally idempotent.

---

## Idempotency in Ledger Double-Entry

The most critical idempotency is in the ledger commit:

```sql
INSERT INTO ledger_entries (transaction_id, type, amount, currency, created_at)
VALUES (?, 'DEBIT', ?, ?, ?)
ON CONFLICT (transaction_id, type) DO NOTHING;
```

A replayed `payment.completed` event will try to insert the same debit/credit entries but the `ON CONFLICT DO NOTHING` ensures the account balance is only updated once.

---

## At-Least-Once vs Exactly-Once

Kafka by default provides **at-least-once** delivery. Exactly-once requires transactions that add significant latency. AegisPay's approach:

> **at-least-once delivery + idempotent consumers = effectively-once processing**

This is the standard production pattern — simpler to reason about, easier to debug than Kafka's transactional exactly-once.
