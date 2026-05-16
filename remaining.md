# AegisPay — Remaining Tasks & Status

_Last updated: May 2026 — all originally-planned items complete_

---

## How to Start Everything

```bash
# macOS / Linux — builds JARs then starts everything
./start-local.sh

# Windows — same, auto-detects Maven on PATH
start-aegispay.bat

# Stop everything
docker compose down && pkill -f 'SNAPSHOT.jar' && pkill -f 'next dev'
```

Both scripts: build all JARs → start Docker infra → wait for Keycloak + ClickHouse + Grafana + Kafka → start services (wave 1, then reconciliation after ledger) → seed test accounts → start frontend.

---

## Service Ports

| Service | Port | Notes |
|---------|------|-------|
| API Gateway | 8080 | |
| User Service | 8081 | |
| Transaction Service | 8082 | |
| Ledger Service | 8083 | |
| Payment Orchestrator | 8084 | |
| Risk Engine | 8085 | |
| Notification Service | 8086 | |
| Reconciliation Service | 8087 | |
| Data Pipeline | 8089 | |
| AI Platform | 8091 | |
| Web App (Next.js) | 3000 | |
| Keycloak | 8180 | admin/admin |
| Kafka UI | 8090 | |
| **Grafana** | **3100** | admin/admin — 3 ClickHouse dashboards |
| ClickHouse HTTP | 8123 | |
| ClickHouse Native | 9000 | |
| PostgreSQL | 5433 | aegispay/aegispay_dev |
| Redis | 6379 | |
| MongoDB | 27017 | |

---

## ✅ Completed

### Infrastructure & Analytics
- [x] Grafana replaces Superset everywhere (docker-compose, Helm, start scripts)
- [x] 3 ClickHouse dashboards: Payment Ops, Fraud Intelligence, SLA & Latency
- [x] Grafana provisioning (datasource + dashboard provider + dashboard JSONs) in Helm `files/`
- [x] Helm v1.1.0: Grafana templates (Deployment, Service, PVC, Ingress, 3 ConfigMaps)
- [x] All env values files updated (dev/staging/prod/on-prem): Grafana, SMTP, fast2sms/clickhouse secrets
- [x] `values-dev.yaml` duplicate `global:` key fixed
- [x] `start-local.sh`: Superset → Grafana, ClickHouse + Grafana health-wait steps
- [x] `start-aegispay.bat`: Maven auto-detect, Grafana/ClickHouse wait, pnpm frontend, proper /MIN flags
- [x] ClickHouse init SQL — 4 tables + 3 materialized views — Helm init-job creates schema on deploy

### Infrastructure Simplification (this session)
- [x] **Deleted** `values-dev.yaml`, `values-staging.yaml` — dev and staging Helm overrides removed
- [x] **Deleted** `app-dev.yaml`, `app-staging.yaml` — ArgoCD Applications for dev/staging removed
- [x] **Deleted** `cd-dev.yml`, `cd-staging.yml` — redundant GitHub Actions CD workflows removed
- [x] **Deleted** `infra/helm/monitoring/values-dev.yaml` — monitoring dev overrides removed
- [x] **`applicationset.yaml`** — trimmed to prod-only entry; on-prem uses standalone `app-onprem.yaml`
- [x] **`app-onprem.yaml`** — `targetRevision: dev` → `targetRevision: main`
- [x] **`cd-onprem.yml`** — now triggers on `main` branch (not `dev`), image tags use `onprem-` prefix
- [x] Two deployed environments only: **on-prem** (k3s, cost-optimised) + **prod** (AWS/GKE); **local** docker-compose preserved

### Notification Service
- [x] SMTP email delivery (COMPLETED + FAILED events)
- [x] Slack Incoming Webhook (FAILED only, stub mode when key absent)
- [x] Fast2SMS SMS (FAILED only, stub mode when key absent)
- [x] `StompAuthChannelInterceptor` — STOMP CONNECT validates JWT → fixes `convertAndSendToUser`
- [x] `UserContactDocument` stores full email; `UserRegisteredConsumer` saves it

### Data Pipeline
- [x] `saga_latencies` SQL fixed (`saga_id`, `started_at` columns added)
- [x] `Array(String)` serialisation fixed (`toArray` not CSV join)
- [x] Dead `TOPIC_ROLLED_BACK` branch removed from `TransactionMetricsStream`

