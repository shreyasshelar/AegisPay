# 🚀 AegisPay — Skills & Technologies

## 🧠 Core Engineering

- Distributed Systems Design
- Event-Driven Architecture (Kafka, CQRS, Outbox, Saga)
- Financial Systems Engineering (Fintech)
- High Availability & Fault Tolerance
- Consistency Models (eventual vs strong, 2PC vs Saga)

---

## 🔁 Microservices & Patterns

| Pattern | Implementation |
|---------|---------------|
| Saga (Orchestration) | Payment Orchestrator drives sequence; compensating transactions on failure |
| CQRS | Write → PostgreSQL; Read → MongoDB denormalised views |
| Outbox Pattern | Events written atomically with domain entity; relay publishes to Kafka |
| Idempotency | API Gateway (Redis SET NX), DB UNIQUE constraints, consumer-level checks |
| Double-Entry Ledger | Append-only `ledger_entries`; `SUM = 0` invariant; reservation + commit phases |
| Circuit Breaker | Resilience4j in API Gateway; failureRate=60%, waitDuration=15s |
| Event Sourcing | Ledger design — state reconstructible from entries |
| Batch Processing | Spring Batch (Reconciliation Service) |

---

## 💳 Fintech Domain

- Payment lifecycle management (PENDING → RESERVED → RISK_CLEARED → PROCESSING → COMPLETED)
- Balance reservation (two-phase: reserve on initiation, commit on success)
- Double-spend prevention (optimistic locking + `FOR UPDATE`)
- Immutable audit trail (append-only ledger entries)
- Risk-based decision systems (rule engine + AI RAG)
- Stripe payment gateway integration (PaymentIntents, pre-flight minimum checks)
- Reconciliation (Ledger ↔ Stripe diff, break detection)

---

## 🤖 AI / GenAI

- RAG (Retrieval-Augmented Generation) with pgvector cosine similarity
- Anthropic Claude API (claude-3-5-sonnet) for generation
- Fraud Copilot (explain why transaction was flagged using historical patterns)
- Error Resolution Agent (failureCode → plain English + action)
- Incident Triage Agent (agentic tool-use loop)
- KYC OCR (multimodal: document image → structured fields)
- Knowledge base management (embeddings stored in pgvector)
- OpenRouter proxy for on-prem cost optimisation

---

## 🔐 Security

- OAuth2 + JWT (Keycloak 24, self-hosted)
- Multi-IdP federation (Google, Microsoft Entra, GitHub, Apple)
- STOMP WebSocket authentication (channel interceptor validates JWT on CONNECT)
- Role-based authorization (CUSTOMER, BACK_OFFICE, ADMIN, PARTNER, MERCHANT_OPERATOR)
- Actor-based audit logging (ActorContext ThreadLocal)
- External Secrets Operator (AWS Secrets Manager + HashiCorp Vault)
- Redis rate limiting (sliding window per userId)
- Idempotency keys (anti-replay at API Gateway layer)
- Sensitive data masking in logs (email, phone, card, JWT)
- Zero-trust: services never trusted without valid JWT

---

## ⚙️ Backend

- Java 21 (virtual threads via Project Loom)
- Spring Boot 3.3.5
- Spring Cloud Gateway (reactive, circuit breaker, retry)
- Spring Security (OAuth2 Resource Server, WebSocket STOMP auth)
- Spring Data JPA (PostgreSQL), Spring Data MongoDB, Spring Data Redis
- Spring Batch (reconciliation scheduled jobs)
- Spring AI (RAG, Anthropic API, pgvector integration)
- Flyway (database migrations, per-service schemas)
- Micrometer + Actuator (Prometheus metrics, health checks)
- Lombok + MapStruct (code generation)
- Resilience4j (circuit breaker, rate limiter)

---

## ⚛️ Frontend

- Next.js 14 (App Router, Server Components)
- React Query (TanStack Query v5) — caching, polling, optimistic updates
- NextAuth.js — session management, Keycloak OAuth2 integration
- STOMP over SockJS — real-time WebSocket notifications
- Zod — runtime schema validation shared between frontend and API types
- TailwindCSS + shadcn/ui — design system
- TypeScript (strict mode)

---

## 📡 Messaging

- Apache Kafka 3.7 (KRaft mode — no ZooKeeper)
- Kafka Streams (TransactionMetricsStream, RiskAnalyticsStream in Data Pipeline)
- Kafka consumer groups with partition-by-userId for ordering
- Dead Letter Queue (DLQ) pattern with auto-promotion
- At-least-once delivery + idempotent consumers = effectively-once processing
- 12 topics across 5 producing services

---

## 🗄️ Data

- PostgreSQL 16 with pgvector extension (OLTP + vector store)
- MongoDB 7 (CQRS read models, user contacts)
- Redis 7 (rate limits, idempotency keys, cache)
- ClickHouse 24.4 (OLAP analytics — column-store, MergeTree, materialized views)
- Separate DB per service (6 Postgres databases: users, transactions, ledger, sagas, risk, ai)
- Flyway schema migrations per service
- Optimistic locking (version columns)
- Append-only ledger design

---

## ☁️ Cloud & DevOps

- Kubernetes (AWS EKS for cloud, k3s for on-prem)
- Helm 3 (single chart for all 10 services, 4 environment value files)
- Argo CD (GitOps — Git is the desired state)
- GitHub Actions (CI/CD: build → test → Docker push → Helm deploy)
- Docker Compose (local full-stack development)
- External Secrets Operator (ESO) — secret rotation without pod restart
- Horizontal Pod Autoscaler (HPA) on write-path services
- PodDisruptionBudgets (PDB) on all services for zero-downtime upgrades

---

## 📊 Observability

- Prometheus + Alertmanager (kube-prometheus-stack)
- Grafana (two instances: Prometheus metrics + ClickHouse analytics)
- 3 pre-built ClickHouse dashboards (Payment Ops, Fraud Intel, SLA & Latency)
- PrometheusRules: saga, Kafka, ledger, notification, data pipeline, reconciliation alerts
- Distributed tracing (W3C traceparent header)
- Structured JSON logging (masked PII)
- Slack + email alerting via Alertmanager

---

## 📈 Data Engineering

- Kafka Streams real-time processing
- ClickHouse batch ingestion (5-second flush cycle)
- Materialized views for pre-aggregated analytics
- Spring Batch reconciliation pipeline
- Array(String) column type for multi-value analytics (rule flags)
- TTL-based data retention policies per table

---

## 🎯 Summary

Designed and built a production-grade fintech platform combining:
- **Distributed systems correctness** (Saga, Outbox, idempotency, double-entry ledger)
- **AI augmentation** (RAG fraud copilot, error explanation, KYC OCR)
- **Full-stack cloud-native** (Kubernetes, Helm, GitOps, ESO, HPA)
- **Real-time analytics** (Kafka → ClickHouse → Grafana)
- **Multi-channel notifications** (WebSocket, Email, SMS, Slack)

All while guaranteeing no double-spend, no lost events, and no silent failures.
