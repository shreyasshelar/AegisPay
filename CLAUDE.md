# 🧠 AegisPay — System Overview for AI Assistants

## 📌 System Type

Production-grade event-driven fintech platform. Every design decision is driven by one guarantee: **money must never be lost or doubled, even when any single component fails.**

Core capabilities:
- Distributed payment transactions (Saga pattern, Outbox, idempotency)
- Real-time fraud detection (rule engine + AI RAG)
- Immutable double-entry ledger
- Real-time notifications (WebSocket, Email, SMS, Slack)
- AI-assisted error explanation and fraud copilot
- Analytics via ClickHouse + Grafana

---

## 🏗️ Architecture

10 Spring Boot microservices + 1 Next.js frontend, connected by Kafka.

```
Next.js (3000)
    ↓ HTTPS/WebSocket
API Gateway (8080) — JWT auth, rate limit, circuit breaker, retry
    ↓ route
User (8081) | Transaction (8082) | Ledger (8083) | Orchestrator (8084)
Risk (8085) | Notification (8086) | Recon (8087) | DataPipeline (8089) | AI (8091)
    ↓ Kafka
Data Layer: Postgres (5433) + Redis (6379) + MongoDB (27017)
    ↓ Kafka → DataPipeline → ClickHouse (8123)
                                  ↓
                           Grafana (3100)
```

---

## 🔁 Core Transaction Flow (numbered steps)

1. User submits payment → Next.js → API Gateway (JWT validate, rate limit, idempotency check)
2. Gateway → Transaction Service: creates transaction (PENDING) + Outbox event (same DB transaction)
3. Outbox Relay → Kafka: `transaction.initiated`
4. Ledger Service: reserves funds (`reserved_balance += amount`)
5. Risk Engine: rule evaluation + AI RAG → `risk.assessed {ALLOW/REVIEW/BLOCK}`
6. Payment Orchestrator (Saga): if ALLOW → call Stripe API
7. Stripe success → `payment.completed` → Ledger commits (debit payer, credit payee)
8. Transaction status → COMPLETED; Notification Service → WebSocket + Email (+SMS+Slack if FAILED)
9. Data Pipeline: Kafka events → ClickHouse (5s batch flush) → Grafana dashboards

---

## 📐 Key Design Patterns

| Pattern | Where used | Why |
|---------|-----------|-----|
| **Outbox Pattern** | Transaction Service | Atomic write to DB + Kafka — event can never be lost |
| **Saga (Orchestration)** | Payment Orchestrator | Distributed transaction with compensating rollback, no 2PC |
| **CQRS** | Transaction Service | Write → Postgres; Read → MongoDB (denormalised) |
| **Idempotency** | API Gateway (Redis), DB (UNIQUE), all Kafka consumers | Exactly-once behaviour from at-least-once delivery |
| **Double-entry Ledger** | Ledger Service | Mathematical correctness: SUM(debits) = SUM(credits) always |
| **Circuit Breaker** | API Gateway (Resilience4j) | Prevents cascade failures; fallback returns structured error |
| **RAG** | AI Platform | Explainable AI — retrieved docs justify every AI response |

---

## 🗄️ Data Architecture

| Store | Services | Data |
|-------|---------|------|
| PostgreSQL (5433) | All services (separate DB per service) | Transactional, normalised, Flyway-managed |
| Redis (6379) | API Gateway, Ledger | Rate limits, idempotency keys, session cache |
| MongoDB (27017) | Transaction Service, Notification Service | CQRS read models, user contacts |
| ClickHouse (8123) | Data Pipeline, Reconciliation | Analytics: `transaction_facts`, `risk_assessments`, `saga_latencies`, `reconciliation_breaks` |
| pgvector (in Postgres) | AI Platform | Embedding vectors for RAG knowledge base |

---

## 📡 Kafka Topics

`transaction.initiated` → `balance.reserved` → `risk.assessed` → `payment.completed` → `ledger.committed` → `transaction.completed` / `transaction.failed`

Cross-cutting: `user.registered` (User Svc → Notification Svc), `risk.assessment.completed` (Risk → DataPipeline)

---

## 🔐 Security Model

- **OAuth2 + JWT** via Keycloak 24 (self-hosted, multi-IdP: Google, Microsoft, GitHub, Apple)
- **JWT claims**: `aegispay_user_id` (domain UUID), `realm_access.roles` (CUSTOMER/BACK_OFFICE/ADMIN/PARTNER)
- **STOMP WebSocket auth**: `StompAuthChannelInterceptor` validates JWT from CONNECT frame headers — required for `convertAndSendToUser` routing
- **Rate limiting**: Redis sliding window per userId (100 req/60s default)
- **Secret management**: External Secrets Operator → AWS Secrets Manager (dev/staging) or HashiCorp Vault (prod/on-prem)

---

## 📊 Observability

**Two Grafana instances** (intentional separation):
- **kube-prometheus-stack Grafana** → Prometheus → JVM, Kafka lag, K8s workloads, HTTP rates
- **AegisPay Grafana (3100)** → ClickHouse → Payment Ops, Fraud Intelligence, SLA & Latency

**PrometheusRules**: `SagaTimeoutRateHigh`, `DlqDepthNonZero`, `BalanceNegative`, `NotificationDeliveryFailureHigh`, `DataPipelineSinkErrorHigh`, `ReconciliationBreakCountHigh`

---

## 🚨 Critical Guarantees

1. **No double-spend**: optimistic locking + `FOR UPDATE` on balance read
2. **No lost events**: Outbox pattern — event in same DB transaction as domain entity
3. **No lost money**: double-entry bookkeeping, `SUM(all_entries) = 0` invariant
4. **No silent failures**: every failure has a `failureCode` + AI explanation + notification
5. **No data drift**: CQRS read models updated by same Kafka events that drive state machine

---

## 🏗️ Infrastructure

| Local | Kubernetes | Notes |
|-------|-----------|-------|
| `docker compose up -d` | Helm chart `infra/helm/aegispay/` | 5 envs: dev/staging/prod/on-prem/local |
| `./start-local.sh` (macOS/Linux) | Argo CD GitOps sync | macOS + Windows bootstrap scripts |
| `start-aegispay.bat` (Windows) | | Auto-detects Maven, waits for all services |

**Ports**: Gateway:8080, User:8081, Tx:8082, Ledger:8083, Orch:8084, Risk:8085, Notify:8086, Recon:8087, DataPipeline:8089, AI:8091, Web:3000, Keycloak:8180, Kafka:9094, KafkaUI:8090, ClickHouse:8123, Grafana:3100, Postgres:5433, Redis:6379, Mongo:27017

---

## 📁 Key File Locations

| Area | Path |
|------|------|
| Docker Compose | `docker-compose.yml` |
| Helm chart | `infra/helm/aegispay/` |
| Grafana dashboards | `infra/grafana/dashboards/` + `infra/helm/aegispay/files/dashboards/` |
| Grafana provisioning | `infra/grafana/provisioning/` |
| ClickHouse init SQL | `infra/clickhouse/init.sql` |
| Local dev guide | `docs/local-dev.md` |
| Architecture docs | `docs/architecture/`, `docs/flows/`, `docs/patterns/` |
| Keycloak realm | `infra/local/keycloak/realm-export.json` |
| Shared libs | `libs/common-domain`, `libs/common-events`, `libs/common-kafka`, `libs/common-security`, `libs/common-observability` |
