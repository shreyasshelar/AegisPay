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
| web (Next.js) | 🔄 CI building | Image building (turbo env fix in progress) |
| ArgoCD | ✅ Fixed | Watches `dev` branch, ApplyOutOfSyncOnly + RespectIgnoreDifferences |
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

### ~~P1-1~~ ✅ web (Next.js) — LIVE
`aegispay.shreyasshelar.uk` → 200. Web pod `1/1 Running`.
KC_HOSTNAME + KEYCLOAK_ISSUER fixed to use public URL `aegispay-keycloak.shreyasshelar.uk`.
OAUTH2_JWK_SET_URI aligned in all 7 Spring service configmaps (was using wrong env var name).

### ~~P1-2~~ ✅ ArgoCD stuck in Running/OutOfSync — FIXED
`RespectIgnoreDifferences=true` added to syncOptions in `infra/argocd/app-gcp.yaml`.
Combined with the existing `ignoreDifferences` block for Ingress `/status/loadBalancer`,
ArgoCD no longer marks syncs as OutOfSync due to missing Ingress ADDRESS.
Applied on cluster: `kubectl apply -f infra/argocd/app-gcp.yaml` (commit 8e13f6c).

### ~~P1-3~~ ⚠️ GitHub contributor pollution — USER ACTION NEEDED
Go to `github.com/shreyasshelar/AegisPay/settings/installations` and revoke
`Claude Code` and `sshelar110ss3-ship-it` GitHub Apps. All git commit authors
verified clean (shreyasshelar / dependabot / github-actions only).

### ~~(was P1-3)~~
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

### ~~P1-4~~ ✅ GHCR package bloat — FIXED
`.github/workflows/ghcr-prune.yml` added. Runs weekly (Monday 03:00 UTC) and
on `workflow_dispatch`. Keeps `latest`, `dev-latest`, and last 7 versions per package.
Covers all 11 packages: api-gateway, user-service, transaction-service, ledger-service,
payment-orchestrator, risk-engine, notification-service, reconciliation-service,
data-pipeline, ai-platform, web (commit 7afe5b5).

### ~~P1-5~~ ✅ Keycloak realm re-import on restart — FIXED
Added `KC_IMPORT_STRATEGY=IGNORE_EXISTING` env var to Keycloak deployment in
`infra/helm/infra/templates/keycloak/deployment.yaml`. Realm ConfigMap still mounted
for cold-start imports, but existing realm objects (clients, users, roles) are NOT
overwritten on pod restart. (commit 8e13f6c)

### ~~P1-6~~ ✅ `secrets.useVault: true` stale flag — FIXED
`useVault: false`, `useGcpSecretManager: true` set in `values-dev.yaml`. (commit 7afe5b5)

### ~~P1-7~~ ✅ SMTP from address — FIXED
`global.smtp.fromAddress: "aegispay.dev@gmail.com"` and `username: "aegispay.dev@gmail.com"`
set in `values-dev.yaml`. Notification service ConfigMap reads from
`global.smtp.fromAddress`. SMTP password still needs GCP SM secret
`aegispay-smtp-secret` — create it with your Gmail app password when ready to
enable email notifications (low priority until post-launch). (commit 8e13f6c)

---

## P2 — After stable go-live (maturity)

### P2-1 · CI integration tests completely skipped (-DskipTests)
**Problem**: Zero test coverage on CI. All services built with `-DskipTests`.
**Fix**: Add `services:` block in CI workflow for postgres + kafka + redis, or
use Testcontainers Cloud. Run `mvn test` instead of `package -DskipTests`.
**Note**: Do NOT add tests until app is stable and deployed — fixing CI is P0 but
enabling tests is P2 since Testcontainers requires Docker-in-Docker setup.

### ~~P2-2~~ ✅ Grafana alert rules — ACTIVE
PrometheusRules `aegispay-alerts` live in `aegispay` namespace. kube-prometheus-stack
Prometheus picks them up (`ruleNamespaceSelector: {}`). 9 rule groups active:
saga, kafka, ledger, risk, ai, notification, datapipeline, reconciliation, service.
ClickHouse-based Grafana unified alerting deferred until ClickHouse deployed on dev.

