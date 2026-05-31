# AegisPay — GCP Free-Tier Deployment Guide

**Domain:** `aegispay.shreyasshelar.uk`  
**ArgoCD:** `argocd.aegispay.shreyasshelar.uk`  
**Budget:** $300 free credits / 90 days (~$215 estimated, ~$85 buffer)  
**VM schedule:** 8 AM – midnight IST (16 h/day) to save 33% compute cost

---

## Budget Summary

| Item | Cost |
|---|---|
| e2-standard-4 (4 vCPU, 16 GB) × 16 h/day × 90 days | ~$193 |
| 100 GB pd-standard disk (always on, even when VM stopped) | ~$12 |
| Networking / egress | ~$5–10 |
| **Total estimate** | **~$210–215** |
| **Buffer remaining** | **~$85** |

---

## Prerequisites (on your laptop)

Install these before starting:

```bash
# macOS
brew install google-cloud-sdk helm kubectl git

# Authenticate gcloud
gcloud auth login
gcloud auth application-default login
```

---

## Phase 1 — GCP Project Setup

### Step 1 — Create a GCP Project

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Click the project dropdown (top bar) → **New Project**
3. Name: `aegispay-gcp` (note the auto-generated Project ID, e.g. `aegispay-gcp-123456`)
4. Click **Create**
5. Wait ~30 seconds for the project to be created

> Your $300 free credit is automatically applied to new accounts. Make sure billing is enabled (it is required even for free trial — you will NOT be charged unless you exceed $300).

### Step 2 — Enable billing

1. GCP Console → **Billing** → Link your project to the free-trial billing account

### Step 3 — Provision the VM (from your laptop)

```bash
export GCP_PROJECT="aegispay-gcp-123456"   # ← your actual project ID
export GCP_ZONE="us-central1-a"

bash infra/gcp/setup-vm.sh
```

This creates:
- An `e2-standard-4` VM named `aegispay-k3s` with no public IP
- A service account `aegispay-eso` with `roles/secretmanager.secretAccessor`
- Firewall: only IAP SSH allowed inbound (Cloudflare tunnel is outbound — no inbound port needed)

### Step 4 — Set VM start/stop schedule

```bash
export GCP_PROJECT="aegispay-gcp-123456"
export GCP_REGION="us-central1"
export GCP_ZONE="us-central1-a"

bash infra/gcp/vm-schedule.sh
```

The VM will now auto-start at **8:00 AM IST** and auto-stop at **midnight IST** every day.

---

## Phase 2 — Cloudflare Tunnel Setup

> You already have a Cloudflare tunnel for SchemaVis. Create a **separate tunnel** for AegisPay.

### Step 5 — Create the tunnel in Cloudflare Dashboard