### Transaction Service
- [x] `failureCode` field: Postgres column + MongoDB view + response DTOs + Flyway V3 migration
- [x] Stripe pre-flight minimum amount check per currency
- [x] `failureCode` propagated through WebSocket push

### API Gateway
- [x] Resilience4j circuit breaker config (slidingWindow=20, failureRate=60%, wait=15s)
- [x] Retry filter on transaction-service (2 retries on 502/503)
- [x] `FallbackController` returns structured `ApiResponse`

### Frontend
- [x] AI error code uses `failureCode` field (not split on colon heuristic)
- [x] Send Money form resets on mount
- [x] "Completed (recent)" → "Completed (last 10)" KPI label
- [x] Timeline connector line overflow fixed (h-full → bottom-0)

### Documentation (new in this session)
- [x] `docs/architecture/overview.md` + `tech-stack.md`
- [x] `docs/flows/transaction-flow.md` (full sequence + state machine + failure recovery)
- [x] `docs/flows/saga-flow.md` (orchestration, state diagram, compensation table)
- [x] `docs/flows/notification-flow.md` (4 channels, STOMP auth deep dive)
- [x] `docs/flows/auth-flow.md` (OAuth2, JWT claims, multi-IdP, rate limiting)
- [x] `docs/flows/data-pipeline.md` (Kafka → ClickHouse, schema, materialized views, reconciliation)
- [x] `docs/patterns/outbox-pattern.md`, `cqrs.md`, `idempotency.md`, `ledger-design.md`
- [x] `docs/services/services-reference.md` (all 10 services)
- [x] `docs/observability/monitoring.md` (Prometheus, Grafana, alerts, tracing, masking)
- [x] `docs/infrastructure/kafka.md` (topics, partitions, DLQ, consumer groups, schemas)
- [x] `docs/ai/ai-platform.md` (RAG, fraud copilot, error agent, pgvector)
- [x] `CLAUDE.md` fully rewritten
- [x] `SKILLS.md` fixed (removed leading `}`, added all new tech)

### Mobile — iOS & Android Fixes (this session)
- [x] **iOS bug fixed**: `AuthStore.swift` JWT claim corrected — reads `aegispay_user_id` instead of `sub` (was mapping Keycloak internal UUID, not AegisPay domain UUID)
- [x] **Android bug fixed**: `AuthRepository.kt` JWT claim corrected — same `sub` → `aegispay_user_id` fix applied
- [x] **iOS fix**: `StompWebSocket.swift` exponential backoff — 5 s → 10 s → 20 s → 40 s → cap 60 s; reset to 0 on CONNECTED frame
- [x] **Android fix**: `StompWebSocketClient.kt` — OkHttp `pingInterval(30s)` on dedicated wsClient + exponential backoff matching iOS; reset on `onOpen`

### Mobile — Onboarding & KYC (this session)
- [x] **iOS**: `UserService.swift` — added `getMe()` (GET /users/me, returns nil on 404) and `register()` (POST /users/register with idempotency key)
- [x] **iOS**: `AuthStore.swift` — added `.needsRegistration(email: String?)` state; `resolveRegistrationState()` called after every login + session restore; `completeRegistration(userId:)` called by OnboardingViewModel
- [x] **iOS**: `RootView.swift` — handles `.needsRegistration` case, shows `OnboardingView`
- [x] **iOS**: `Features/Onboarding/OnboardingViewModel.swift` + `OnboardingView.swift` — firstName/lastName/email form with validation; submits to `/register`, calls `authStore.completeRegistration`; pre-fills email from Keycloak JWT; sign-out link
- [x] **iOS**: `SendMoneyViewModel.swift` + `SendMoneyView.swift` — KYC guard: loads `kycStatus` on init; if not `.approved` shows blocked screen with CTA to ProfileView instead of the send wizard
- [x] **iOS**: `DashboardViewModel.swift` — loads `kycStatus` alongside account + transactions in `load(userId:)`
- [x] **iOS**: `DashboardView.swift` — shows `kycNudgeBanner()` above balance card when `kycStatus != .approved`; taps navigate to ProfileView
- [x] **Android**: `AuthRepository.kt` — added `NeedsRegistration(email)` to `AuthState`; `resolveRegistrationState()` calls `api.getMe()` (404 → NeedsRegistration); `completeRegistration(userId)` stores UUID + transitions to Authenticated
- [x] **Android**: `AegisApiService.kt` — added `getMe()` (GET /users/me) and `registerUser()` (POST /users/register)
- [x] **Android**: `ApiModels.kt` — added `UserRegistrationRequest` data class
- [x] **Android**: `ui/onboarding/OnboardingViewModel.kt` + `OnboardingScreen.kt` — firstName/lastName/email form; calls `api.registerUser()` then `authRepository.completeRegistration()`; email pre-filled from `NeedsRegistration` state; sign-out button
- [x] **Android**: `AegisNavHost.kt` — `ONBOARDING` route constant + composable; `LaunchedEffect` handles `NeedsRegistration` → navigate to onboarding, popping back stack
- [x] **Android**: `SendMoneyViewModel.kt` — `kycStatus + kycLoading` added to `SendMoneyUiState`; loads on `init` via `api.getUser()`
- [x] **Android**: `SendMoneyScreen.kt` — KYC guard: shows `KycBlockedCard` with "Complete KYC now" button (navigates to Profile) if `kycStatus != APPROVED`; `onNavigateToProfile` param wired in NavHost
- [x] **Android**: `DashboardViewModel.kt` — `kycStatus: KycStatus?` added to `DashboardUiState`; loaded alongside account + transactions
- [x] **Android**: `DashboardScreen.kt` — `KycNudgeBanner` composable shown as first LazyColumn item when `kycStatus != APPROVED`; uses `tertiaryContainer`/`errorContainer` color scheme; taps navigate to Profile

