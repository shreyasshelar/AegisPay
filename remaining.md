# AegisPay — Remaining / Planned Features

Tracked here: items deferred or in-progress. Fix before GA.

---

## How to Start Everything

```bash
# Stop everything
docker compose down
pkill -f "SNAPSHOT.jar"
pkill -f "next dev"

# Build (only after code changes)
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn install -f libs/pom.xml -N -q && \
  mvn clean install -DskipTests -q

# Start (infra + services + web)
./start-local.sh
```

`start-local.sh` does NOT run Maven. It only starts existing JARs.

---

## Service Ports

| Service | Port |
|---|---|
| API Gateway | 8080 |
| User Service | 8081 |
| Transaction Service | 8082 |
| Ledger Service | 8083 |
| Payment Orchestrator | 8084 |
| Risk Engine | 8085 |
| Notification Service | 8086 |
| Reconciliation Service | 8087 |
| Data Pipeline | 8089 |
| AI Platform | 8091 |
| Web App (Next.js) | 3000 |
| Keycloak | 8180 (admin/admin) |
| Kafka UI | 8090 |
| Superset | 8088 (admin/admin) |
| PostgreSQL | 5432 (aegispay/aegispay_dev) |

---

## Completed Fixes (Already in Code)

All 13 original start-up bugs from the first session are fixed (see git history). Summary:
- Lombok/MapStruct annotation processing order (`pom.xml`)
- `start-local.sh` — healthchecks, wave ordering, Java 21 path
- `docker-compose.yml` — Kafka/MongoDB/Keycloak/Superset healthchecks
- `libs/pom.xml` install step documented
- Ledger-service Flyway baseline-version:0
- Reconciliation batch schema init (Flyway)
- Transaction CHAR(3)→VARCHAR(3)
- Port conflict 8087 vs 8091
- Redis NOAUTH in ledger-service
- SecurityConfig for reconciliation-service
- LedgerServiceTest MapStruct instantiation
- next.config.ts → next.config.js
- `.js` extensions stripped from workspace packages

### End-to-End Bug Fixes (2026-05-11) ✅ Done

**1. Auth/Signout — cache not cleared on sign-out**
- `apps/web/components/sidebar.tsx`: added `queryClient.clear()` before `signOut()` so no authenticated data leaks to the next session on the same browser tab.

**2. API Client — success:false silently swallowed with HTTP 200**
- `packages/api-client/src/client/base.ts`: response interceptor now rejects with `ApiError` when `envelope.success === false`, even if HTTP status is 2xx. Previously the client set `res.data = undefined` and returned success, causing either silent data loss or a cryptic Zod parse error that hid the real backend message.

**3. Transaction Step 3 (Review → Confirm) failures**
- **`payerId` required in body** — `services/transaction-service/.../TransactionRequest.java`: removed `payerId` from the request DTO. `TransactionService.createNew()` now sets `payerId = userId` (the authenticated JWT subject). This also closes a payer-impersonation hole.
- **`note` never stored** — `TransactionRequest` now has an optional `String note` field. `createNew()` merges it into the metadata map so `TransactionMapper.toResponse()` extracts it correctly.
- **`BigDecimal amount` serialized as number, not string** — `TransactionResponse.amount` now carries `@JsonFormat(shape = STRING)`. Frontend Zod schema already expects `z.string()` for amount.
- **List endpoint returned too-slim DTO** — `GET /api/v1/transactions` previously returned `PagedResponse<TransactionStatusResponse>` (missing amount, currency, payeeId, initiatedAt, completedAt). Added `TransactionMapper.toListItemResponse(TransactionView)` and changed list return type to `PagedResponse<TransactionResponse>`. Frontend `TransactionSummarySchema` now gets all required fields.
- **`PagedTransactionsSchema.number` vs `PagedResponse.page`** — `packages/shared-types/src/transaction.ts`: renamed `number` to `page` in `PagedTransactionsSchema`. Updated `getNextPageParam` in `useInfiniteTransactions`.

---

## Backend

### Transaction List — Status/Date Filters Not Wired
- `GET /api/v1/transactions` accepts `page` and `size` only; `status`, `fromDate`, `toDate` params are sent by the frontend but silently ignored
- Fix: add `@RequestParam` for filters to the controller + `findByUserIdAndStatusAndInitiatedAtBetween(...)` to `TransactionViewRepository`

