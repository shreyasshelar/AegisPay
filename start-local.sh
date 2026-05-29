#!/bin/bash
# Compatible with macOS (BSD sed/bash 3.2) and Linux (GNU sed/bash 5+)
#
# Usage:
#   ./start-local.sh                  — infra + backend + web only
#   ./start-local.sh --android        — also builds & installs Android APK on emulator
#   ./start-local.sh --ios            — also boots iOS Simulator (macOS only)
#   ./start-local.sh --android --ios  — all three
#
# Physical device flag (overrides 10.0.2.2 / localhost with your LAN IP):
#   ./start-local.sh --android --device-ip 192.168.1.50
set -e

# ── Parse flags ────────────────────────────────────────────────────────────────
START_ANDROID=0
START_IOS=0
DEVICE_IP=""
for arg in "$@"; do
  case $arg in
    --android)    START_ANDROID=1 ;;
    --ios)        START_IOS=1 ;;
    --device-ip=*) DEVICE_IP="${arg#*=}" ;;
    --device-ip)  ;; # handled by next arg check
  esac
done
# handle --device-ip <ip> (two-arg form)
for i in $(seq 1 $#); do
  if [ "${!i}" = "--device-ip" ]; then
    j=$((i+1)); DEVICE_IP="${!j}"
  fi
done

# ── Node.js DNS fix — prefer IPv4 so localhost -> 127.0.0.1 not ::1 ──────────
export NODE_OPTIONS="${NODE_OPTIONS:---dns-result-order=ipv4first}"

# ── Dev secrets ────────────────────────────────────────────────────────────────
export DB_PORT="${DB_PORT:-5433}"

# OAuth2 / Keycloak
export OAUTH2_ISSUER_URI="http://localhost:8180/realms/aegispay"
export OAUTH2_PRIMARY_ISSUER_URI="http://localhost:8180/realms/aegispay"
export OAUTH2_ISSUER_KEYCLOAK="http://localhost:8180/realms/aegispay"

# Service-to-service URIs
export USER_SERVICE_URI="http://localhost:8081"
export TRANSACTION_SERVICE_URI="http://localhost:8082"
export LEDGER_SERVICE_URI="http://localhost:8083"
export ORCHESTRATOR_SERVICE_URI="http://localhost:8084"
export RISK_ENGINE_URI="http://localhost:8085"
export NOTIFICATION_SERVICE_URI="http://localhost:8086"
export AI_PLATFORM_URI="http://localhost:8091"

# Stripe — both keys needed: secret (backend) + publishable (mobile/web)
export STRIPE_SECRET_KEY="${STRIPE_SECRET_KEY:-sk_test_51TTkk2CyjRW67i1DP4dcrEgzhOm9dUe61k9U5kPNoDST6Deuy9rAvgJY0ZL93kKbDmdP7SEAUXUM6M4TMMtxkWNb00eKgRIql5}"
export STRIPE_PUBLISHABLE_KEY="${STRIPE_PUBLISHABLE_KEY:-pk_test_51TTkk2CyjRW67i1Dr44Sfw2W1FzJ2taFP757phrJYxYlwFTThQEEdL2eUnugV6w50ySvXjHUUXO35yHd6y3HAgiP007Aa0VZdL}"

# Google OAuth (Keycloak social login)
# Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET in your environment before running.
# Get them from: https://console.cloud.google.com/apis/credentials
[[ -z "${GOOGLE_CLIENT_ID:-}" ]]     && echo "WARNING: GOOGLE_CLIENT_ID not set — Google social login disabled"
[[ -z "${GOOGLE_CLIENT_SECRET:-}" ]] && echo "WARNING: GOOGLE_CLIENT_SECRET not set — Google social login disabled"

# Microsoft OAuth (Keycloak social login)
# Set MICROSOFT_CLIENT_ID and MICROSOFT_CLIENT_SECRET in your environment.
# Get them from: https://portal.azure.com/ -> App Registrations -> Certificates and Secrets
[[ -z "${MICROSOFT_CLIENT_ID:-}" ]]     && echo "WARNING: MICROSOFT_CLIENT_ID not set — Microsoft social login disabled"
[[ -z "${MICROSOFT_CLIENT_SECRET:-}" ]] && echo "WARNING: MICROSOFT_CLIENT_SECRET not set — Microsoft social login disabled"
export MICROSOFT_TENANT_ID="${MICROSOFT_TENANT_ID:-common}"

# Email / SMS / Slack
export SMTP_PASSWORD="${SMTP_PASSWORD:-mcinrqrbfqayklee}"
export SLACK_WEBHOOK_URL="${SLACK_WEBHOOK_URL:-https://hooks.slack.com/services/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}"
export FAST2SMS_API_KEY="${FAST2SMS_API_KEY:-ZNd8Xx4lqrERbj67Unwi1LHvB0smOFDayTCkgczIYKM9oPAfV2jDTskbhao0QZ3luvA7VfiLdWM2KNOe}"

# ── Java 21 ────────────────────────────────────────────────────────────────────
JAVA21_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
if [ -n "$JAVA21_HOME" ]; then
  export JAVA_HOME="$JAVA21_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
else
  echo "⚠️  Java 21 not found via /usr/libexec/java_home. Install from https://www.oracle.com/java/technologies/downloads/#java21"
  echo "   Current java: $(java -version 2>&1 | head -1)"
fi

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✅  $*${NC}"; }
warn() { echo -e "${YELLOW}⚠️   $*${NC}"; }
info() { echo -e "${CYAN}ℹ️   $*${NC}"; }
step() { echo -e "\n${BOLD}${GREEN}▶ $*${NC}"; }

port_name() {
  case $1 in
    5433) echo "PostgreSQL" ;; 6379) echo "Redis" ;; 27017) echo "MongoDB" ;;
    9094) echo "Kafka" ;; 8180) echo "Keycloak" ;; 8123) echo "ClickHouse HTTP" ;;
    9000) echo "ClickHouse Native" ;; 3100) echo "Grafana" ;; 8091) echo "AI Platform" ;;
    8090) echo "Kafka UI" ;; 8080) echo "API Gateway" ;; 8081) echo "User Service" ;;
    8082) echo "Transaction Service" ;; 8083) echo "Ledger Service" ;;
    8084) echo "Payment Orchestrator" ;; 8085) echo "Risk Engine" ;;
    8086) echo "Notification Service" ;; 8087) echo "Reconciliation Service" ;;
    8089) echo "Data Pipeline" ;; 3000) echo "Web App" ;; *) echo "Unknown" ;;
  esac
}

