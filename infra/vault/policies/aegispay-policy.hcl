# AegisPay application policy
# Read-only access to all secrets under secret/data/aegispay/<env>/*
# Written by: infra/vault/init.sh

# Database credentials
path "secret/data/aegispay/+/db" {
  capabilities = ["read"]
}

# Redis credentials
path "secret/data/aegispay/+/redis" {
  capabilities = ["read"]
}

# Kafka credentials
path "secret/data/aegispay/+/kafka" {
  capabilities = ["read"]
}

# AI API keys (Anthropic, OpenAI)
path "secret/data/aegispay/+/ai-keys" {
  capabilities = ["read"]
}

# Stripe payment keys
path "secret/data/aegispay/+/stripe" {
  capabilities = ["read"]
}

# SMTP credentials
path "secret/data/aegispay/+/smtp" {
  capabilities = ["read"]
}

# Slack webhook
path "secret/data/aegispay/+/slack" {
  capabilities = ["read"]
}

# Cloudflare tunnel token (if used)
path "secret/data/aegispay/+/cloudflare" {
  capabilities = ["read"]
}

# Allow renewing own token
path "auth/token/renew-self" {
  capabilities = ["update"]
}

# Allow looking up own token
path "auth/token/lookup-self" {
  capabilities = ["read"]
}
