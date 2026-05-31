# AegisPay — End-to-End Transaction Flow

This document traces every step from "user taps Send Money" to "both balances updated and notifications delivered."

---

## Sequence Diagram

```mermaid
sequenceDiagram
    actor User
    participant WebApp as Next.js Web App<br/>(port 3000)
    participant Gateway as API Gateway<br/>(port 8080)
    participant TxSvc as Transaction Service<br/>(port 8082)
    participant Outbox as PostgreSQL Outbox<br/>(aegispay_transactions)
    participant Kafka as Apache Kafka
    participant Ledger as Ledger Service<br/>(port 8083)
    participant Risk as Risk Engine<br/>(port 8085)
    participant Orch as Payment Orchestrator<br/>(port 8084)
    participant Stripe as Stripe API
    participant Notify as Notification Service<br/>(port 8086)
    participant DP as Data Pipeline<br/>(port 8089)
    participant CH as ClickHouse

    User->>WebApp: Fill Send Money form (amount, payee, note)
    WebApp->>Gateway: POST /api/v1/transactions<br/>{ recipientId, amount, currency, note }<br/>Authorization: Bearer JWT<br/>Idempotency-Key: UUID

    %% Gateway auth
    Gateway->>Gateway: Validate JWT (Keycloak JWKS)
    Gateway->>Gateway: Check Idempotency-Key (Redis SET NX 24h)
    Gateway->>Gateway: Rate limit check (Redis INCR sliding window)
    Gateway->>TxSvc: Forward request

    %% Transaction creation
    TxSvc->>TxSvc: Extract userId from JWT claim aegispay_user_id
    TxSvc->>Outbox: BEGIN TRANSACTION<br/>INSERT INTO transactions (status=PENDING, ...)<br/>INSERT INTO outbox_events (topic=transaction.initiated, payload=...)
    note over Outbox: Both rows committed atomically<br/>Event can never be lost

    TxSvc-->>WebApp: 201 Created { transactionId, status: PENDING }
    WebApp->>WebApp: Open WebSocket subscription to /user/queue/transactions/{id}

    %% Outbox relay
    Outbox->>Kafka: Outbox Relay polls outbox table<br/>publishes transaction.initiated event
    Outbox->>Outbox: Mark outbox row as published

    %% Ledger reservation
    Kafka->>Ledger: Consume transaction.initiated
    Ledger->>Ledger: SELECT account WHERE user_id=payerId FOR UPDATE<br/>Check available_balance >= amount
    alt Insufficient funds
        Ledger->>Kafka: Publish balance.reservation.failed
        Kafka->>TxSvc: Consume → UPDATE status=FAILED, failureCode=INSUFFICIENT_FUNDS
        TxSvc->>Kafka: Publish transaction.failed
    else Sufficient funds
        Ledger->>Ledger: UPDATE accounts SET reserved_balance += amount<br/>(does NOT deduct yet — reserve only)
        Ledger->>Kafka: Publish balance.reserved
    end

    %% Risk assessment
    Kafka->>Risk: Consume balance.reserved
    Risk->>Risk: Run rule engine (velocity, amount, device, geography)
    Risk->>Risk: RAG query AI Platform for similar fraud patterns
    Risk->>Kafka: Publish risk.assessed { score, decision: ALLOW|REVIEW|BLOCK }

    alt BLOCK decision
        Kafka->>Orch: Consume risk.assessed (BLOCK)
        Orch->>Ledger: Release reservation (compensating)
        Orch->>Kafka: Publish transaction.failed (failureCode=RISK_BLOCKED)
    else ALLOW or REVIEW
        Kafka->>Orch: Consume risk.assessed (ALLOW)
    end

    %% Saga: Payment execution
    Orch->>Orch: Start/resume saga for transactionId
    Orch->>Stripe: POST /v1/payment_intents { amount, currency, metadata }
    alt Stripe returns error
        Stripe-->>Orch: { error: { code: "amount_too_small" } }
        Orch->>Orch: Map Stripe error code → failureCode
        Orch->>Kafka: Publish payment.failed { transactionId, failureCode }
        Kafka->>TxSvc: Consume → UPDATE status=FAILED, failureCode=amount_too_small
        Orch->>Ledger: Release reservation (compensating)
        Orch->>Kafka: Publish transaction.failed
    else Stripe success
        Stripe-->>Orch: { id: "pi_xxx", status: "succeeded" }
        Orch->>Kafka: Publish payment.completed { transactionId, stripePaymentIntentId }
    end

    %% Ledger commit
    Kafka->>Ledger: Consume payment.completed
    Ledger->>Ledger: BEGIN TRANSACTION<br/>INSERT ledger_entry (payer DEBIT)<br/>INSERT ledger_entry (payee CREDIT)<br/>UPDATE accounts (reserved_balance -= amount, available_balance -= amount for payer)<br/>UPDATE accounts (available_balance += amount for payee)
    note over Ledger: Append-only ledger entries<br/>Double-entry bookkeeping<br/>Net sum always = 0

    Ledger->>Kafka: Publish ledger.committed
    Kafka->>TxSvc: Consume → UPDATE status=COMPLETED

    %% WebSocket push
    TxSvc->>Notify: Publish transaction.status.changed (internal Kafka)
    Notify->>WebApp: STOMP /user/queue/transactions push (WebSocket)
    WebApp->>User: Status badge updates COMPLETED ✅

    %% Notifications
    TxSvc->>Kafka: Publish transaction.completed
    Kafka->>Notify: Consume transaction.completed
    Notify->>Notify: Lookup user email from MongoDB (UserContactDocument)
    par Email
        Notify->>User: Send "Payment successful" email (Gmail SMTP)
    and In-app badge
        Notify->>WebApp: WebSocket notification count increment
    end

    %% Analytics
    Kafka->>DP: Consume transaction.completed + risk.assessed
    DP->>DP: Buffer in TransactionMetricsStream / RiskAnalyticsStream
    DP->>CH: Flush batch every 5s → INSERT INTO transaction_facts / risk_assessments / saga_latencies
    CH-->>Grafana: Queries on next dashboard refresh
```

