# AegisPay — Platform Status & Fix Backlog

> **Goal**: GCP K3s cluster = dev environment, live at `*.shreyasshelar.uk`.
> **Timeline**: Everything before tomorrow night (2026-06-03).
> **Git rule**: every commit → `shreyasshelar` / `shreyasshelarrr@gmail.com`
> **Config rule**: all project emails → `aegispay.dev@gmail.com`

---

## Current State (as of 2026-06-02)

| Component | Status | URL |
|-----------|--------|-----|
| api-gateway | ✅ Live | `aegispay-api.shreyasshelar.uk` → 401 JWT required |
| user-service | ✅ Running | internal |
| transaction-service | ✅ Running | internal |
| ledger-service | ✅ Running | internal |
| payment-orchestrator | ✅ Running | internal, Stripe test mode |
| risk-engine | ✅ Running | internal |
| notification-service | ✅ Running | internal |
| data-pipeline | ✅ Running | internal |
| reconciliation-service | ✅ Running | internal |
| ai-platform | ✅ Running | internal, pgvector baked in |
| grafana | ✅ Live | `aegispay-grafana.shreyasshelar.uk` → 200 |
| keycloak | ✅ Live | `aegispay-keycloak.shreyasshelar.uk` → 200 |
| kafka-ui | ✅ Live | `aegispay-kafka.shreyasshelar.uk` → 200 |
| cloudflared | ✅ Running | 4 QUIC connections |
| web (Next.js) | 🔄 CI building | Image building — will deploy once CI completes |
| ArgoCD | ✅ Fixed | Watches `dev` branch, ApplyOutOfSyncOnly |
| CI/CD | ✅ Fixed | cd-dev.yml correct name + git identity |

---

## P0 — Must fix to go live (in order)

### ~~P0-1~~ ✅ ArgoCD branch — FIXED
**File**: `infra/argocd/app-gcp.yaml`
**Problem**: `targetRevision: main` but GCP K3s is the dev environment. All our
deployment fixes live on `main` and need to be on `dev` too.
**Steps**:
1. Merge `main` → `dev` (33 commits of fixes currently only on main)
2. Change `app-gcp.yaml` `targetRevision: main` → `dev`
3. Add `ApplyOutOfSyncOnly=true` syncOption so ArgoCD only touches changed resources
4. Add `ignoreDifferences` for Ingress (no ADDRESS = forever unhealthy on k3s+Cloudflare)
**Impact without fix**: ArgoCD syncs wrong branch; dev deploys go nowhere.

### ~~P0-2~~ ✅ cd-dev.yml — FIXED
**File**: `.github/workflows/cd-dev.yml`
**Problem**:
- `workflow_run: workflows: ["CI — Build & Test"]` → wrong name, CI is `CI — Java (Smart)`
  → CD never triggers; dev Docker images never get built/pushed
- Git config `aegispay-ci[bot]` / `aegispay-ci@users.noreply.github.com` → must be
  `shreyasshelar` / `shreyasshelarrr@gmail.com`
- Image tag format in yq command uses `services.<key>` but `web` key not in values-dev.yaml
**Impact without fix**: No automated CI/CD to dev. Images only `latest` (manually built).

