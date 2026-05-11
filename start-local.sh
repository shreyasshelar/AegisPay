#!/bin/bash
# Compatible with macOS (BSD sed/bash 3.2) and Linux (GNU sed/bash 5+)
set -e

# ── Node.js DNS fix — prefer IPv4 so localhost -> 127.0.0.1 not ::1 ──────────
# Without this, Node.js (Next.js) tries IPv6 first which times out on macOS
# when Keycloak only listens on IPv4, causing OAuthSignin errors.
export NODE_OPTIONS="${NODE_OPTIONS:---dns-result-order=ipv4first}"

# ── Dev secrets (exported so Spring Boot services inherit them) ────────────────
export DB_PORT="${DB_PORT:-5433}"

# ── OAuth2 / Keycloak (Keycloak runs on 8180 locally, not 8080 which is the gateway) ──
export OAUTH2_ISSUER_URI="http://localhost:8180/realms/aegispay"
export OAUTH2_PRIMARY_ISSUER_URI="http://localhost:8180/realms/aegispay"
export OAUTH2_ISSUER_KEYCLOAK="http://localhost:8180/realms/aegispay"

# ── Service-to-service URIs (override K8s DNS defaults for local dev) ─────────
export USER_SERVICE_URI="http://localhost:8081"
export TRANSACTION_SERVICE_URI="http://localhost:8082"
export LEDGER_SERVICE_URI="http://localhost:8083"
export ORCHESTRATOR_SERVICE_URI="http://localhost:8084"
export RISK_ENGINE_URI="http://localhost:8085"
export NOTIFICATION_SERVICE_URI="http://localhost:8086"
export AI_PLATFORM_URI="http://localhost:8091"
export STRIPE_SECRET_KEY="${STRIPE_SECRET_KEY:-sk_test_51TTkk2CyjRW67i1DP4dcrEgzhOm9dUe61k9U5kPNoDST6Deuy9rAvgJY0ZL93kKbDmdP7SEAUXUM6M4TMMtxkWNb00eKgRIql5}"
# Gmail App Password (aegispay.dev@gmail.com) — spaces stripped per Google convention
export SMTP_PASSWORD="${SMTP_PASSWORD:-mcinrqrbfqayklee}"
# Slack webhook for alertmanager dev alerts
export SLACK_WEBHOOK_URL="${SLACK_WEBHOOK_URL:-https://hooks.slack.com/services/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}"
# Fast2SMS API key — Quick route, no DLT required for personal projects
export FAST2SMS_API_KEY="${FAST2SMS_API_KEY:-ZNd8Xx4lqrERbj67Unwi1LHvB0smOFDayTCkgczIYKM9oPAfV2jDTskbhao0QZ3luvA7VfiLdWM2KNOe}"

# ── Java 21 required — point Maven at the right JDK ───────────────────────────
JAVA21_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
if [ -n "$JAVA21_HOME" ]; then
  export JAVA_HOME="$JAVA21_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
else
  echo "⚠️  Java 21 not found via /usr/libexec/java_home. Install from https://www.oracle.com/java/technologies/downloads/#java21"
  echo "   Current java: $(java -version 2>&1 | head -1)"
fi

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✅  $*${NC}"; }
warn() { echo -e "${YELLOW}⚠️   $*${NC}"; }
step() { echo -e "\n${GREEN}▶ $*${NC}"; }

port_name() {
  case $1 in
    5433) echo "PostgreSQL" ;;
    6379) echo "Redis" ;;
    27017) echo "MongoDB" ;;
    9094) echo "Kafka" ;;
    8180) echo "Keycloak" ;;
    8123) echo "ClickHouse" ;;
    8088) echo "Superset" ;;
    8091) echo "AI Platform" ;;
    8090) echo "Kafka UI" ;;
    8080) echo "API Gateway" ;;
    8081) echo "User Service" ;;
    8082) echo "Transaction Service" ;;
    8083) echo "Ledger Service" ;;
    8084) echo "Payment Orchestrator" ;;
    8085) echo "Risk Engine" ;;
    8086) echo "Notification Service" ;;
    8087) echo "Reconciliation Service" ;;
    8089) echo "Data Pipeline" ;;
    3000) echo "Web App" ;;
    *) echo "Unknown" ;;
  esac
}

