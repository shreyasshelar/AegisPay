#!/bin/bash
# Compatible with macOS default Bash 3.2 (no associative arrays)
set -e

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✅  $*${NC}"; }
warn() { echo -e "${YELLOW}⚠️   $*${NC}"; }
step() { echo -e "\n${GREEN}▶ $*${NC}"; }

port_name() {
  case $1 in
    5432) echo "PostgreSQL" ;;
    6379) echo "Redis" ;;
    27017) echo "MongoDB" ;;
    9094) echo "Kafka" ;;
    8180) echo "Keycloak" ;;
    8123) echo "ClickHouse" ;;
    8088) echo "Superset / ai-platform" ;;
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

# ── Port conflict check ────────────────────────────────────────────────────────
step "Checking for port conflicts..."
CONFLICT=0
INFRA_PORTS="5432 6379 27017 9094 8180 8123 8088 8090"
SVC_PORTS="8080 8081 8082 8083 8084 8085 8086 8087 8089 3000"

for port in $INFRA_PORTS $SVC_PORTS; do
  if lsof -ti:$port > /dev/null 2>&1; then
    warn "Port $port in use → $(port_name $port)"
    CONFLICT=1
  fi
done

if [ "$CONFLICT" = "1" ]; then
  echo ""
  warn "Tip — stop local Postgres if that's the issue:"
  echo "  sudo launchctl stop com.edb.launchd.postgresql-16"
  echo "  (or: brew services stop postgresql@16)"
  echo ""
  printf "Continue anyway? [y/N] "; read -r REPLY; echo
  [[ $REPLY =~ ^[Yy]$ ]] || exit 1
fi
ok "Port check done"

# ── Infrastructure ─────────────────────────────────────────────────────────────
step "Starting infrastructure..."
docker compose up -d

step "Waiting for Keycloak realm to be ready (polling /realms/aegispay)..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:8180/realms/aegispay > /dev/null 2>&1; then
    ok "Keycloak ready (attempt $i)"
    break
  fi
  printf "  waiting... %d/30\r" "$i"
  sleep 3
done
curl -sf http://localhost:8180/realms/aegispay > /dev/null || warn "Keycloak not ready yet — auth may fail on first request"

# ── Java shared libs ───────────────────────────────────────────────────────────
step "Building shared Java libraries..."
mvn clean install \
  -pl libs/common-domain,libs/common-security,libs/common-kafka,libs/common-observability \
  -DskipTests -q
ok "Shared libs installed"

# ── Backend services ───────────────────────────────────────────────────────────
step "Starting backend services..."
SERVICES="api-gateway user-service transaction-service ledger-service \
payment-orchestrator risk-engine notification-service \
ai-platform data-pipeline reconciliation-service"

for svc in $SERVICES; do
  mvn -pl services/$svc spring-boot:run > /tmp/aegispay-$svc.log 2>&1 &
  echo "  ↳ $svc  (PID $!  |  log: /tmp/aegispay-$svc.log)"
done

step "Waiting 25s for services to start..."
sleep 25

step "Health check..."
# port:service pairs as flat list — Bash 3.2 compatible
CHECKS="8080:api-gateway 8081:user-service 8082:transaction-service \
        8083:ledger-service 8084:payment-orchestrator 8085:risk-engine \
        8086:notification-service 8087:reconciliation-service \
        8089:data-pipeline"

ALL_OK=1
for pair in $CHECKS; do
  port="${pair%%:*}"
  svc="${pair##*:}"
  status=$(curl -sf -o /dev/null -w "%{http_code}" \
    "http://localhost:$port/actuator/health" 2>/dev/null || echo "DOWN")
  if [ "$status" = "200" ]; then
    ok "$svc :$port"
  else
    warn "$svc :$port → $status  (tail: /tmp/aegispay-$svc.log)"
    ALL_OK=0
  fi
done

# ai-platform uses same port as Superset in Docker (8088) — check separately
status=$(curl -sf -o /dev/null -w "%{http_code}" \
  "http://localhost:8088/actuator/health" 2>/dev/null || echo "DOWN")
if [ "$status" = "200" ]; then
  ok "ai-platform :8088"
else
  warn "ai-platform :8088 → $status  (tail: /tmp/aegispay-ai-platform.log)"
  ALL_OK=0
fi

# ── Web app ────────────────────────────────────────────────────────────────────
step "Starting web app..."
if [ ! -f apps/web/.env.local ]; then
  cp apps/web/.env.local.example apps/web/.env.local
  SECRET=$(openssl rand -base64 32)
  sed -i '' "s|REPLACE_WITH_RANDOM_32_BYTE_SECRET|$SECRET|" apps/web/.env.local
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
echo "  📊  Superset     → http://localhost:8088  (admin / admin)"
echo "  🐘  PostgreSQL   → localhost:5432         (aegispay / aegispay_dev)"
echo ""
echo "  Stop everything:"
echo "    docker compose down && pkill -f 'spring-boot:run'"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