### Web — Full Frontend Audit Fixes (this session)
- [x] **`packages/api-client/src/client/users.ts`** — Added `list(params)` (`GET /api/v1/users`, paginated + KYC filter) and `updateKycStatus(userId, status)` (`PATCH /api/v1/users/{id}/kyc/status`) methods to `UsersClient`. Back-office Users page was calling both → runtime crash at startup.
- [x] **`packages/api-client/src/hooks/useUsers.ts`** — `useUser()` now accepts optional `{ enabled }` second parameter instead of silently ignoring it. Added `useUserList(params)` hook (wraps `users.list()`) so back-office users page can use the shared hook instead of raw `useQuery`.
- [x] **`packages/api-client/src/hooks/index.ts`** — Exported `useUserList` from the package.
- [x] **`apps/web/app/(back-office)/users/users-client.tsx`** — Removed duplicate inline `UserSummary` / `PagedUsers` types; replaced with shared `User` type from `@aegispay/shared-types`. Switched `useQuery` → `useUserList`. Fixed null-safety on `user.name` (was calling `.charAt(0)` / `.toLowerCase()` directly on a `string | null | undefined` field → runtime TypeError).
- [x] **`apps/web/app/(dashboard)/send/steps/StepStatus.tsx`** — `errorCode` now uses `tx.failureCode` (machine-readable field) with graceful fallback to last segment after `:` in `failureReason`, matching `transaction-detail-client.tsx`. Was incorrectly using the old `split(':')[0]` heuristic (opposite end of the string). Dep array changed to `[tx?.transactionId, tx?.status]` to avoid spurious re-triggers.
- [x] **`apps/web/app/(dashboard)/dashboard/dashboard-client.tsx`** — Replaced `window.location.href = /transactions/...` (full page reload) with `router.push()` (SPA navigation). Added `useRouter` import.
- [x] **`apps/web/app/(dashboard)/send/send-money-client.tsx`** — KYC status loading state now shows a centered `Loader2` spinner instead of returning `null` (blank white area). Guard condition also tightened: `if (!user || user.kycStatus !== 'APPROVED')` — previously `if (user && ...)` would silently let through if the user API call failed.

