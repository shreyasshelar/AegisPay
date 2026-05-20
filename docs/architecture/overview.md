# AegisPay — System Architecture Overview

AegisPay is a production-grade, event-driven fintech platform. Every design decision is driven by one constraint: **money must never be lost or doubled, even when any single component fails**.

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT TIER                                     │
│                                                                              │
│   Next.js Web (3000)          iOS (SwiftUI)         Android (Compose)       │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ HTTPS / WebSocket
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            API GATEWAY (8080)                                │
│  Spring Cloud Gateway                                                        │
│  • JWT validation (Keycloak JWKS)                                            │
│  • Rate limiting (Redis token bucket)                                        │
│  • Idempotency key enforcement                                               │
│  • Circuit breaker (Resilience4j)                                            │
│  • Retry (GET + POST on 502/503)                                             │
│  • Route → downstream service                                                │
└──────────┬───────────────┬──────────────────────┬───────────────────────────┘
           │               │                      │
    ┌──────▼──────┐ ┌──────▼──────┐      ┌───────▼──────┐
    │ User Svc    │ │ Transaction │      │ AI Platform  │
    │ (8081)      │ │ Svc (8082)  │      │ (8091)       │
    └──────┬──────┘ └──────┬──────┘      └──────────────┘
           │               │
           │        ┌──────▼───────────────────────────────────────────────┐
           │        │                    KAFKA                              │
           │        │   transaction.initiated / risk.assessed               │
           │        │   payment.completed / transaction.failed              │
           │        │   ledger.reserved / saga.*                            │
           │        └──────┬──────────────────────────────────────┬─────────┘
           │               │                                      │
    ┌──────▼──────┐ ┌──────▼──────┐ ┌──────────────┐ ┌──────────▼──────────┐
    │ Ledger Svc  │ │ Risk Engine │ │  Payment     │ │ Notification Svc    │
    │ (8083)      │ │ (8085)      │ │  Orchestrator│ │ (8086)              │
    │             │ │             │ │  (8084)      │ │ WS / Email / SMS /  │
    │ append-only │ │ rules + RAG │ │ Saga coord.  │ │ Slack               │
    └──────┬──────┘ └─────────────┘ └──────────────┘ └─────────────────────┘
           │
    ┌──────▼───────────────────────────────────────────────────────────────┐
    │                         DATA LAYER                                    │
    │                                                                       │
    │  PostgreSQL 16 (pgvector)      Redis 7        MongoDB 7              │
    │  ├─ aegispay_users             ├─ sessions    ├─ tx read models      │
    │  ├─ aegispay_transactions      ├─ rate limits ├─ user contacts       │
    │  ├─ aegispay_ledger            └─ idempotency └─ notification log    │
    │  ├─ aegispay_sagas                                                   │
    │  ├─ aegispay_risk                                                     │
    │  ├─ aegispay_ai                                                       │
    │  └─ aegispay_keycloak                                                 │
    └───────────────────────────────────────────────────────────────────────┘
           │
    ┌──────▼────────────────────────────────────────────────────────────────┐
    │                       ANALYTICS LAYER                                  │
    │                                                                        │
    │  Data Pipeline (8089)          ClickHouse 24.4                        │
    │  Kafka → ClickHouseSink  →     ├─ transaction_facts                   │
    │  5-second batch flush          ├─ risk_assessments                    │
    │                                ├─ saga_latencies                      │
    │  Reconciliation Svc (8087)     └─ reconciliation_breaks               │
    │  Scheduled batch job                   │                              │
    │  Ledger ↔ Stripe diff check            ▼                              │
    │                                Grafana 10.4 (3100)                    │
    │                                3 pre-built dashboards                  │
    └───────────────────────────────────────────────────────────────────────┘
```

---

## Component Roles (one sentence each)

| Component | Role |
|-----------|------|
| **API Gateway** | Single entry point — authenticates every request, enforces rate limits, routes to services |
| **User Service** | Manages user registration, KYC status, profile — source of truth for identity |
| **Transaction Service** | Owns the transaction state machine; publishes events via Outbox |
| **Ledger Service** | Append-only double-entry ledger; the financial truth store |
| **Payment Orchestrator** | Saga coordinator — drives the sequence: reserve → risk → pay → commit |
| **Risk Engine** | Rule-based + AI RAG fraud scoring; issues ALLOW / REVIEW / BLOCK decisions |
| **Notification Service** | Delivers WebSocket, Email, SMS, Slack notifications; maps userId → contact |
| **AI Platform** | RAG queries (Anthropic Claude), error explanation, fraud copilot, KYC OCR |
| **Data Pipeline** | Consumes Kafka events, batch-flushes to ClickHouse every 5 s |
| **Reconciliation Service** | Nightly batch — diffs ledger vs Stripe, writes breaks to ClickHouse |
| **Grafana** | Live dashboards backed by ClickHouse (Payment Ops, Fraud Intel, SLA) |

---

## Data Flow Principles

1. **Write path** → REST API → Transaction Service (writes to Postgres + Outbox) → Kafka → downstream consumers
2. **Read path** → API Gateway → MongoDB read models (CQRS) — no joins, no OLTP pressure
3. **Analytics path** → Kafka → Data Pipeline → ClickHouse → Grafana (eventual, not real-time SQL)
4. **Notification path** → Kafka consumer → resolve contact (MongoDB) → dispatch all channels in parallel

---

## Why Event-Driven?

Traditional synchronous REST chains fail under partial failure: if Ledger Service is slow, Transaction Service hangs, API Gateway times out, user gets an error — but money may already be reserved. An event-driven design decouples every step:

- **Saga via Kafka** — each step publishes an event; the next step only starts when its input event arrives
- **Outbox Pattern** — events are written to the DB in the same transaction as the domain entity, so an event can never be lost even if the broker is temporarily down
- **Idempotency** — every consumer is idempotent; replaying an event does not double-charge

---

## Deployment Environments

| Environment | Kubernetes | Grafana | Secrets | Notes |
|-------------|-----------|---------|---------|-------|
| Local | Docker Compose | ✅ port 3100 | plaintext `.env.local` | Full stack, seeds test data |
| Dev (k3s) | `aegispay` namespace | ✅ Traefik TLS | HashiCorp Vault | Single-node, OpenRouter AI, Stripe test mode |
| Production (AWS EKS) | `aegispay-prod` namespace | ✅ TLS + basic-auth | HashiCorp Vault | HA, PDB, HPA |
