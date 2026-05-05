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

# AI API keys (OpenRouter key on on-prem; Anthropic key on AWS prod)
path "secret/data/aegispay/ai" {
  capabilities = ["read"]
}

# Keycloak admin password
path "secret/data/aegispay/keycloak" {
  capabilities = ["read"]
}

# Payment gateway credentials (Razorpay / Stripe — stubbed on on-prem)
path "secret/data/aegispay/payment-gateway" {
  capabilities = ["read"]
}

# Grafana admin credentials
path "secret/data/aegispay/grafana" {
  capabilities = ["read"]
}

# Allow ESO to renew its own token
path "auth/token/renew-self" {
  capabilities = ["update"]
}

path "auth/token/lookup-self" {
  capabilities = ["read"]
}