# ── Port conflict check + auto-kill ───────────────────────────────────────────
step "Freeing required ports..."
INFRA_PORTS="5433 6379 27017 9094 8180 8123 8088 8090"
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
mvn --batch-mode --no-transfer-progress clean package \
  -pl libs/common-domain,libs/common-security,libs/common-kafka,libs/common-observability \
  --also-make \
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

# Tear down existing infra so Keycloak always starts with a fresh DB.
# This guarantees KC_BOOTSTRAP_ADMIN_* fires and configure-keycloak.sh succeeds.
# Volumes are recreated instantly by db-init and Flyway migrations on every start.
step "Tearing down previous infra (clean slate)..."
docker compose down -v --remove-orphans 2>/dev/null || true
ok "Previous infra removed"

docker compose up -d

step "Waiting for Keycloak realm to be ready (polling /realms/aegispay)..."
printf "  ⏳  waiting for Keycloak ..."
while true; do
  if curl -sf http://localhost:8180/realms/aegispay > /dev/null 2>&1; then
    echo ""
    ok "Keycloak ready"
    break
  fi
  printf "."
  sleep 3
done

step "Configuring Keycloak client scopes via Admin API..."
infra/local/keycloak/configure-keycloak.sh
ok "Keycloak configured"

step "Waiting for Kafka broker to be ready..."
printf "  ⏳  waiting for Kafka :9094 ..."
while true; do
  if docker exec aegispay-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
    echo ""
    ok "Kafka ready"
    break
  fi
  printf "."
  sleep 3
done

# UUIDs fixed by realm-export.json — pinned for stable DB seeds across restarts
CUSTOMER_KC_UUID="59295e61-a284-40ed-8d3b-9e15bedeb040"
PAYEE_KC_UUID="3bf3e523-9de8-4254-9cc9-d5fa50ff8d4a"

# ── Backend services ───────────────────────────────────────────────────────────
step "Starting backend services..."
# ledger-service must be healthy before reconciliation-service starts
# (reconciliation validates ledger_entries table via ddl-auto:validate)
WAVE1="api-gateway user-service transaction-service ledger-service \
payment-orchestrator risk-engine notification-service \
ai-platform data-pipeline"
WAVE2="reconciliation-service"

start_svc() {
  svc=$1
  JAR="services/$svc/target/$svc-1.0.0-SNAPSHOT.jar"
  if [ ! -f "$JAR" ]; then
    warn "JAR not found for $svc — skipping"
    return
  fi
  java -jar "$JAR" > /tmp/aegispay-$svc.log 2>&1 &
  echo "  ↳ $svc  (PID $!  |  log: /tmp/aegispay-$svc.log)"
}

for svc in $WAVE1; do start_svc "$svc"; done