### Web — Notification Fixes (this session)
- [x] **`packages/shared-types/src/notification.ts`** — `PagedNotificationsSchema` field renamed `number` → `page` (backend `PagedResponse` serialises it as `page`; Zod parse was throwing → `data` undefined → empty list)
- [x] **`apps/web/lib/auth.ts`** — `profile()` callback now sets `id: profile.aegispay_user_id ?? profile.sub`; `session.user.id` is now the AegisPay domain UUID (not Keycloak sub), fixing STOMP subscription destination and all `useUser(session.user.id)` API calls
- [x] **`packages/api-client/src/hooks/useWebSocket.ts`** — added `stoppedRef` flag; `ws.onclose` skips reconnect scheduling if cleanup already ran (fixes React StrictMode double-mount race: no phantom 3rd connection after 5 s)
- [x] **`useTransactionStatusSocket`** — same `stoppedRef` fix applied for consistency
- [x] **`apps/web/app/(dashboard)/notifications/notifications-client.tsx`** — removed duplicate `useTransactionSocket` call, unused `session`/`queryClient`/`toast`/`increment` imports; sidebar owns the single WebSocket connection
- [x] **`apps/web/components/sidebar.tsx`** — global `onNotification` handler now calls `toast.info(title, { description: body })` when user is not already on `/notifications`; single authoritative source for both badge increment and toast

### Web — KYC & Back-Office (this session)
- [x] `GET /api/v1/users` endpoint added to User Service (back-office role guard, paginated, filterable by kycStatus)
- [x] `UserRepository` — `findAllByKycStatusOrderByCreatedAtDesc` + `findAllByOrderByCreatedAtDesc` added
- [x] `UserService.listUsers()` + `UserController` `@GetMapping` endpoint added
- [x] Android `AegisApiService.listUsers()` + `PagedUsers` model added
- [x] Back-office Users page (`apps/web/app/(back-office)/users/`) — paginated table, KYC status filter chips, approve/reject/manual-review modal
- [x] Sidebar — Users2 nav item for back-office role
- [x] Middleware matcher — `/back-office/:path*` added
- [x] Send Money KYC guard — blocks the flow with a CTA to `/profile` if `kycStatus !== 'APPROVED'`

### Mobile — iOS & Android Core
- [x] iOS biometric auth: `BiometricAuthService.swift` — Face ID / Touch ID / OpticID via `LocalAuthentication`
- [x] iOS Keychain token storage: `TokenStore.swift` — `.whenUnlockedThisDeviceOnly`, no iCloud backup
- [x] iOS APNs push: `PushNotificationHandler.swift` — permission request, device token → backend, foreground banners
- [x] iOS certificate pinning: `CertificatePinningDelegate.swift`
- [x] iOS haptic feedback: `HapticFeedback.swift`
- [x] iOS STOMP WebSocket: `StompWebSocket.swift`
- [x] Android biometric: `BiometricAuthManager.kt` — `BiometricPrompt` with `BIOMETRIC_STRONG`, suspend API
- [x] Android encrypted token storage: `TokenStore.kt` — `EncryptedSharedPreferences` AES256-SIV/GCM
- [x] Android FCM: `AegisFcmService.kt` — `onNewToken` registers to backend, `onMessageReceived` shows notification

### KYC Backend
- [x] `KycStateMachine` — full state transitions in User Service
- [x] `POST /{userId}/kyc/documents` — document upload + AI OCR callback
- [x] `PATCH /{userId}/kyc` — user confirmation step (PENDING → DOCUMENT_SUBMITTED)
- [x] `PATCH /{userId}/kyc/status` — AI platform callback endpoint
- [x] `POST /{userId}/push-token` — APNs + FCM token registration endpoint

### Backend — Filters, Exchange Rates, CD Token
- [x] Transaction list filters wired: `status`, `fromDate`, `toDate` `@RequestParam` → dynamic `Criteria` in `TransactionService.listForUser()`
- [x] Live exchange rates: `ExchangeRateService` in Ledger Service → Frankfurter API (ECB, no key) → Redis cache `fx:rates:{BASE}` TTL=1h → hardcoded pivot fallback on failure
- [x] `GIT_DEPLOY_TOKEN` wired in all 4 CD workflows (GitHub Fine-Grained PAT with `contents: write`)

### Earlier Session Fixes
- [x] Lombok/MapStruct annotation processing order
- [x] Keycloak configure script + client scopes
- [x] Ledger-service Flyway baseline-version:0
- [x] Redis NOAUTH, port conflicts, SecurityConfig reconciliation
- [x] Auth signout cache clear, API client envelope success:false
- [x] Transaction step 3 (payerId, note, amount serialisation, list DTO, pagination)

### Rules Engine Seeding (this session)
- [x] **`V3__seed_rules.sql`** — Flyway migration creates `rule_config` table with 7 default rules (AMOUNT_THRESHOLD, VELOCITY_CHECK, BLACKLIST_CHECK, GEO_LOCATION, TIME_OF_DAY, NEW_DEVICE, STRIPE_RADAR_EFW) and seeds initial `fraud_blacklist` entries. Rules match hardcoded `RiskProperties.java` defaults; `rule_config` table is the authoritative store for operator-tunable thresholds.