# ── Port conflict check + auto-kill ───────────────────────────────────────────
step "Freeing required ports..."
INFRA_PORTS="5433 6379 27017 9094 8180 8123 3100 8090"
SVC_PORTS="8080 8081 8082 8083 8084 8085 8086 8087 8089 8091 3000"

for port in $INFRA_PORTS $SVC_PORTS; do
  PIDS=$(lsof -ti:$port 2>/dev/null || true)
  if [ -n "$PIDS" ]; then
    echo "$PIDS" | xargs kill -9 2>/dev/null || true
    ok "Killed PID(s) on port $port → $(port_name $port)"
  fi
done
ok "All ports free"

# ── Build all services ─────────────────────────────────────────────────────────
step "Building shared libs + all services (skipping tests)..."
mvn --batch-mode --no-transfer-progress -f libs/pom.xml clean install \
  -DskipTests -q
ok "Shared libs built"

ALL_SERVICES="api-gateway user-service transaction-service ledger-service \
  payment-orchestrator risk-engine notification-service \
  ai-platform data-pipeline reconciliation-service"

for svc in $ALL_SERVICES; do
  printf "  ↳ building %-35s" "$svc ..."
  mvn --batch-mode --no-transfer-progress package \
    -pl "services/$svc" --also-make \
    -DskipTests -q
  echo " done"
done
ok "All service JARs built"

# ── Infrastructure ─────────────────────────────────────────────────────────────
step "Starting infrastructure..."
if ! docker info > /dev/null 2>&1; then
  warn "Docker daemon not running — starting Docker Desktop..."
  open -a Docker
  printf "  ⏳  waiting for Docker daemon ..."
  until docker info > /dev/null 2>&1; do printf "."; sleep 2; done
  echo ""
fi
ok "Docker ready"

step "Tearing down previous infra (clean slate)..."
docker compose down -v --remove-orphans 2>/dev/null || true
ok "Previous infra removed"

docker compose up -d

