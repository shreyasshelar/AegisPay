# ADR-001: Saga Orchestration for Distributed Payment Transactions

**Status**: Accepted
**Date**: 2026-05-02
**Deciders**: Platform Engineering

---

## Context

A payment transaction in AegisPay spans multiple services (Ledger, Risk Engine, external payment gateway). These services each own their own database; there is no shared transaction coordinator.

We need a strategy that:
- Ensures exactly-once money movement across services
- Handles partial failures with compensating transactions
- Remains consistent under network partitions or service restarts

---

## Decision

Use the **Saga Orchestration** pattern with a dedicated `payment-orchestrator` service.

The orchestrator owns the saga state machine and drives each step by publishing Kafka commands, then reacting to reply events. Steps and their compensations:

| Step | Command | Reply | Compensation |
|---|---|---|---|
| 1 | `balance.reserve.requested` | `balance.reserved` / `balance.reserve.failed` | `balance.rollback.requested` |
| 2 | `risk.assessment.requested` | `risk.assessed` | `balance.rollback.requested` |
| 3 | External gateway HTTP call | `payment.processed` | `balance.rollback.requested` |
| 4 | `balance.commit.requested` | `balance.committed` | — |

Saga state is persisted in PostgreSQL (`sagas` + `saga_steps` tables). Every Kafka consumer is idempotent — receiving the same reply twice is a no-op if the step is already COMPLETED.

---

## Alternatives Considered

**Two-Phase Commit (2PC)**: Rejected. Requires a distributed transaction coordinator; poor availability under coordinator failure; not supported by all services.

**Choreography (event-driven, no orchestrator)**: Rejected for the happy path because it makes observability and debugging very difficult — there is no single component that knows the overall transaction state. Kept for notification events which have no compensation logic.

---

## Consequences

- The orchestrator is a stateful service; it must have a timeout scheduler to detect stuck sagas and trigger compensation.
- All saga reply consumers must be idempotent.
- Adding a new step requires changes only in the orchestrator.
- Compensation ordering must be reverse of execution ordering and must be tested explicitly.
