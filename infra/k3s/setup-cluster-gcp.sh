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
section "Creating ClusterSecretStore for GCP Secret Manager"
cat <<EOF | kubectl apply -f -
apiVersion: external-secrets.io/v1beta1
kind: ClusterSecretStore
metadata:
  name: gcp-secret-store
spec:
  provider:
    gcpsm:
      projectID: "${GCP_PROJECT}"
EOF
info "ClusterSecretStore created — ESO will use the VM service account via ADC."

# ── 5. kube-prometheus-stack (lightweight config for single node) ──────────────
section "Installing Prometheus + Grafana (infra monitoring)"
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --set prometheus.prometheusSpec.resources.requests.memory=256Mi \
  --set prometheus.prometheusSpec.resources.limits.memory=512Mi \
  --set alertmanager.enabled=false \
  --set grafana.enabled=false \
  --wait

# ── 6. PostgreSQL init ConfigMap ──────────────────────────────────────────────
section "Creating PostgreSQL init ConfigMap"
kubectl create configmap postgres-init-scripts \
  --from-file=infra/local/postgres/init/ \
  --namespace aegispay-infra \
  --dry-run=client -o yaml | kubectl apply -f -

# ── 7. Keycloak realm ConfigMap ───────────────────────────────────────────────
section "Creating Keycloak realm ConfigMap"
kubectl create configmap keycloak-realm-config \
  --from-file=realm-export.json=infra/local/keycloak/realm-export.json \
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