### AI Knowledge Base (this session)
- [x] **Already complete** — `KnowledgeBaseSeeder.java` loads on startup from `knowledge/fraud_cases.json` (272 lines), `knowledge/bank_error_codes.json` (313 lines), `knowledge/incident_logs.json` (674 lines). Seeds into pgvector only when table is empty. No further action needed.

### Stripe Radar Integration (this session)
- [x] **`StripeWebhookController.java`** — Added `radar.early_fraud_warning.created` handler. Retrieves the associated `PaymentIntent`, extracts `transaction_id` metadata, calls `sagaOrchestrator.onStripeRadarEarlyFraudWarning()`.
- [x] **`PaymentSagaOrchestrator.java`** — Added `onStripeRadarEarlyFraudWarning()`: publishes a `risk.stripe-radar-efw` Kafka outbox event so the Risk Engine can escalate to MANUAL_REVIEW or auto-reject (configurable via `rule_config.STRIPE_RADAR_EFW`).
- [x] **Javadoc updated** — Webhook controller now documents all 4 required Stripe event subscriptions.
- [x] **Risk metadata already in PaymentIntent** — `StripePaymentGatewayClient` stamps `transaction_id`, `payer_id`, `payee_id` in `putMetadata()` at PaymentIntent creation. Stripe Radar can use these as custom metadata attributes.

### Android Fixes (this session)
- [x] **`SendMoneyScreen.kt`** — `keyboardType = KeyboardType.Decimal` was already correct ✅ (no change needed)
- [x] **`AegisComponents.kt`** — `formatAmount()` already uses `Locale("en", "IN")` explicitly for INR ✅ (no change needed)
- [x] **`AegisFcmService.kt`** — Notification channels split into three: `COMPLETED` (`IMPORTANCE_DEFAULT`, pattern vibration), `FAILED` (`IMPORTANCE_HIGH`, alarm sound + long vibration), `GENERAL` (`IMPORTANCE_LOW`). Routes FCM messages by `data["type"]` field with title/body fallback heuristic.

### Deep Links (this session)
- [x] **`assetlinks.json`** — created at `api-gateway/src/main/resources/static/.well-known/assetlinks.json` for Android App Links verification (SHA-256 fingerprint placeholder — replace before release)
- [x] **`apple-app-site-association`** — created at same `.well-known/` path for iOS Universal Links (`/transactions/*`, `/send`, `/profile`, `/wallet` paths)
- [x] **`AndroidManifest.xml`** — Added `aegispay://app/*` custom-scheme intent filters + HTTPS App Link intent filters for `api.aegispay.shreyasshelar.uk` (transactions, send, wallet paths) with `autoVerify="true"`
- [x] **`AegisNavHost.kt`** — `TRANSACTION_DETAIL` and `SEND_MONEY` composables wired with `deepLinks = listOf(navDeepLink { uriPattern = ... })` for both schemes
- [x] **`Info.plist`** — Added `com.apple.developer.associated-domains` key with `applinks:` and `webcredentials:` entries for `api.aegispay.shreyasshelar.uk`
- [x] **`DeepLinkRouter.swift`** — New Swift class: parses universal links and custom-scheme URLs into `DeepLinkDestination` enum; publishes `pendingDestination` via `@Published` for SwiftUI `onChange` consumption
- [x] **`AegisPayApp.swift`** — `onOpenURL` now routes to `DeepLinkRouter.shared.handle()` vs `AuthStore.handleRedirectURL()` based on URL host
- [x] **`RootView.swift`** — Observes `DeepLinkRouter.shared`; passes it as environment object to `MainTabView`

