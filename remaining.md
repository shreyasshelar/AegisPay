# AegisPay — Remaining Tasks & Status

_Last updated: May 2026_

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

---

## 🔧 Remaining / Planned

### Backend — High Priority

#### Rules Engine Has No Rules Loaded
- Risk Engine framework works but no default rules are seeded — all transactions score 0, decision=ALLOW
- **Fix**: Flyway seed migration creating default rules (velocity limit, amount threshold, new device, geography)

#### AI Service End-to-End
- AI Platform endpoints exist; pgvector knowledge base is empty
- **Fix**: seed knowledge base with fraud patterns + error resolution docs; verify RAG retrieval end-to-end

#### ~~Live Exchange Rates~~ ✅ Done
- `ExchangeRateService` in Ledger Service calls Frankfurter API (`api.frankfurter.app` — ECB data, no API key required)
- Rates cached in Redis under `fx:rates:{BASE}` with 1-hour TTL; fallback to hardcoded pivot rates (via INR) on network failure
- No extra infrastructure — reuses the existing Ledger Service Redis connection

### Backend — Medium Priority

#### ~~Transaction Filters Not Wired~~ ✅ Done
- `@RequestParam` bindings for `status`, `fromDate`, `toDate` are wired in `TransactionController`
- `TransactionService.listForUser()` builds dynamic `Criteria` conditions and applies them via `MongoTemplate`

#### Stripe Radar Integration
- AegisPay risk score not sent to Stripe; `radar.early_fraud_warning` webhooks not consumed
- **Fix**: POST risk metadata to `PaymentIntent.metadata`; add webhook handler for EFW

### Backend — Low Priority

#### Multi-Tenancy (PostgreSQL RLS)
- `tenantId` in `ActorContext` but RLS policies not created
- Pattern: `CREATE POLICY tenant_isolation ON transactions USING (tenant_id = current_setting('app.tenant_id'))`

#### k6 Load Tests
- 500 RPS happy path; concurrent idempotency key test; saga timeout under pressure
- Location: `tests/load/k6/`

#### ClickHouse HA (2-shard / 2-replica)
- Current: single-node. Plan: ClickHouse Keeper + 2-shard 2-replica in `infra/helm/infra/`

### Frontend — Medium Priority

#### Onboarding & KYC Flow

**Backend** — ✅ Fully implemented in User Service
- `POST /{userId}/kyc/documents` — document upload + AI OCR via `AiPlatformClient`
- `PATCH /{userId}/kyc` — user confirms AI-reviewed data (PENDING → DOCUMENT_SUBMITTED)
- `PATCH /{userId}/kyc/status` — AI platform callback updates OCR results
- `KycStateMachine` drives state transitions; `KycDocumentRepository` persists docs
- `POST /{userId}/push-token` — APNs + FCM token registration endpoint wired

**Social sign-up** — ✅ Via Keycloak (Google, Microsoft, GitHub, Apple already configured as IdPs in realm)

**Web Frontend**
- [x] KYC upload + AI review + confirm: fully in `(dashboard)/profile/profile-client.tsx` (drag-and-drop, camera, quality bars, tampering detection, confirm step)
- [x] Back-office: `(back-office)/users/page.tsx` + `users-client.tsx` — paginated user list, KYC status filter, approve/reject/manual-review modal
- [x] `middleware.ts` — `/back-office/:path*` added to matcher; send flow blocks non-verified users
- [x] Send Money KYC guard — `send-money-client.tsx` blocks with CTA to Profile if `kycStatus !== 'APPROVED'`
- [ ] Wallet top-up flow: `(dashboard)/wallet/page.tsx` → Stripe PaymentIntent for adding balance (still pending)

**Mobile** — ✅ KYC guard + onboarding registration implemented
- [x] iOS + Android: new-user detection via `GET /users/me` (404 → registration flow; 200 → store AegisPay UUID)
- [x] iOS + Android: first-time registration form (`OnboardingView` / `OnboardingScreen`) with name + email, submits to `POST /users/register`
- [x] iOS + Android: `AuthState.needsRegistration / NeedsRegistration` — dedicated route in RootView/NavHost
- [x] iOS + Android: Send Money KYC guard — blocked screen with CTA to Profile if not APPROVED
- [x] iOS + Android: Dashboard KYC nudge banner (above balance card) — links to Profile
- [ ] iOS: `Features/Onboarding/KycUploadView.swift` — note: KYC *upload* UI is already built in `ProfileView.swift`; nothing additional needed here
- [ ] Android: same — KYC upload UI already exists in `ProfileScreen.kt` / `ProfileViewModel.kt`

#### Mobile Apps — iOS & Android

**iOS (SwiftUI) — `apps/ios/`** — ✅ Core features implemented

