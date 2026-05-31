#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# AegisPay — GCP Secret Manager: Seed All Secrets
#
# Run this ONCE from your laptop before the first ArgoCD sync.
# Requires gcloud CLI authenticated with roles/secretmanager.admin on the project.
#
# Usage:
#   export GCP_PROJECT="your-project-id"
#   # Set all the variables below, then:
#   bash infra/gcp/secrets-init.sh
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

: "${GCP_PROJECT:?Set GCP_PROJECT}"

# ── Set your secrets here ─────────────────────────────────────────────────────
: "${DB_PASSWORD:?Set DB_PASSWORD}"
: "${REDIS_PASSWORD:?Set REDIS_PASSWORD}"
: "${MONGO_PASSWORD:?Set MONGO_PASSWORD}"
: "${KEYCLOAK_ADMIN_PASSWORD:?Set KEYCLOAK_ADMIN_PASSWORD}"
: "${CLICKHOUSE_PASSWORD:?Set CLICKHOUSE_PASSWORD}"
: "${OPENROUTER_API_KEY:?Set OPENROUTER_API_KEY (free at openrouter.ai)}"
: "${CLOUDFLARE_TUNNEL_TOKEN:?Set CLOUDFLARE_TUNNEL_TOKEN from Cloudflare Dashboard}"

# Optional — warn if missing
[ -z "${KAFKA_PASSWORD:-}"           ] && KAFKA_PASSWORD="$(openssl rand -hex 16)"    && echo "[WARN] KAFKA_PASSWORD not set — generated: $KAFKA_PASSWORD"
[ -z "${STRIPE_SECRET_KEY:-}"        ] && STRIPE_SECRET_KEY="sk_test_placeholder"      && echo "[WARN] STRIPE_SECRET_KEY not set — using placeholder"
[ -z "${STRIPE_WEBHOOK_SECRET:-}"    ] && STRIPE_WEBHOOK_SECRET="whsec_placeholder"    && echo "[WARN] STRIPE_WEBHOOK_SECRET not set — using placeholder"
[ -z "${SMTP_PASSWORD:-}"            ] && SMTP_PASSWORD="placeholder"                  && echo "[WARN] SMTP_PASSWORD not set — email notifications disabled"
[ -z "${SLACK_WEBHOOK_URL:-}"        ] && SLACK_WEBHOOK_URL="https://placeholder"      && echo "[WARN] SLACK_WEBHOOK_URL not set — Slack alerts disabled"
[ -z "${FAST2SMS_API_KEY:-}"         ] && FAST2SMS_API_KEY="placeholder"               && echo "[WARN] FAST2SMS_API_KEY not set — SMS disabled"

GREEN='\033[0;32m'; NC='\033[0m'
info() { echo -e "${GREEN}[INFO]${NC} $*"; }

gcloud config set project "$GCP_PROJECT"

# Helper: create or update a secret
upsert_secret() {
  local name="$1"
  local value="$2"

  if gcloud secrets describe "$name" --project="$GCP_PROJECT" &>/dev/null; then
    info "Updating secret $name..."
    echo -n "$value" | gcloud secrets versions add "$name" --data-file=- --project="$GCP_PROJECT"
  else
    info "Creating secret $name..."
    echo -n "$value" | gcloud secrets create "$name" \
      --data-file=- \
      --replication-policy=automatic \
      --project="$GCP_PROJECT"
  fi
}

# ── Seed all secrets ──────────────────────────────────────────────────────────
upsert_secret "aegispay-db-password"              "$DB_PASSWORD"
upsert_secret "aegispay-redis-password"            "$REDIS_PASSWORD"
upsert_secret "aegispay-mongo-password"            "$MONGO_PASSWORD"
upsert_secret "aegispay-kafka-password"            "$KAFKA_PASSWORD"
upsert_secret "aegispay-openrouter-api-key"        "$OPENROUTER_API_KEY"
upsert_secret "aegispay-stripe-secret-key"         "$STRIPE_SECRET_KEY"
upsert_secret "aegispay-stripe-webhook-secret"     "$STRIPE_WEBHOOK_SECRET"
upsert_secret "aegispay-smtp-password"             "$SMTP_PASSWORD"
upsert_secret "aegispay-slack-webhook-url"         "$SLACK_WEBHOOK_URL"
upsert_secret "aegispay-fast2sms-api-key"          "$FAST2SMS_API_KEY"
upsert_secret "aegispay-clickhouse-password"       "$CLICKHOUSE_PASSWORD"
upsert_secret "aegispay-keycloak-admin-password"   "$KEYCLOAK_ADMIN_PASSWORD"
upsert_secret "aegispay-cloudflare-tunnel-token"   "$CLOUDFLARE_TUNNEL_TOKEN"

info ""
info "✅ All secrets seeded in GCP Secret Manager for project $GCP_PROJECT."
info "   ESO will pull them into K8s Secrets automatically on the next sync cycle."
info ""
info "To verify:"
info "  gcloud secrets list --project=$GCP_PROJECT"
