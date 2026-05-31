#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# AegisPay — k3s + Tooling Installation
#
# Run this ONCE on your on-prem server (Ubuntu 22.04 / Debian 12 recommended).
# After this script completes, run infra/k3s/setup-cluster.sh to install
# ArgoCD, Vault, ESO, and the monitoring stack.
#
# Usage:
#   bash infra/k3s/install.sh
#
# What it installs:
#   - k3s (Kubernetes lightweight distro with Traefik ingress built in)
#   - kubectl alias pointing at k3s kubeconfig
#   - Helm 3
#   - cert-manager (Let's Encrypt TLS for all ingresses)
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info() { echo -e "${GREEN}[INFO]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }

# ── 1. Install k3s ────────────────────────────────────────────────────────────
# --disable traefik=false  → keep Traefik (default; handles ingress + TLS)
# --write-kubeconfig-mode  → make kubeconfig world-readable for your user
info "Installing k3s..."
curl -sfL https://get.k3s.io | sh -s - \
  --write-kubeconfig-mode 644 \
  --disable servicelb   # We'll use Traefik's ClusterIP + ingress instead of LoadBalancer

info "Waiting for k3s to be ready..."
sleep 10
until k3s kubectl get nodes 2>/dev/null | grep -q " Ready"; do
  sleep 3
done
info "k3s node is Ready."

# ── 2. Set up kubeconfig for regular user ─────────────────────────────────────
mkdir -p "$HOME/.kube"
cp /etc/rancher/k3s/k3s.yaml "$HOME/.kube/config"
sed -i "s/127.0.0.1/$(hostname -I | awk '{print $1}')/g" "$HOME/.kube/config" 2>/dev/null || true
export KUBECONFIG="$HOME/.kube/config"

# Persist for future shell sessions
if ! grep -q 'KUBECONFIG' "$HOME/.bashrc" 2>/dev/null; then
  echo 'export KUBECONFIG="$HOME/.kube/config"' >> "$HOME/.bashrc"
fi

# ── 3. Install Helm 3 ─────────────────────────────────────────────────────────
if ! command -v helm &>/dev/null; then
  info "Installing Helm 3..."
  curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
else
  info "Helm already installed: $(helm version --short)"
fi

# ── 4. Add Helm repos ─────────────────────────────────────────────────────────
info "Adding Helm repositories..."
helm repo add bitnami            https://charts.bitnami.com/bitnami
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add hashicorp          https://helm.releases.hashicorp.com
helm repo add external-secrets   https://charts.external-secrets.io
helm repo add argo               https://argoproj.github.io/argo-helm
helm repo update
info "Helm repos added and updated."

# ── 5. Install cert-manager (Let's Encrypt TLS for Traefik ingresses) ─────────
info "Installing cert-manager..."
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/latest/download/cert-manager.crds.yaml
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --set installCRDs=false \
  --wait 2>/dev/null || \
helm upgrade --install cert-manager cert-manager \
  --repo https://charts.jetstack.io \
  --namespace cert-manager --create-namespace \
  --set installCRDs=false \
  --wait

info "Waiting for cert-manager to be ready..."
kubectl wait --for=condition=Available deployment/cert-manager \
  -n cert-manager --timeout=120s

# ── 6. Create ClusterIssuer for Let's Encrypt ─────────────────────────────────
# Replace YOUR_EMAIL with your real email — Let's Encrypt sends cert expiry warnings.
info "Creating Let's Encrypt ClusterIssuer..."
cat <<'EOF' | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: YOUR_EMAIL@example.com       # ← replace with your real email
    privateKeySecretRef:
      name: letsencrypt-prod-key
    solvers:
      - http01:
          ingress:
            class: traefik
EOF

info ""
info "✅ k3s + tooling installed successfully."
info ""
info "Next step: run infra/k3s/setup-cluster.sh to install"
info "ArgoCD, Vault, ESO, and the monitoring stack."
info ""
info "Then run infra/vault/init.sh to seed secrets."
