# AegisPay — Compromised Features (Need Future Fix)

This document tracks features that were disabled, simplified, or deferred during the
initial GCP K3s deployment. Each item must be revisited before the platform is
considered production-grade.

---

## 🚨 Critical — Fix Before Production

### 1. Grafana Alert Rules Removed
**Status**: Disabled  
**Files**: `infra/grafana/provisioning/alerting/aegispay-rules.yaml`  
**Why disabled**: ClickHouse-based alert queries using `{From:0s To:0s}` time-range syntax
caused Grafana 10.4 to crash on startup (CrashLoopBackOff).  
**Fix needed**: Rewrite alert rules using valid Grafana unified alerting query syntax.
Re-add via Grafana UI (Alerting → Alert rules) or fix YAML time-range format.  
**Impact**: No automated alerts for SagaTimeout, DlqDepth, BalanceNegative, etc.

### 2. Grafana Slack Alerting Disabled
**Status**: Disabled  
**Files**: `infra/grafana/provisioning/alerting/aegispay-rules.yaml`  
**Why disabled**: Grafana 10.4 rejects `${ENV_VAR}` syntax in webhook URLs at parse
time (before env substitution occurs). Using a hardcoded URL would expose the webhook.  
**Fix needed**: Inject the Slack webhook URL as a Kubernetes secret and mount as env var
inside the Grafana pod, then configure via Grafana API/UI rather than provisioning YAML.  
Alternatively upgrade to Grafana 11 which supports secret refs in alerting provisioning.  
**Impact**: No Slack notifications on payment failures or infrastructure alerts.

### 3. CI Integration Tests Skipped
**Status**: All services built with `-DskipTests`  
**Files**: `.github/workflows/ci-java.yml`  
**Why disabled**: Integration tests require live Postgres, Kafka, Redis (via Testcontainers).
GitHub Actions runners don't have Docker-in-Docker configured for Testcontainers.  
**Fix needed**: Add `services:` block in CI for postgres/kafka/redis, or configure
Testcontainers Cloud, or use `@SpringBootTest` with `@DirtiesContext` and H2/embedded.  
**Impact**: Zero test coverage on CI. Regressions in DB queries, Kafka consumers,
and Saga flows will only be caught in production.

---

## ⚠️ Medium — Fix Within 2 Weeks

### 4. Helm Lint Failures in CI (Non-Blocking)
**Status**: Helm lint step fails but doesn't block image builds  
**Files**: `.github/workflows/ci-java.yml`, `infra/helm/aegispay/`  
**Why**: Helm chart has lint warnings (likely missing required values, unused templates).  
**Fix needed**: Run `helm lint infra/helm/aegispay -f values.yaml -f values-dev.yaml`
locally, fix all warnings, and make the lint step a blocking gate in CI.

### 5. Keycloak Secret Rotation — Manual GCP Update Required
**Status**: Partial — rotation job removed Vault write, now logs only  
**Files**: `infra/helm/infra/templates/keycloak-secret-rotation-job.yaml`  
**Why**: The rotation job regenerates the Keycloak client secret but cannot write it back
to GCP Secret Manager without a Workload Identity + IAM binding for the rotation SA.  
**Fix needed**:
1. Create `keycloak-rotation-sa` GCP Service Account with `secretmanager.versions.add` IAM role.
2. Bind via Workload Identity: `iam.gke.io/gcp-service-account` annotation on K8s SA.
3. Update rotation script to use `gcloud secrets versions add` instead of Vault write.

### 6. ArgoCD Per-Service ApplicationSet (Optional Enhancement)
**Status**: Single monolithic `aegispay-gcp` ArgoCD Application  
**Current behaviour**: ArgoCD already does smart resource-level sync — only changed
Deployments are updated. Other pods are NOT restarted.  
**Enhancement**: Create an ApplicationSet that generates one ArgoCD Application per
service so each service has independent sync history, rollback, and health status.  
**Fix**: Create `infra/argocd/applicationset-per-service.yaml` using list generator
with one entry per service pointing to `infra/helm/aegispay/templates/<service>/`.

---

## 🔵 Low — Nice to Have

### 7. SMTP From Address Not Configured
**Files**: `infra/helm/aegispay/values-dev.yaml` (smtp.fromAddress = "")  
**Fix needed**: Set `smtp.fromAddress` to `aegispay.dev@gmail.com` and populate
username from ESO secret `aegispay-smtp-secret`.

### 8. api-gateway Docker Image May Need Retrigger
**Status**: Transient 504 from GHCR during CI run  
**Fix**: Verify `ghcr.io/shreyasshelar/aegispay/api-gateway:latest` exists.
If not, manually trigger CI via `workflow_dispatch` with `force_all: true`.

### 9. values-dev.yaml `secrets.useVault: true` Stale Flag
**Files**: `infra/helm/aegispay/values-dev.yaml`  
**Issue**: `secrets.useVault: true` still set, but Vault is not deployed. ESO
reads directly from GCP Secret Manager. This flag may confuse future maintainers.  
**Fix**: Set `useVault: false`, add `useGcpSecretManager: true`.

---

## ✅ Already Fixed (Context)

| Issue | Fix Applied |
|-------|------------|
| Vault annotations in keycloak-secret-rotation-job | Removed — ESO secret used directly |
| Grafana ops email was sshelar110.ss3 | Fixed to aegispay.dev@gmail.com |
| ai-platform OPENROUTER_API_KEY wrong key | Fixed in values.yaml |
| payment-orchestrator stripe webhook secret ref | Fixed in values.yaml |
| Tomcat /tmp read-only crash | emptyDir at /tmp added to all pods |
| Liveness probe killing during slow startup | TCP socket probe, 180s initial delay |
| Spring Boot config crash (optional file) | `optional:` prefix on config location |
| CI service builds failing (missing parent POMs) | Added `mvn install -N` steps |
| Docker build context wrong for services | Fixed to `context: services/<name>` |
| Co-Authored-By lines in git history | Stripped via git-filter-repo |