1. Go to [one.dash.cloudflare.com](https://one.dash.cloudflare.com) → **Zero Trust** → **Networks** → **Tunnels**
2. Click **Create a tunnel** → Choose **Cloudflared** → Next
3. Name: `aegispay-gcp`
4. Click **Save tunnel**
5. On the next screen, copy the **tunnel token** (the long string after `--token`) — save it somewhere safe
6. Click **Next** (skip the connector install for now — we'll run it in K3s)

### Step 6 — Add DNS ingress routes

Still in the Cloudflare tunnel config, click **Public Hostname** tab → **Add a public hostname** for each:

| Subdomain | Service |
|---|---|
| `aegispay.shreyasshelar.uk` | `http://web.aegispay.svc.cluster.local:3000` |
| `api.aegispay.shreyasshelar.uk` | `http://api-gateway.aegispay.svc.cluster.local:8080` |
| `ws.aegispay.shreyasshelar.uk` | `http://notification-service.aegispay.svc.cluster.local:8086` |
| `argocd.aegispay.shreyasshelar.uk` | `http://argocd-server.argocd.svc.cluster.local:80` |
| `grafana.aegispay.shreyasshelar.uk` | `http://grafana.aegispay.svc.cluster.local:3000` |
| `keycloak.aegispay.shreyasshelar.uk` | `http://keycloak.aegispay-infra.svc.cluster.local:8080` |
| `kafka.aegispay.shreyasshelar.uk` | `http://kafka-ui.aegispay-infra.svc.cluster.local:8080` |

> These are internal K8s service DNS names — they only work inside the cluster. The cloudflared pod runs inside the cluster and can reach them.

Click **Save tunnel**.

---

## Phase 3 — Seed Secrets to GCP Secret Manager

### Step 7 — Prepare your secrets and seed them

```bash
export GCP_PROJECT="aegispay-gcp-123456"

# Required secrets
export DB_PASSWORD="choose-a-strong-password"
export REDIS_PASSWORD="choose-a-strong-password"
export MONGO_PASSWORD="choose-a-strong-password"
export KEYCLOAK_ADMIN_PASSWORD="choose-a-strong-password"
export CLICKHOUSE_PASSWORD="choose-a-strong-password"
export OPENROUTER_API_KEY="sk-or-..."          # free at openrouter.ai
export CLOUDFLARE_TUNNEL_TOKEN="eyJ..."        # copied in Step 5

# Optional (can be placeholders initially)
export STRIPE_SECRET_KEY="sk_test_..."
export STRIPE_WEBHOOK_SECRET="whsec_..."
export SMTP_PASSWORD="your-gmail-app-password"
export SLACK_WEBHOOK_URL="https://hooks.slack.com/..."
export FAST2SMS_API_KEY="your-key"

bash infra/gcp/secrets-init.sh
```

Verify they were created:
```bash
gcloud secrets list --project=$GCP_PROJECT
# Should show 13 aegispay-* secrets
```

---

## Phase 4 — Update values-gcp.yaml with your Project ID

### Step 8 — Set your GCP Project ID in Helm values

Open [infra/helm/aegispay/values-gcp.yaml](../infra/helm/aegispay/values-gcp.yaml) and replace:

```yaml
gcpSm:
  projectId: "YOUR_GCP_PROJECT_ID"
```

with your actual project ID:

```yaml
gcpSm:
  projectId: "aegispay-gcp-123456"
```

Commit and push this to `main`:

```bash
git add infra/helm/aegispay/values-gcp.yaml
git commit -m "chore: set GCP project ID for deployment"
git push
```

---

## Phase 5 — SSH into the VM and Install K3s

### Step 9 — SSH via IAP (no public IP needed)

```bash
gcloud compute ssh aegispay-k3s \
  --tunnel-through-iap \
  --zone=us-central1-a \
  --project=aegispay-gcp-123456
```

> First time: gcloud will ask you to create SSH keys — press Enter for defaults.

### Step 10 — Clone the repo on the VM

```bash
# On the VM
sudo apt update && sudo apt install -y git
git clone https://github.com/shreyasshelar/AegisPay.git
cd AegisPay
```

### Step 11 — Install K3s and tooling

```bash
# On the VM, inside AegisPay/
bash infra/k3s/install.sh
```

This installs:
- K3s with Traefik ingress (built-in)
- Helm 3
- cert-manager with Let's Encrypt ClusterIssuer

Wait until you see: `✅ k3s + tooling installed successfully.`

> If cert-manager install fails, run it again — it sometimes times out on first try on a fresh VM.

---

## Phase 6 — Bootstrap the Cluster

### Step 12 — Run GCP cluster setup

```bash
# On the VM, inside AegisPay/
export GCP_PROJECT="aegispay-gcp-123456"
export GITHUB_REPO="https://github.com/shreyasshelar/AegisPay.git"

bash infra/k3s/setup-cluster-gcp.sh
```

This installs (in order):
1. Namespaces (`aegispay`, `aegispay-infra`, `monitoring`, `argocd`, `external-secrets`)
2. ArgoCD → exposed at `argocd.aegispay.shreyasshelar.uk` via Traefik
3. External Secrets Operator
4. ClusterSecretStore → GCP Secret Manager (uses VM service account automatically)
5. Prometheus stack (lightweight, no Grafana — Grafana runs in AegisPay namespace)
6. Keycloak + PostgreSQL ConfigMaps
7. ArgoCD Project + Application (`aegispay-gcp`)

**Save the ArgoCD admin password printed at the end.**

### Step 13 — Deploy the cloudflared tunnel pod

```bash
# On the VM
kubectl apply -f infra/cloudflare/tunnel-gcp.yaml
```

Within 30 seconds, check the Cloudflare Dashboard → Tunnels → `aegispay-gcp` should show **Healthy**.

---

## Phase 7 — Watch ArgoCD Sync

### Step 14 — Monitor ArgoCD

Open `https://argocd.aegispay.shreyasshelar.uk` in your browser.

- Login: username `admin`, password from Step 12
- You should see the `aegispay-gcp` application
- Status: **Syncing** → will become **Healthy** in 5–10 minutes

ArgoCD will:
1. Pull the Helm chart from `main` branch
2. Apply all manifests (services, infra, ExternalSecrets)
3. ESO pulls secrets from GCP SM → creates K8s Secrets
4. Pods start up one by one

### Step 15 — Verify everything is running

```bash
# On the VM
kubectl get pods -n aegispay          # AegisPay microservices
kubectl get pods -n aegispay-infra    # Kafka, Postgres, Redis, MongoDB, Keycloak
kubectl get externalsecrets -n aegispay  # Should all show Ready=True
```

Expected healthy pods (takes 5–10 minutes for all to be Running):
- `api-gateway-*`, `user-service-*`, `transaction-service-*`, `ledger-service-*`
- `payment-orchestrator-*`, `risk-engine-*`, `notification-service-*`
- `ai-platform-*`, `data-pipeline-*`, `reconciliation-service-*`
- `web-*`, `grafana-*`, `cloudflared-*`

---

## Phase 8 — GitOps CI/CD

### How deployments work going forward

```
You push to main
       ↓
GitHub Actions: ci-java.yml builds + pushes Docker images to GHCR
       ↓
GitHub Actions: cd-gcp.yml updates image tags in values-dev.yaml → commits [skip ci]
       ↓
ArgoCD detects git change (polls every 3 min) → auto-sync
       ↓
New pods roll out with the new image
```

**No manual kubectl needed.** Everything is GitOps — git is the source of truth.

### Manual deploy trigger

If you want to force a deploy:
1. Go to GitHub → Actions → **CD — GCP K3s (main)**
2. Click **Run workflow** → enter a SHA or tag → **Run workflow**

---

## Phase 9 — Verify Live URLs

After everything is Healthy:

| URL | Service |
|---|---|
| `https://aegispay.shreyasshelar.uk` | Next.js frontend |
| `https://api.aegispay.shreyasshelar.uk/actuator/health` | API Gateway health |
| `https://argocd.aegispay.shreyasshelar.uk` | ArgoCD UI |
| `https://grafana.aegispay.shreyasshelar.uk` | Grafana dashboards |
| `https://keycloak.aegispay.shreyasshelar.uk` | Keycloak login |

---

## Maintenance

### SSH into the VM for debugging

```bash
gcloud compute ssh aegispay-k3s \
  --tunnel-through-iap \
  --zone=us-central1-a \
  --project=aegispay-gcp-123456
```

### Check resource usage

```bash
# On the VM
kubectl top nodes
kubectl top pods -n aegispay
kubectl top pods -n aegispay-infra
```

### Start/stop VM manually

```bash
# Start
gcloud compute instances start aegispay-k3s --zone=us-central1-a --project=aegispay-gcp-123456

# Stop (saves compute cost — disk still accrues ~$0.04/GB/month)
gcloud compute instances stop aegispay-k3s --zone=us-central1-a --project=aegispay-gcp-123456
```

### Update a secret

```bash
echo -n "new-value" | gcloud secrets versions add aegispay-db-password \
  --data-file=- \
  --project=aegispay-gcp-123456

# Force ESO to refresh immediately (normally refreshes every 1h)
kubectl annotate externalsecret aegispay-db-secret -n aegispay \
  force-sync=$(date +%s) --overwrite
```

### Check remaining free credit

GCP Console → **Billing** → **Credits** — shows remaining balance and expiry date.

---

## Troubleshooting

**Pod stuck in Pending:**
```bash
kubectl describe pod <pod-name> -n aegispay
# Look for: Insufficient memory/cpu, or PVC not bound
```

**ExternalSecret not Ready:**
```bash
kubectl describe externalsecret aegispay-db-secret -n aegispay
# If "permission denied" → VM service account missing secretmanager.secretAccessor role
# If "secret not found" → re-run infra/gcp/secrets-init.sh
```

**Cloudflare tunnel shows Unhealthy:**
```bash
kubectl logs -n aegispay -l app=cloudflared
# Usually means CLOUDFLARE_TUNNEL_TOKEN secret is wrong
```

**ArgoCD shows OutOfSync but won't sync:**
```bash
# Force sync via ArgoCD UI: App → Sync → Force
# Or via CLI on the VM:
kubectl exec -n argocd deploy/argocd-server -- \
  argocd app sync aegispay-gcp --auth-token <token>
```

**OOMKilled pods (out of memory):**
- Check `kubectl top nodes` — if >14 GB used, reduce replicas of non-critical services
- Disable reconciliation service temporarily: set `enabled: false` in values-gcp.yaml
- ClickHouse is the biggest consumer — check its memory: `kubectl top pods -n aegispay-infra`