step "Waiting for Keycloak realm to be ready..."
printf "  ⏳  waiting for Keycloak ..."
while true; do
  if curl -sf http://localhost:8180/realms/aegispay > /dev/null 2>&1; then
    echo ""; ok "Keycloak ready"; break
  fi
  printf "."; sleep 3
done

step "Configuring Keycloak client scopes via Admin API..."
infra/local/keycloak/configure-keycloak.sh
ok "Keycloak configured"

step "Seeding Google and Microsoft OAuth identity providers in Keycloak..."
KC_ADMIN_URL="http://localhost:8180"
KC_REALM="aegispay"
KC_TOKEN=$(curl -sf -X POST "${KC_ADMIN_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=admin-cli&username=admin&password=admin" \
  2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null || true)

if [[ -z "$KC_TOKEN" ]]; then
  warn "Could not obtain Keycloak admin token — skipping IDP seeding (Keycloak may not be ready yet)"
else
  # Google IDP
  GOOGLE_BODY="{\"alias\":\"google\",\"providerId\":\"google\",\"displayName\":\"Google\",\"enabled\":true,\"config\":{\"clientId\":\"${GOOGLE_CLIENT_ID}\",\"clientSecret\":\"${GOOGLE_CLIENT_SECRET}\",\"defaultScope\":\"email profile openid\"}}"
  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "${KC_ADMIN_URL}/admin/realms/${KC_REALM}/identity-provider/instances" \
    -H "Authorization: Bearer ${KC_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$GOOGLE_BODY" 2>/dev/null)
  if [[ "$HTTP_STATUS" == "201" ]]; then
    ok "Google IDP created"
  elif [[ "$HTTP_STATUS" == "409" ]]; then
    # Already exists from realm import — update credentials via PUT so real keys take effect
    PUT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT \
      "${KC_ADMIN_URL}/admin/realms/${KC_REALM}/identity-provider/instances/google" \
      -H "Authorization: Bearer ${KC_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "$GOOGLE_BODY" 2>/dev/null)
    if [[ "$PUT_STATUS" == "204" ]]; then
      ok "Google IDP credentials updated"
    else
      warn "Google IDP update: HTTP ${PUT_STATUS}"
    fi
  else
    warn "Google IDP: HTTP ${HTTP_STATUS}"
  fi

  # Microsoft IDP
  MICROSOFT_BODY="{\"alias\":\"microsoft\",\"providerId\":\"microsoft\",\"displayName\":\"Microsoft\",\"enabled\":true,\"config\":{\"clientId\":\"${MICROSOFT_CLIENT_ID}\",\"clientSecret\":\"${MICROSOFT_CLIENT_SECRET}\",\"tenantId\":\"${MICROSOFT_TENANT_ID}\",\"defaultScope\":\"openid email profile User.Read\"}}"
  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "${KC_ADMIN_URL}/admin/realms/${KC_REALM}/identity-provider/instances" \
    -H "Authorization: Bearer ${KC_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$MICROSOFT_BODY" 2>/dev/null)
  if [[ "$HTTP_STATUS" == "201" ]]; then
    ok "Microsoft IDP created"
  elif [[ "$HTTP_STATUS" == "409" ]]; then
    # Already exists from realm import — update credentials via PUT so real keys take effect
    PUT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT \
      "${KC_ADMIN_URL}/admin/realms/${KC_REALM}/identity-provider/instances/microsoft" \
      -H "Authorization: Bearer ${KC_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "$MICROSOFT_BODY" 2>/dev/null)
    if [[ "$PUT_STATUS" == "204" ]]; then
      ok "Microsoft IDP credentials updated"
    else
      warn "Microsoft IDP update: HTTP ${PUT_STATUS}"
    fi
  else
    warn "Microsoft IDP: HTTP ${HTTP_STATUS}"
  fi
fi