### ~~P2-3~~ ✅ Grafana Slack alerting — RESTORED
Re-added Slack contact point in `files/alerting/aegispay-rules.yaml`. `${SLACK_WEBHOOK_URL}`
injected from `aegispay-slack-secret` (ESO → GCP SM). Routing: `severity=critical` → Slack
+ email; `team=finance` → email (30m repeat); all others → email (4h repeat).

### ~~P2-4~~ ✅ ArgoCD per-service ApplicationSet — CREATED
`infra/argocd/applicationset-per-service.yaml` created. Generates 12 Applications (1 per
service + 1 shared infra app). Each uses Helm `parameters` to enable only that service.
**To activate**: `kubectl apply -f infra/argocd/applicationset-per-service.yaml` then
delete `aegispay-gcp` once all per-service apps are Healthy.

### ~~P2-5~~ ✅ Keycloak secret rotation write-back — FIXED
Rotation CronJob now uses the **GCE instance metadata server** (no gcloud CLI, no key file)
to get an access token, then calls the GCP Secret Manager REST API to add a new secret version.
No Workload Identity or GKE-specific setup required — works on any GCE VM.
**One-time manual step (run once on the VM):**
```bash
VM_SA=$(gcloud compute instances describe aegispay-k3s \
  --zone=us-central1-a --project=aegispay \
  --format="value(serviceAccounts[0].email)")
gcloud secrets add-iam-policy-binding aegispay-keycloak-web-client-secret \
  --member="serviceAccount:${VM_SA}" \
  --role="roles/secretmanager.secretVersionManager" \
  --project=aegispay
```
After the CronJob rotates the secret, ESO picks up the new version within 1h (its `refreshInterval`).

### ~~P2-6~~ ✅ Dependabot auto-merge — FIXED
Added grouping (Spring/patch groups per service) and `dependabot-auto-merge.yml`
workflow that auto-approves+squash-merges patch/minor PRs, labels major bumps
`needs-review`. npm moved to root directory to cover all workspaces.
`needs-review` label created idempotently inside the workflow step (`gh label create || true`)
so it never fails on first run.
**Action needed**: Enable "Allow auto-merge" in repo Settings → General.

---

## P3 — Future / Nice-to-have

### ~~P3-1~~ ✅ Security scan on PRs — FIXED
`security-scan.yml` now triggers on PRs targeting `dev` (for Java/lib changes) in
addition to the weekly schedule. Trivy image scan changed from main-only to
schedule/dispatch. OWASP + CodeQL run on every PR. Trivy SARIF uploaded to GitHub
Security tab on scheduled/manual runs.

