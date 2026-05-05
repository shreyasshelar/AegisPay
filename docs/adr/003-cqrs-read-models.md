# ADR-003: CQRS with MongoDB Read Models for Dashboard Queries

**Status**: Accepted
**Date**: 2026-05-02
**Deciders**: Platform Engineering

---

## Context

`transaction-service` writes to a PostgreSQL relational model optimised for transactional correctness (normalised, with foreign keys and optimistic locking). Dashboard and customer-facing queries need:
- Paginated transaction history with filters (status, date range, amount)
- Rich denormalised views (last event, estimated completion time, AI explanation)
- Sub-millisecond read latency at high concurrency

Serving these from the write-side PostgreSQL requires complex joins and risks read/write contention on hot rows.

---

## Decision

Apply the **CQRS** (Command Query Responsibility Segregation) pattern:

- **Write side**: PostgreSQL, owned by `transaction-service`. Source of truth. Mutated only via the domain service layer.
- **Read side**: MongoDB, maintained by a `TransactionReadModelConsumer` Kafka consumer inside `transaction-service`. Listens on `transaction.completed`, `transaction.failed`, `transaction.rolled-back` and upserts a `transaction_views` document.

The read model document is a denormalised projection: it contains status, amount, currency, timestamps, last event type, and (when available) AI delay explanation. It is rebuilt from Kafka events; if it is lost, replaying the topic reconstructs it.

---

## Alternatives Considered

**Single PostgreSQL database with read replicas**: Simpler operationally but doesn't eliminate the schema mismatch between write-optimised rows and read-optimised projections.

**Elasticsearch**: Better full-text search capability but operationally heavier and not needed for our current query patterns.

**Materialised views in PostgreSQL**: Requires periodic refresh and adds write-side latency; doesn't decouple read schema from write schema.

---

## Consequences

- Read models are **eventually consistent** with the write side. The lag is bounded by Kafka consumer lag (typically < 500ms).
- If a consumer falls behind (e.g. during a restart), the dashboard shows stale data. This is acceptable; financial correctness lives on the write side.
- MongoDB schema is schema-free, allowing the read model to evolve independently of the PostgreSQL write schema.
- `notification-service` stores notification history in MongoDB using the same approach (no write-side PostgreSQL).
