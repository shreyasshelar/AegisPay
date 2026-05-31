#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# AegisPay — Demo User Seed Script
#
# Creates 4 demo users in Keycloak + user-service + ledger-service.
# Flyway migrations (V5/V6/V8 user-service, V6/V7 ledger-service) handle the
# PostgreSQL rows automatically on service startup. This script handles the
# Keycloak side so the accounts are actually loginable.
#
# Works for:
#   Local  → KEYCLOAK_URL=http://localhost:8180  API_URL=http://localhost:8080
#   GCP    → KEYCLOAK_URL=https://aegispay-keycloak.shreyasshelar.uk
#             API_URL=https://aegispay-api.shreyasshelar.uk
#
# Usage:
#   # Local
#   bash infra/seed/seed-users.sh
#
#   # GCP
#   KEYCLOAK_URL=https://aegispay-keycloak.shreyasshelar.uk \
#   API_URL=https://aegispay-api.shreyasshelar.uk \
#   KEYCLOAK_ADMIN_PASSWORD="your-admin-pass" \
#   bash infra/seed/seed-users.sh
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
API_URL="${API_URL:-http://localhost:8080}"
KEYCLOAK_REALM="aegispay"
KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${GREEN}[SEED]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
section() { echo -e "\n${CYAN}── $* ──────────────────────────────${NC}"; }

# ─── 4 demo users ─────────────────────────────────────────────────────────────
# Keycloak sub UUIDs MUST match:
#   - user-service V5 (customer, payee)
#   - user-service V6 (admin, backoffice)
#   - user-service V8 (alice, bob)
# ─────────────────────────────────────────────────────────────────────────────

declare -A USERS
# Format: "email|password|firstName|lastName|role|keycloak_sub"
USERS[alice]="alice@aegispay.local|Demo@1234|Alice|Sharma|CUSTOMER|c1a2b3c4-d5e6-4f78-9012-abcdef123456"
USERS[bob]="bob@aegispay.local|Demo@1234|Bob|Mehta|CUSTOMER|d2b3c4d5-e6f7-4089-0123-bcdef1234567"
USERS[backoffice]="backoffice@aegispay.local|Staff@1234|Back|Office|BACK_OFFICE|b2c3d4e5-f6a7-4901-bc02-f12345678901"
USERS[admin]="admin@aegispay.local|Admin@1234|Admin|User|ADMIN|a1b2c3d4-e5f6-4890-ab01-ef1234567890"

# ─── Step 1: Get Keycloak admin token ─────────────────────────────────────────
section "Getting Keycloak admin token"
ADMIN_TOKEN=$(curl -sf -X POST \
  "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=${KEYCLOAK_ADMIN}" \
  -d "password=${KEYCLOAK_ADMIN_PASSWORD}" \
  | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

[ -z "$ADMIN_TOKEN" ] && { echo "Failed to get admin token. Is Keycloak running?"; exit 1; }
info "Got admin token."

# ─── Helper: create or update user in Keycloak ────────────────────────────────
create_keycloak_user() {
  local email="$1" password="$2" first="$3" last="$4" role="$5" sub="$6"

  # Check if user already exists
  EXISTING=$(curl -sf \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users?email=${email}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -c '"id"' || true)

  if [ "$EXISTING" -gt 0 ]; then
    warn "Keycloak user ${email} already exists — skipping create."
    return 0
  fi

  # Create user with the fixed sub UUID so it matches the DB seed
  curl -sf -X POST \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{
      \"id\": \"${sub}\",
      \"username\": \"$(echo $email | cut -d@ -f1)\",
      \"email\": \"${email}\",
      \"firstName\": \"${first}\",
      \"lastName\": \"${last}\",
      \"enabled\": true,
      \"emailVerified\": true,
      \"credentials\": [{\"type\":\"password\",\"value\":\"${password}\",\"temporary\":false}],
      \"attributes\": {
        \"aegispay_user_id\": [\"${sub}\"],
        \"aegispay_role\":    [\"${role}\"]
      }
    }" && info "Created Keycloak user: ${email}" || warn "Failed to create ${email} (may already exist)"
}

# ─── Step 2: Create all 4 users in Keycloak ───────────────────────────────────
section "Creating Keycloak users"
for key in alice bob backoffice admin; do
  IFS='|' read -r email password first last role sub <<< "${USERS[$key]}"
  create_keycloak_user "$email" "$password" "$first" "$last" "$role" "$sub"
done

# ─── Step 3: Register customer users in user-service via API ──────────────────
# CUSTOMER role users need to call /api/v1/users/register.
# ADMIN + BACK_OFFICE are seeded by Flyway — no API call needed.
section "Registering customer users via API"

register_customer() {
  local email="$1" password="$2" first="$3" last="$4" phone="$5"

  # Get user token
  TOKEN=$(curl -sf -X POST \
    "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token" \
    -d "grant_type=password" \
    -d "client_id=aegispay-web" \
    -d "username=${email}" \
    -d "password=${password}" \
    | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4 || true)

  [ -z "$TOKEN" ] && { warn "Could not get token for ${email} — skipping register"; return 0; }

  curl -sf -X POST "${API_URL}/api/v1/users/register" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "X-Idempotency-Key: seed-$(echo $email | md5sum | head -c 8)" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"firstName\":\"${first}\",\"lastName\":\"${last}\",\"phone\":\"${phone}\",\"tenantId\":\"default\"}" \
    > /dev/null && info "Registered ${email} in user-service" || warn "${email} may already be registered"
}

register_customer "alice@aegispay.local"     "Demo@1234"  "Alice"  "Sharma" "+919900001111"
register_customer "bob@aegispay.local"       "Demo@1234"  "Bob"    "Mehta"  "+919900002222"

# ─── Done ─────────────────────────────────────────────────────────────────────
echo ""
info "✅ Seed complete. Demo accounts:"
echo ""
printf "  %-35s %-12s %-15s %s\n" "Email" "Password" "Role" "Balance"
printf "  %-35s %-12s %-15s %s\n" "─────────────────────────────────" "────────────" "───────────────" "──────────"
printf "  %-35s %-12s %-15s %s\n" "alice@aegispay.local"      "Demo@1234"  "CUSTOMER"    "₹5,00,000"
printf "  %-35s %-12s %-15s %s\n" "bob@aegispay.local"        "Demo@1234"  "CUSTOMER"    "₹2,50,000"
printf "  %-35s %-12s %-15s %s\n" "backoffice@aegispay.local" "Staff@1234" "BACK_OFFICE" "—"
printf "  %-35s %-12s %-15s %s\n" "admin@aegispay.local"      "Admin@1234" "ADMIN"       "—"
echo ""
