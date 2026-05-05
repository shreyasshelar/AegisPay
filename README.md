<div align="center">

<!-- Banner -->
<img src="https://capsule-render.vercel.app/api?type=waving&color=0:3b82f6,100:1d4ed8&height=200&section=header&text=AegisPay&fontSize=72&fontColor=ffffff&fontAlignY=38&desc=Production-grade%20Event-Driven%20Fintech%20Platform&descAlignY=58&descSize=18&animation=fadeIn" width="100%"/>

<!-- Badges row 1 -->
<p>
  <img src="https://img.shields.io/badge/Next.js-15-black?style=for-the-badge&logo=next.js&logoColor=white"/>
  <img src="https://img.shields.io/badge/SwiftUI-iOS%2017+-blue?style=for-the-badge&logo=swift&logoColor=white"/>
  <img src="https://img.shields.io/badge/Jetpack_Compose-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Apache_Kafka-3.7-231F20?style=for-the-badge&logo=apachekafka&logoColor=white"/>
</p>

<!-- Badges row 2 -->
<p>
  <img src="https://img.shields.io/badge/PostgreSQL-16_+_pgvector-4169E1?style=for-the-badge&logo=postgresql&logoColor=white"/>
  <img src="https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white"/>
  <img src="https://img.shields.io/badge/ClickHouse-OLAP_Analytics-FFCC01?style=for-the-badge&logo=clickhouse&logoColor=black"/>
  <img src="https://img.shields.io/badge/Apache_Superset-Dashboard-20A6C9?style=for-the-badge&logo=apache&logoColor=white"/>
  <img src="https://img.shields.io/badge/Stripe-Payments-635BFF?style=for-the-badge&logo=stripe&logoColor=white"/>
</p>

<!-- Badges row 3 -->
<p>
  <img src="https://img.shields.io/badge/Claude_AI-Anthropic-D97706?style=for-the-badge&logo=anthropic&logoColor=white"/>
  <img src="https://img.shields.io/badge/Kubernetes-Helm_+_ArgoCD-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring_Batch-Reconciliation-6DB33F?style=for-the-badge&logo=spring&logoColor=white"/>
  <img src="https://img.shields.io/badge/Kafka_Streams-Real--time_Analytics-231F20?style=for-the-badge&logo=apachekafka&logoColor=white"/>
  <img src="https://img.shields.io/badge/License-MIT-22c55e?style=for-the-badge"/>
</p>

<br/>

> **AegisPay** is a full-stack, event-driven fintech platform built to production standards —  
> multi-platform frontends (Web · iOS · Android), **10 Java microservices**, AI-augmented fraud detection,  
> real-time transactions, immutable ledger accounting, **Stripe-native payments**, and a complete  
> **data engineering layer** (ClickHouse + Kafka Streams + Spring Batch reconciliation + Superset dashboards) — all orchestrated via Kafka Sagas.

<br/>

