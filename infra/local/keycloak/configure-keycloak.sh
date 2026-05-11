#!/usr/bin/env bash
# Configures aegispay-web client scopes via Keycloak Admin REST API.
# Runs after Keycloak is healthy — all built-in scopes exist at this point.
# Idempotent: safe to run on every start-local.sh invocation.
#
# Why not realm-export.json?
# Built-in scopes (roles, offline_access, profile, email) are created by
# Keycloak AFTER realm initialisation. Referencing them in the import JSON
# silently drops them. The Admin API runs post-startup so all scopes exist.

set -euo pipefail

KC_URL="${KC_URL:-http://localhost:8180}"
REALM="aegispay"
ADMIN_USER="${KC_ADMIN:-admin}"
ADMIN_PASS="${KC_ADMIN_PASSWORD:-admin}"
CLIENT_ID_NAME="aegispay-web"
MAX_WAIT=120
INTERVAL=3

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GREEN}[keycloak-config]${NC} $*"; }
fail() { echo -e "${RED}[keycloak-config][ERROR]${NC} $*" >&2; exit 1; }

# ── Wait for admin endpoint to accept credentials ──────────────────────────────
ok "Waiting for Keycloak admin to accept credentials..."
ELAPSED=0
TOKEN=""
while [ -z "$TOKEN" ]; do
  TOKEN=$(curl -sf "$KC_URL/realms/master/protocol/openid-connect/token" \
    -d "client_id=admin-cli" \
    -d "username=$ADMIN_USER" \
    -d "password=$ADMIN_PASS" \
    -d "grant_type=password" 2>/dev/null \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('access_token',''))" 2>/dev/null || true)

  if [ -n "$TOKEN" ]; then break; fi

  if [ "$ELAPSED" -ge "$MAX_WAIT" ]; then
    fail "Timed out waiting for Keycloak admin token after ${MAX_WAIT}s. Check KC_BOOTSTRAP_ADMIN_* env vars."
  fi
  printf "."
  sleep $INTERVAL
  ELAPSED=$((ELAPSED + INTERVAL))
done
echo ""
ok "Admin token obtained"

# ── Helpers ────────────────────────────────────────────────────────────────────
kc_get() { curl -sf "$KC_URL$1" -H "Authorization: Bearer $TOKEN"; }
kc_put() { curl -sf -X PUT "$KC_URL$1" -H "Authorization: Bearer $TOKEN" "$@"; }

resolve_scope_id() {
  local name=$1
  kc_get "/admin/realms/$REALM/client-scopes" \
    | python3 -c "
import sys,json
data = json.load(sys.stdin)
matches = [x['id'] for x in data if x['name'] == '$name']
print(matches[0] if matches else '')"
}

# ── Resolve aegispay-web client UUID ──────────────────────────────────────────
CLIENT_UUID=$(kc_get "/admin/realms/$REALM/clients?clientId=$CLIENT_ID_NAME" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['id'] if d else '')" 2>/dev/null || true)

[ -z "$CLIENT_UUID" ] && fail "Client '$CLIENT_ID_NAME' not found in realm '$REALM'"
ok "Client UUID resolved: $CLIENT_UUID"

# ── Assign scopes ──────────────────────────────────────────────────────────────
assign_scope() {
  local name=$1 type=$2   # type = default | optional
  local scope_id
  scope_id=$(resolve_scope_id "$name")
  if [ -z "$scope_id" ]; then
    ok "  [skip] scope '$name' not found in realm"
    return
  fi
  curl -sf -X PUT \
    "$KC_URL/admin/realms/$REALM/clients/$CLIENT_UUID/${type}-client-scopes/$scope_id" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Length: 0" \
    > /dev/null && ok "  [$type] $name" || ok "  [$type] $name (already set)"
}

# Default scopes — always included in every token
assign_scope "openid"   "default"
assign_scope "profile"  "default"
assign_scope "email"    "default"
assign_scope "roles"    "default"

# Optional scopes — included when client requests them
assign_scope "offline_access"  "optional"   # refresh tokens
assign_scope "aegispay-claims" "optional"   # custom role + tenant_id claims

ok "Client '$CLIENT_ID_NAME' fully configured in realm '$REALM'"