Already done:
- [x] Biometric auth: `BiometricAuthService.swift` — Face ID / Touch ID / OpticID via `LocalAuthentication`; preference stored in UserDefaults
- [x] Keychain token storage: `TokenStore.swift` — all tokens in Keychain with `.whenUnlockedThisDeviceOnly` (no iCloud backup)
- [x] APNs push: `PushNotificationHandler.swift` — requests permission, registers device token, POSTs to backend, shows foreground banners
- [x] Certificate pinning: `CertificatePinningDelegate.swift` — present
- [x] Haptic feedback: `HapticFeedback.swift` — present
- [x] STOMP WebSocket: `StompWebSocket.swift` — present with 5 s reconnect on error

Remaining gaps:
- [x] **Fixed**: `AuthStore.swift` JWT claim — now reads `aegispay_user_id` (Keycloak custom claim = AegisPay domain UUID)
- [x] **Fixed**: `StompWebSocket` exponential backoff — 5 s → 10 s → 20 s → 40 s → cap 60 s; reset on CONNECTED
- [x] **Done**: Onboarding registration flow + KYC send guard + Dashboard banner (see Onboarding section above)
- [ ] Deep links: add `apple-app-site-association` file to Gateway + `Info.plist` associated domains for payment confirmation deep links
- [ ] Widget extension: WidgetKit live activity for wallet balance (post KYC completion)

**Android (Jetpack Compose) — `apps/android/`** — ✅ Core features implemented

Already done:
- [x] Biometric: `BiometricAuthManager.kt` — `BiometricPrompt` with `BIOMETRIC_STRONG`, suspend API, availability checks
- [x] Encrypted token storage: `TokenStore.kt` — `EncryptedSharedPreferences` with AES256-SIV/GCM
- [x] FCM: `AegisFcmService.kt` — `onNewToken` POSTs to backend; `onMessageReceived` shows notification channel + badge update
- [x] STOMP WebSocket: `StompWebSocketClient.kt` — present

Remaining gaps:
- [x] **Fixed**: `StompWebSocketClient` — OkHttp `pingInterval(30s)` on dedicated wsClient + exponential backoff; resets on `onOpen`
- [x] **Fixed**: `AuthRepository.kt` JWT claim — `aegispay_user_id` instead of `sub`
- [x] **Done**: Onboarding registration flow + KYC send guard + Dashboard banner (see Onboarding section above)
- [ ] **Fix**: amount input in `SendMoneyScreen.kt` — set `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)`; currently `KeyboardType.Text` allows non-numeric characters
- [ ] **Fix**: currency formatter — use `Locale("en", "IN")` explicitly instead of `Locale.getDefault()` to keep INR formatting consistent on non-Indian devices
- [ ] Deep links: `assetlinks.json` hosted on Gateway + `AndroidManifest.xml` intent filters for `aegispay://` scheme
- [ ] Separate notification channels: `COMPLETED` (low priority, no sound) vs `FAILED` (high priority, distinct sound)

**Shared / Cross-Platform**
- [ ] Offline queue: queue initiated payments locally when offline; replay on reconnect with original idempotency key preserved
- [ ] E2E tests: Detox (iOS) + Maestro (Android) — happy-path, biometric, offline + reconnect flows

### Infrastructure

#### ~~GIT_DEPLOY_TOKEN~~ ✅ Done
- `${{ secrets.GIT_DEPLOY_TOKEN }}` is wired in all 4 CD workflows (`cd-dev`, `cd-staging`, `cd-prod`, `cd-onprem`)
- Secret must exist in GitHub repo settings (Fine-Grained PAT with `contents: write`)

#### Cloudflare Tunnel (On-Prem)
- `setup-cluster.sh` references it but no config committed
- Needed for on-prem external access without public IP

#### Keycloak Client Secret Rotation (GA)
- Local dev: `aegispay-backend-dev-secret` hardcoded (intentional)
- Prod GA: K8s Job to rotate via Keycloak Admin API → write to Vault

### Security

#### OpenRouter for On-Prem AI
- On-prem should use OpenRouter (cost-optimised) via `OPENROUTER_API_KEY` from Vault
- Spring AI config needs `springProfile: onprem` switching to OpenRouter base URL

---

---

## ⛔ PROD ONLY — Last Step: Live Transaction (Do This Last)

> **DO NOT attempt until every item above is checked off and verified in prod.**
> This section is intentionally placed last. It involves real money on the `main` branch.

### Prerequisites (must all be true before proceeding)

- [ ] All remaining backend tasks completed and deployed to prod via Argo CD
- [ ] All mobile fixes merged to `main`
- [ ] Rules Engine default rules seeded and verified (no more score=0 decisions)
- [ ] AI knowledge base seeded and RAG retrieval returning correct explanations
- [ ] Grafana dashboards showing clean metrics in prod (no DLQ depth, no saga timeouts)
- [ ] Live exchange rate streaming fixed and tested in staging first (see below)

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