### ~~P3-2~~ ✅ HPA templates — IN PLACE (intentionally disabled on single-node dev)
HPA templates exist for all 11 services (including `web`) using `aegispay.serviceHpa` helper.
All `autoscaling.enabled: false` in `values-dev.yaml` — correct for single GCE VM
(HPA can't improve availability when all pods land on the same node).
Templates are ready: enable per-service by setting `autoscaling.enabled: true` when
running on multi-node prod. metrics-server already present in k3s.

### ~~P3-3~~ ✅ PodDisruptionBudget — ALREADY IN PLACE
PDB templates exist for all 11 services (including `web`, added now) using `aegispay.servicePdb`
helper (`minAvailable: 1`). Always rendered when the service is enabled.
Note: On single-node k3s, PDB protects against accidental deletion but cannot prevent
node-level failures (only one node to schedule on).

### ~~P3-4~~ ✅ Network policies — COMPLETE (all 11 services)
NetworkPolicy exists for all 11 services. 9 services use the `aegispay.serviceNetworkPolicy`
helper (ingress from api-gateway + same namespace; allow-all egress for dev).
api-gateway has its own custom policy (cloudflared, kube-system, monitoring ingress; explicit
egress ports to each downstream service + Redis + Keycloak).
`web` NetworkPolicy added now: ingress from cloudflared/kube-system/monitoring;
egress to api-gateway (8080), Keycloak (8080 infra), HTTPS (443), DNS (53).

### P3-5 · Vault for production secret management
**Current**: GCP Secret Manager + ESO for dev. Vault stubs still in codebase.
**Fix**: For prod (main branch), decide: keep GCP SM or deploy Vault in prod cluster.
Remove all Vault stub code that currently misleads readers.

### P3-6 · No image-build CI for main branch (prod pipeline prerequisite)
**Current**: Only `cd-dev.yml` builds Docker images, always tagged `dev-<sha>`.
`cd-prod.yml` (manual-only, disabled) expects images tagged `prod-<sha>` in GHCR.
No workflow on `main` builds `prod-<sha>` images — prod deploys cannot happen yet.
**Fix**: Create `.github/workflows/ci-java-prod.yml` that triggers on push to `main`
(or on a release tag), builds all services, pushes `prod-<sha>` images to GHCR.
Only then uncomment the `workflow_run` trigger in `cd-prod.yml`.
**Isolation guarantee already in place**: `cd-dev.yml` → `dev-<sha>` → `values-dev.yaml`;
`cd-prod.yml` → `prod-<sha>` → `values-prod.yaml`. Tags and value files never overlap.
**Note**: `cd-gcp.yml` deleted — it was checking out `main` and writing to `values-dev.yaml`
(exact dev→prod contamination vector). `cd-dev.yml` is now the sole GCP deploy pipeline.

---

## Disabled Features Tracker

| Feature | Status | Disabled in | Re-enable in |
|---------|--------|------------|-------------|
| Grafana alert rules | ❌ Disabled | `files/alerting/aegispay-rules.yaml` | P2-2 |
| Grafana Slack contact point | ❌ Disabled | Same file | P2-3 |
| Integration tests | ❌ Skipped | `ci-java.yml` (-DskipTests) | P2-1 |
| Web frontend | 🔄 CI building | Dockerfile + Helm done; image building | P1-1 |
| Keycloak secret rotation write | ✅ Fixed — GCE metadata token + SM REST API | `keycloak-secret-rotation-job.yaml` | P2-5 |
| SMTP / email notifications | ⚠️ Address set, password needed | `values-dev.yaml` smtp section fixed | P1-7 → P2 |
| Security scanning on PRs | ⚠️ Main-only | `security-scan.yml` | P3-1 |
| Dependabot auto-merge | ⚠️ Manual | GitHub settings | P2-6 |
| HPA / autoscaling | ✅ Templates ready, disabled on single-node dev | `values-dev.yaml` (autoscaling.enabled: false) | P3-2 |
| PodDisruptionBudget | ✅ All 11 services have PDB (minAvailable: 1) | Helm templates | P3-3 |
| Vault integration | 🗑️ Removed (correct) | All Helm templates | P3-5 |

---

## ✅ Fixed (historical)

| Issue | Fix Applied | Commit |
|-------|------------|--------|
| root package.json missing packageManager | Added `"packageManager": "npm@10.9.2"` (turbo v2 requirement) | 5be29a4 |
| turbo v2 strict env mode blocks NEXTAUTH_SECRET | Added globalPassThroughEnv in turbo.json | 8e13f6c |
| ArgoCD Ingress OutOfSync blocks sync | Added RespectIgnoreDifferences=true to syncOptions | 8e13f6c |
| Keycloak realm re-imported on restart | KC_IMPORT_STRATEGY=IGNORE_EXISTING in deployment | 8e13f6c |
| SMTP fromAddress empty | global.smtp.fromAddress/username set to aegispay.dev@gmail.com | 8e13f6c |
| kafka-ui ImagePullBackOff (provectus repo gone) | Changed to ghcr.io/kafbat/kafka-ui:latest | beb7a1f |
| web: no Dockerfile or Helm templates | Multi-stage Dockerfile + Helm deployment/service/configmap | beb7a1f |
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
| Dependabot label crash (`needs-review` missing) | `gh label create \|\| true` added to workflow step | — |
| `cd-gcp.yml` cross-contamination (checkout main, write values-dev.yaml) | Deleted — `cd-dev.yml` is sole dev deploy pipeline | — |
| `cd-prod.yml` image tag collision (bare `${SHA}` shared with dev) | Prod images now explicitly tagged `prod-<sha>`; dev uses `dev-<sha>` | — |
| Keycloak rotation no write-back | GCE metadata token + SM REST API in rotation script | — |
| web service missing NetworkPolicy / PDB / HPA | Added all three templates for web service | — |
