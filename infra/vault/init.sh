#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Vault Init + Secret Seeding Script
#
# Run ONCE after Vault is deployed and before any AegisPay service starts.
# Prerequisites:
#   - Vault pod running:  kubectl get pods -n aegispay-vault
#   - vault CLI installed: brew install vault   OR   apt install vault
#   - kubectl access to the cluster
#
# Usage:
#   export DB_PASSWORD="your-postgres-password"
#   export REDIS_PASSWORD="your-redis-password"
#   export OPENROUTER_API_KEY="sk-or-..."
#   export KEYCLOAK_ADMIN_PASSWORD="your-keycloak-admin-password"
#   export GRAFANA_ADMIN_PASSWORD="your-grafana-password"
#   bash infra/vault/init.sh
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

VAULT_NAMESPACE="aegispay-vault"
VAULT_POD="vault-0"
POLICY_FILE="infra/vault/policies/aegispay-policy.hcl"

# Colour helpers
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()    { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── Validate required env vars ────────────────────────────────────────────────
: "${DB_PASSWORD:?Set DB_PASSWORD before running this script}"
: "${REDIS_PASSWORD:?Set REDIS_PASSWORD before running this script}"
: "${OPENROUTER_API_KEY:?Set OPENROUTER_API_KEY (get a free key at openrouter.ai)}"
: "${KEYCLOAK_ADMIN_PASSWORD:?Set KEYCLOAK_ADMIN_PASSWORD}"
: "${GRAFANA_ADMIN_PASSWORD:?Set GRAFANA_ADMIN_PASSWORD}"
# Optional but recommended — warn if not set
[ -z "${STRIPE_SECRET_KEY:-}"   ] && warn "STRIPE_SECRET_KEY not set — payment gateway will be stubbed"
[ -z "${STRIPE_WEBHOOK_SECRET:-}" ] && warn "STRIPE_WEBHOOK_SECRET not set"
[ -z "${SMTP_PASSWORD:-}"       ] && warn "SMTP_PASSWORD not set — email notifications disabled"
[ -z "${SLACK_WEBHOOK_URL:-}"   ] && warn "SLACK_WEBHOOK_URL not set — Slack alerts disabled"

# ── Port-forward Vault to localhost ───────────────────────────────────────────
info "Port-forwarding Vault to localhost:8200..."
kubectl port-forward -n "$VAULT_NAMESPACE" "pod/$VAULT_POD" 8200:8200 &
PF_PID=$!
trap "kill $PF_PID 2>/dev/null || true" EXIT
sleep 3

export VAULT_ADDR="http://127.0.0.1:8200"

# ── Check if Vault is already initialized ─────────────────────────────────────
INIT_STATUS=$(vault status -format=json 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('initialized','false'))" 2>/dev/null || echo "false")

if [ "$INIT_STATUS" = "false" ]; then
  info "Initialising Vault (first run)..."
  INIT_OUTPUT=$(vault operator init -key-shares=1 -key-threshold=1 -format=json)
  UNSEAL_KEY=$(echo "$INIT_OUTPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['unseal_keys_b64'][0])")
  ROOT_TOKEN=$(echo "$INIT_OUTPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['root_token'])")

  warn "============================================================"
  warn "SAVE THESE SOMEWHERE SAFE — you need them to unseal Vault"
  warn "Unseal Key : $UNSEAL_KEY"
  warn "Root Token : $ROOT_TOKEN"
  warn "============================================================"

  info "Unsealing Vault..."
  vault operator unseal "$UNSEAL_KEY"

  info "Logging in with root token..."
  vault login "$ROOT_TOKEN"
else
  info "Vault already initialized."
  if [ -z "${VAULT_TOKEN:-}" ]; then
    error "VAULT_TOKEN not set. Export your root (or admin) token and re-run."
  fi
  SEALED=$(vault status -format=json | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('sealed','true'))")
  if [ "$SEALED" = "true" ]; then
    warn "Vault is sealed. Run: vault operator unseal <key>"
    exit 1
  fi
fi

# ── Enable KV v2 secrets engine ───────────────────────────────────────────────
info "Enabling KV v2 secrets engine at 'secret/'..."
vault secrets enable -path=secret kv-v2 2>/dev/null || warn "KV engine already enabled"

# ── Write secrets ─────────────────────────────────────────────────────────────
info "Writing database secrets..."
vault kv put secret/aegispay/database \
  password="$DB_PASSWORD" \
  username="aegispay"

info "Writing Redis secrets..."
vault kv put secret/aegispay/redis \
  password="$REDIS_PASSWORD"

info "Writing Kafka secrets (plaintext on-prem — no SASL)..."
vault kv put secret/aegispay/kafka \
  sasl_username="" \
  sasl_password=""

info "Writing AI secrets (OpenRouter for on-prem)..."
vault kv put secret/aegispay/ai \
  openrouter_api_key="$OPENROUTER_API_KEY" \
  anthropic_api_key="" \
  openai_api_key=""

info "Writing Keycloak secrets..."
vault kv put secret/aegispay/keycloak \
  admin_password="$KEYCLOAK_ADMIN_PASSWORD"

info "Writing Grafana secrets..."
vault kv put secret/aegispay/grafana \
  admin_user="admin" \
  admin_password="$GRAFANA_ADMIN_PASSWORD"

info "Writing Stripe payment gateway secrets..."
vault kv put secret/aegispay/stripe \
  secret_key="${STRIPE_SECRET_KEY:-sk_test_placeholder}" \
  webhook_secret="${STRIPE_WEBHOOK_SECRET:-whsec_placeholder}"

info "Writing SMTP (email) secrets..."
vault kv put secret/aegispay/smtp \
  password="${SMTP_PASSWORD:-}"

info "Writing Slack alerting secrets..."
vault kv put secret/aegispay/slack \
  webhook_url="${SLACK_WEBHOOK_URL:-}"

info "Writing ClickHouse analytics DB password..."
vault kv put secret/aegispay/clickhouse \
  password="${CLICKHOUSE_PASSWORD:-}"

info "Writing OpenRouter API key (AI — on-prem free tier)..."
vault kv put secret/aegispay/ai-keys \
  openrouter_api_key="$OPENROUTER_API_KEY" \
  anthropic_api_key="" \
  openai_api_key=""

# ── Create policy ─────────────────────────────────────────────────────────────
info "Writing AegisPay access policy..."
vault policy write aegispay-policy "$POLICY_FILE"

# ── Enable Kubernetes auth method ─────────────────────────────────────────────
info "Enabling Kubernetes auth method..."
vault auth enable kubernetes 2>/dev/null || warn "Kubernetes auth already enabled"

# Read K8s config from the cluster
K8S_HOST=$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
K8S_CA=$(kubectl get secret -n "$VAULT_NAMESPACE" \
  "$(kubectl get serviceaccount -n "$VAULT_NAMESPACE" vault -o jsonpath='{.secrets[0].name}' 2>/dev/null || echo 'vault-token')" \
  -o jsonpath='{.data.ca\.crt}' 2>/dev/null | base64 -d || \
  kubectl config view --raw --minify --flatten -o jsonpath='{.clusters[].cluster.certificate-authority-data}' | base64 -d)

vault write auth/kubernetes/config \
  kubernetes_host="$K8S_HOST" \
  kubernetes_ca_cert="$K8S_CA"

# ── Create Kubernetes auth role for ESO ───────────────────────────────────────
info "Creating Kubernetes auth role for External Secrets Operator..."
vault write auth/kubernetes/role/aegispay-role \
  bound_service_account_names="external-secrets" \
  bound_service_account_namespaces="external-secrets" \
  policies="aegispay-policy" \
  ttl="1h"

info "✅ Vault initialization complete."
info "All secrets written to secret/aegispay/*"
info "ESO will now be able to sync secrets into K8s automatically."
