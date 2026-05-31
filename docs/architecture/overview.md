# AegisPay — System Architecture Overview

AegisPay is a production-grade, event-driven fintech platform. Every design decision is driven by one constraint: **money must never be lost or doubled, even when any single component fails**.

---

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                               CLIENT TIER                                     │
│                                                                               │
│  Next.js Web (3000)     iOS (SwiftUI + AppAuth)    Android (Compose + Hilt)  │
│  NextAuth.js · Zustand  TokenStore · Keychain      TokenStore · EncryptedSP  │
│  ReactMarkdown          AegisMarkdownView           MarkdownText composable   │
└───────────────────────────────────┬──────────────────────────────────────────┘
                                    │ HTTPS / WebSocket (STOMP)
                                    │ JWT Bearer — all three clients identical
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                             API GATEWAY (8080)                                │
│  Spring Cloud Gateway                                                         │
│  • JWT validation (Keycloak JWKS, cached)    • max-http-request-header: 32KB │
│  • Rate limiting (Redis sliding window)       • Idempotency key enforcement   │
│  • Circuit breaker (Resilience4j)             • Retry (GET + POST 502/503)   │
│  • Route → downstream service                 • X-User-Id header injection    │
└──────────┬────────────────┬─────────────────────────┬────────────────────────┘
           │                │                         │
    ┌──────▼──────┐  ┌──────▼──────┐         ┌───────▼──────┐
    │ User Svc    │  │ Transaction │         │ AI Platform  │
    │ (8081)      │  │ Svc (8082)  │         │ (8091)       │
    │ KYC · roles │  │ Outbox·CQRS │         │ RAG·Triage   │
    └──────┬──────┘  └──────┬──────┘         │ KYC OCR      │
           │                │                └──────────────┘
           │         ┌──────▼──────────────────────────────────────────────┐
           │         │                     KAFKA (KRaft)                    │
           │         │   transaction.initiated  · balance.reserved          │
           │         │   risk.assessed          · payment.completed         │
           │         │   ledger.committed       · transaction.failed        │
           │         │   user.registered        · risk.assessment.completed │
           │         └──────┬───────────────────────────────────┬───────────┘
           │                │                                   │
    ┌──────▼──────┐  ┌──────▼──────┐  ┌─────────────┐  ┌──────▼──────────────┐
    │ Ledger Svc  │  │ Risk Engine │  │  Payment    │  │ Notification Svc    │
    │ (8083)      │  │ (8085)      │  │ Orchestrator│  │ (8086)              │
    │ double-entry│  │ rules + RAG │  │  (8084)     │  │ WebSocket · Email   │
    │ append-only │  │ ALLOW/BLOCK │  │ Saga + Stripe│  │ SMS · Slack        │
    └──────┬──────┘  └─────────────┘  └─────────────┘  └─────────────────────┘
           │
    ┌──────▼────────────────────────────────────────────────────────────────┐
    │                           DATA LAYER                                   │
    │                                                                        │
    │  PostgreSQL 16 (pgvector)       Redis 7          MongoDB 7            │
    │  ├─ aegispay_users              ├─ idempotency   ├─ tx read models    │
    │  ├─ aegispay_transactions       ├─ rate limits   ├─ user contacts     │
    │  ├─ aegispay_ledger             └─ session cache └─ notification log  │
    │  ├─ aegispay_sagas                                                    │
    │  ├─ aegispay_risk                                                     │
    │  ├─ aegispay_ai  (+ pgvector embeddings)                              │
    │  └─ aegispay_keycloak                                                 │
    └───────────────────────────────────────────────────────────────────────┘
           │
    ┌──────▼────────────────────────────────────────────────────────────────┐
    │                         ANALYTICS LAYER                                │
    │                                                                        │
    │  Data Pipeline (8089)          ClickHouse 24.4                        │
    │  Kafka → ClickHouseSink  →     ├─ transaction_facts                   │
    │  5-second batch flush          ├─ risk_assessments                    │
    │                                ├─ saga_latencies                      │
    │  Reconciliation Svc (8087)     └─ reconciliation_breaks               │
    │  Ledger ↔ Stripe nightly diff           │                             │
    │                                         ▼                             │
    │                                Grafana 10.4 (3100)                    │
    │                                3 pre-built dashboards                  │
    └───────────────────────────────────────────────────────────────────────┘
```

---

## Component Roles (one sentence each)

| Component | Role |
|-----------|------|
| **Next.js Web** | NextAuth.js PKCE session, role-based nav (Customer/BackOffice/Admin), ReactMarkdown for AI output |
| **iOS App** | SwiftUI + AppAuth PKCE, TokenStore (Keychain), AegisMarkdownView, Stripe iOS SDK |
| **Android App** | Jetpack Compose + Hilt, AppAuth PKCE, TokenStore (EncryptedSharedPreferences), MarkdownText composable |
| **API Gateway** | Single entry point — authenticates every request, enforces rate limits, routes to services |
| **User Service** | Manages user registration (SSO/PKCE), KYC status, profile — source of truth for identity |
| **Transaction Service** | Owns the transaction state machine; publishes events via Outbox; CQRS writes to MongoDB |
| **Ledger Service** | Append-only double-entry ledger; the financial truth store |
| **Payment Orchestrator** | Saga coordinator — drives the sequence: reserve → risk → pay → commit |
| **Risk Engine** | Rule-based + AI RAG fraud scoring; issues ALLOW / REVIEW / BLOCK decisions; exposes `/cases` for back-office |
| **Notification Service** | Delivers WebSocket (STOMP), Email, SMS, Slack notifications; maps userId → contact |
| **AI Platform** | RAG queries (Anthropic Claude), error explanation, fraud copilot, incident triage agent, KYC OCR |
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
| Local (Windows) | Docker Compose (`start-aegispay.bat`) | ✅ port 3100 | plaintext `.env.local` | Full stack, seeds admin/backoffice users |
| Dev / GCP VM | k3s on GCP `e2-standard-4` (16 GB), Traefik, ArgoCD GitOps | ✅ TLS via cert-manager | HashiCorp Vault in-cluster + GCP KMS auto-unseal | Single-node, OpenRouter AI, Stripe test mode, VM scheduled start/stop |
| Production | k3s or managed K8s, `aegispay-prod` namespace | ✅ TLS + auth | HashiCorp Vault | HA replicas, PDB, HPA |

### GCP VM Cost Strategy

Dev VM scheduled on/off via GCP Cloud Scheduler (matches Indian tech-community peak hours):
- Weekdays: **08:00–22:00 IST** on, off overnight
- Weekends: **09:00–15:00 IST** on, off rest of day
- Average uptime ≈ 49 % → compute cost ≈ $50/month on `e2-standard-4` vs $105 always-on