### Frontend Wallet Top-Up (this session)
- [x] **`apps/web/app/(dashboard)/wallet/page.tsx`** — Server component; auth-guarded; renders `WalletClient`
- [x] **`apps/web/app/(dashboard)/wallet/wallet-client.tsx`** — Four-state flow: `form` (amount + currency + presets) → `processing` → `success` / `failed`. Uses `useTopUp()` hook for createIntent + confirm API calls. Currency selector (INR/USD/EUR/GBP) with quick-amount preset buttons. Current balance shown via `useAccount()`.
- [x] **`packages/api-client/src/client/ledger.ts`** — Added `createTopUpIntent(amount, currency)` (`POST /api/v1/ledger/topup/intent`) and `confirmTopUp(paymentIntentId)` (`POST /api/v1/ledger/topup/confirm`) methods + Zod schemas
- [x] **`packages/api-client/src/hooks/useAccount.ts`** — Added `useTopUp()` mutation hook: wraps both steps, invalidates `accountKeys.me()` on success
- [x] **`packages/api-client/src/hooks/index.ts`** — Exported `useTopUp`, `useAccountsByUser`
- [x] **`apps/web/components/sidebar.tsx`** — Added `Wallet` icon + `/wallet` nav item

### Keycloak Secret Rotation (this session)
- [x] **`infra/helm/infra/templates/keycloak-secret-rotation-job.yaml`** — `CronJob` (runs 1st of each month at 03:00 UTC) + `ServiceAccount`. Shell script: authenticates to Keycloak Admin API using Vault-injected admin password, resolves client UUID, calls `regenerate-secret`, writes new secret to Vault at `secret/data/aegispay/keycloak/client-secret`. Vault Agent Injector annotations inject secrets at pod start. Gate: `keycloak.secretRotation.enabled`.
- [x] **`infra/helm/infra/values-onprem.yaml`** — Added `keycloak.secretRotation`, `keycloak.adminUrl`, `keycloak.adminUsername`, `keycloak.clientId`, `keycloak.realm`, `vault.addr`, `namespace` values.

### Cloudflare Tunnel (this session)
- [x] **`infra/cloudflare/tunnel-onprem.yaml`** — On-prem tunnel: `cloudflared` Deployment in `kube-system`, 2 replicas, label `environment: onprem`, token from `cloudflare-tunnel-onprem-secret`. Metrics Service + ServiceMonitor. Tolerates control-plane taint.
- [x] **`infra/cloudflare/tunnel-prod.yaml`** — Prod tunnel: `cloudflared` Deployment in `aegispay-prod`, 3 replicas, `topologySpreadConstraints` (maxSkew:1), pod anti-affinity, PodDisruptionBudget (`minAvailable: 2`), metrics Service + ServiceMonitor.

### OpenRouter for On-Prem AI (this session)
- [x] **`AiModelConfig.java`** — `@Bean @Primary @Profile("!onprem")` → Anthropic Claude; `@Bean @Primary @Profile("onprem")` → OpenAI adapter pointing to `https://openrouter.ai/api/v1` with free Llama model. Both beans avoid duplicate `@Primary` via profile exclusion.
- [x] **`application-onprem.yml`** — already configured: `spring.ai.openai.base-url`, `.api-key`, `.chat.options.model = meta-llama/llama-3.1-8b-instruct:free`.

### Multi-Tenancy — PostgreSQL RLS (this session)
- [x] **`user-service/V4__rls_tenant_isolation.sql`** — Backfills `tenant_id NOT NULL` on `users` + adds `tenant_id` to `kyc_documents` (backfilled from parent). Creates `aegispay_user_svc` role, enables RLS + `FORCE ROW LEVEL SECURITY`, policy: `tenant_id = current_setting('app.tenant_id', TRUE)`.
- [x] **`transaction-service/V4__rls_tenant_isolation.sql`** — Adds `tenant_id` to `transactions` + `outbox_events`. Creates `aegispay_txn_svc` role, full RLS setup.
- [x] **`ledger-service/V3__rls_tenant_isolation.sql`** — Adds + backfills `tenant_id` on `accounts`, `ledger_entries`, `balance_locks`, `outbox_events`. Creates `aegispay_ledger_svc` role, full RLS setup with composite indexes.
- [x] **`TenantTransactionInterceptor.java`** (common-security lib) — AOP `@Around @Transactional` aspect fires `SET LOCAL app.tenant_id = ?` using value from `ActorContext.get().getTenantId()` (JWT claim). Auto-wired by `AegisPaySecurityAutoConfig` only when `JdbcTemplate` is present (gateway and messaging services unaffected).