### ~~P0-3~~ ✅ pgvector persistence — FIXED
**File**: `infra/helm/infra/templates/postgresql/statefulset.yaml`
**Problem**: Using `postgres:16-alpine` which has no pgvector. pgvector was manually
compiled and installed inside the running pod — it disappears on any pod restart or
VM stop/start.
**Fix**: Change image to `pgvector/pgvector:pg16` (official, DockerHub, free).
**Risk**: Changing the StatefulSet image tag causes a rolling restart of PostgreSQL.
PVC data is preserved; Flyway migrations are idempotent (won't re-run).
**Impact without fix**: ai-platform fails with "extension vector does not exist" on restart.

### ~~P0-4~~ ✅ Grafana — LIVE (port 3100, HTTP 200)
`grafana.port: 3000` in values-dev.yaml; K8s Service + GF_SERVER_HTTP_PORT both 3000.
Grafana responds HTTP 200 on `grafana.aegispay.svc.cluster.local:3000`.
`aegispay-grafana.shreyasshelar.uk` should now return Grafana UI.

### ~~P0-5~~ ✅ Keycloak — LIVE (HTTP 200 via Cloudflare)
Added `keycloak` ClusterIP Service in `aegispay-infra` namespace on port 8080 (selector: app=keycloak).
Cloudflare tunnel target `keycloak.aegispay-infra.svc.cluster.local:8080` now routes correctly.
Keycloak responds HTTP 200. `aegispay-keycloak.shreyasshelar.uk` should now load.

### ~~P0-6~~ ✅ CI GitHub Actions — FIXED
**Files**: `.github/workflows/ci-java.yml`, `ci-web.yml`
**Known failures**:
- `helm-lint` fails because `helm lint` is run without value files
  → Fix: `helm lint infra/helm/aegispay -f values.yaml -f values-dev.yaml`
- `ci-web.yml` unit-tests fail because no `test` script defined in web workspace
  → Fix: add or skip gracefully
- `ci-web.yml` Docker push only on `main` — dev never gets web images
  → Fix: add dev branch Docker push to ci-web.yml
- `security-scan.yml` `codeql` runs on all pushes to main including `[skip ci]` commits
  → Fix: add `[skip ci]` filter
**Impact without fix**: Every push shows red CI. Blocks PR merges. No dev images built.

### ~~P0-7~~ ✅ `main` branch upstream tracking — FIXED
`git branch --set-upstream-to=origin/main main` applied. Both main and origin/main
at same SHA. CI pushes will resolve correctly.

---

## P1 — Fix within 1 week (stability)

### P1-1 · web (Next.js) — 🔄 CI BUILDING (image push pending)
**Status**: Dockerfile, Helm templates, ESO secret, K8s secret all created. CI building image.
**Fix needed**:
1. Add `apps/web/Dockerfile` (multi-stage Next.js build)
2. Add `infra/helm/aegispay/templates/web/` deployment + service + networkpolicy
3. Add `services.web` to `values.yaml` and `values-dev.yaml`
4. Fix `ci-web.yml` to push `web:dev-latest` on dev branch
5. Fix `cd-dev.yml` to include `web` in image tag update loop
**Impact**: `aegispay.shreyasshelar.uk` returns 502 (no pod).

### P1-2 · ArgoCD stuck in Running/OutOfSync (Ingress health)
**Problem**: ArgoCD waits for ALL resources healthy. Ingress resources never get
an `ADDRESS` on k3s + Cloudflare (no LoadBalancer, traffic comes from cloudflared pod).
ArgoCD marks Ingress as `Progressing` indefinitely → entire sync stays `Running`.
**Fix**: Add to `app-gcp.yaml`:
```yaml
ignoreDifferences:
  - group: networking.k8s.io
    kind: Ingress
    jsonPointers:
      - /status
```
And add `syncOptions: - RespectIgnoreDifferences=true` + `ApplyOutOfSyncOnly=true`

### P1-3 · GitHub contributor pollution (@claude, @sshelar110ss3-ship-it)
**Problem**: Two bot identities appear in GitHub contributors:
- `claude/` branches (worktrees created by Claude Code) may have been pushed to remote
- `sshelar110ss3-ship-it` is a GitHub Actions bot from old CD commits
**Fix**:
1. Delete remote `claude/` branches: `git push origin --delete claude/tender-wozniak-205b4d claude/zen-gauss-cfbbed`
2. Fix cd-dev.yml and cd-prod.yml git config to use `shreyasshelar`/`shreyasshelarrr@gmail.com`
   (GitHub attributes commits to the email, not the display name)
3. For `sshelar110ss3-ship-it`: find the commit(s) with that author and rewrite history
   on dev/main using git-filter-repo if needed.
**Impact**: GitHub shows wrong contributors; looks unprofessional on portfolio.

### P1-4 · GHCR package bloat — old tags accumulate
**Problem**: Every CI run pushes a new SHA-tagged image. GHCR free tier has 500MB limit.
Hundreds of old tags waste storage.
**Fix**: Add `.github/workflows/ghcr-prune.yml` that runs weekly and deletes all
non-`latest` / non-`dev-latest` package versions older than 30 days using
`gh api` or `actions/delete-package-versions`.

### P1-5 · Keycloak realm re-imported on every restart (data loss risk)
**Problem**: Keycloak starts with `--import-realm` flag. This re-imports the realm
from ConfigMap on EVERY pod restart. Any changes made via Keycloak UI (new clients,
user attributes, etc.) are overwritten.
**Fix**: Remove `--import-realm` from args after first successful start, OR use
`KC_IMPORT_STRATEGY=IGNORE_EXISTING` env var to make import idempotent.

### P1-6 · `secrets.useVault: true` stale flag in values-dev.yaml
**File**: `infra/helm/aegispay/values-dev.yaml`
**Problem**: Vault is not deployed. This flag misleads future maintainers.
**Fix**: `useVault: false`, add `useGcpSecretManager: true`.

### P1-7 · SMTP from address not configured
**File**: `infra/helm/aegispay/values-dev.yaml` — `smtp.fromAddress: ""`
**Fix**: Set to `aegispay.dev@gmail.com`; wire username from ESO secret `aegispay-smtp-secret`.

---

## P2 — After stable go-live (maturity)

### P2-1 · CI integration tests completely skipped (-DskipTests)
**Problem**: Zero test coverage on CI. All services built with `-DskipTests`.
**Fix**: Add `services:` block in CI workflow for postgres + kafka + redis, or
use Testcontainers Cloud. Run `mvn test` instead of `package -DskipTests`.
**Note**: Do NOT add tests until app is stable and deployed — fixing CI is P0 but
enabling tests is P2 since Testcontainers requires Docker-in-Docker setup.

### P2-2 · Grafana alert rules disabled (ClickHouse query format)
**Problem**: Alert rules removed because Grafana 10.4 rejected `{From:0s To:0s}` time range.
**Fix**: Rewrite rules using valid Grafana unified alerting syntax with proper `for:` duration.
Re-add via Grafana UI → Export as JSON → store in `infra/grafana/dashboards/`.
**Alerts needed**: SagaTimeoutRateHigh, DlqDepthNonZero, BalanceNegative,
NotificationDeliveryFailureHigh, ReconciliationBreakCountHigh.

### P2-3 · Grafana Slack alerting disabled
**Problem**: `${SLACK_WEBHOOK_URL}` env var syntax rejected at parse time by Grafana 10.4.
**Fix**: Upgrade to Grafana 11+ which supports secret refs, OR configure contact
point via Grafana API (not provisioning YAML), OR inject pre-expanded URL from ESO secret.

### P2-4 · ArgoCD per-service ApplicationSet
**Current**: Single monolithic `aegispay-gcp` ArgoCD Application syncs all 10+ services.
ArgoCD IS smart enough to only restart changed Deployments — but all services share
one sync history, one rollback point, one health status.
**Enhancement**: Create `infra/argocd/applicationset-per-service.yaml` using list
generator. Each service gets own Application: independent rollback, independent health,
independent sync trigger.

### P2-5 · Keycloak secret rotation requires Workload Identity
**Current**: Rotation job only reads the secret, cannot write new version back to GCP SM.
**Fix**:
1. Create `keycloak-rotation-sa@<project>.iam.gserviceaccount.com` with `secretmanager.versions.add`
2. Bind via Workload Identity to `aegispay-infra/keycloak-rotation-sa` K8s SA
3. Update rotation script: `gcloud secrets versions add aegispay-keycloak-secret --data-file=-`

### P2-6 · Dependabot PRs accumulating (80+ open)
**Problem**: Dependabot has opened 80+ PRs across services. Most are safe minor/patch bumps.
**Fix**: Configure `dependabot.yml` with `auto-merge: true` for patch-level updates.
Enable GitHub's auto-merge on PRs that pass CI. Group related dependabot PRs together.

---

## P3 — Future / Nice-to-have

### P3-1 · Security scan (OWASP, Trivy, CodeQL) on every PR
**Current**: `security-scan.yml` runs on push to main only.
**Fix**: Add PR trigger. Add SARIF upload to GitHub Security tab.
Enable Dependabot security alerts + auto-dismiss low-severity.

### P3-2 · Horizontal Pod Autoscaler (HPA) for gateway + risk-engine
**Current**: All services run at fixed 1 replica.
**Fix**: Add HPA to api-gateway (CPU 70%) and risk-engine (CPU 80%).
Requires metrics-server (already present in k3s).

### P3-3 · PodDisruptionBudget for critical services
**Fix**: Add PDB (`minAvailable: 1`) to api-gateway, ledger-service, transaction-service.

### P3-4 · Network policies for remaining services
**Current**: Only api-gateway has a NetworkPolicy. Other services are open.
**Fix**: Add egress-only NetworkPolicy per service limiting traffic to declared dependencies.

### P3-5 · Vault for production secret management
**Current**: GCP Secret Manager + ESO for dev. Vault stubs still in codebase.
**Fix**: For prod (main branch), decide: keep GCP SM or deploy Vault in prod cluster.
Remove all Vault stub code that currently misleads readers.

---

## Disabled Features Tracker

| Feature | Status | Disabled in | Re-enable in |
|---------|--------|------------|-------------|
| Grafana alert rules | ❌ Disabled | `files/alerting/aegispay-rules.yaml` | P2-2 |
| Grafana Slack contact point | ❌ Disabled | Same file | P2-3 |
| Integration tests | ❌ Skipped | `ci-java.yml` (-DskipTests) | P2-1 |
| Web frontend | ❌ Not deployed | No image / no Helm template | P1-1 |
| Keycloak secret rotation write | ⚠️ Read-only | `keycloak-secret-rotation-job.yaml` | P2-5 |
| SMTP / email notifications | ⚠️ Unconfigured | `values-dev.yaml` smtp section | P1-7 |
| Security scanning on PRs | ⚠️ Main-only | `security-scan.yml` | P3-1 |
| Dependabot auto-merge | ⚠️ Manual | GitHub settings | P2-6 |
| HPA / autoscaling | ❌ Not configured | Helm chart | P3-2 |
| PodDisruptionBudget | ❌ Not configured | Helm chart | P3-3 |
| Vault integration | 🗑️ Removed (correct) | All Helm templates | P3-5 |

---

## ✅ Fixed (historical)

| Issue | Fix Applied | Commit |
|-------|------------|--------|
| pgvector missing (ephemeral install) | `pgvector/pgvector:pg16` image in statefulset | 7afe5b5 |
| Vault in keycloak-secret-rotation-job | Removed; ESO secret used directly | c58ef47 |
| Grafana ops email sshelar110.ss3 | Fixed to aegispay.dev@gmail.com | 3e8bd07 |
| Grafana Slack receiver CrashLoop | Removed Slack receiver from provisioning YAML | bca9bbc |
| ai-platform DeploymentHistoryTool NPE | null-safe projectRoot + SPRING_APPLICATION_JSON | 2f00a25 |
| payment-orchestrator wrong DB name | configmap uses values db.name | 373ba1f |
| reconciliation init container stuck | wget --spider on /readiness endpoint | 6001143 |
| reconciliation OOMKilled | Increased to 512Mi limit | ff0d778 |
| api-gateway Service port 80 vs 8080 | Changed to use .Values port (8080) | e9f750a |
| api-gateway NetworkPolicy blocking cloudflared | Added cloudflared + kube-system ingress rules | e9f750a |
| MongoDB "Database name empty" | Fixed duplicate global block + auth URI | 373ba1f |
| Tomcat /tmp read-only crash | emptyDir at /tmp for all pods | 6fb9075 |
| Liveness probe killing slow startup | TCP socket probe, 180s initialDelay | 539dd7b |
| Spring Boot optional config crash | `optional:` prefix on config location | d33646d |
| CI missing parent POM install | `mvn install -N` step added | cb525cb |
| Docker build context wrong | Fixed to `context: services/<name>` | 2beb517 |
| All images ImagePullBackOff after filter-repo | Changed all tags to `latest` | 373ba1f |
| Co-Authored-By Claude in git history | Stripped via git-filter-repo | (history rewrite) |