---

## State Machine

```
PENDING → RESERVED → RISK_CLEARED → PROCESSING → COMPLETED
                                                 ↘
              PENDING → FAILED (at any stage)
```

| Transition | Triggered by | Responsible service |
|-----------|-------------|---------------------|
| PENDING → RESERVED | `balance.reserved` consumed | Transaction Service |
| RESERVED → RISK_CLEARED | `risk.assessed (ALLOW)` consumed | Transaction Service |
| RISK_CLEARED → PROCESSING | Saga starts Stripe call | Payment Orchestrator |
| PROCESSING → COMPLETED | `ledger.committed` consumed | Transaction Service |
| Any → FAILED | Any failure event | Transaction Service |

---

## Idempotency Deep Dive

Three layers protect against duplicate execution:

1. **API Gateway** — `Idempotency-Key` header checked against Redis with `SET NX PX 86400000`. Second request with same key returns the cached response immediately without hitting Transaction Service.

2. **Outbox table** — `external_idempotency_key` has a `UNIQUE` constraint. A duplicate `INSERT` raises a DB constraint error, which Transaction Service catches and returns the existing transaction.

3. **Kafka consumers** — every consumer checks if the event's `transactionId` has already been processed (MongoDB `_id` uniqueness or Postgres `ON CONFLICT DO NOTHING`) before applying side effects.

---

## Failure Recovery

| Failure scenario | Recovery mechanism |
|-----------------|-------------------|
| Transaction Service crashes after INSERT but before Kafka publish | Outbox relay retries; event published on next poll (≤5s) |
| Ledger Service down when event arrives | Kafka consumer group lag builds; Ledger processes backlog when it recovers |
| Stripe API timeout | Payment Orchestrator saga has a timeout; triggers compensating transaction (release reservation) |
| ClickHouse sink error | Data Pipeline buffers in memory; retries with exponential backoff; Kafka consumer lag accumulates — no data loss |
| Notification delivery failure | Logged as warning; other channels still attempt delivery; never blocks main transaction flow |
