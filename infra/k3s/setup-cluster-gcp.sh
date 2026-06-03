#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# AegisPay — GCP K3s Cluster Setup
#
# Run AFTER infra/k3s/install.sh ON THE GCP VM.
# Installs ArgoCD + External Secrets Operator (GCP SM backend).
# No Vault — saves ~512 MB RAM on the 16 GB single-node VM.
#
# Usage (on the GCP VM):
#   export GCP_PROJECT="your-project-id"
#   export GITHUB_REPO="https://github.com/shreyasshelar/AegisPay.git"
#   bash infra/k3s/setup-cluster-gcp.sh
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

: "${GCP_PROJECT:?Set GCP_PROJECT}"
: "${GITHUB_REPO:=https://github.com/shreyasshelar/AegisPay.git}"
: "${GHCR_TOKEN:?Set GHCR_TOKEN=<github-pat-with-read:packages>}"

DOMAIN="aegispay.shreyasshelar.uk"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
section() { echo -e "\n${CYAN}══════════════════════════════════════${NC}"; echo -e "${CYAN}  $*${NC}"; echo -e "${CYAN}══════════════════════════════════════${NC}"; }

export KUBECONFIG="$HOME/.kube/config"

# ── 1. Namespaces ──────────────────────────────────────────────────────────────
section "Creating namespaces"
for ns in aegispay aegispay-infra monitoring argocd external-secrets; do
  kubectl create namespace "$ns" --dry-run=client -o yaml | kubectl apply -f -
done

# ── 1b. GHCR imagePullSecret — images are private, K3s needs credentials ──────
section "Creating GHCR imagePullSecret"
kubectl create secret docker-registry ghcr-pull-secret \
  --docker-server=ghcr.io \
  --docker-username=shreyasshelar \
  --docker-password="${GHCR_TOKEN}" \
  --docker-email=sshelar110.ss3@gmail.com \
  --namespace=aegispay \
  --dry-run=client -o yaml | kubectl apply -f -
info "ghcr-pull-secret created in aegispay namespace"

# ── 2. ArgoCD ─────────────────────────────────────────────────────────────────
section "Installing ArgoCD"
helm upgrade --install argocd argo/argo-cd \
  --namespace argocd \
  --set server.ingress.enabled=true \
  --set server.ingress.ingressClassName=traefik \
  --set "server.ingress.hosts[0]=aegispay-argocd.shreyasshelar.uk" \
  --set "server.ingress.annotations.cert-manager\\.io/cluster-issuer=letsencrypt-prod" \
  --set server.ingress.tls[0].secretName=argocd-tls \
  --set "server.ingress.tls[0].hosts[0]=aegispay-argocd.shreyasshelar.uk" \
  --set configs.params."server\\.insecure"=true \
  --wait

ARGOCD_PASS=$(kubectl get secret -n argocd argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d)
warn "ArgoCD admin password: $ARGOCD_PASS"
warn "▶ Save this — it is shown once. Change it at https://aegispay-argocd.shreyasshelar.uk/settings/accounts/admin"

# ── 3. External Secrets Operator ──────────────────────────────────────────────
section "Installing External Secrets Operator"
helm upgrade --install external-secrets external-secrets/external-secrets \
  --namespace external-secrets \
  --set installCRDs=true \
  --wait

# ── 4. ClusterSecretStore → GCP Secret Manager ────────────────────────────────
# The GCE VM's attached service account (aegispay-eso) provides credentials
# via the metadata server — no explicit key needed.
#
# Wait for ESO CRDs to be registered with the API server before applying.
# Helm --wait only waits for the controller pod, not CRD propagation.
section "Waiting for ESO CRDs to be ready"
for crd in clustersecretstores.external-secrets.io externalsecrets.external-secrets.io secretstores.external-secrets.io; do
  info "Waiting for CRD: $crd"
  until kubectl get crd "$crd" &>/dev/null; do
    sleep 3
  done
  kubectl wait --for condition=established crd/"$crd" --timeout=60s
done
info "ESO CRDs ready."

section "Creating ClusterSecretStore for GCP Secret Manager"
cat <<EOF | kubectl apply -f -
apiVersion: external-secrets.io/v1
kind: ClusterSecretStore
metadata:
  name: gcp-secret-store
spec:
  provider:
    gcpsm:
      projectID: "${GCP_PROJECT}"
EOF
info "ClusterSecretStore created — ESO will use the VM service account via ADC."

# ── 5. kube-prometheus-stack (with Grafana — exposed at aegispay-metrics.shreyasshelar.uk) ──
section "Installing Prometheus + Grafana (infra monitoring)"
# Requires grafana-admin-secret in monitoring namespace before this step.
# If ESO is not yet seeded, create it manually first:
#   kubectl create secret generic grafana-admin-secret -n monitoring \
#     --from-literal=admin-user=admin --from-literal=admin-password=<choose-password>
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  -f infra/helm/monitoring/values-dev.yaml \
  --wait \
  --timeout 10m

# ── 6. PostgreSQL init ConfigMap ──────────────────────────────────────────────
section "Creating PostgreSQL init ConfigMap"
kubectl create configmap postgres-init-scripts \
  --from-file=infra/local/postgres/init/ \
  --namespace aegispay-infra \
  --dry-run=client -o yaml | kubectl apply -f -

# ── 7. Keycloak realm ConfigMap ───────────────────────────────────────────────
# NOTE: The ConfigMap is now managed by the infra Helm chart (ArgoCD will keep it
# in sync). This step only pre-creates it for the initial cluster bootstrap before
# ArgoCD first sync. Canonical source: infra/helm/infra/files/realm-export.json
section "Creating Keycloak realm ConfigMap"
kubectl create configmap keycloak-realm-config \
  --from-file=realm-export.json=infra/helm/infra/files/realm-export.json \
  --namespace aegispay-infra \
  --dry-run=client -o yaml | kubectl apply -f -

# ── 8. Bootstrap ArgoCD project + GCP application ─────────────────────────────
section "Bootstrapping ArgoCD"
kubectl apply -f infra/argocd/project.yaml
kubectl apply -f infra/argocd/app-gcp.yaml

info ""
info "✅ GCP cluster setup complete."
info ""
info "ArgoCD will now sync AegisPay from main branch."
info "Monitor sync at: https://aegispay-argocd.shreyasshelar.uk"
info ""
info "ArgoCD admin password: $ARGOCD_PASS"
info ""
info "Services will be available once ESO pulls secrets and pods start (5-10 min):"
info "  App:      https://aegispay.shreyasshelar.uk"
info "  API:      https://aegispay-api.shreyasshelar.uk"
info "  Grafana:  https://aegispay-grafana.shreyasshelar.uk"
info "  Keycloak: https://aegispay-keycloak.shreyasshelar.uk"
info "  ArgoCD:   https://aegispay-argocd.shreyasshelar.uk"
