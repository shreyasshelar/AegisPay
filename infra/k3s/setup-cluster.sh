#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# AegisPay — Cluster Setup
#
# Run AFTER infra/k3s/install.sh.
# Installs ArgoCD, Vault, External Secrets Operator, Prometheus+Grafana stack,
# and all backing services (Kafka, PostgreSQL, MongoDB, Redis, Keycloak).
#
# Usage:
#   export DOMAIN="aegispay.yourdomain.com"   # your subdomain
#   bash infra/k3s/setup-cluster.sh
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

: "${DOMAIN:?Set DOMAIN= to your subdomain, e.g. aegispay.yourdomain.com}"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
section() { echo -e "\n${CYAN}══════════════════════════════════════${NC}"; echo -e "${CYAN}  $*${NC}"; echo -e "${CYAN}══════════════════════════════════════${NC}"; }

# Patch YOUR_DOMAIN placeholder in values files
patch_domain() {
  local file="$1"
  sed -i.bak "s/YOUR_DOMAIN\.com/$DOMAIN/g" "$file" && rm -f "${file}.bak"
  info "Patched domain in $file → $DOMAIN"
}

# ── 1. Create namespaces ───────────────────────────────────────────────────────
section "Creating namespaces"
for ns in aegispay aegispay-infra aegispay-vault monitoring argocd external-secrets; do
  kubectl create namespace "$ns" --dry-run=client -o yaml | kubectl apply -f -
done

# ── 2. Install ArgoCD ─────────────────────────────────────────────────────────
section "Installing ArgoCD"
helm upgrade --install argocd argo/argo-cd \
  --namespace argocd \
  --set server.ingress.enabled=true \
  --set server.ingress.ingressClassName=traefik \
  --set "server.ingress.hosts[0]=argocd.$DOMAIN" \
  --set server.ingress.tls[0].secretName=argocd-tls \
  --set "server.ingress.tls[0].hosts[0]=argocd.$DOMAIN" \
  --set "server.ingress.annotations.cert-manager\.io/cluster-issuer=letsencrypt-prod" \
  --set configs.params."server\.insecure"=true \
  --wait

ARGOCD_PASS=$(kubectl get secret -n argocd argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d)
info "ArgoCD initial admin password: $ARGOCD_PASS"
warn "Save this password — it is shown only once."

# ── 3. Install HashiCorp Vault ─────────────────────────────────────────────────
section "Installing HashiCorp Vault"
patch_domain "infra/vault/values.yaml"
helm upgrade --install vault hashicorp/vault \
  --namespace aegispay-vault \
  -f infra/vault/values.yaml \
  --wait

warn "Vault is installed but SEALED. Run infra/vault/init.sh to initialize."

# ── 4. Install External Secrets Operator ──────────────────────────────────────
section "Installing External Secrets Operator"
helm upgrade --install external-secrets external-secrets/external-secrets \
  --namespace external-secrets \
  --set installCRDs=true \
  --wait

# ── 5. Install Prometheus + Grafana ───────────────────────────────────────────
section "Installing kube-prometheus-stack"
patch_domain "infra/helm/monitoring/values-onprem.yaml"
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  -f infra/helm/monitoring/values-onprem.yaml \
  --wait

# ── 6. Create Postgres init ConfigMap ─────────────────────────────────────────
section "Creating PostgreSQL init scripts ConfigMap"
kubectl create configmap postgres-init-scripts \
  --from-file=infra/local/postgres/init/ \
  --namespace aegispay-infra \
  --dry-run=client -o yaml | kubectl apply -f -

# ── 7. Create Keycloak realm ConfigMap ────────────────────────────────────────
# NOTE: The ConfigMap is now managed by the infra Helm chart (ArgoCD will keep it
# in sync). This step only pre-creates it for the initial cluster bootstrap before
# ArgoCD first sync. Canonical source: infra/helm/infra/files/realm-export.json
section "Creating Keycloak realm ConfigMap"
kubectl create configmap keycloak-realm-config \
  --from-file=realm-export.json=infra/helm/infra/files/realm-export.json \
  --namespace aegispay-infra \
  --dry-run=client -o yaml | kubectl apply -f -

# ── 8. Install backing services ───────────────────────────────────────────────
section "Installing backing services (PostgreSQL, MongoDB, Redis, Kafka, Keycloak)"
warn "ESO must be configured + Vault secrets seeded before this step."
warn "Backing services read their passwords from K8s secrets that ESO creates."
warn "Run infra/vault/init.sh first, then re-run this step."

helm dependency update infra/helm/infra/
helm upgrade --install aegispay-infra infra/helm/infra/ \
  --namespace aegispay-infra \
  -f infra/helm/infra/values-onprem.yaml \
  --wait \
  --timeout 10m

# ── 9. Verify Keycloak realm import succeeded ─────────────────────────────────
# Keycloak client scope configuration is handled automatically by the
# Helm post-install/post-upgrade Job in infra/helm/infra/templates/keycloak-configure-job.yaml
section "Verifying Keycloak realm import"
KEYCLOAK_SVC="aegispay-infra-keycloak.aegispay-infra.svc.cluster.local"
KEYCLOAK_PORT="80"
MAX_WAIT=120
ELAPSED=0
info "Polling Keycloak for realm 'aegispay' (timeout: ${MAX_WAIT}s)..."
until kubectl run -n aegispay-infra keycloak-realm-check --rm -i --restart=Never \
    --image=curlimages/curl:8.6.0 --quiet -- \
    curl -sf "http://${KEYCLOAK_SVC}:${KEYCLOAK_PORT}/realms/aegispay" > /dev/null 2>&1; do
  if [ "$ELAPSED" -ge "$MAX_WAIT" ]; then
    warn "Keycloak realm 'aegispay' not ready after ${MAX_WAIT}s."
    warn "Check Keycloak logs: kubectl logs -n aegispay-infra -l app.kubernetes.io/name=keycloak"
    warn "If realm was not imported, re-apply the ConfigMap and restart Keycloak:"
    warn "  kubectl rollout restart -n aegispay-infra deploy/aegispay-infra-keycloak"
    break
  fi
  info "Waiting for Keycloak realm... (${ELAPSED}s elapsed)"
  sleep 10
  ELAPSED=$((ELAPSED + 10))
done
[ "$ELAPSED" -lt "$MAX_WAIT" ] && info "✅ Keycloak realm 'aegispay' confirmed."

# ── 10. Bootstrap ArgoCD Applications ─────────────────────────────────────────
section "Bootstrapping ArgoCD Applications"
kubectl apply -f infra/argocd/project.yaml
kubectl apply -f infra/argocd/app-onprem.yaml

info ""
info "✅ Cluster setup complete."
info ""
info "Access points (after DNS/Cloudflare Tunnel is configured):"
info "  Web app    → https://app.$DOMAIN"
info "  API        → https://api.$DOMAIN"
info "  ArgoCD     → https://argocd.$DOMAIN"
info "  Grafana    → https://grafana.$DOMAIN"
info "  Vault UI   → https://vault.$DOMAIN"
info "  Keycloak   → configure via kubectl port-forward initially"
info ""
info "Next steps:"
info "  1. Run:  bash infra/vault/init.sh"
info "  2. Configure Cloudflare Tunnel: see infra/cloudflare/README.md"
info "  3. Push to dev branch → ArgoCD auto-deploys AegisPay services"