# Wait for ledger-service before starting reconciliation
printf "  ⏳  waiting for ledger-service :8083 before reconciliation..."
while true; do
  code=$(curl -sf -o /dev/null -w "%{http_code}" http://localhost:8083/actuator/health 2>/dev/null || echo "000")
  [ "$code" = "200" ] && break
  printf "."; sleep 3
done
echo ""; ok "ledger-service ready — starting reconciliation-service"

for svc in $WAVE2; do start_svc "$svc"; done

step "Waiting for all services to be healthy (polls every 3s, no fixed timeout)..."
CHECKS="8080:api-gateway 8081:user-service 8082:transaction-service \
        8083:ledger-service 8084:payment-orchestrator 8085:risk-engine \
        8086:notification-service 8087:reconciliation-service \
        8089:data-pipeline 8091:ai-platform"

ALL_OK=1
for pair in $CHECKS; do
  port="${pair%%:*}"
  svc="${pair##*:}"
  printf "  ⏳  waiting for %-30s" "$svc :$port ..."
  while true; do
    code=$(curl -sf -o /dev/null -w "%{http_code}" \
      "http://localhost:$port/actuator/health" 2>/dev/null || echo "000")
    if [ "$code" = "200" ]; then
      echo ""
      ok "$svc :$port"
      break
    fi
    printf "."
    sleep 3
  done
done

# ── Seed test data (Flyway has run by now — tables exist) ─────────────────────
step "Seeding test accounts..."
docker exec aegispay-postgres psql -U aegispay -d aegispay_ledger -c "
INSERT INTO accounts (user_id, currency, available_balance, reserved_balance)
VALUES
  ('${CUSTOMER_KC_UUID}', 'INR', 50000.00, 0.00),
  ('${PAYEE_KC_UUID}',   'INR', 25000.00, 0.00)
ON CONFLICT (user_id, currency) DO NOTHING;
" > /dev/null 2>&1 || warn "Ledger seed skipped (accounts may already exist)"

docker exec aegispay-postgres psql -U aegispay -d aegispay_users -c "
INSERT INTO users (external_id, email, first_name, last_name, phone, role, kyc_status, is_active)
VALUES
  ('${CUSTOMER_KC_UUID}', 'customer@aegispay.local', 'Test', 'Customer', '+919000000001', 'CUSTOMER', 'APPROVED', true),
  ('${PAYEE_KC_UUID}',    'payee@aegispay.local',    'Test', 'Payee',    '+919000000002', 'CUSTOMER', 'APPROVED', true)
ON CONFLICT DO NOTHING;
" > /dev/null 2>&1 || warn "User seed skipped (users may already exist)"
ok "Test data seeded — customer ₹50,000 | payee ₹25,000"

# ── Web app ────────────────────────────────────────────────────────────────────
step "Starting web app..."
if [ ! -f apps/web/.env.local ]; then
  cp apps/web/.env.local.example apps/web/.env.local
  SECRET=$(openssl rand -base64 32)
  # -i.bak is portable: works on both BSD (macOS) sed and GNU (Linux) sed
  sed -i.bak "s|REPLACE_WITH_RANDOM_32_BYTE_SECRET|${SECRET}|" apps/web/.env.local && rm -f apps/web/.env.local.bak
  ok "Created apps/web/.env.local with generated NEXTAUTH_SECRET"
fi
npm install --silent --prefer-offline
npm run dev --workspace=apps/web > /tmp/aegispay-web.log 2>&1 &
echo "  ↳ Web app (PID $!  |  log: /tmp/aegispay-web.log)"

# ── Summary ────────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ "$ALL_OK" = "1" ]; then
  ok "All services healthy!"
else
  warn "Some services still starting — check logs above"
fi
echo ""
echo "  🌐  Web app      → http://localhost:3000"
echo "  🔌  API Gateway  → http://localhost:8080/actuator/health"
echo "  🔑  Keycloak     → http://localhost:8180  (admin / admin)"
echo "  📨  Kafka UI     → http://localhost:8090"
echo "  🤖  AI Platform  → http://localhost:8091/actuator/health"
echo "  📊  Superset     → http://localhost:8088  (admin / admin)"
echo "  🐘  PostgreSQL   → localhost:5433         (aegispay / aegispay_dev)"
echo ""
echo "  Test accounts:"
echo "    Sender : customer@aegispay.local / Test@1234  (₹50,000)"
echo "    Payee  : payee@aegispay.local    / Test@1234  (₹25,000)"
echo "    Payee UUID (use in Send Money): ${PAYEE_KC_UUID}"
echo ""
echo "  Stop everything:"
echo "    docker compose down && pkill -f 'aegispay'"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
