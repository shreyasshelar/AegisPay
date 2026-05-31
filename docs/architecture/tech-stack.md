# AegisPay — Technology Stack Decisions

Every technology choice answers the question: *"what problem does this solve that the alternative cannot?"*

---

## Backend — Java 21 + Spring Boot 3.3

**Why Java 21?**
- Virtual threads (Project Loom) replace reactive WebFlux complexity for I/O-bound workloads without callback hell
- Strong typing enforces contract correctness across services
- Spring ecosystem maturity: Security, Cloud Gateway, Batch, Data JPA all work out of the box

**Why Spring Boot over Quarkus/Micronaut?**
- Keycloak's resource server adapter has first-class Spring Security support
- Spring Cloud Gateway's reactive circuit breaker + retry integrates natively with Resilience4j
- Micrometer + Actuator gives Prometheus metrics with zero config

**Why not Go / Node?**
- Financial arithmetic in Go/JS requires external decimal libraries to avoid floating-point errors; Java's `BigDecimal` is built-in
- Flyway + Hibernate handle schema migrations and ORM with battle-tested reliability

---

## API Gateway — Spring Cloud Gateway

**Why a dedicated gateway?**
- Centralises cross-cutting concerns: JWT validation, rate limiting, idempotency enforcement
- Services don't need to re-implement auth — they trust the gateway-validated JWT
- Circuit breaker + retry at the gateway level prevents cascading failures

**Why not Kong / NGINX?**
- Spring Cloud Gateway is code-first: routes are defined in Java, tested in unit tests, version-controlled
- Resilience4j circuit breaker integrates without a plugin

---

## Message Broker — Apache Kafka 3.7 (KRaft)

**Why Kafka over RabbitMQ / SQS?**
- **Replay**: Kafka retains events for 7 days by default. Any consumer can replay from the beginning — critical for rebuilding CQRS read models or debugging
- **Partitioning**: transaction events partitioned by `userId` guarantee ordering per user
- **Streams**: Kafka Streams (TransactionMetricsStream, RiskAnalyticsStream) runs in-process, no external stream processor required

**Why KRaft (no ZooKeeper)?**
- KRaft mode eliminates a separate ZooKeeper cluster — simpler operations, faster startup, same reliability

**Exactly-once semantics**: not configured at broker level (adds latency); instead we achieve exactly-once *behaviour* via idempotency keys checked in every consumer before processing.

---

## Database Layer

### PostgreSQL 16 with pgvector extension

**Why PostgreSQL?**
- ACID transactions are non-negotiable for financial data
- `pgvector` extension stores embedding vectors for AI RAG knowledge base alongside relational data — no separate vector DB service

**Why pgvector instead of Pinecone / Weaviate?**
- One less operational dependency; pgvector's HNSW index has comparable recall for our dataset size
- Vectors live in the same DB as the domain data → a single Flyway migration creates both tables

**Separate databases per service** (`aegispay_users`, `aegispay_transactions`, `aegispay_ledger`, etc.):
- Each service owns its schema — no cross-service joins
- Independent Flyway migration chains per service
- In production, each can be a separate RDS instance

### Redis 7

**Why Redis over Memcached / DynamoDB?**
- `SET NX PX` for idempotency key reservation in a single atomic operation
- `INCR` + `EXPIRE` for sliding-window rate limiting (no external library)
- Pub/sub and sorted sets for real-time features

### MongoDB 7

**Why MongoDB for CQRS read models and notification contacts?**
- Denormalised documents map 1:1 to frontend view models — no JOIN overhead on reads
- Schema flexibility: adding new fields to a read model requires no migration
- `$lookup` for occasional cross-document joins in reporting

### ClickHouse 24.4

**Why ClickHouse for analytics?**
- Column-store: a query scanning 10M transactions for `SUM(amount) GROUP BY currency` reads only 2 columns vs a row-store reading the full row
- `MergeTree` engine handles high-ingest from Kafka with automatic background merges
- `Array(String)` type for `rule_flags` avoids a separate join table
- Materialized views auto-aggregate hourly summaries as data lands — Grafana queries the summary, not the raw table

**Why not a PostgreSQL analytics schema?**
- OLTP and OLAP on the same Postgres cluster would compete for I/O and lock resources
- Postgres query planner is not optimised for full-table-scan aggregations

---

## Identity — Keycloak 24

**Why Keycloak over Auth0 / Cognito?**
- Self-hosted → zero per-MAU cost, zero data leaves the cluster
- Multi-IdP federation (Azure Entra, Okta, Google) with a single realm config
- Fine-grained realm export/import for version-controlled realm configuration
- Realm auto-import on container start via `--import-realm` flag

**JWT claims mapping**: Keycloak issues JWTs with `aegispay_user_id` custom claim that maps to the internal UUID, decoupling Keycloak subject from the domain user ID.

---

## Observability — Prometheus + Grafana

**Two Grafana instances** (by design):

| Instance | Source | Dashboards |
|----------|--------|-----------|
| `kube-prometheus-stack` Grafana | Prometheus (JVM, Kafka, K8s) | Spring Boot stats, JVM GC, Kafka lag, K8s workloads |
| AegisPay Grafana (3100) | ClickHouse | Payment Ops, Fraud Intelligence, SLA & Latency |

**Why separate?** Prometheus tracks infrastructure health (is the JVM healthy?). ClickHouse tracks business health (are payments succeeding?). Mixing them in one datasource creates confusion and query interference.

---

## Secret Management — External Secrets Operator (ESO) + HashiCorp Vault

