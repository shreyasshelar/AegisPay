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
  <img src="https://img.shields.io/badge/Claude_AI-Anthropic-D97706?style=for-the-badge&logo=anthropic&logoColor=white"/>
  <img src="https://img.shields.io/badge/Kubernetes-Helm_+_ArgoCD-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white"/>
  <img src="https://img.shields.io/badge/License-MIT-22c55e?style=for-the-badge"/>
</p>

<br/>

> **AegisPay** is a full-stack, event-driven fintech platform built to production standards —  
> multi-platform frontends (Web · iOS · Android), 8 Java microservices, AI-augmented fraud detection,  
> real-time transactions, and immutable ledger accounting — all orchestrated via Kafka Sagas.

<br/>

[![CI](https://img.shields.io/github/actions/workflow/status/shreyasshelar/AegisPay/ci.yml?branch=main&label=CI&style=flat-square&logo=githubactions)](https://github.com/shreyasshelar/AegisPay/actions)
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
- [🔐 Security Model](#-security-model)
- [📡 Event Topology](#-event-topology)
- [🗄️ Data Architecture](#️-data-architecture)
- [🚀 Getting Started](#-getting-started)
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
- **iOS** — SwiftUI with `@MainActor`, AppAuth PKCE, native APNs
- **Android** — Jetpack Compose + Hilt, FCM, Material 3

</td>
<td width="50%">

### 📊 Full Observability Stack

- **W3C `traceparent`** propagated through every HTTP call and Kafka message header
- **Structured MDC logging** with automatic PAN / Aadhaar / CVV masking
- **Micrometer metrics** feeding Prometheus → Grafana dashboards
- **Alert rules** on saga timeouts, DLQ depth > 0, balance inconsistency

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
│                    API GATEWAY  (Spring Cloud Gateway)                   │
│         OAuth2 · Rate Limiting (Redis) · JWT Relay · Tracing             │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ REST (JWT forwarded)
        ┌──────────────────────┼──────────────────────────┐
        │                      │                          │
┌───────▼──────┐    ┌──────────▼──────┐    ┌─────────────▼─────────┐
│ user-service │    │transaction-svc  │    │  risk-engine          │
│ KYC · PKCE   │    │ State Machine   │    │  Rules + RAG + AI     │
│ Multi-IdP    │    │ CQRS + Outbox   │    │                       │
└───────┬──────┘    └──────────┬──────┘    └─────────────┬─────────┘
        │                      │                          │
        └──────────────────────┼──────────────────────────┘
                               │
                   ┌───────────▼───────────┐
                   │     APACHE KAFKA      │
                   │   18 topics · KRaft   │
                   └───────────┬───────────┘
                               │
        ┌──────────────────────┼──────────────────────────┐
        │                      │                          │
┌───────▼──────┐    ┌──────────▼──────┐    ┌─────────────▼─────────┐
│ledger-service│    │payment-orchestr.│    │notification-service   │
│Immutable     │    │Saga Coordinator │    │WebSocket · Email · SMS │
│Append-only   │    │Compensation     │    │                       │
└──────────────┘    └─────────────────┘    └───────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │    ai-platform      │
                    │  RAG · Agents · OCR │
                    │  pgvector · Claude  │
                    └─────────────────────┘
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
| Networking | `URLSession` with generic `ApiClient<T: Decodable>` |
| Realtime | STOMP WebSocket client + 4 s polling fallback |
| Camera | `UIViewControllerRepresentable` wrapping `UIImagePickerController` |
| Layout | Custom `FlowLayout: Layout` for dynamic rule-flag chips |

**Key screens:**
- MainTabView with dynamic back-office tab (role-gated, BACK_OFFICE / ADMIN)
- 4-step Send Money with `AegisStatusTimeline` + `HapticFeedback.success()`
- KYC profile — camera / gallery picker, `QualityBarRow`, tamper detection, extracted data
- Back-office — `RiskCase` with custom `Decodable` for arbitrary `ruleFlags` JSON object

</details>

### 🤖 Android — Jetpack Compose

<details>
<summary><b>Tech stack & key patterns</b></summary>

| Concern | Choice |
|---|---|
| UI | Jetpack Compose + Material 3 + Hilt |
| Auth | AppAuth-Android (PKCE) + `EncryptedSharedPreferences` |
| Networking | Retrofit 2 + Moshi (`KotlinJsonAdapterFactory`) |
| Push | Firebase Cloud Messaging + `@AndroidEntryPoint` service |
| Badge state | `@Singleton NotificationBadgeState` (`StateFlow<Int>`) |
| Camera | `ActivityResultContracts.TakePicture` + `FileProvider` |
| Animations | `AnimatedContent` with slide + fade transitions |

**Key screens:**
- Dashboard with `BadgedBox` notification badge + role-gated "Back Office" card
- Send Money `AnimatedContent` wizard + `VibrationEffect` on COMPLETED
- KYC `LinearProgressIndicator` quality bars + extracted data cards
- Back-office `TabRow` — risk case detail panel + incident triage with monospace report card

</details>

---

## ⚙️ Backend Microservices

```
services/
├── api-gateway/          Spring Cloud Gateway — auth, rate-limit, trace
├── user-service/         KYC state machine, multi-IdP federation
├── transaction-service/  Payment state machine, CQRS, WebSocket status
├── ledger-service/       Immutable append-only ledger, balance reservation
├── payment-orchestrator/ Saga coordinator — 5-step, full compensation
├── risk-engine/          Rules engine + RAG fraud copilot
├── notification-service/ WebSocket registry, email/SMS adapters
└── ai-platform/          RAG pipeline, agents, OCR+KYC
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
<td>

**Fraud Copilot**

</td>
<td>RAG + Claude</td>
<td>Risk score + rule flags → pgvector similarity search over historical fraud KB → LLM explanation</td>
</tr>
<tr>
<td>

**Error Resolution Agent**

</td>
<td>RAG + Claude</td>
<td>Bank error code → incident log KB search → plain-English fix + retry CTA</td>
</tr>
<tr>
<td>

**Incident Triage Agent**

</td>
<td>Agentic AI (tool use)</td>
<td>Service name + symptoms → reads logs → queries metrics → checks deployments → root cause report</td>
</tr>
<tr>
<td>

**OCR + KYC AI**

</td>
<td>Multimodal LLM</td>
<td>Document image → name, DOB, ID number extraction + tampering detection + quality score</td>
</tr>
</table>

**AI audit trail**: every LLM call is logged to `ai_audit_log` with masked inputs, model version, and latency — required for RBI / DPDP regulatory compliance.

---

## 🔐 Security Model

```
┌─────────────────────────────────────────────────┐
│               IDENTITY PROVIDERS                │
│   Keycloak ·  Azure Entra ID ·  Okta           │
└──────────────────────┬──────────────────────────┘
                       │ PKCE / OIDC
┌──────────────────────▼──────────────────────────┐
│              API GATEWAY                        │
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
- iOS — `EncryptedSharedPreferences`-equivalent Keychain via AppAuth token storage
- Android — `EncryptedSharedPreferences` (AES256-SIV keys + AES256-GCM values)
- Both — certificate pinning ready, biometric auth hook points in Phase F7

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

</details>

---

## 🗄️ Data Architecture

| Store | Usage |
|---|---|
| **PostgreSQL 16** | Primary write models — transactions, users, ledger, sagas |
| **PostgreSQL + pgvector** | AI knowledge base — fraud cases, error codes, incident logs (1536-dim embeddings) |
| **MongoDB 7** | CQRS read models — `transaction_views` collection, notification history |
| **Redis 7** | Idempotency keys, rate-limit token buckets, API-gateway session cache |

### Ledger Guarantee

```sql
-- ledger_entries is APPEND-ONLY — no UPDATE, no DELETE, ever.
-- Balance can always be recomputed:
SELECT SUM(CASE WHEN entry_type IN ('CREDIT','RELEASE') THEN amount ELSE -amount END)
FROM ledger_entries
WHERE account_id = $1;
```

An `@EntityListeners` guard throws `IllegalStateException` on any `@PreUpdate` or `@PreRemove` call — enforced at the ORM layer, not just convention.

---

## 🚀 Getting Started

### Prerequisites

```bash
node >= 20    # for web
java 21       # for backend services
xcode 15+     # for iOS
android studio hedgehog+  # for Android
docker        # for local infra
```

### 1 — Clone & install

```bash
git clone https://github.com/shreyasshelar/AegisPay.git
cd AegisPay

# Frontend monorepo
npm install          # installs all workspaces (web + packages)
```

### 2 — Spin up local infra

```bash
docker compose up -d   # PostgreSQL + Redis + MongoDB + Kafka (KRaft)
```

### 3 — Run the web app

```bash
npm run dev --workspace=apps/web
# → http://localhost:3000
```

### 4 — Run iOS

```bash
open apps/ios/AegisPay.xcodeproj
# Select simulator → ⌘R
```

### 5 — Run Android

```bash
# Open apps/android in Android Studio → Run
```

### 6 — Run a backend service (example)

```bash
cd services/transaction-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

---

## 📦 Monorepo Structure

```
AegisPay/
│
├── apps/
│   ├── web/                   Next.js 15 App Router
│   │   ├── app/(dashboard)/   Customer-facing screens
│   │   ├── app/(back-office)/ BACK_OFFICE / ADMIN only
│   │   └── components/        Shared UI components
│   │
│   ├── ios/                   SwiftUI (iOS 17+)
│   │   └── AegisPay/
│   │       ├── Features/      Screen-level feature modules
│   │       ├── Network/       ApiClient, Services, Endpoints
│   │       └── DesignSystem/  Tokens, Components
│   │
│   └── android/               Jetpack Compose (API 26+)
│       └── app/src/main/java/com/aegispay/android/
│           ├── ui/            Screen + ViewModel per feature
│           ├── network/       Retrofit service + Moshi models
│           └── push/          FCM service + badge state
│
├── packages/
│   ├── api-client/            Shared TypeScript API client + React hooks
│   ├── shared-types/          Zod schemas shared across web packages
│   └── design-system/         Tailwind tokens + component library
│
└── services/ (Java 21 backend)
    ├── api-gateway/
    ├── user-service/
    ├── transaction-service/
    ├── ledger-service/
    ├── payment-orchestrator/
    ├── risk-engine/
    ├── notification-service/
    └── ai-platform/
```

---

## 🎯 Feature Phases

| Phase | Status | Description |
|---|---|---|
| **F1** | ✅ | Auth (OAuth2 PKCE), splash, login — all 3 platforms |
| **F2** | ✅ | Dashboard — balance, recent transactions, navigation |
| **F3** | ✅ | Send Money — 4-step wizard, STOMP live status, AI error resolution |
| **F4** | ✅ | KYC — camera/gallery, OCR quality scoring, tamper detection, extracted data review |
| **F5** | ✅ | Push Notifications — APNs (iOS), FCM (Android), WebSocket badge (web) |
| **F6** | ✅ | Back-office — risk case queue, AI fraud explanation, incident triage agent |
| **F7** | 🔜 | Hardening — biometric auth, certificate pinning, accessibility, Baseline Profiles |
| **B1–B10** | 🔜 | Java backend — all 8 microservices, Kafka sagas, ArgoCD deploy |

---

## 🔭 Roadmap

- [ ] **Biometric auth** — Face ID / Touch ID (iOS) + BiometricPrompt (Android)
- [ ] **Certificate pinning** — TrustKit (iOS) + OkHttp CertificatePinner (Android)
- [ ] **Android Baseline Profiles** — startup time optimisation
- [ ] **Backend Phase B1** — Foundation: shared libs + Maven multi-module + CI/CD
- [ ] **Backend Phase B2** — API Gateway with Redis rate limiting
- [ ] **Backend Phase B6** — Payment Orchestrator Saga engine
- [ ] **Backend Phase B9** — AI Platform: RAG pipeline + agentic incident triage

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