### k6 Load Tests (this session)
- [x] **`tests/load/k6/happy-path.js`** — `constant-arrival-rate` executor, 500 RPS × 5 min. Polls saga to terminal state, custom `txn_poll_latency_ms` metric. Thresholds: p95 ≤ 800 ms, p99 ≤ 1500 ms, error rate < 1%.
- [x] **`tests/load/k6/idempotency.js`** — 100 VUs competing over 50 shared idempotency keys (`SharedArray`). Threshold: `txn_duplicate_count == 0` (zero tolerance for duplicate transactions).
- [x] **`tests/load/k6/saga-timeout.js`** — 800 VUs, `X-Fault-Delay-Ms: 25000` header triggers gateway fault injection. Validates compensation fires within 35 s; `saga_stuck == 0` threshold.
- [x] **`tests/load/k6/auth.js`** + **`config.js`** — Shared Keycloak ROPC token helper and environment config; override via `--env` flags.
- [x] **`tests/load/k6/run.sh`** — Convenience runner: `./run.sh [happy-path|idempotency|saga-timeout|all]`.

### ClickHouse HA — 2-shard / 2-replica (this session)
- [x] **`infra/helm/infra/templates/clickhouse/configmap.yaml`** — Cluster topology (2 shards × 2 replicas in `remote_servers`), Keeper ZooKeeper-compat endpoints, `users.xml` with env-var password injection for `aegispay_app` user.
- [x] **`infra/helm/infra/templates/clickhouse/keeper-statefulset.yaml`** — 3-node ClickHouse Keeper (Raft quorum). Init container generates per-pod `keeper_config.xml` from StatefulSet ordinal. Headless Service + ServiceMonitor. `topologySpreadConstraints` across nodes.
- [x] **`infra/helm/infra/templates/clickhouse/statefulset.yaml`** — Two StatefulSets (`shard-0`, `shard-1`) via `range` loop, 2 replicas each. Init container writes `macros.xml` (shard/replica FQDN). Hard anti-affinity within a shard, soft anti-affinity across shards. PDB `minAvailable: 1` per shard. Single `clickhouse` ClusterIP service + ServiceMonitor.
- [x] **`infra/helm/infra/values-prod.yaml`** — New file: full prod infra values (ClickHouse HA enabled with 200 Gi storage + 16 Gi mem limit, PostgreSQL primary + 1 read replica, Redis Sentinel, Kafka 3 brokers, `premium-rwo` storage class).
- [x] **`infra/helm/infra/values-onprem.yaml`** — `clickhouse.enabled: false` (on-prem is single-node; HA requires ≥4 nodes).

### Mobile — iOS WidgetKit Live Activity (this session)
- [x] **`apps/ios/AegisPay/Widget/PaymentLiveActivity.swift`** — `PaymentActivityAttributes` + `ContentState` (status enum, amount, currency). `PaymentLiveActivityManager` (`@MainActor`) with `startActivity()`, `updateActivity()`, `endActivity()`. Lock-screen banner view (`PaymentLiveActivityView`). Dynamic Island widget (`PaymentDynamicIsland`) with expanded / compact-leading / compact-trailing / minimal regions. SF Symbol `.variableColor` animation during processing state. iOS 16.1 degrades to lock-screen banner.

### Mobile — Android Offline Queue (this session)
- [x] **`OfflinePaymentEntity.kt`** — Room entity: idempotency key PK, status enum (PENDING/SYNCING/DONE/FAILED), retry count, TTL timestamp, `serverTransactionId`, `failureReason`.
- [x] **`OfflinePaymentDao.kt`** — FIFO `pendingPayments()`, `pendingCount()` Flow (drives UI badge), status update methods, `resetStuckSyncing()`, `pruneOldTerminal(cutoffMs)`.
- [x] **`OfflineDatabase.kt`** — Room singleton with `TypeConverter` for status enum.
- [x] **`OfflinePaymentQueue.kt`** — Hilt singleton facade: `enqueue()` persists + schedules WorkManager with `ExistingWorkPolicy.KEEP`. `scheduleSync()` requires `CONNECTED` network constraint.
- [x] **`PaymentSyncWorker.kt`** — `@HiltWorker`; resets stuck SYNCING rows; replays PENDING FIFO; 409 → DONE, 4xx → FAILED (unretriable), 5xx/network → RETRY; prunes terminal rows older than 7 days.
- [x] **`OfflineModule.kt`** — Hilt module: provides DB, DAO, Hilt-aware `WorkManager.Configuration`.
- [x] **`AegisPayApplication.kt`** — Implements `Configuration.Provider`; schedules sync on cold start.
- [x] **`libs.versions.toml`** + **`build.gradle.kts`** — Added `work-runtime-ktx`, `hilt-work`, `hilt-work-compiler`.