# ── Seed admin and back-office users in Keycloak ─────────────────────────────
if [[ -n "$KC_TOKEN" ]]; then
  step "Seeding admin and back-office users in Keycloak..."
  seed_kc_user() {
    local username="$1" firstname="$2" lastname="$3" email="$4" password="$5" role="$6"
    local body="{\"username\":\"${username}\",\"firstName\":\"${firstname}\",\"lastName\":\"${lastname}\",\"email\":\"${email}\",\"emailVerified\":true,\"enabled\":true,\"credentials\":[{\"type\":\"password\",\"value\":\"${password}\",\"temporary\":false}],\"attributes\":{\"aegispay_role\":[\"${role}\"],\"aegispay_tenant_id\":[\"default\"]}}"
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
      "${KC_ADMIN_URL}/admin/realms/${KC_REALM}/users" \
      -H "Authorization: Bearer ${KC_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "$body" 2>/dev/null)
    if [[ "$status" == "201" ]]; then
      ok "  ${username} created"
    elif [[ "$status" == "409" ]]; then
      ok "  ${username} already exists"
    else
      warn "  ${username} create: HTTP ${status}"
    fi
    # Fetch user ID
    local uid
    uid=$(curl -s "${KC_ADMIN_URL}/admin/realms/${KC_REALM}/users?username=${username}&exact=true" \
      -H "Authorization: Bearer ${KC_TOKEN}" 2>/dev/null \
      | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['id'] if d else '')" 2>/dev/null)
    [[ -z "$uid" ]] && { warn "  ${username} -> user ID not found, skipping role assign"; return; }
    # Fetch role representation (id + name) required by Keycloak role-mappings API
    local role_json role_id role_name
    role_json=$(curl -s "${KC_ADMIN_URL}/admin/realms/${KC_REALM}/roles/${role}" \
      -H "Authorization: Bearer ${KC_TOKEN}" 2>/dev/null)
    role_id=$(echo   "$role_json" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('id',''))"   2>/dev/null)
    role_name=$(echo "$role_json" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('name',''))" 2>/dev/null)
    [[ -z "$role_id" ]] && { warn "  ${username} -> role ${role} not found in realm"; return; }
    # Assign realm role (idempotent — 204 on success or already assigned)
    local rs
    rs=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
      "${KC_ADMIN_URL}/admin/realms/${KC_REALM}/users/${uid}/role-mappings/realm" \
      -H "Authorization: Bearer ${KC_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "[{\"id\":\"${role_id}\",\"name\":\"${role_name}\"}]" 2>/dev/null)
    if [[ "$rs" == "204" ]]; then
      ok "  ${username} -> role ${role} assigned"
    else
      warn "  ${username} -> role assign: HTTP ${rs}"
    fi
  }
  seed_kc_user "admin"      "AegisPay" "Admin"  "admin@aegispay.local"      "Admin@1234" "ADMIN"
  seed_kc_user "backoffice" "Back"     "Office" "backoffice@aegispay.local" "BO@1234"    "BACK_OFFICE"
fi

step "Waiting for ClickHouse to be ready..."
printf "  ⏳  waiting for ClickHouse :8123 ..."
while true; do
  if curl -sf http://localhost:8123/ping 2>/dev/null | grep -q "Ok"; then
    echo ""; ok "ClickHouse ready"; break
  fi
  printf "."; sleep 5
done

step "Waiting for Grafana to be ready..."
printf "  ⏳  waiting for Grafana :3100 ..."
while true; do
  if curl -sf http://localhost:3100/api/health 2>/dev/null | grep -q "ok"; then
    echo ""; ok "Grafana ready"; break
  fi
  printf "."; sleep 5
done

step "Waiting for Kafka broker to be ready..."
printf "  ⏳  waiting for Kafka :9094 ..."
while true; do
  if docker exec aegispay-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
    echo ""; ok "Kafka ready"; break
  fi
  printf "."; sleep 3
done

CUSTOMER_KC_UUID="59295e61-a284-40ed-8d3b-9e15bedeb040"
PAYEE_KC_UUID="3bf3e523-9de8-4254-9cc9-d5fa50ff8d4a"

# ── Backend services ───────────────────────────────────────────────────────────
step "Starting backend services..."
WAVE1="api-gateway user-service transaction-service ledger-service \
payment-orchestrator risk-engine notification-service \
ai-platform data-pipeline"
WAVE2="reconciliation-service"

start_svc() {
  svc=$1
  JAR="services/$svc/target/$svc-1.0.0-SNAPSHOT.jar"
  if [ ! -f "$JAR" ]; then
    warn "JAR not found for $svc — skipping"; return
  fi
  java -jar "$JAR" > /tmp/aegispay-$svc.log 2>&1 &
  echo "  ↳ $svc  (PID $!  |  log: /tmp/aegispay-$svc.log)"
}

for svc in $WAVE1; do start_svc "$svc"; done

printf "  ⏳  waiting for ledger-service :8083 before reconciliation..."
while true; do
  code=$(curl -sf -o /dev/null -w "%{http_code}" http://localhost:8083/actuator/health 2>/dev/null || echo "000")
  [ "$code" = "200" ] && break
  printf "."; sleep 3
done
echo ""; ok "ledger-service ready — starting reconciliation-service"
for svc in $WAVE2; do start_svc "$svc"; done

step "Waiting for all services to be healthy..."
CHECKS="8080:api-gateway 8081:user-service 8082:transaction-service \
        8083:ledger-service 8084:payment-orchestrator 8085:risk-engine \
        8086:notification-service 8087:reconciliation-service \
        8089:data-pipeline 8091:ai-platform"

for pair in $CHECKS; do
  port="${pair%%:*}"; svc="${pair##*:}"
  printf "  ⏳  waiting for %-30s" "$svc :$port ..."
  while true; do
    code=$(curl -sf -o /dev/null -w "%{http_code}" \
      "http://localhost:$port/actuator/health" 2>/dev/null || echo "000")
    if [ "$code" = "200" ]; then
      echo ""; ok "$svc :$port"; break
    fi
    printf "."; sleep 3
  done
done

# ── Seed test data ─────────────────────────────────────────────────────────────
step "Seeding test accounts..."
docker exec aegispay-postgres psql -U aegispay -d aegispay_ledger -c "
INSERT INTO accounts (user_id, currency, available_balance, reserved_balance)
VALUES
  ('${CUSTOMER_KC_UUID}', 'INR', 50000.00, 0.00),
  ('${PAYEE_KC_UUID}',   'INR', 25000.00, 0.00)
ON CONFLICT (user_id, currency) DO NOTHING;
" > /dev/null 2>&1 || warn "Ledger seed skipped"

docker exec aegispay-postgres psql -U aegispay -d aegispay_users -c "
INSERT INTO users (external_id, email, first_name, last_name, phone, role, kyc_status, is_active)
VALUES
  ('${CUSTOMER_KC_UUID}', 'customer@aegispay.local', 'Test', 'Customer', '+919000000001', 'CUSTOMER', 'APPROVED', true),
  ('${PAYEE_KC_UUID}',    'payee@aegispay.local',    'Test', 'Payee',    '+919000000002', 'CUSTOMER', 'APPROVED', true)
ON CONFLICT DO NOTHING;
" > /dev/null 2>&1 || warn "User seed skipped"
ok "Test data seeded — customer ₹50,000 | payee ₹25,000"

# ── Web app ────────────────────────────────────────────────────────────────────
step "Starting web app..."
if [ ! -f apps/web/.env.local ]; then
  cp apps/web/.env.local.example apps/web/.env.local
  SECRET=$(openssl rand -base64 32)
  sed -i.bak "s|REPLACE_WITH_RANDOM_32_BYTE_SECRET|${SECRET}|" apps/web/.env.local && rm -f apps/web/.env.local.bak
  ok "Created apps/web/.env.local with generated NEXTAUTH_SECRET"
fi
npm install --silent --prefer-offline
npm run dev --workspace=apps/web > /tmp/aegispay-web.log 2>&1 &
WEB_PID=$!
echo "  ↳ Web app (PID $WEB_PID  |  log: /tmp/aegispay-web.log)"

# ─────────────────────────────────────────────────────────────────────────────
# ── ANDROID LOCAL TESTING ────────────────────────────────────────────────────
# ─────────────────────────────────────────────────────────────────────────────
if [ "$START_ANDROID" = "1" ]; then
  step "Setting up Android local dev..."

  # Detect ANDROID_HOME
  if [ -z "$ANDROID_HOME" ]; then
    # Common macOS locations
    for candidate in \
      "$HOME/Library/Android/sdk" \
      "$HOME/Android/Sdk" \
      "/usr/local/lib/android/sdk"; do
      if [ -d "$candidate" ]; then
        export ANDROID_HOME="$candidate"
        export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
        break
      fi
    done
  fi

  if [ -z "$ANDROID_HOME" ]; then
    warn "ANDROID_HOME not set and SDK not found in common locations."
    warn "Install Android Studio and set: export ANDROID_HOME=\$HOME/Library/Android/sdk"
  else
    ok "ANDROID_HOME = $ANDROID_HOME"

    # ── Inject STRIPE_PUBLISHABLE_KEY into gradle.properties ─────────────────
    GRADLE_PROPS="apps/android/gradle.properties"
    if ! grep -q "STRIPE_PUBLISHABLE_KEY" "$GRADLE_PROPS" 2>/dev/null; then
      echo "STRIPE_PUBLISHABLE_KEY=${STRIPE_PUBLISHABLE_KEY}" >> "$GRADLE_PROPS"
      ok "Added STRIPE_PUBLISHABLE_KEY to gradle.properties"
    fi

    # Determine host IP for Android (emulator uses 10.0.2.2, physical device needs LAN IP)
    if [ -n "$DEVICE_IP" ]; then
      ANDROID_HOST="$DEVICE_IP"
      info "Physical device mode — using host IP: $ANDROID_HOST"
    else
      ANDROID_HOST="10.0.2.2"
      info "Emulator mode — host address: $ANDROID_HOST (10.0.2.2)"
    fi

    # ── Start emulator if none running ────────────────────────────────────────
    if ! adb devices 2>/dev/null | grep -q "emulator"; then
      # List available AVDs
      AVDS=$("$ANDROID_HOME/emulator/emulator" -list-avds 2>/dev/null || true)
      AVD=$(echo "$AVDS" | grep -v "^$" | head -1)
      if [ -n "$AVD" ]; then
        step "Starting Android emulator: $AVD"
        "$ANDROID_HOME/emulator/emulator" \
          -avd "$AVD" \
          -no-snapshot-save \
          -no-boot-anim \
          -no-audio \
          -gpu swiftshader_indirect \
          > /tmp/aegispay-emulator.log 2>&1 &
        EMULATOR_PID=$!
        echo "  ↳ Emulator PID $EMULATOR_PID | log: /tmp/aegispay-emulator.log"

        # Wait for emulator to finish booting
        printf "  ⏳  waiting for emulator to boot ..."
        while true; do
          BOOT=$(adb -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
          [ "$BOOT" = "1" ] && break
          printf "."; sleep 3
        done
        echo ""; ok "Emulator booted"
      else
        warn "No AVDs found. Create one in Android Studio → Tools → Device Manager."
        warn "Recommended: Pixel 7 API 35 (x86_64, without Play Store)"
      fi
    else
      ok "Android emulator/device already connected"
    fi

    # ── Build debug APK and install ───────────────────────────────────────────
    step "Building Android debug APK..."
    (
      cd apps/android
      ./gradlew installDebug \
        -PAPI_BASE_URL="http://${ANDROID_HOST}:8080" \
        -PWS_BASE_URL="ws://${ANDROID_HOST}:8080" \
        -PKEYCLOAK_ISSUER="http://${ANDROID_HOST}:8180/realms/aegispay" \
        -PSTRIPE_PUBLISHABLE_KEY="${STRIPE_PUBLISHABLE_KEY}" \
        --no-daemon \
        > /tmp/aegispay-android-build.log 2>&1
    )
    ok "APK installed on device/emulator"

    # Launch the app
    adb shell am start -n com.aegispay.android.debug/com.aegispay.android.ui.MainActivity \
      > /dev/null 2>&1 && ok "AegisPay Android launched" || warn "Could not auto-launch app — open it manually"
  fi
fi

# ─────────────────────────────────────────────────────────────────────────────
# ── iOS LOCAL TESTING (macOS only) ──────────────────────────────────────────
# ─────────────────────────────────────────────────────────────────────────────
if [ "$START_IOS" = "1" ]; then
  step "Setting up iOS local dev..."

  if ! command -v xcrun &>/dev/null; then
    warn "Xcode Command Line Tools not found. Install with: xcode-select --install"
  else
    ok "Xcode tools found: $(xcode-select -p)"

    # Determine host for iOS (simulator uses localhost; physical device needs LAN IP)
    if [ -n "$DEVICE_IP" ]; then
      IOS_HOST="$DEVICE_IP"
      info "Physical device mode — using host IP: $IOS_HOST"
    else
      IOS_HOST="localhost"
      info "Simulator mode — host address: $IOS_HOST"
    fi

    # ── Write iOS local xcconfig (injects build settings) ────────────────────
    XCCONFIG="apps/ios/LocalDev.xcconfig"
    cat > "$XCCONFIG" << EOF
// Auto-generated by start-local.sh — do NOT commit.
// Overrides Info.plist build settings for local dev.
API_BASE_URL = http://${IOS_HOST}:8080
WS_BASE_URL = ws://${IOS_HOST}:8080
KEYCLOAK_ISSUER = http://${IOS_HOST}:8180/realms/aegispay
OAUTH_CLIENT_ID = aegispay-ios
STRIPE_PUBLISHABLE_KEY = ${STRIPE_PUBLISHABLE_KEY}
EOF
    ok "Created $XCCONFIG (API → http://${IOS_HOST}:8080)"

    # ── Add STRIPE_PUBLISHABLE_KEY to Info.plist if not already there ─────────
    PLIST="apps/ios/AegisPay/App/Info.plist"
    if ! grep -q "STRIPE_PUBLISHABLE_KEY" "$PLIST" 2>/dev/null; then
      # Insert after the OAUTH_CLIENT_ID entry
      sed -i.bak 's|<key>OAUTH_CLIENT_ID<\/key>|<key>STRIPE_PUBLISHABLE_KEY<\/key>\
\t<string>$(STRIPE_PUBLISHABLE_KEY)<\/string>\
\n\t<key>OAUTH_CLIENT_ID<\/key>|' "$PLIST" && rm -f "${PLIST}.bak"
      ok "Added STRIPE_PUBLISHABLE_KEY to Info.plist"
    fi

    # ── Boot iOS Simulator ────────────────────────────────────────────────────
    # Pick the first available iPhone simulator (prefer iPhone 16, fallback to any)
    SIM_UDID=$(xcrun simctl list devices available -j 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
preferred = ['iPhone 16 Pro', 'iPhone 16', 'iPhone 15 Pro', 'iPhone 15', 'iPhone 14']
all_iphones = []
for runtime, devices in data.get('devices', {}).items():
    if 'iOS' not in runtime:
        continue
    for dev in devices:
        if 'iPhone' in dev.get('name','') and dev.get('isAvailable', False) and dev.get('state','') != 'unavailable':
            all_iphones.append((dev['name'], dev['udid']))
for pref in preferred:
    for name, udid in all_iphones:
        if pref in name:
            print(udid); sys.exit(0)
if all_iphones:
    print(all_iphones[0][1])
" 2>/dev/null || true)

    SIM_NAME=$(xcrun simctl list devices available 2>/dev/null | grep "$SIM_UDID" | sed 's/ (.*//' | xargs || echo "Unknown")

    if [ -n "$SIM_UDID" ] && [ "$SIM_UDID" != "None" ]; then
      BOOT_STATE=$(xcrun simctl list devices 2>/dev/null | grep "$SIM_UDID" | grep -o "Booted" || true)
      if [ "$BOOT_STATE" != "Booted" ]; then
        xcrun simctl boot "$SIM_UDID" 2>/dev/null || true
        ok "iOS Simulator booting: $SIM_NAME ($SIM_UDID)"
      else
        ok "iOS Simulator already booted: $SIM_NAME"
      fi
      open -a Simulator 2>/dev/null || true
    else
      warn "No iPhone simulator found. Open Xcode → Window → Devices & Simulators to create one."
    fi

    # ── Open project in Xcode ─────────────────────────────────────────────────
    step "Opening iOS project in Xcode..."
    # Package.swift opens as a Swift Package in Xcode
    open apps/ios/Package.swift
    ok "Xcode opening — in Xcode:"
    echo "    1. Select scheme 'AegisPay' + your simulator"
    echo "    2. Product → Scheme → Edit Scheme → Run → Arguments → Environment Variables"
    echo "       (or use LocalDev.xcconfig — already written to apps/ios/LocalDev.xcconfig)"
    echo "    3. Press ▶ Run (Cmd+R)"
  fi
fi

# ── Summary ────────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ok "AegisPay is running!"
echo ""
echo "  🌐  Web            → http://localhost:3000"
echo "  🔌  API Gateway    → http://localhost:8080/actuator/health"
echo "  🔑  Keycloak       → http://localhost:8180  (admin / admin)"
echo "  📨  Kafka UI       → http://localhost:8090"
echo "  📊  Grafana        → http://localhost:3100  (admin / admin)"
echo "  🗄️  ClickHouse     → http://localhost:8123"
echo "  🐘  PostgreSQL     → localhost:5433  (aegispay / aegispay_dev)"
echo ""
echo "  Test accounts:"
echo "    Sender     : customer@aegispay.local    / Test@1234    (₹50,000)"
echo "    Payee      : payee@aegispay.local       / Test@1234    (₹25,000)"
echo "    Admin      : admin@aegispay.local        / Admin@1234   (role: ADMIN)"
echo "    Back-office: backoffice@aegispay.local   / BO@1234      (role: BACK_OFFICE)"
echo "    Payee UUID : ${PAYEE_KC_UUID}"
echo ""

# ── Android testing instructions ──────────────────────────────────────────────
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  📱  ANDROID TESTING"
echo ""
if [ "$START_ANDROID" = "1" ]; then
  echo "  ✅  APK built and installed (log: /tmp/aegispay-android-build.log)"
  echo "  ✅  Emulator running"
else
  echo "  ➜  Run with --android to auto-build and install:"
  echo "     ./start-local.sh --android"
  echo ""
  echo "  Manual steps (emulator):"
  echo "    1. Open Android Studio → Run on emulator  OR"
  echo "       cd apps/android && ./gradlew installDebug && \\"
  echo "       adb shell am start -n com.aegispay.android.debug/com.aegispay.android.ui.MainActivity"
  echo ""
  echo "  Manual steps (physical device):"
  echo "    1. Find your Mac's LAN IP: ipconfig getifaddr en0"
  echo "    2. ./start-local.sh --android --device-ip <your-mac-ip>"
  echo "       OR manually pass to gradle:"
  echo "       cd apps/android && ./gradlew installDebug \\"
  echo "         -PAPI_BASE_URL=http://<mac-ip>:8080 \\"
  echo "         -PKEYCLOAK_ISSUER=http://<mac-ip>:8180/realms/aegispay \\"
  echo "         -PSTRIPE_PUBLISHABLE_KEY=${STRIPE_PUBLISHABLE_KEY}"
fi
echo ""
echo "  Network (emulator → host):"
echo "    API:      http://10.0.2.2:8080   (auto-configured)"
echo "    Keycloak: http://10.0.2.2:8180   (auto-configured)"
echo "    WS:       ws://10.0.2.2:8080     (auto-configured)"
echo ""

# ── iOS testing instructions ──────────────────────────────────────────────────
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  🍎  iOS TESTING (macOS only)"
echo ""
if [ "$START_IOS" = "1" ]; then
  if command -v xcrun &>/dev/null; then
    echo "  ✅  Simulator booting — Xcode opened"
    echo "  ✅  LocalDev.xcconfig written to apps/ios/LocalDev.xcconfig"
    echo ""
    echo "  In Xcode:"
    echo "    • Select scheme 'AegisPay' + an iPhone simulator"
    echo "    • Press ▶  Run (Cmd+R)"
  fi
else
  echo "  ➜  Run with --ios to auto-boot simulator and open Xcode:"
  echo "     ./start-local.sh --ios"
  echo ""
  echo "  Manual steps:"
  echo "    1. open apps/ios/Package.swift          ← opens in Xcode"
  echo "    2. Select scheme 'AegisPay' + iPhone 16 simulator"
  echo "    3. Press ▶ Run (Cmd+R)"
fi
echo ""
echo "  Network (simulator → host):"
echo "    API:      http://localhost:8080   (auto-configured)"
echo "    Keycloak: http://localhost:8180   (auto-configured)"
echo "    WS:       ws://localhost:8080     (auto-configured)"
echo ""
echo "  Physical device:"
echo "    1. Find your Mac IP: ipconfig getifaddr en0"
echo "    2. ./start-local.sh --ios --device-ip <mac-ip>"
echo "       (writes the IP into apps/ios/LocalDev.xcconfig)"
echo "    3. In Xcode: set your iPhone as destination + Run"
echo ""

# ── Stop instructions ──────────────────────────────────────────────────────────
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Stop everything:"
echo "    docker compose down && pkill -f 'aegispay' 2>/dev/null || true"
echo "    # Kill emulator: adb emu kill"
echo "    # Kill simulator: xcrun simctl shutdown all"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
