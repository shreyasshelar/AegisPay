# AegisPay — Outbox Pattern

## Problem

In a distributed system, publishing a Kafka event after a database write has a fundamental race condition:

```
1. Write transaction to Postgres ✅
2. Server crashes here ❌
3. Publish event to Kafka ← never happens
```

The transaction is created in the DB but the payment flow never continues — a "ghost transaction" that appears PENDING forever.

Alternatively:
```
1. Publish event to Kafka ✅
2. Server crashes here ❌
3. Write transaction to Postgres ← never happens
```

Now Kafka consumers start processing an event for a transaction that doesn't exist in the DB.

**Neither order is safe.** You cannot atomically write to both a relational database and a message broker.

---

## Solution: Transactional Outbox

Write the event to the **same database** as the domain entity, inside the **same transaction**. A separate relay process then publishes from the DB to Kafka.

```
Transaction Service                Outbox Relay
────────────────────               ────────────────────────────
BEGIN;
  INSERT INTO transactions ...
  INSERT INTO outbox_events ...     poll outbox_events WHERE published = false
COMMIT;                    ──────▶ kafka.send(topic, payload)
                                   UPDATE outbox_events SET published = true
```

If the relay crashes after publishing but before marking as published, it will re-publish on restart. Kafka consumers are idempotent to handle this.

---

## Schema

```sql
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,  -- e.g. "Transaction"
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,  -- e.g. "transaction.initiated"
    topic           VARCHAR(200) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMP DEFAULT now(),
    published       BOOLEAN DEFAULT false,
    published_at    TIMESTAMP
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (published, created_at) WHERE published = false;
```

---

## Guarantees

| Scenario | Outcome |
|----------|---------|
| DB write succeeds, broker publish fails | Event stays in outbox; relay retries |
| DB write succeeds, relay crashes before marking published | Event replayed on relay restart (at-least-once delivery) |
| DB transaction rolls back | Outbox row is also rolled back — event never published |
| Duplicate publish (relay publishes, crashes before mark) | Consumer idempotency handles duplicate |

**At-least-once delivery** + **consumer idempotency** = **effectively-once processing**.

---

## Why Not Kafka Transactions?

Kafka's transactional producer can write to Kafka and commit a consumer offset atomically. But this only works within Kafka — it doesn't help when you also need to write to Postgres in the same atomic operation. The Outbox pattern is the only general solution that works with any combination of databases.