### E2E Tests (this session)
- [x] **Detox (iOS)** — `.detoxrc.js` (3 device/app configs: debug, release, CI), `jest.config.js`.
  - `specs/happy-path.spec.js` — login → dashboard → send ₹100 → transaction history → COMPLETED
  - `specs/biometric.spec.js` — background/resume → Face ID approve → unlock; reject → error state
  - `specs/offline-reconnect.spec.js` — disable network → queue payment → restore → WorkManager sync → COMPLETED
- [x] **Maestro (Android)** — `maestro/happy-path.yaml`, `maestro/biometric.yaml`, `maestro/offline-reconnect.yaml`; shared adb scripts: `android-airplane-on/off.js`, `android-fingerprint-accept/reject.js`.

---

## ✅ ALL TASKS COMPLETE

> Every item in the original backlog has been implemented. The only remaining action is the **live production transaction** below, which is intentionally gated behind prod verification steps.

---

## ⛔ PROD ONLY — Last Step: Live Transaction (Do This Last)

> **DO NOT attempt until every item above is checked off and verified in prod.**
> This section is intentionally placed last. It involves real money on the `main` branch.

### Prerequisites (must all be true before proceeding)

- [x] Rules Engine default rules seeded and verified (no more score=0 decisions)
- [x] AI knowledge base seeded and RAG retrieval returning correct explanations
- [x] All remaining backend tasks completed and deployed to prod via Argo CD
- [x] All mobile fixes merged to `main`
- [ ] Grafana dashboards showing clean metrics in prod (no DLQ depth, no saga timeouts)
- [x] Live exchange rates integrated (`ExchangeRateService` → Frankfurter API, Redis cache, hardcoded fallback)

### Step 1 — Verify Live Exchange Rates in Prod ✅ Already Integrated

`ExchangeRateService` (Ledger Service) uses Frankfurter API — no API key required, ECB data.
Rates are cached in Redis `fx:rates:{BASE}` with 1-hour TTL. Fallback to hardcoded pivot rates on network failure.

**Prod verification checklist** (before live transaction):
- [ ] Confirm Redis in prod has `fx:rates:USD` (or any base) populated: `redis-cli GET fx:rates:USD`
- [ ] Hit `GET /api/v1/rates` via Gateway and confirm real ECB rates are returned (not hardcoded fallback values)
- [ ] Confirm Frankfurter is reachable from the prod cluster (outbound HTTPS to `api.frankfurter.app`)

### Step 2 — Prod Live E2E Transaction

> Use a small, intentional amount. This is real money.

```
Branch: main (prod Argo CD target)
Environment: prod
Stripe keys: LIVE keys (sk_live_...) from Vault aegispay/prod/stripe/secret-key
Amount: ₹1.00 (minimum valid INR amount — ₹50 per Stripe minimum)
Actually: use ₹50.00 — meets Stripe INR minimum
```

**Checklist**:
- [ ] Confirm `STRIPE_SECRET_KEY` in prod Vault is `sk_live_...` (NOT `sk_test_...`)
- [ ] Confirm live exchange rates are streaming (Redis key `exchange:rates:latest` has real data)
- [ ] Log into prod frontend as `customer@aegispay.io` (prod account, not `.local`)
- [ ] Send ₹50.00 to payee account; note the `idempotencyKey` from browser Network tab
- [ ] Verify: transaction status → COMPLETED in UI (WebSocket push received)
- [ ] Verify: Grafana Payment Ops dashboard shows the transaction in the last-24h panel
- [ ] Verify: Email notification received by sender
- [ ] Verify: Ledger entries: `SUM(amount) = 0` invariant holds
- [ ] Verify: Stripe dashboard shows the live charge

**Rollback**: if anything fails, the transaction is already atomic (Saga compensates automatically). Manually verify ledger is balanced. Do NOT retry — investigate first.

---

## Test Accounts

```
Sender:  customer@aegispay.local / Test@1234  (₹50,000)
Payee:   payee@aegispay.local    / Test@1234  (₹25,000)
Payee UUID (for Send Money form): 3bf3e523-9de8-4254-9cc9-d5fa50ff8d4a
```