[![CI](https://img.shields.io/github/actions/workflow/status/shreyasshelar/AegisPay/ci-java.yml?branch=main&label=CI%20Java&style=flat-square&logo=githubactions)](https://github.com/shreyasshelar/AegisPay/actions)
[![CI Web](https://img.shields.io/github/actions/workflow/status/shreyasshelar/AegisPay/ci-web.yml?branch=main&label=CI%20Web&style=flat-square&logo=githubactions)](https://github.com/shreyasshelar/AegisPay/actions)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](https://github.com/shreyasshelar/AegisPay/pulls)
[![Stars](https://img.shields.io/github/stars/shreyasshelar/AegisPay?style=flat-square&color=fbbf24)](https://github.com/shreyasshelar/AegisPay/stargazers)

</div>

---

## 📋 Table of Contents

- [✨ What Makes This Different](#-what-makes-this-different)
- [🏗️ Architecture](#️-architecture)
- [📱 Frontend Platforms](#-frontend-platforms)
- [⚙️ Backend Microservices](#️-backend-microservices)
- [🤖 AI Components](#-ai-components)
- [📊 Data Engineering Layer](#-data-engineering-layer)
- [🔐 Security Model](#-security-model)
- [📡 Event Topology](#-event-topology)
- [🗄️ Data Architecture](#️-data-architecture)
- [🚀 Getting Started](#-getting-started)
- [🌿 Branch Guide](#-branch-guide)
- [📦 Monorepo Structure](#-monorepo-structure)
- [🎯 Feature Phases](#-feature-phases)
- [🔭 Roadmap](#-roadmap)

---

## ✨ What Makes This Different

<table>
<tr>
<td width="50%">

### 🎯 Production Patterns, Not Tutorials

Every design decision mirrors what top-tier fintech engineering teams run in production:

- **Saga orchestration** — distributed transactions that never get stuck
- **Transactional Outbox** — guaranteed exactly-once Kafka delivery
- **Immutable ledger** — append-only `ledger_entries` that can always recompute balance
- **Idempotency at every layer** — Redis SETNX + DB unique constraints + Saga step guards
- **Zero-trust networking** — every inter-service HTTP call carries a forwarded JWT

</td>
<td width="50%">

### 🤖 Real AI, Not Mock Stubs

Three distinct AI integration patterns:

- **RAG (Retrieval-Augmented Generation)** — pgvector + Claude for fraud explanation
- **Agentic AI** — multi-step incident triage agent with tool use (logs, metrics, deployments)
- **Multimodal OCR** — vision model extracts and validates KYC documents
- **Error Resolution** — LLM translates cryptic bank codes into plain English + CTA

</td>
</tr>
<tr>
<td width="50%">

### 📱 Three Native Frontends

Not a PWA — three real native apps, feature-parity across all:

- **Web** — Next.js 15 App Router, TanStack Query v5, Zustand v5
- **iOS** — SwiftUI with `@MainActor`, AppAuth PKCE, native APNs, Face ID / Touch ID
- **Android** — Jetpack Compose + Hilt, FCM, Material 3, BiometricPrompt

</td>
<td width="50%">

### 📊 Full Observability Stack

- **W3C `traceparent`** propagated through every HTTP call and Kafka message header
- **Structured MDC logging** with automatic PAN / Aadhaar / CVV masking
- **Micrometer metrics** feeding Prometheus → Grafana dashboards
- **Alert rules** on saga timeouts, DLQ depth > 0, balance inconsistency

</td>
</tr>
<tr>
<td width="50%">

### 💳 Real Stripe Integration

Not a mock gateway — live Stripe PaymentIntents:

- **PaymentIntent confirm** — server-side creation + 3DS async webhook flow
- **Stripe Webhooks** — signature-verified `payment_intent.succeeded` / `.payment_failed`
- **Zero-decimal currency** — JPY, KRW, etc. handled correctly in both directions
- **Daily reconciliation** — Spring Batch compares every Stripe settlement to the immutable ledger

</td>
<td width="50%">

### 🔢 Production Data Engineering

Three real fintech analytics problems solved:

- **Settlement Reconciliation** — daily Spring Batch job catches `MISSING_IN_STRIPE`, `MISSING_IN_LEDGER`, and `AMOUNT_MISMATCH` breaks vs Stripe's Balance Transactions API
- **Fraud Velocity Streaming** — Kafka Streams tumbling-window aggregations detect card-testing rings in real time
- **Superset Dashboards** — ClickHouse MergeTree + Materialized Views power instant OLAP over billions of payment events

</td>
</tr>
</table>

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                                     │
│   ┌──────────────┐    ┌──────────────┐    ┌──────────────────────────┐  │
│   │  Next.js 15  │    │  SwiftUI iOS │    │  Jetpack Compose Android │  │
│   │  App Router  │    │  AppAuth     │    │  Hilt + Compose          │  │
│   └──────┬───────┘    └──────┬───────┘    └───────────┬──────────────┘  │
└──────────┼──────────────────┼────────────────────────┼──────────────────┘
           │  HTTPS + JWT     │                        │
┌──────────▼──────────────────▼────────────────────────▼──────────────────┐
│                    API GATEWAY  (Spring Cloud Gateway)  :8080            │
│         OAuth2 · Rate Limiting (Redis) · JWT Relay · Tracing             │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ REST (JWT forwarded)
        ┌──────────────────────┼──────────────────────────┐
        │                      │                          │
┌───────▼──────┐    ┌──────────▼──────┐    ┌─────────────▼─────────┐
│ user-service │    │transaction-svc  │    │  risk-engine          │
│  :8081       │    │  :8082          │    │  :8085                │
│ KYC · PKCE   │    │ State Machine   │    │  Rules + RAG + AI     │
│ Multi-IdP    │    │ CQRS + Outbox   │    │                       │
└───────┬──────┘    └──────────┬──────┘    └─────────────┬─────────┘
        │                      │                          │
        └──────────────────────┼──────────────────────────┘
                               │
                   ┌───────────▼───────────┐
                   │     APACHE KAFKA      │
                   │   18 topics · KRaft   │
                   │   localhost:9094      │
                   └───────────┬───────────┘
                               │
        ┌──────────────────────┼──────────────────────────┐
        │                      │                          │
┌───────▼──────┐    ┌──────────▼──────┐    ┌─────────────▼─────────┐
│ledger-service│    │payment-orchestr.│    │notification-service   │
│  :8083       │    │  :8084          │    │  :8086 (+ WS)         │
│Immutable     │    │Saga + Stripe    │    │WebSocket · Email · SMS │
│Append-only   │    │PaymentIntents   │    │                       │
└──────────────┘    └─────────────────┘    └───────────────────────┘
         │                     │
         │         ┌───────────▼──────────┐
         │         │    ai-platform       │
         │         │  :8088               │
         │         │  RAG · Agents · OCR  │
         │         │  pgvector · Claude   │
         │         └──────────────────────┘
         │
         │  ◄──────── writes reconciliation breaks
         │
┌────────▼─────────────────────────────────────────────────────────┐
│                  DATA ENGINEERING LAYER                           │
│                                                                   │
│  ┌─────────────────────┐    ┌─────────────────────────────────┐  │
│  │  data-pipeline :8089 │    │  reconciliation-service :8087   │  │
│  │  Kafka Streams       │    │  Spring Batch (daily 06:00 UTC)  │  │
│  │  - TransactionMetrics│    │  Ledger COMMIT vs Stripe API    │  │
│  │  - RiskAnalytics     │    │  MISSING_IN_STRIPE / LEDGER     │  │
│  │  - Velocity windows  │    │  AMOUNT_MISMATCH detection      │  │
│  └─────────┬───────────┘    └──────────────┬──────────────────┘  │
│            │  batch flush (5s)              │ batchUpdate         │
│            └────────────────┬──────────────┘                     │
│                             ▼                                     │
│              ┌──────────────────────────────┐                    │
│              │   ClickHouse  :8123           │                    │
│              │   aegispay_analytics DB       │                    │
│              │   transaction_facts           │                    │
│              │   risk_assessments            │                    │
│              │   saga_latencies              │                    │
│              │   reconciliation_breaks       │                    │
│              │   + 3 Materialized Views      │                    │
│              └──────────────┬───────────────┘                    │
│                             │ SQL (clickhouse-connect)           │
│              ┌──────────────▼───────────────┐                    │
│              │   Apache Superset  :8088      │                    │
│              │   Finance dashboards          │                    │
│              │   Reconciliation reports      │                    │
│              │   Fraud velocity charts       │                    │
│              └──────────────────────────────┘                    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 📱 Frontend Platforms

### 🌐 Web — Next.js 15

<details>
<summary><b>Tech stack & key patterns</b></summary>

| Concern | Choice |
|---|---|
| Framework | Next.js 15 App Router with Server Components |
| State | Zustand v5 (`create`) for UI state |
| Data fetching | TanStack Query v5 (`useQuery`, `useMutation`, `useInfiniteQuery`) |
| Auth | NextAuth.js + Keycloak PKCE |
| Design system | Custom `@aegispay/design-system` (Tailwind 3 tokens) |
| Realtime | STOMP over WebSocket for live transaction status |
| Notifications | Zustand badge counter + WebSocket push |

**Key screens:**
- Dashboard with live balance + animated transaction feed
- Multi-step Send Money wizard with STOMP live status + AI error resolution
- KYC document upload — drag-drop / camera capture, OCR quality bars, extracted data review
- Back-office — risk case queue + AI fraud explanation panel + incident triage

</details>

### 🍎 iOS — SwiftUI

<details>
<summary><b>Tech stack & key patterns</b></summary>

| Concern | Choice |
|---|---|
| UI | SwiftUI with `@MainActor`, `@StateObject`, `@EnvironmentObject` |
| Auth | AppAuth-iOS (PKCE) → Keycloak + `UNUserNotificationCenter` APNs |
| Networking | `URLSession` with generic `ApiClient<T: Decodable>` + `ApiResponse<T>` unwrapping |
| Security | Face ID / Touch ID (`BiometricAuthService`) + SPKI certificate pinning |
| Realtime | STOMP WebSocket client + 4 s polling fallback |
| Camera | `UIViewControllerRepresentable` wrapping `UIImagePickerController` |
| Accessibility | `AccessibilityHelper` extension — VoiceOver labels on all interactive elements |

**Key screens:**
- MainTabView with dynamic back-office tab (role-gated, BACK_OFFICE / ADMIN)
- 4-step Send Money with `AegisStatusTimeline` + `HapticFeedback.success()`
- KYC profile — camera / gallery picker, `QualityBarRow`, tamper detection, extracted data
- `BiometricLockView` overlay — app locks on backgrounding, unlocks with biometric

</details>

### 🤖 Android — Jetpack Compose

<details>
<summary><b>Tech stack & key patterns</b></summary>

| Concern | Choice |
|---|---|
| UI | Jetpack Compose + Material 3 + Hilt |
| Auth | AppAuth-Android (PKCE) + `EncryptedSharedPreferences` |
| Networking | Retrofit 2 + Moshi + OkHttp envelope-unwrap interceptor |
| Security | `BiometricPrompt` (BIOMETRIC_STRONG) + OkHttp `CertificatePinner` |
| Performance | Android Baseline Profiles (`BaselineProfileGenerator`) |
| Push | Firebase Cloud Messaging + `@AndroidEntryPoint` service |
| Camera | `ActivityResultContracts.TakePicture` + `FileProvider` |

**Key screens:**
- Dashboard with `BadgedBox` notification badge + role-gated "Back Office" card
- Send Money `AnimatedContent` wizard + `VibrationEffect` on COMPLETED
- KYC `LinearProgressIndicator` quality bars + extracted data cards
- `BiometricLockOverlay` composable — biometric gate on every app resume

</details>

---

## ⚙️ Backend Microservices

```
services/
├── api-gateway/              :8080  Spring Cloud Gateway — auth, rate-limit, trace
├── user-service/             :8081  KYC state machine, multi-IdP federation
├── transaction-service/      :8082  Payment state machine, CQRS, WebSocket status
├── ledger-service/           :8083  Immutable append-only ledger, balance reservation
├── payment-orchestrator/     :8084  Saga + Stripe PaymentIntents + webhooks
├── risk-engine/              :8085  Rules engine + RAG fraud copilot
├── notification-service/     :8086  WebSocket registry, email/SMS adapters
├── reconciliation-service/   :8087  Spring Batch — daily Stripe vs ledger reconciliation
├── ai-platform/              :8088  RAG pipeline, agents, OCR+KYC
└── data-pipeline/            :8089  Kafka Streams — real-time fraud analytics → ClickHouse
```

### Saga Transaction Flow

```
transaction-service          payment-orchestrator              external
      │                             │
      │  transaction.initiated ───► │
      │                             │──── balance.reserve.requested ──► ledger-service
      │                             │◄─── balance.reserved ────────────
      │                             │──── risk.assessment.requested ──► risk-engine
      │                             │◄─── risk.assessed (APPROVED) ───
      │                             │──── [HTTP] payment gateway
      │                             │◄─── payment.processed ──────────
      │                             │──── balance.commit.requested ───► ledger-service
      │                             │◄─── balance.committed ──────────
      │◄── transaction.completed ───│
```

Each step has a **compensating transaction** — a failure at step N triggers rollback of steps N-1 → 1 in reverse order.

---

## 🤖 AI Components

<table>
<tr>
<th>Component</th>
<th>Pattern</th>
<th>Input → Output</th>
</tr>
<tr>
<td><b>Fraud Copilot</b></td>
<td>RAG + Claude</td>
<td>Risk score + rule flags → pgvector similarity search over historical fraud KB → LLM explanation</td>
</tr>
<tr>
<td><b>Error Resolution Agent</b></td>
<td>RAG + Claude</td>
<td>Bank error code → incident log KB search → plain-English fix + retry CTA</td>
</tr>
<tr>
<td><b>Incident Triage Agent</b></td>
<td>Agentic AI (tool use)</td>
<td>Service name + symptoms → reads logs → queries metrics → checks deployments → root cause report</td>
</tr>
<tr>
<td><b>OCR + KYC AI</b></td>
<td>Multimodal LLM</td>
<td>Document image → name, DOB, ID number extraction + tampering detection + quality score</td>
</tr>
</table>

**AI audit trail**: every LLM call is logged to `ai_audit_log` with masked inputs, model version, and latency — required for RBI / DPDP regulatory compliance.

---

## 📊 Data Engineering Layer

Phase 11 adds a complete data platform solving three real fintech problems that every payments company eventually hits.

### Problem 1 — "Did Stripe actually settle what our ledger says?"

**`reconciliation-service`** runs a Spring Batch job daily at 06:00 UTC:

```
AegisPay Ledger (PostgreSQL)          Stripe Balance Transactions API
 COMMIT entries for yesterday    ←→   auto-paginated BalanceTransaction.list()
         │                                        │
         └──────────── match by PaymentIntent ID ─┘
                                │
                     ┌──────────▼──────────────┐
                     │  Break Detection         │
                     │  MISSING_IN_STRIPE       │ ← ledger COMMIT, no Stripe PI
                     │  MISSING_IN_LEDGER       │ ← Stripe settled, no ledger entry
                     │  AMOUNT_MISMATCH         │ ← diff > 1 minor unit tolerance
                     └──────────┬──────────────┘
                                │ batchUpdate
                     ┌──────────▼──────────────┐
                     │  ClickHouse              │
                     │  reconciliation_breaks   │
                     └─────────────────────────┘
```

**REST API:**

| Endpoint | Description |
|---|---|
| `GET /api/v1/reconciliation/reports/{date}` | All breaks for a date (paginated, filterable by breakType / breakStatus) |
| `GET /api/v1/reconciliation/summary/{date}` | Aggregated stats — break counts by type, total break amount |
| `POST /api/v1/reconciliation/run?date=2024-01-15` | Trigger an ad-hoc reconciliation run |
| `PATCH /api/v1/reconciliation/breaks/{id}/status` | Mark a break CLOSED / IN_REVIEW / ESCALATED |

---

### Problem 2 — "Are we being card-tested right now?"

**`data-pipeline`** runs Kafka Streams topologies consuming every transaction and risk event:

- `TransactionMetricsStream` — 1-minute tumbling windows over `transaction.completed/failed/rolled-back`
- `RiskAnalyticsStream` — tracks rule flags from `risk.assessed`, surfaces REJECTED velocity spikes
- `ClickHouseSink` — buffers writes in `ConcurrentLinkedQueue`, batch-flushes to ClickHouse every 5 seconds (no per-event round trips)

**Health check:**
```bash
curl http://localhost:8089/api/v1/pipeline/status
# → { "status": "HEALTHY", "kafkaStreamsState": "RUNNING", "clickhouseConnected": true, "totalFlushedRecords": 14832 }
```

---

### Problem 3 — "Show me yesterday's P&L reconciliation summary before the morning standup"

**ClickHouse schema** (`infra/clickhouse/init.sql`):

| Table | Engine | Retention | Purpose |
|---|---|---|---|
| `transaction_facts` | MergeTree | 2 years | Every completed payment fact |
| `risk_assessments` | MergeTree | 1 year | Fraud scores + rule flags per transaction |
| `saga_latencies` | MergeTree | 1 year | End-to-end saga duration SLA tracking |
| `reconciliation_breaks` | MergeTree | 3 years | All Stripe vs ledger discrepancies |
| `mv_hourly_transaction_summary` | Materialized View | — | Pre-aggregated hourly payment totals |
| `mv_hourly_risk_summary` | Materialized View | — | Pre-aggregated hourly risk decision counts |
| `mv_daily_reconciliation_summary` | Materialized View | — | Pre-aggregated daily break amounts |

**Superset** at `http://localhost:8088` (admin / admin) is pre-wired to ClickHouse via `clickhouse-connect`. Connect the ClickHouse datasource once on first login:
1. **Databases → + Database → ClickHouse Connect**
2. SQLALCHEMY URI: `clickhouse+native://default:@clickhouse:9000/aegispay_analytics`
3. Save → **Datasets** → import from `aegispay_analytics` tables above → build charts

---

## 🔐 Security Model

```
┌─────────────────────────────────────────────────┐
│               IDENTITY PROVIDERS                │
│   Keycloak ·  Azure Entra ID ·  Okta           │
└──────────────────────┬──────────────────────────┘
                       │ PKCE / OIDC
┌──────────────────────▼──────────────────────────┐
│              API GATEWAY  :8080                 │
│  • JWT validation (multi-JWKS)                  │
│  • Rate limiting — Redis token bucket           │
│    per-userId AND per-IP (dual-key)             │
│  • X-Correlation-ID injection                   │
│  • W3C traceparent propagation                  │
└──────────────────────┬──────────────────────────┘
                       │ JWT forwarded
┌──────────────────────▼──────────────────────────┐
│              SERVICES (zero-trust)              │
│  • Re-validate JWT on every call                │
│  • Role-based: CUSTOMER · MERCHANT_OPS ·        │
│    BACK_OFFICE · ADMIN · PARTNER                │
│  • Sensitive field masking in all logs          │
│    (PAN · Aadhaar · CVV · Phone)               │
│  • Idempotency keys — Redis SETNX + DB unique  │
└─────────────────────────────────────────────────┘
```

**Mobile security:**
- iOS — Keychain token storage (AppAuth) + Face ID / Touch ID app lock + SPKI certificate pinning
- Android — `EncryptedSharedPreferences` (AES256) + `BiometricPrompt` BIOMETRIC_STRONG + OkHttp `CertificatePinner`
- Both — `ApiResponse<T>` envelope unwrapping at the client layer; no raw JSON exposure

---

## 📡 Event Topology

<details>
<summary><b>Full Kafka topic registry (18 topics + DLQs)</b></summary>

| Topic | Producer | Consumer(s) |
|---|---|---|
| `transaction.initiated` | transaction-service | payment-orchestrator |
| `transaction.completed` | payment-orchestrator | transaction-service · notification-service |
| `transaction.failed` | payment-orchestrator | transaction-service · notification-service |
| `transaction.rolled-back` | payment-orchestrator | transaction-service · ledger-service · notification-service |
| `balance.reserve.requested` | payment-orchestrator | ledger-service |
| `balance.reserved` | ledger-service | payment-orchestrator |
| `balance.reserve.failed` | ledger-service | payment-orchestrator |
| `balance.commit.requested` | payment-orchestrator | ledger-service |
| `balance.committed` | ledger-service | payment-orchestrator |
| `balance.rollback.requested` | payment-orchestrator | ledger-service |
| `balance.rolled-back` | ledger-service | payment-orchestrator |
| `risk.assessment.requested` | payment-orchestrator | risk-engine |
| `risk.assessed` | risk-engine | payment-orchestrator |
| `payment.process.requested` | payment-orchestrator | payment-orchestrator |
| `payment.processed` | payment-orchestrator | payment-orchestrator |
| `user.registered` | user-service | notification-service |
| `kyc.status.changed` | user-service | notification-service · risk-engine |
| `notification.send.requested` | any service | notification-service |

Every topic has a `.DLQ` counterpart. Retention: financial topics → 30 days · notification topics → 7 days.

**Data Engineering consumers (read-only, no side-effects):**

| Topic | Consumer | Purpose |
|---|---|---|
| `transaction.completed` | data-pipeline | Metrics stream → ClickHouse `transaction_facts` |
| `transaction.failed` | data-pipeline | Failure rate aggregation |
| `transaction.rolled-back` | data-pipeline | Compensation rate tracking |
| `risk.assessed` | data-pipeline | Fraud velocity analytics → ClickHouse `risk_assessments` |

</details>

---

## 🗄️ Data Architecture

| Store | Port | Usage |
|---|---|---|
| **PostgreSQL 16 + pgvector** | 5432 | Primary write models per service + AI vector embeddings |
| **MongoDB 7** | 27017 | CQRS read models — `transaction_views`, notification history |
| **Redis 7** | 6379 | Idempotency keys, rate-limit token buckets, session cache |
| **Kafka 3.7 (KRaft)** | 9094 | All async inter-service messaging (18 topics + DLQs) |
| **ClickHouse 24** | 8123 | OLAP analytics — payment facts, fraud scores, reconciliation breaks |
| **Apache Superset 3.1** | 8088 | Business intelligence dashboards over ClickHouse |

Each microservice owns its own PostgreSQL **database** (not just schema) for full isolation:

| Service | Database |
|---|---|
| user-service | `aegispay_users` |
| transaction-service | `aegispay_transactions` |
| ledger-service | `aegispay_ledger` |
| payment-orchestrator | `aegispay_sagas` |
| risk-engine | `aegispay_risk` |
| ai-platform | `aegispay_ai` |

### Ledger Guarantee

```sql
-- ledger_entries is APPEND-ONLY — no UPDATE, no DELETE, ever.
-- Balance can always be recomputed:
SELECT SUM(CASE WHEN entry_type IN ('CREDIT','RELEASE') THEN amount ELSE -amount END)
FROM ledger_entries
WHERE account_id = $1;
```

An `@EntityListeners` guard throws `IllegalStateException` on any `@PreUpdate` or `@PreRemove` — enforced at the ORM layer.

---

## 🚀 Getting Started

### Prerequisites

| Tool | Version | Required for |
|---|---|---|
| Docker Desktop | 4.x+ | All local infra (Postgres, Redis, Kafka, Keycloak) |
| Java (Temurin) | 21 | Backend microservices |
| Maven | 3.9+ | Backend build (`./mvnw` wrapper included) |
| Node.js | 20+ | Web app + shared TypeScript packages |
| Xcode | 15+ | iOS app (macOS only) |
| Android Studio | Hedgehog+ | Android app |

---

### Step 1 — Clone the repo

```bash
git clone https://github.com/shreyasshelar/AegisPay.git
cd AegisPay
```

---

### Step 2 — Start local infrastructure

All infrastructure (Postgres, Redis, MongoDB, Kafka, Keycloak) runs via Docker Compose.

```bash
docker compose up -d
```

This starts:

| Service | URL / Port | Credentials |
|---|---|---|
| PostgreSQL 16 | `localhost:5432` | user: `aegispay` / pass: `aegispay_dev` |
| Redis 7 | `localhost:6379` | pass: `aegispay_dev` |
| MongoDB 7 | `localhost:27017` | user: `aegispay` / pass: `aegispay_dev` |
| Kafka (KRaft) | `localhost:9094` | no auth (local only) |
| Kafka UI | http://localhost:8090 | no auth |
| Keycloak 24 | http://localhost:8180 | admin: `admin` / `admin` |
| **ClickHouse** | http://localhost:8123 | user: `default` / no password |
| **Apache Superset** | http://localhost:8088 | admin: `admin` / `admin` |

Wait ~60 seconds for Keycloak to finish importing the realm. Superset takes ~90 seconds on first boot (runs DB migrations on startup).

**Check everything is healthy:**
```bash
docker compose ps
# All services should show "healthy" or "running"
```

**Six per-service databases are created automatically** by `infra/local/postgres/init/01_create_databases.sql` on first startup:
`aegispay_users`, `aegispay_transactions`, `aegispay_ledger`, `aegispay_sagas`, `aegispay_risk`, `aegispay_ai`

**ClickHouse** initialises the `aegispay_analytics` database and all 4 tables + 3 Materialized Views from `infra/clickhouse/init.sql` on first startup — no manual step needed.

---

### Step 3 — Configure the web app

```bash
cp apps/web/.env.local.example apps/web/.env.local
```

Then edit `apps/web/.env.local`:

```env
NEXTAUTH_URL=http://localhost:3000
NEXTAUTH_SECRET=<generate with: openssl rand -base64 32>

# Keycloak (started by docker compose above)
KEYCLOAK_ID=aegispay-web
KEYCLOAK_SECRET=                          # leave blank — public client in local realm
KEYCLOAK_ISSUER=http://localhost:8180/realms/aegispay

# API Gateway
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080

# WebSocket (notification-service)
NEXT_PUBLIC_WS_BASE_URL=ws://localhost:8086
```

---

### Step 4 — Build shared Java libraries

Shared libs must be installed into the local Maven repository before any service can compile.

```bash
./mvnw clean install \
  -pl libs/common-domain,libs/common-security,libs/common-kafka,libs/common-observability \
  -DskipTests
```

---

### Step 5 — Run backend services

Each service reads config from environment variables with sensible local defaults (see `application.yml` in each service). Run each in a separate terminal, or use your IDE's run configurations.

**Option A — Individual services (separate terminals):**

```bash
# Terminal 1 — API Gateway (all external traffic enters here)
./mvnw -pl services/api-gateway spring-boot:run

# Terminal 2 — User Service
./mvnw -pl services/user-service spring-boot:run

# Terminal 3 — Transaction Service
./mvnw -pl services/transaction-service spring-boot:run

# Terminal 4 — Ledger Service
./mvnw -pl services/ledger-service spring-boot:run

# Terminal 5 — Payment Orchestrator (set Stripe keys for real payments)
STRIPE_SECRET_KEY=sk_test_... \
STRIPE_WEBHOOK_SECRET=whsec_... \
./mvnw -pl services/payment-orchestrator spring-boot:run

# Terminal 6 — Risk Engine
./mvnw -pl services/risk-engine spring-boot:run

# Terminal 7 — Notification Service
SMTP_PASSWORD=your-gmail-app-password \
./mvnw -pl services/notification-service spring-boot:run

# Terminal 8 — AI Platform (requires ANTHROPIC_API_KEY)
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw -pl services/ai-platform spring-boot:run

# Terminal 9 — Reconciliation Service (Spring Batch + ClickHouse)
STRIPE_SECRET_KEY=sk_test_... \
CLICKHOUSE_URL=jdbc:clickhouse://localhost:8123/aegispay_analytics \
./mvnw -pl services/reconciliation-service spring-boot:run

# Terminal 10 — Data Pipeline (Kafka Streams → ClickHouse)
CLICKHOUSE_URL=jdbc:clickhouse://localhost:8123/aegispay_analytics \
./mvnw -pl services/data-pipeline spring-boot:run
```

**Option B — All at once with Maven (background):**

```bash
./mvnw -pl services/api-gateway,services/user-service,services/transaction-service, \
            services/ledger-service,services/payment-orchestrator,services/risk-engine, \
            services/notification-service,services/data-pipeline,services/reconciliation-service \
       spring-boot:run -Dspring-boot.run.fork=true
```

**Service ports at a glance:**

| Service | Port | Health endpoint | Notes |
|---|---|---|---|
| api-gateway | 8080 | http://localhost:8080/actuator/health | Entry point for all clients |
| user-service | 8081 | http://localhost:8081/actuator/health | |
| transaction-service | 8082 | http://localhost:8082/actuator/health | WS at :8082/ws/transactions/{id}/status |
| ledger-service | 8083 | http://localhost:8083/actuator/health | |
| payment-orchestrator | 8084 | http://localhost:8084/actuator/health | Stripe webhook at :8084/internal/webhooks/stripe |
| risk-engine | 8085 | http://localhost:8085/actuator/health | |
| notification-service | 8086 | http://localhost:8086/actuator/health | WS at :8086/ws/notifications |
| reconciliation-service | 8087 | http://localhost:8087/actuator/health | Batch runs daily at 06:00 UTC |
| ai-platform | 8088 | http://localhost:8088/actuator/health | Needs ANTHROPIC_API_KEY |
| data-pipeline | 8089 | http://localhost:8089/api/v1/pipeline/status | Kafka Streams health |

**Stripe local webhook testing** (optional — only needed for 3DS / async payment flows):
```bash
# Install Stripe CLI, then forward events to your local payment-orchestrator:
stripe listen --forward-to http://localhost:8084/internal/webhooks/stripe
# Stripe CLI will print the webhook signing secret — set as STRIPE_WEBHOOK_SECRET above
```

---

### Step 6 — Run the web app

```bash
npm install          # install all JS/TS workspace dependencies
npm run dev          # starts Next.js at http://localhost:3000
```

---

### Step 7 — Test with pre-seeded accounts

The Keycloak realm includes two ready-to-use accounts:

| Role | Email | Password | Notes |
|---|---|---|---|
| `CUSTOMER` | `customer@aegispay.local` | `Test@1234` | End-user flows, KYC, send money |
| `ADMIN` | `admin@aegispay.local` | `Admin@1234` | Back-office tab, risk cases, incident triage |

**Quick smoke test — register the customer user:**
```bash
# Get a token (replace with the token from Keycloak login)
TOKEN=$(curl -s -X POST http://localhost:8180/realms/aegispay/protocol/openid-connect/token \
  -d "grant_type=password&client_id=aegispay-web&username=customer@aegispay.local&password=Test@1234" \
  | jq -r .access_token)

# Register the user (idempotent — safe to call multiple times)
curl -s -X POST http://localhost:8080/api/v1/users/register \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@aegispay.local","firstName":"Test","lastName":"Customer","phone":"+919876543210","tenantId":"default"}' \
  | jq .
```

---

### Step 8 — Run iOS (macOS only)

```bash
# Install dependencies
cd apps/ios
xcodegen generate   # if XcodeGen is used — otherwise open the .xcodeproj directly

open AegisPay.xcodeproj
# Select an iPhone simulator → ⌘R
```

The app reads `AppConfig.swift` for `apiBaseURL` and `keycloakIssuer`. Update the constants to point to `localhost:8080` and `localhost:8180` for local dev.

---

### Step 9 — Run Android

```bash
# Open in Android Studio:
# File → Open → select apps/android/

# Or build from CLI:
cd apps/android
./gradlew assembleDebug
```

Set `API_BASE_URL=http://10.0.2.2:8080` in `apps/android/app/build.gradle.kts` for the Android emulator (10.0.2.2 maps to the host machine's localhost).

---

### Running tests

```bash
# Java unit + integration tests (Testcontainers — requires Docker)
./mvnw test

# Java tests for a single service
./mvnw -pl services/user-service test

# Web linting + type checking
npm run lint
npm run typecheck

# Web unit tests
npm run test
```

---

### Stopping everything

```bash
docker compose down          # stop containers, keep volumes
docker compose down -v       # stop containers AND delete all data (full reset)
```

---

## 🌿 Branch Guide

AegisPay maintains three long-lived branches targeting different deployment environments.

| Branch | Purpose | Infra cost | CD target |
|---|---|---|---|
| `feat/monorepo-restructure` | **Production-grade** — AWS EKS, managed Kafka (MSK), RDS, ElastiCache, Vault Agent Injector, full resource limits, replicas ≥ 2 for all services | High | ArgoCD → prod EKS cluster |
| `feat/cost-optimised-onprem` | **Dev / on-prem** — k3s single-node, Kafka in-cluster, Postgres + Redis in-cluster, OpenRouter API (free tier AI), replicas = 1, reduced resource requests | Low | ArgoCD → `app-onprem.yaml` watches `dev` branch |
| `feat/data-engineering` | Source branch for Phase 11 — merged into both above | — | (merged, no direct CD) |

### What differs between the two runnable branches

<details>
<summary><b>feat/monorepo-restructure (prod)</b></summary>

```yaml
# infra/helm/aegispay/values.yaml defaults
global:
  kafka.brokers: "kafka-headless.aegispay-infra.svc.cluster.local:9092"
  clickhouse.url: "jdbc:clickhouse://clickhouse.aegispay-infra.svc.cluster.local:8123/aegispay_analytics"

# Services run with replicas ≥ 2, CPU/memory limits sized for production traffic
# Secrets from HashiCorp Vault or AWS Secrets Manager via External Secrets Operator
# STRIPE_SECRET_KEY = sk_live_... (live mode)
# ANTHROPIC_API_KEY = production Claude API key
```

**Running on prod branch locally** — use `values-dev.yaml` overrides:
```bash
git checkout feat/monorepo-restructure
docker compose up -d          # starts all infra including ClickHouse + Superset
./mvnw clean install -DskipTests -pl libs/common-domain,libs/common-security,libs/common-kafka,libs/common-observability
# Then run individual services as described in Step 5 above
```

**Vault secrets initialisation** (one-time per environment):
```bash
export DB_PASSWORD="..."
export STRIPE_SECRET_KEY="sk_live_..."
export STRIPE_WEBHOOK_SECRET="whsec_..."
export SMTP_PASSWORD="..."
export SLACK_WEBHOOK_URL="..."
export CLICKHOUSE_PASSWORD="..."    # leave empty for local dev
bash infra/vault/init.sh prod
```

</details>

<details>
<summary><b>feat/cost-optimised-onprem (dev / k3s)</b></summary>

```yaml
# infra/helm/aegispay/values-dev.yaml overrides
global:
  clickhouse.url: "jdbc:clickhouse://localhost:8123/aegispay_analytics"

# All services: replicas: 1, cpu requests: 100m, memory requests: 256Mi
# Secrets from Vault running inside k3s (port-forwarded)
# AI: OPENROUTER_API_KEY (free-tier models via OpenRouter instead of direct Anthropic)
# STRIPE_SECRET_KEY = sk_test_... (test mode only)
```

**Running on on-prem branch locally** — identical docker-compose, just different env vars:
```bash
git checkout feat/cost-optimised-onprem
docker compose up -d          # same stack, same ports
# Set OPENROUTER_API_KEY instead of ANTHROPIC_API_KEY for AI Platform
OPENROUTER_API_KEY=sk-or-... \
./mvnw -pl services/ai-platform spring-boot:run
```

**Vault secrets initialisation** (k3s — uses kubectl port-forward):
```bash
export DB_PASSWORD="..."
export OPENROUTER_API_KEY="sk-or-..."
export KEYCLOAK_ADMIN_PASSWORD="admin"
export GRAFANA_ADMIN_PASSWORD="grafana"
export STRIPE_SECRET_KEY="sk_test_..."    # test mode
export CLICKHOUSE_PASSWORD=""             # empty = no auth in dev
bash infra/vault/init.sh
```

**ArgoCD on-prem deployment**:
```bash
# Apply the ArgoCD application (watches feat/cost-optimised-onprem branch)
kubectl apply -f infra/argocd/app-onprem.yaml
# ArgoCD auto-syncs on every push to the branch
```

</details>

### Deploying Phase 11 services (both branches)

Both branches already have the data engineering layer merged in. After deploying via Helm:

```bash
# Verify reconciliation-service is up
kubectl get pods -n aegispay | grep reconciliation

# Trigger a manual reconciliation run (replaces yesterday's date)
curl -X POST "http://api.aegispay.io/api/v1/reconciliation/run?date=$(date -d yesterday +%F)" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Check data-pipeline Kafka Streams health
curl http://api.aegispay.io/api/v1/pipeline/status

# View reconciliation breaks for today
curl "http://api.aegispay.io/api/v1/reconciliation/summary/$(date -d yesterday +%F)" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .
```

---

## 📦 Monorepo Structure

```
AegisPay/                              ← single GitHub repository
│
├── apps/                              ← Multi-platform frontends
│   ├── web/                           Next.js 15 App Router
│   │   ├── app/(dashboard)/           Customer-facing screens
│   │   ├── app/(back-office)/         BACK_OFFICE / ADMIN only
│   │   ├── components/                Shared UI components
│   │   └── .env.local.example         ← copy to .env.local before running
│   │
│   ├── ios/                           SwiftUI (iOS 17+)
│   │   └── AegisPay/
│   │       ├── Features/              Screen-level feature modules
│   │       ├── Auth/                  BiometricAuthService, TokenStore
│   │       ├── Network/               ApiClient (with envelope unwrap), Services, Endpoints
│   │       └── DesignSystem/          Tokens, Components
│   │
│   └── android/                       Jetpack Compose (API 26+)
│       ├── app/src/main/java/com/aegispay/android/
│       │   ├── ui/                    Screen + ViewModel per feature
│       │   ├── auth/                  BiometricAuthManager
│       │   ├── network/               Retrofit service + envelope-unwrap interceptor
│       │   └── push/                  FCM service + badge state
│       └── macrobenchmark/            Android Baseline Profile generator
│
├── packages/                          ← Shared TypeScript packages
│   ├── api-client/                    Axios client (ApiResponse<T> unwrap) + TanStack Query hooks
│   ├── shared-types/                  Zod schemas (Transaction, User, KYC, Risk…)
│   └── design-system/                 Tailwind tokens + component library
│
├── services/                          ← Java 21 microservices (Spring Boot 3.3)
│   ├── api-gateway/        :8080      Spring Cloud Gateway
│   ├── user-service/       :8081      KYC state machine, multi-IdP
│   ├── transaction-service/:8082      Payment state machine, CQRS, WebSocket
│   ├── ledger-service/     :8083      Immutable append-only ledger
│   ├── payment-orchestrator/:8084     Saga + Stripe PaymentIntents + webhooks
│   ├── risk-engine/        :8085      Rules engine + RAG fraud copilot
│   ├── notification-service/:8086     WebSocket registry, email/SMS adapters
│   ├── reconciliation-service/:8087   Spring Batch — daily Stripe vs ledger
│   ├── ai-platform/        :8088      RAG pipeline, agents, OCR+KYC
│   ├── data-pipeline/      :8089      Kafka Streams → ClickHouse analytics
│   └── e2e-tests/                     Testcontainers end-to-end test suite
│
├── libs/                              ← Shared Java libraries (built first)
│   ├── common-domain/                 Kafka event POJOs, enums, base exceptions, ApiResponse<T>
│   ├── common-security/               JWT filter, RBAC, ActorContext
│   ├── common-kafka/                  Producer template, Outbox scheduler, DLQ
│   └── common-observability/          MDC logging, tracing, field masking
│
├── infra/
│   ├── local/                         Local dev infra config
│   │   ├── postgres/init/             SQL scripts run on first Postgres startup
│   │   └── keycloak/realm-export.json Pre-seeded realm with test users + JWT claims
│   ├── clickhouse/
│   │   └── init.sql                   ClickHouse schema + Materialized Views (auto-loaded)
│   ├── superset/
│   │   └── superset_config.py         Superset config (ClickHouse datasource, Redis cache)
│   ├── helm/aegispay/                 Umbrella Helm chart (all 10 services)
│   │   ├── values.yaml                Base values (includes global.clickhouse)
│   │   ├── values-dev.yaml            On-prem k3s overrides (1 replica, 256Mi requests)
│   │   ├── values-staging.yaml
│   │   └── values-prod.yaml
│   ├── helm/monitoring/               kube-prometheus-stack + Alertmanager routing
│   │   ├── values-dev.yaml            On-prem Prometheus + Grafana config
│   │   └── values-prod.yaml           Prod (30d retention, gp3, Slack + Gmail alerts)
│   └── argocd/                        ArgoCD ApplicationSet (dev / staging / prod)
│       └── app-onprem.yaml            Watches feat/cost-optimised-onprem branch
│
├── docs/adr/                          Architecture Decision Records
│   ├── 001-saga-orchestration.md
│   ├── 002-outbox-pattern.md
│   ├── 003-cqrs-read-models.md
│   └── 004-ai-platform-design.md
│
├── .github/workflows/
│   ├── ci-web.yml                     Next.js lint + build + test
│   ├── ci-ios.yml                     Xcode build + unit tests
│   ├── ci-android.yml                 Gradle build + instrumented tests
│   ├── ci-java.yml                    Maven build matrix (libs first → 8 services in parallel)
│   ├── cd-dev.yml                     Push to main → Docker image → yq patch → ArgoCD sync
│   ├── cd-staging.yml                 On tag → staging deploy
│   ├── cd-prod.yml                    Manual approval gate → prod deploy
│   └── security-scan.yml              OWASP dep-check + Trivy image scan
│
├── docker-compose.yml                 ← Local dev stack (Postgres + Redis + Mongo + Kafka + Keycloak + ClickHouse + Superset)
├── pom.xml                            Maven root (manages all Java modules)
├── package.json                       npm workspaces root
├── turbo.json                         Turborepo pipeline config
└── tsconfig.base.json                 Shared TypeScript config
```

---

## 🎯 Feature Phases

### Frontend (all 3 platforms — Web · iOS · Android)

| Phase | Status | Description |
|---|---|---|
| **F1** | ✅ | Auth — OAuth2 PKCE, splash screen, login flow |
| **F2** | ✅ | Dashboard — live balance card, recent transactions, navigation |
| **F3** | ✅ | Send Money — 4-step wizard, STOMP live status, AI error resolution |
| **F4** | ✅ | KYC — camera/gallery, OCR quality scoring, tamper detection, extracted data review |
| **F5** | ✅ | Push Notifications — APNs (iOS), FCM (Android), WebSocket badge (web) |
| **F6** | ✅ | Back-office — risk case queue, AI fraud explanation, incident triage agent |
| **F7** | ✅ | Hardening — biometric auth (Face ID / BiometricPrompt), certificate pinning, VoiceOver / TalkBack accessibility, Android Baseline Profiles |

### Backend (Java 21 — Spring Boot 3.3 microservices)

| Phase | Status | Description |
|---|---|---|
| **B1** | ✅ | Foundation — shared libs, Maven multi-module skeleton, CI/CD, Helm charts, ArgoCD |
| **B2** | ✅ | API Gateway — OAuth2, Redis rate limiting, JWT relay, W3C tracing |
| **B3** | ✅ | User Service — KYC state machine (5 states), multi-IdP federation, outbox |
| **B4** | ✅ | Transaction Service — payment state machine, CQRS + MongoDB read models, WebSocket |
| **B5** | ✅ | Ledger Service — immutable append-only ledger, optimistic locking, balance reservation |
| **B6** | ✅ | Payment Orchestrator — 5-step Saga + compensation, timeout detection, external gateway |
| **B7** | ✅ | Risk Engine — velocity/geo/amount rules, RAG fraud copilot, blacklist management |
| **B8** | ✅ | Notification Service — WebSocket registry, email/SMS adapters, notification history |
| **B9** | ✅ | AI Platform — RAG pipeline, Fraud Copilot, Error Agent, Incident Triage Agent, OCR+KYC |
| **B10** | ✅ | Integration hardening — Stripe live payments + webhooks, ESO secrets, Alertmanager routing (Slack + Gmail), AI knowledge base seed, expanded e2e Testcontainers suite (11 tests), cost-optimised on-prem k3s stack |
| **B11** | ✅ | **Data Engineering** — Spring Batch settlement reconciliation vs Stripe, Kafka Streams fraud analytics, ClickHouse OLAP schema, Apache Superset dashboards, REST reconciliation API, `feat/data-engineering` branch merged to prod + dev |

---

## 🔭 Roadmap

**Shipped ✅**
- [x] **Biometric auth** — Face ID / Touch ID (iOS `BiometricAuthService`) + BiometricPrompt BIOMETRIC_STRONG (Android)
- [x] **Certificate pinning** — SPKI SHA-256 pinning (iOS `CertificatePinningDelegate`) + OkHttp `CertificatePinner` (Android)
- [x] **Android Baseline Profiles** — cold-start + navigation benchmarks via `BaselineProfileGenerator`
- [x] **Accessibility** — VoiceOver labels (iOS `AccessibilityHelper`) + Compose semantics (Android) + ARIA roles (Web)
- [x] **ApiResponse envelope unwrapping** — consistent across Axios (Web), OkHttp (Android), URLSession (iOS)
- [x] **Stripe live payments** — PaymentIntent confirm + 3DS webhook (`payment_intent.succeeded/.payment_failed`) + zero-decimal currency handling
- [x] **External Secrets Operator** — Vault/AWS Secrets Manager → K8s secrets for Stripe, SMTP, Slack, ClickHouse
- [x] **Alertmanager routing** — critical → Slack + Gmail, warning → Slack; secrets mounted as files
- [x] **AI knowledge base** — 30 fraud cases + 30 bank error codes + 20 incident logs seeded to pgvector on startup
- [x] **Settlement Reconciliation** — Spring Batch daily job, Stripe API pagination, 4 break types, REST API, ClickHouse write
- [x] **Fraud velocity streaming** — Kafka Streams tumbling windows, ClickHouseSink batch flush, pipeline health endpoint
- [x] **ClickHouse analytics schema** — 4 MergeTree tables + 3 Materialized Views, 2-3 year TTL
- [x] **Apache Superset** — docker-compose integrated, pre-wired to ClickHouse, Superset config with Redis cache + SMTP alerts
- [x] **Cost-optimised on-prem stack** — k3s `feat/cost-optimised-onprem` branch (1 replica, 256Mi, OpenRouter AI, no cloud costs)

**Planned 🔜**
- [ ] **Multi-tenancy** — `tenantId` propagation through JWT claims → PostgreSQL row-level security per tenant
- [ ] **k6 load test** — happy-path transaction at 500 RPS, verify no double-spend under concurrency
- [ ] **Superset pre-built dashboards** — export JSON for Finance Summary, Fraud Velocity, Reconciliation Breaks, SLA Tracking
- [ ] **ClickHouse replication** — 2-shard 2-replica ClickHouseKeeper cluster for prod HA
- [ ] **Stripe Radar rules** — custom fraud rules feeding back from risk-engine risk score

---

<div align="center">

**Built with precision. Designed for scale. Powered by AI.**

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:1d4ed8,100:3b82f6&height=100&section=footer" width="100%"/>

<p>
  <a href="https://github.com/shreyasshelar/AegisPay/issues">🐛 Report Bug</a> ·
  <a href="https://github.com/shreyasshelar/AegisPay/pulls">✨ Request Feature</a> ·
  <a href="https://github.com/shreyasshelar/AegisPay/stargazers">⭐ Star this repo</a>
</p>

<sub>Made by <a href="https://github.com/shreyasshelar">@shreyasshelar</a></sub>

</div>