### Multi-Tenancy (PostgreSQL Row-Level Security)
- `tenantId` is already extracted from JWT claims and stored in `ActorContext`
- Missing: RLS policies on all tenant-scoped tables (`transactions`, `accounts`, `ledger_entries`, `sagas`, `risk_cases`)
- Pattern: `CREATE POLICY tenant_isolation ON transactions USING (tenant_id = current_setting('app.tenant_id'))`
- Required: set `app.tenant_id` on every DB connection via Hibernate interceptor

### k6 Load Tests (500 RPS Happy Path)
- Verify no double-spend under concurrent load
- Scenarios: concurrent `POST /api/v1/transactions` with same idempotency key, saga timeout under pressure
- Target: 500 RPS, p99 < 2s, zero double-spends, zero lost events
- Location: `tests/load/k6/`

### Stripe Radar Integration (Risk Feedback Loop)
- Currently: AegisPay risk score is computed internally, not sent to Stripe
- Goal: POST risk metadata back to Stripe via `PaymentIntent.metadata` so Stripe Radar rules can use it
- Also: consume Stripe Radar `radar.early_fraud_warning` webhooks to update risk cases

### ClickHouse High-Availability (2-shard / 2-replica)
- Current: single-node ClickHouse (docker-compose + Helm single StatefulSet)
- Plan: ClickHouse Keeper (ZooKeeper replacement) + 2-shard 2-replica cluster
- Affects: `infra/helm/infra/` — add ClickHouse StatefulSet + Keeper StatefulSet

### Spring Batch Schema Init for Reconciliation ✅ Done

---

## Frontend

### Superset Dashboard JSON Export
- Dashboards exist but not committed — requires manual recreation after Superset init
- Plan: export via Superset API (`GET /api/v1/assets/export`) and commit to `infra/superset/dashboards/`

### iOS App (SwiftUI)
- Exists at `apps/ios/` — requires Xcode 15+ to build
- Remaining: biometric (Face ID / Touch ID) auth flow, push notification registration, deep links

### Android App (Jetpack Compose)
- Exists at `apps/android/` — requires Android Studio Hedgehog+ to build
- Remaining: biometric (Fingerprint) auth flow, FCM push token registration, deep links

---

## Infrastructure

### GIT_DEPLOY_TOKEN Documentation
- CD workflows reference `secrets.GIT_DEPLOY_TOKEN` (used to push tag commits back to repo)
- Required: create a GitHub Fine-Grained PAT with `contents: write` on this repo
- Store as: GitHub repo secret `GIT_DEPLOY_TOKEN`

### SMS Notifications via Fast2SMS ✅ Done

### Domain Configuration (On-Prem) ✅ Done
- Domain: `shreyasshelar.uk` → base subdomain `aegispay.shreyasshelar.uk`
- When running setup-cluster.sh: `export DOMAIN=shreyasshelar.uk && bash infra/k3s/setup-cluster.sh`

### Cloudflare Tunnel Setup
- Referenced in `setup-cluster.sh` step 3 ("Configure Cloudflare Tunnel")
- No config files committed yet — needed for on-prem exposure without public IP

### Image Tag Promotion Strategy (Prod / Staging)
- Currently: values-prod.yaml and values-staging.yaml use `latest`, CI patches at deploy time via `yq`
- Low priority — current CI patching pattern works correctly

---

## Security

### Keycloak Backend Client Secret (GA task)
- Local dev: `aegispay-backend-dev-secret` hardcoded in realm-export.json — intentional
- For K8s prod: add post-deploy Kubernetes Job to rotate via `PUT /admin/realms/aegispay/clients/{id}/client-secret` and write to Vault

### ANTHROPIC_API_KEY for AI Platform (On-Prem)
- On-prem uses OpenRouter as cost-optimised proxy (set `OPENROUTER_API_KEY` in Vault)
- Cloud/prod uses Anthropic directly (set `ANTHROPIC_API_KEY` in Vault)
- Currently: placeholder in application.yml; no OpenRouter routing logic in Spring AI config

---

*Last updated: 2026-05-11*
