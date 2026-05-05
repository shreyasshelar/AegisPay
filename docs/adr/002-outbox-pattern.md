# ADR-002: Transactional Outbox Pattern for Reliable Kafka Publishing

**Status**: Accepted
**Date**: 2026-05-02
**Deciders**: Platform Engineering

---

## Context

Services must publish Kafka events as a consequence of state changes (e.g., a transaction being created triggers `transaction.initiated`). Naive approaches fail:

- Publishing directly from the service layer **before** DB commit: if the DB write fails, an event has been published for a transaction that doesn't exist.
- Publishing **after** DB commit: if the service crashes before publishing, the event is lost.

Both result in phantom events or lost events — unacceptable for financial data.

---

## Decision

Use the **Transactional Outbox** pattern across all event-producing services.

Every service writes to an `outbox_entries` table **within the same database transaction** as the business entity change. A dedicated `OutboxScheduler` (extending `OutboxSchedulerBase` from `common-kafka`) polls the table with `SELECT ... FOR UPDATE SKIP LOCKED`, publishes to Kafka, then marks entries as PUBLISHED.

The abstract `OutboxSchedulerBase` provides:
- Batch polling (configurable batch size)
- Marking published / failed with timestamps
- DLQ publishing after max retries

Each service provides concrete `OutboxEntryRepository` and `OutboxScheduler` implementations.

---

## Alternatives Considered

**Debezium CDC (Change Data Capture)**: Would also solve the dual-write problem and is operationally superior at scale. Rejected for now due to additional operational complexity (Debezium connector cluster) and the desire to avoid external dependencies in Phase 1. Can be adopted in a future phase without changing service code.

**Kafka Transactions (idempotent producer)**: Does not solve the dual-write problem between the DB and Kafka; it only ensures exactly-once delivery within Kafka itself.

---

## Consequences

- Every event-producing service needs an `outbox_entries` table and a scheduler bean.
- There is an inherent latency of up to `poll-interval-ms` (default 500ms) between the DB commit and Kafka publish — acceptable for our SLA.
- The `outbox_entries` table must be pruned periodically (published entries older than N days).
- `SKIP LOCKED` requires PostgreSQL 9.5+ — already satisfied by our PostgreSQL 16 baseline.
