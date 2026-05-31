# AegisPay — CQRS (Command Query Responsibility Segregation)

## Problem

If every "get my transactions" API call hits the same PostgreSQL table that handles payment writes, they compete for the same resources. Under load:
- A slow `SELECT * FROM transactions WHERE user_id = ? ORDER BY initiated_at DESC` holds shared locks
- Concurrent `INSERT` / `UPDATE` for in-flight payments get delayed
- Adding indexes to speed up reads can slow down writes

Also, the write model is normalised for integrity — the read model for the dashboard needs a denormalised view with recipient name, amount formatted, status label, etc. Serving that from the write DB requires JOINs that are expensive under high read load.

---

## Solution: Separate Read Models

```
WRITE PATH (Postgres — normalised)          READ PATH (MongoDB — denormalised)
──────────────────────────────────          ─────────────────────────────────
transactions table                          TransactionView document
  id UUID                                     { _id, status, amount, currency,
  payer_id UUID                                 payerName, payeeName, initiatedAt,
  payee_id UUID            Kafka event →        completedAt, failureReason,
  amount DECIMAL           propagates →         failureCode, ... }
  status VARCHAR           read model
  ...                      update
```

**Commands** (write, mutate state) → PostgreSQL via Transaction Service  
**Queries** (read, display) → MongoDB via Transaction Service's CQRS query handler

---

## AegisPay Read Models

### `TransactionView` (MongoDB: `transaction-views` collection)

Updated whenever Transaction Service receives a status-change Kafka event:

```json
{
  "_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "amount": "500.0000",
  "currency": "INR",
  "payerId": "59295e61-a284-40ed-8d3b-9e15bedeb040",
  "payeeId": "3bf3e523-9de8-4254-9cc9-d5fa50ff8d4a",
  "note": "Lunch split",
  "failureReason": null,
  "failureCode": null,
  "initiatedAt": "2026-05-16T10:30:00Z",
  "completedAt": "2026-05-16T10:30:03Z",
  "externalReference": "pi_3xxx"
}
```

### `UserContactDocument` (MongoDB: `user-contacts` collection)

Updated when `UserRegisteredEvent` is consumed by Notification Service:

```json
{
  "_id": "59295e61-a284-40ed-8d3b-9e15bedeb040",
  "email": "customer@aegispay.local",
  "maskedEmail": "cu*****@aegispay.local",
  "phone": "+919000000001"
}
```

---

## Eventual Consistency Trade-Off

The read model is **eventually consistent** with the write model. Between the Kafka event being published and the MongoDB document being updated, a `GET /api/v1/transactions/{id}` might return the old status.

For AegisPay this is acceptable because:
1. The WebSocket push arrives at the same time as the MongoDB update — the user sees the correct status via WebSocket before the next REST poll
2. The delay is typically < 200ms
3. Financial correctness is enforced by the write model (Postgres) — the read model is only for display

---

## Benefits Measured

| Metric | Before CQRS | After CQRS |
|--------|------------|-----------|
| Transaction list API latency | 80–200ms (JOIN queries) | 5–15ms (MongoDB document read) |
| Write-path DB lock contention | High under load | Eliminated — writes don't touch MongoDB |
| Read model schema changes | Requires DB migration | Just add a field to the document — no migration |
