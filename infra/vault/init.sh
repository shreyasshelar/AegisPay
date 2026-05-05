#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# AegisPay Vault Initialisation Script (prod/AWS environment)
#
# Usage: ./infra/vault/init.sh <environment> [--force]
#   environment: dev | staging | prod
#
# Prerequisites:
#   - Vault installed and VAULT_ADDR / VAULT_TOKEN set, OR
#   - kubectl port-forward is active to vault pod (auto-detected)
#   - All required env vars set (see "Required environment variables" below)
#
# Required environment variables:
#   VAULT_ADDR          (default: https://vault.aegispay.io)
#   VAULT_TOKEN         Root token (only used for initial setup)
#   DB_PASSWORD         PostgreSQL aegispay user password
#   REDIS_PASSWORD      Redis password
#   KAFKA_SASL_USERNAME Kafka SASL username
#   KAFKA_SASL_PASSWORD Kafka SASL password
#   ANTHROPIC_API_KEY   Claude API key (prod AI model)
#   STRIPE_SECRET_KEY   Stripe secret key (sk_live_... for prod)
#   STRIPE_WEBHOOK_SECRET Stripe webhook signing secret
#   SMTP_PASSWORD       Gmail app password
#   SLACK_WEBHOOK_URL   Slack Incoming Webhook URL
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

ENV="${1:-dev}"
FORCE="${2:-}"

# ── Validate environment ──────────────────────────────────────────────────────
case "$ENV" in
  dev|staging|prod) ;;
  *) echo "❌ Unknown environment: $ENV (use dev|staging|prod)"; exit 1 ;;
esac
echo "🔐 Initialising Vault secrets for environment: $ENV"

# ── Connection ────────────────────────────────────────────────────────────────
VAULT_ADDR="${VAULT_ADDR:-https://vault.aegispay.io}"
export VAULT_ADDR

if [ -z "${VAULT_TOKEN:-}" ]; then
  echo "❌ VAULT_TOKEN is not set. Export your Vault root/admin token."
  exit 1
fi
export VAULT_TOKEN

# ── Verify Vault is reachable and unsealed ────────────────────────────────────
if ! vault status > /dev/null 2>&1; then
  echo "❌ Cannot reach Vault at $VAULT_ADDR or Vault is sealed."
  exit 1
fi
echo "✅ Vault is reachable and unsealed"

# ── Enable KV v2 if not already enabled ──────────────────────────────────────
if ! vault secrets list | grep -q "^secret/"; then
  vault secrets enable -path=secret kv-v2
  echo "✅ KV v2 secrets engine enabled at secret/"
else
  echo "ℹ️  KV v2 already enabled at secret/"
fi

# ── Helper function ───────────────────────────────────────────────────────────
write_secret() {
  local path="$1"; shift
  local full_path="secret/data/aegispay/${ENV}/${path}"
  if vault kv get "secret/aegispay/${ENV}/${path}" > /dev/null 2>&1 && [ "$FORCE" != "--force" ]; then
    echo "ℹ️  Secret already exists: ${full_path} (use --force to overwrite)"
    return
  fi
  vault kv put "secret/aegispay/${ENV}/${path}" "$@"
  echo "✅ Written: secret/aegispay/${ENV}/${path}"
}

# ── Validate required variables ───────────────────────────────────────────────
required_vars=(
  DB_PASSWORD REDIS_PASSWORD KAFKA_SASL_USERNAME KAFKA_SASL_PASSWORD
  ANTHROPIC_API_KEY STRIPE_SECRET_KEY STRIPE_WEBHOOK_SECRET
  SMTP_PASSWORD SLACK_WEBHOOK_URL
)
for var in "${required_vars[@]}"; do
  if [ -z "${!var:-}" ]; then
    echo "❌ Required variable not set: $var"
    exit 1
  fi
done

# ── Write secrets ─────────────────────────────────────────────────────────────
echo ""
echo "📝 Writing secrets to Vault (path: secret/aegispay/${ENV}/*)..."

write_secret "db" \
  password="${DB_PASSWORD}"

write_secret "redis" \
  password="${REDIS_PASSWORD}"

write_secret "kafka" \
  sasl_username="${KAFKA_SASL_USERNAME}" \
  sasl_password="${KAFKA_SASL_PASSWORD}"

write_secret "ai-keys" \
  anthropic_api_key="${ANTHROPIC_API_KEY}" \
  openai_api_key="${OPENAI_API_KEY:-}"

write_secret "stripe" \
  secret_key="${STRIPE_SECRET_KEY}" \
  webhook_secret="${STRIPE_WEBHOOK_SECRET}"

write_secret "smtp" \
  password="${SMTP_PASSWORD}"

write_secret "slack" \
  webhook_url="${SLACK_WEBHOOK_URL}"

# ── Enable Kubernetes auth if not already ─────────────────────────────────────
if ! vault auth list | grep -q "^kubernetes/"; then
  vault auth enable kubernetes
  # Configure with in-cluster Kubernetes API (assumes running inside Kubernetes)
  vault write auth/kubernetes/config \
    kubernetes_host="https://${KUBERNETES_SERVICE_HOST:-kubernetes.default.svc}:${KUBERNETES_SERVICE_PORT_HTTPS:-443}"
  echo "✅ Kubernetes auth method enabled"
else
  echo "ℹ️  Kubernetes auth already enabled"
fi

# ── Apply policy ──────────────────────────────────────────────────────────────
POLICY_FILE="$(dirname "$0")/policies/aegispay-policy.hcl"
if [ -f "$POLICY_FILE" ]; then
  vault policy write "aegispay-${ENV}-app" "$POLICY_FILE"
  echo "✅ Policy applied: aegispay-${ENV}-app"
fi

# ── Bind policy to Kubernetes ServiceAccount ──────────────────────────────────
NAMESPACE="aegispay-${ENV}"
[ "$ENV" = "dev" ] && NAMESPACE="aegispay-dev"
[ "$ENV" = "prod" ] && NAMESPACE="aegispay-prod"

vault write "auth/kubernetes/role/aegispay-${ENV}-app" \
  bound_service_account_names="*" \
  bound_service_account_namespaces="${NAMESPACE}" \
  policies="aegispay-${ENV}-app" \
  ttl=1h
echo "✅ Kubernetes role bound: aegispay-${ENV}-app → namespace ${NAMESPACE}"

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "─────────────────────────────────────────────────────────────────"
echo "✅ Vault initialisation complete for environment: $ENV"
echo ""
echo "Secrets written to: secret/aegispay/${ENV}/"
echo "  db, redis, kafka, ai-keys, stripe, smtp, slack"
echo ""
echo "Next steps:"
echo "  1. Apply ESO ExternalSecrets:  helm upgrade --install aegispay infra/helm/aegispay ..."
echo "  2. Verify ESO sync:            kubectl get externalsecrets -n ${NAMESPACE}"
echo "  3. Check secret:               kubectl get secret aegispay-stripe-secret -n ${NAMESPACE}"
echo "─────────────────────────────────────────────────────────────────"