**Why ESO over hardcoded K8s Secrets?**
- K8s Secrets are base64, not encrypted at rest (unless etcd encryption is enabled)
- ESO pulls secrets from Vault on a refresh interval → secret rotation without pod restart
- Single `SecretStore` CR per environment; all `ExternalSecret` CRs reference it
- No passwords anywhere in code, values files, or Git — only in Vault and a git-ignored `.secrets.env`

**Backend: HashiCorp Vault for all environments**
- Local: `.env.local` (git-ignored) — Vault not used locally
- Dev (GCP k3s): Vault in-cluster (`aegispay-vault` namespace), **GCP KMS auto-unseal** so VM restarts don't leave Vault sealed
- Production: Vault in-cluster, same GCP KMS auto-unseal pattern

**Bootstrap flow** (one-time, run from developer machine):
```
infra/secrets/.secrets.env   ← git-ignored, all real values in one file
infra/scripts/vault-bootstrap.sh  ← reads .secrets.env, writes to Vault KV paths
```
After bootstrap, ESO creates K8s Secrets from Vault paths; pods mount them via `secretKeyRef`. Zero secrets in source control.

---

## Container Orchestration — Kubernetes + Helm

**Why Helm over plain manifests / Kustomize?**
- `values-{env}.yaml` overrides make environment-specific config explicit and diff-able
- Helm hooks (`post-install`, `post-upgrade`) run the ClickHouse init job exactly once on deploy
- A single `helm upgrade --install` deploys 10 services atomically with rollback support

**PodDisruptionBudgets** on all services ensure rolling upgrades never take more replicas offline than the configured `minAvailable`.

**HPA on write-path services** (transaction, ledger, payment-orchestrator): these scale under payment volume spikes. Notification and reconciliation are excluded — notification scales by queue lag, reconciliation is a scheduled batch.

---

## Mobile Clients

### iOS — Swift 6, SwiftUI, SPM

| Library | Role |
|---------|------|
| AppAuth-iOS | OAuth 2.0 PKCE flow — no implicit grant, no client secret in binary |
| KeychainAccess | `TokenStore` wraps Keychain — access token, refresh token, userId, role, all under `whenUnlockedThisDeviceOnly` |
| Stripe iOS SDK | `PaymentSheet` for wallet top-up |
| URLSession (native) | `ApiClient` — JWT auto-injection, certificate pinning via `CertificatePinningDelegate` |
| Combine / `@StateObject` | `AuthStore` (@MainActor ObservableObject) owns the PKCE session and token refresh lifecycle |

**`AuthStore.refreshTokens()`** starts `updatedUserId` from the existing stored value and only overwrites if a newer `aegispay_user_id` is found in the new ID token or access token — prevents claim loss when Keycloak omits the ID token on refresh.

**Role-based UI**: `MainTabView` hides all customer tabs for staff roles; admin lands on Triage tab (tag 7) via `.task` on first appearance.

**AegisMarkdownView**: custom SwiftUI view using `AttributedString(markdown:)` (iOS 15+) to render AI triage reports and fraud explanations with headings, bold, inline code, and bullets.

### Android — Kotlin, Jetpack Compose, Hilt

| Library | Role |
|---------|------|
| AppAuth-Android | OAuth 2.0 PKCE flow via Chrome Custom Tab — no password in APK |
| EncryptedSharedPreferences | `TokenStore` — AES256-GCM encrypted token storage |
| Hilt | DI framework — `@Singleton TriageSessionStore` survives NavBackStackEntry pops |
| Retrofit + Moshi | Type-safe API client; `BigDecimalAdapter` for financial amounts |
| OkHttp | `StompWebSocketClient` for live transaction status; certificate pinning |
| Stripe Android SDK | `PaymentSheet` for wallet top-up |
| WorkManager | `PaymentSyncWorker` — offline payment queue retry on reconnect |
| FCM | `AegisFcmService` — push notifications for transaction state changes |

**`AuthRepository.persistTokens()`** decodes both ID token and access token to extract `aegispay_user_id`; falls back to the existing stored value when neither token carries the claim (Keycloak often omits ID token on refresh).

**Role-based navigation**: `AegisNavHost` routes admin → `Route.TRIAGE`, back-office → `Route.BACK_OFFICE`, customer → `Route.DASHBOARD` on `AuthState.Authenticated`.

**`MarkdownText` composable**: renders `# H1`, `## H2`, `- bullets`, `**bold**`, `` `code` `` from AI triage and fraud explanation responses.

---

## CI/CD — GitHub Actions + ArgoCD GitOps

| Workflow | Trigger | Action |
|----------|---------|--------|
| `ci.yml` | Push to any branch | Maven build + test, Docker build + push to GHCR |
| `cd-dev.yml` | Push to `dev` | `yq` patches image tag in `values-dev.yaml`, ArgoCD GCP k3s sync |
| `cd-prod.yml` | Manual workflow dispatch | Updates `values-prod.yaml`, opens PR for review |

**Why Argo CD (GitOps)?** The cluster's desired state is always in Git. Any manual `kubectl apply` is overwritten on the next sync cycle, preventing drift.

**ArgoCD diff behaviour**: only the Deployment whose image tag changed is re-applied. Kafka, Postgres, Redis, MongoDB, ClickHouse StatefulSets are untouched by app-layer deploys — they live in a separate `aegispay-infra` ArgoCD Application.

**GCP Cloud Scheduler** starts and stops the GCP VM on a cron schedule (08:00/22:00 IST weekdays, 09:00/15:00 IST weekends) using the Compute Engine API via a Service Account — no always-on cost while the project is sleeping.
