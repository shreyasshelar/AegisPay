# ─────────────────────────────────────────────────────────────────────────────
# Vault policy — AegisPay application access
#
# Grants read-only access to the secret paths that ESO will pull from.
# Applied to the Kubernetes auth role used by External Secrets Operator.
# ─────────────────────────────────────────────────────────────────────────────

# Database credentials (PostgreSQL + MongoDB passwords)
path "secret/data/aegispay/database" {
  capabilities = ["read"]
}

# Redis password
path "secret/data/aegispay/redis" {
  capabilities = ["read"]
}

# Kafka SASL credentials (not used on on-prem plain — kept for parity with prod)
path "secret/data/aegispay/kafka" {
  capabilities = ["read"]
}

# AI API keys (OpenRouter on on-prem; Anthropic + OpenAI on cloud)
path "secret/data/aegispay/ai" {
  capabilities = ["read"]
}

path "secret/data/aegispay/ai-keys" {
  capabilities = ["read"]
}

# Keycloak admin password
path "secret/data/aegispay/keycloak" {
  capabilities = ["read"]
}

# Stripe payment gateway (test key for dev, live key for prod)
path "secret/data/aegispay/stripe" {
  capabilities = ["read"]
}

# SMTP credentials (Gmail App Password)
path "secret/data/aegispay/smtp" {
  capabilities = ["read"]
}

# Slack alertmanager webhook
path "secret/data/aegispay/slack" {
  capabilities = ["read"]
}

# ClickHouse analytics DB
path "secret/data/aegispay/clickhouse" {
  capabilities = ["read"]
}

# Grafana admin credentials
path "secret/data/aegispay/grafana" {
  capabilities = ["read"]
}

# KV metadata (required for ESO list operations)
path "secret/metadata/aegispay/*" {
  capabilities = ["list", "read"]
}

# Allow ESO to renew its own token
path "auth/token/renew-self" {
  capabilities = ["update"]
}

path "auth/token/lookup-self" {
  capabilities = ["read"]
}
