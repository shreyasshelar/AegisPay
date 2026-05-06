#!/bin/bash
set -e

# ─── Colour helpers ───────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✅ $*${NC}"; }
warn() { echo -e "${YELLOW}⚠️  $*${NC}"; }
err()  { echo -e "${RED}❌ $*${NC}"; }
step() { echo -e "\n${GREEN}▶ $*${NC}"; }

# ─── Port conflict check ──────────────────────────────────────────────────────
step "Checking for port conflicts..."
CONFLICT=0
declare -A PORT_NAMES=(
  [5433]="PostgreSQL (Docker)"
  [6379]="Redis"
  [27017]="MongoDB"
  [9094]="Kafka"
  [8180]="Keycloak"
  [8123]="ClickHouse"
  [8088]="Superset"
  [8090]="Kafka UI"
  [8080]="API Gateway"
  [8081]="User Service"
  [8082]="Transaction Service"
  [8083]="Ledger Service"
  [8084]="Payment Orchestrator"
  [8085]="Risk Engine"
  [8086]="Notification Service"
  [8087]="Reconciliation Service"
  [8089]="Data Pipeline"
  [3000]="Web App"
)
for port in "${!PORT_NAMES[@]}"; do
  if lsof -ti:$port > /dev/null 2>&1; then
    warn "Port $port already in use → ${PORT_NAMES[$port]}"
    CONFLICT=1
  fi
done
if [ "$CONFLICT" = "1" ]; then
  echo ""
  warn "Fix port conflicts before continuing. Common causes:"
  echo "  Local Postgres on 5432/5433: sudo launchctl stop com.edb.launchd.postgresql-16"
  echo "  Leftover containers:        docker compose down"
  echo "  Other services:             lsof -ti:<port> | xargs kill -9"
  echo ""
  read -p "Continue anyway? [y/N] " -n 1 -r; echo
  [[ $REPLY =~ ^[Yy]$ ]] || exit 1
fi

# ─── Infrastructure ───────────────────────────────────────────────────────────
step "Starting infrastructure (Postgres · Redis · Mongo · Kafka · Keycloak · ClickHouse · Superset)..."
docker compose up -d

step "Waiting for Keycloak realm to be ready (~60s)..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:8180/realms/aegispay > /dev/null 2>&1; then
    ok "Keycloak ready"
    break
  fi
  printf "  attempt $i/30...\r"
  sleep 2
done
curl -sf http://localhost:8180/realms/aegispay > /dev/null || warn "Keycloak not ready — services may fail auth on first request"

# ─── Java shared libs ─────────────────────────────────────────────────────────
step "Building shared Java libraries..."
mvn clean install \
  -pl libs/common-domain,libs/common-security,libs/common-kafka,libs/common-observability \
  -DskipTests -q
ok "Shared libs installed"

# ─── Backend services ─────────────────────────────────────────────────────────
step "Starting backend services (logs in /tmp/aegispay-<service>.log)..."
SERVICES=(
  api-gateway
  user-service
  transaction-service
  ledger-service
  payment-orchestrator
  risk-engine
  notification-service
  ai-platform
  data-pipeline
  reconciliation-service
)
for svc in "${SERVICES[@]}"; do
  mvn -pl services/$svc spring-boot:run > /tmp/aegispay-$svc.log 2>&1 &
  echo "  ↳ $svc  (PID $!)"
done

step "Waiting 20s for services to start..."
sleep 20

step "Health check — all backend services:"
declare -A SERVICE_PORTS=(
  [api-gateway]=8080
  [user-service]=8081
  [transaction-service]=8082
  [ledger-service]=8083
  [payment-orchestrator]=8084
  [risk-engine]=8085
  [notification-service]=8086
  [reconciliation-service]=8087
  [ai-platform]=8088
  [data-pipeline]=8089
)
ALL_OK=1
for svc in "${!SERVICE_PORTS[@]}"; do
  port=${SERVICE_PORTS[$svc]}
  status=$(curl -sf -o /dev/null -w "%{http_code}" http://localhost:$port/actuator/health 2>/dev/null || echo "DOWN")
  if [ "$status" = "200" ]; then
    ok "$svc → http://localhost:$port (200)"
  else
    warn "$svc → http://localhost:$port ($status) — check /tmp/aegispay-$svc.log"
    ALL_OK=0
  fi
done

# ─── Web app ─────────────────────────────────────────────────────────────────
step "Starting web app..."
if [ ! -f apps/web/.env.local ]; then
  cp apps/web/.env.local.example apps/web/.env.local
  SECRET=$(openssl rand -base64 32)
  sed -i '' "s|REPLACE_WITH_RANDOM_32_BYTE_SECRET|$SECRET|" apps/web/.env.local
  ok "Created apps/web/.env.local with generated NEXTAUTH_SECRET"
fi
npm install --silent --prefer-offline
npm run dev --workspace=apps/web > /tmp/aegispay-web.log 2>&1 &
echo "  ↳ Web app (PID $!) → /tmp/aegispay-web.log"

# ─── Summary ─────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ "$ALL_OK" = "1" ]; then
  ok "Everything is up!"
else
  warn "Some services are still starting — check logs above"
fi
echo ""
echo "  🌐 Web app       → http://localhost:3000"
echo "  🔌 API Gateway   → http://localhost:8080/actuator/health"
echo "  🔑 Keycloak      → http://localhost:8180  (admin/admin)"
echo "  📨 Kafka UI      → http://localhost:8090"
echo "  📊 Superset      → http://localhost:8088  (admin/admin)"
echo "  🐘 PostgreSQL    → localhost:5433         (aegispay/aegispay_dev)"
echo ""
echo "  📋 Service logs  → /tmp/aegispay-<service>.log"
echo "  📋 Web log       → /tmp/aegispay-web.log"
echo ""
echo "  To stop: docker compose down && pkill -f 'spring-boot:run'"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
