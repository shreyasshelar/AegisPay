# AegisPay — Local Development Setup

End-to-end guide for running the full AegisPay stack on **macOS**, **Windows** (WSL 2 or native PowerShell), and for running the **iOS** and **Android** apps against the local backend.

After following this guide you will have every service running locally, ClickHouse populated with analytics data, Grafana dashboards live, and can send an end-to-end payment through the system.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Repository Setup](#2-repository-setup)
3. [Start the Infrastructure](#3-start-the-infrastructure)
4. [Configure Environment Variables](#4-configure-environment-variables)
5. [Start the Backend Services](#5-start-the-backend-services)
6. [Start the Frontend](#6-start-the-frontend)
7. [Port Reference](#7-port-reference)
8. [End-to-End Test Flow](#8-end-to-end-test-flow)
9. [Verify the Data Pipeline](#9-verify-the-data-pipeline)
10. [Grafana Dashboards](#10-grafana-dashboards)
11. [Notification Channels](#11-notification-channels)
12. [Mobile Apps (Android & iOS)](#12-mobile-apps-android--ios)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Prerequisites

### macOS

| Tool | Version | Install |
|------|---------|---------|
| Homebrew | latest | `/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"` |
| Docker Desktop | ≥ 4.28 | `brew install --cask docker` |
| Java 21 (Temurin) | 21.x | `brew install --cask temurin@21` |
| Maven | 3.9+ | `brew install maven` |
| Node.js | 20 LTS | `brew install node@20 && brew link node@20` |
| pnpm | 9+ | `npm install -g pnpm` |

Verify:
```bash
docker --version          # Docker version 25+
java -version             # openjdk 21
mvn -version              # Apache Maven 3.9+
node -v                   # v20.x
pnpm -v                   # 9.x
```

### Windows (WSL 2)

> All commands run inside **WSL 2** (Ubuntu 22.04 recommended).  
> Docker Desktop must have the WSL 2 backend enabled (Settings → Resources → WSL Integration → enable your distro).

```powershell
# In PowerShell (admin) — install WSL 2 + Ubuntu
wsl --install -d Ubuntu-22.04
```

Inside WSL 2 shell:
```bash
# Java 21
sudo apt install -y wget apt-transport-https
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo apt-key add -
echo "deb https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update && sudo apt install -y temurin-21-jdk

# Maven
sudo apt install -y maven

# Node 20 via nvm
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
source ~/.bashrc
nvm install 20 && nvm use 20

# pnpm
npm install -g pnpm
```

Docker is managed from Windows Docker Desktop — no separate install inside WSL 2.

---

## 2. Repository Setup

```bash
git clone https://github.com/shreyasshelar/AegisPay.git
cd AegisPay
```

Install frontend dependencies once:
```bash
pnpm install
```

---

## 3. Start the Infrastructure

All infrastructure (Postgres, Redis, MongoDB, Kafka, Keycloak, ClickHouse, Grafana) runs in Docker Compose.

```bash
# From the repo root
docker compose up -d
```

Wait for all services to become healthy (~2 min):
```bash
docker compose ps
```

All services should show **`healthy`** or **`running`** before starting Spring Boot services.  
If ClickHouse is still starting (it takes up to 90 s on first run), you can check:
```bash
docker compose logs -f clickhouse
# Wait until you see: "Application: Ready for connections"
```

> **Windows tip:** run the command inside your WSL 2 terminal, not PowerShell.

### ClickHouse schema initialisation

The ClickHouse init SQL is auto-run on first start via the `docker-entrypoint-initdb.d` mount.  
If you need to re-run it manually:
```bash
docker exec -it aegispay-clickhouse \
  clickhouse-client --query "$(cat infra/clickhouse/init.sql)"
```

Verify tables exist:
```bash
docker exec -it aegispay-clickhouse \
  clickhouse-client --query "SHOW TABLES FROM aegispay_analytics"
# Expected: reconciliation_breaks  risk_assessments  saga_latencies  transaction_facts
```

---

## 4. Configure Environment Variables

Create a file **`.env.local`** in the repo root (git-ignored).  
This file is sourced by the Maven run script below.

```bash
# .env.local — local dev overrides (never commit this file)

# ── Databases (matches docker-compose ports) ──────────────────────────────────
DB_HOST=localhost
DB_PORT=5433          # host port mapped from container's 5432
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=aegispay_dev
MONGODB_URI=mongodb://aegispay:aegispay_dev@localhost:27017/aegispay?authSource=admin

# ── Kafka ─────────────────────────────────────────────────────────────────────
KAFKA_BOOTSTRAP_SERVERS=localhost:9094   # EXTERNAL listener on host

# ── Keycloak ──────────────────────────────────────────────────────────────────
OAUTH2_ISSUER_URI=http://localhost:8180/realms/aegispay

# ── ClickHouse ────────────────────────────────────────────────────────────────
CLICKHOUSE_URL=jdbc:clickhouse://localhost:8123/aegispay_analytics
CLICKHOUSE_PASSWORD=

# ── Notifications (optional — services start without these) ───────────────────
# SMTP_HOST=smtp.gmail.com
# SMTP_PORT=587
# SMTP_USERNAME=your-gmail@gmail.com
# SMTP_PASSWORD=your-app-password      # Gmail App Password (not your account password)
# SMTP_FROM_ADDRESS=your-gmail@gmail.com
# SLACK_WEBHOOK_URL=https://hooks.slack.com/services/xxx/yyy/zzz
# FAST2SMS_API_KEY=your-fast2sms-key

# ── Stripe (use test keys) ────────────────────────────────────────────────────
STRIPE_SECRET_KEY=sk_test_xxxxxxxxxxxx
STRIPE_WEBHOOK_SECRET=whsec_xxxxxxxx

# ── AI (optional) ─────────────────────────────────────────────────────────────
ANTHROPIC_API_KEY=sk-ant-xxxxxxxxxxxx
```

### Load env into current shell

**macOS / Linux / WSL 2:**
```bash
set -o allexport; source .env.local; set +o allexport
```

**Windows (PowerShell — if not using WSL):**
```powershell
Get-Content .env.local | ForEach-Object {
  if ($_ -match '^([^#=]+)=(.*)$') { [System.Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2].Trim(), 'Process') }
}
```

---

## 5. Start the Backend Services

### Windows (recommended) — automated script

Double-click **`start-aegispay.bat`** in the repo root, or run from PowerShell:
```powershell
.\start-aegispay.bat
```

The script auto-detects Maven, loads `.env.local`, starts all 10 services in separate windows in dependency order, and waits for each health check to pass before starting the next.

### macOS / Linux — manual

Each service runs as a standard Spring Boot app. Open a separate terminal tab for each, or use a process manager like [honcho](https://github.com/nicowillis/honcho) / [tmux](https://github.com/tmux/tmux).

**Source the env file in every terminal before running:**
```bash
set -o allexport; source .env.local; set +o allexport
```

Start services in this order (dependency order):

```bash
# 1. User Service (port 8081)
mvn -pl services/user-service spring-boot:run

# 2. Transaction Service (port 8082)
mvn -pl services/transaction-service spring-boot:run

# 3. Ledger Service (port 8083)
mvn -pl services/ledger-service spring-boot:run

# 4. Payment Orchestrator (port 8084)
mvn -pl services/payment-orchestrator spring-boot:run

# 5. Risk Engine (port 8085)
mvn -pl services/risk-engine spring-boot:run

# 6. Notification Service (port 8086)
mvn -pl services/notification-service spring-boot:run

# 7. Reconciliation Service (port 8087)
mvn -pl services/reconciliation-service spring-boot:run

# 8. Data Pipeline (port 8089)
mvn -pl services/data-pipeline spring-boot:run

# 9. AI Platform (port 8091)
mvn -pl services/ai-platform spring-boot:run

# 10. API Gateway (port 8080) — start last
mvn -pl services/api-gateway spring-boot:run
```

### Verify all services are up

```bash
for port in 8080 8081 8082 8083 8084 8085 8086 8087 8089 8091; do
  status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$port/actuator/health)
  echo "Port $port → $status"
done
```

All should return `200`.

---

## 6. Start the Frontend

```bash
# In a new terminal from repo root
pnpm --filter @aegispay/web dev
```

The Next.js app starts on **http://localhost:3000**.

---

## 7. Port Reference

| Service | Host Port | Notes |
|---------|-----------|-------|
| **Next.js Web App** | 3000 | Frontend |
| **API Gateway** | 8080 | Single entry point for all API calls |
| **User Service** | 8081 | Internal only (go through gateway) |
| **Transaction Service** | 8082 | Internal only |
| **Ledger Service** | 8083 | Internal only |
| **Payment Orchestrator** | 8084 | Internal only |
| **Risk Engine** | 8085 | Internal only |
| **Notification Service** | 8086 | Internal only |
| **Reconciliation Service** | 8087 | Internal only |
| **Data Pipeline** | 8089 | Kafka → ClickHouse ETL |
| **AI Platform** | 8091 | Internal only |
| **PostgreSQL** | 5433 | Host port (container uses 5432) |
| **Redis** | 6379 | |
| **MongoDB** | 27017 | |
| **Kafka** | 9094 | EXTERNAL listener for host access |
| **Kafka UI** | 8090 | http://localhost:8090 |
| **Keycloak** | 8180 | http://localhost:8180  admin/admin |
| **ClickHouse HTTP** | 8123 | JDBC / SQL queries |
| **ClickHouse Native** | 9000 | Used by Grafana plugin |
| **Grafana** | 3100 | http://localhost:3100  admin/admin |

---

## 8. End-to-End Test Flow

### 8.1 Register a user

AegisPay uses **PKCE / OAuth2 — there is no username+password registration endpoint**.  
New users register through the web UI at **http://localhost:3000** (click "Sign in with Google" or any configured IdP). Keycloak creates the Keycloak account; the first call to the backend's `/register` endpoint is made automatically by the web app after the PKCE flow completes.

To test the backend directly, use the **pre-seeded admin account** or register through Keycloak's admin console:

```
Keycloak Admin Console: http://localhost:8180  (admin / admin)
Realm: aegispay
```

### 8.2 Get a JWT for testing (Keycloak resource-owner password grant — dev only)

> **Dev only.** Resource-owner password grant is enabled in `realm-export.json` for testing convenience. Never enable this in production.

```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8180/realms/aegispay/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=aegispay-app&client_secret=aegispay-secret&grant_type=password&username=customer@aegispay.local&password=Test@1234&scope=openid" \
  | jq -r '.access_token')

echo "TOKEN=$TOKEN"
```

### 8.3 Send a payment

```bash
# First register a second user as the recipient, then:
curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "recipientId": "<recipient-user-id>",
    "amount": 500.00,
    "currency": "INR",
    "note": "Lunch split"
  }' | jq .
```

Expected: `201 Created` with `status: "PROCESSING"`.

### 8.4 Track transaction status

```bash
TX_ID="<transaction-id-from-step-above>"

curl -s http://localhost:8080/api/v1/transactions/$TX_ID \
  -H "Authorization: Bearer $TOKEN" | jq .status
```

Poll until `COMPLETED` (usually within 2–5 seconds locally).

### 8.5 Verify real-time notification (WebSocket)

Open the web app at http://localhost:3000, log in, and trigger a payment through the UI.  
The notification bell should show an in-app badge update without page refresh.

---

## 9. Verify the Data Pipeline

After completing at least one transaction, verify ClickHouse is receiving data.

```bash
# Connect to ClickHouse CLI
docker exec -it aegispay-clickhouse clickhouse-client

# In the ClickHouse shell:
SELECT count() FROM aegispay_analytics.transaction_facts;
SELECT count() FROM aegispay_analytics.risk_assessments;
SELECT count() FROM aegispay_analytics.saga_latencies;

# Check a recent transaction fact
SELECT transaction_id, status, amount, currency, event_time
FROM aegispay_analytics.transaction_facts
ORDER BY event_time DESC
LIMIT 5;
```

### If ClickHouse is empty after transactions

1. Check the data pipeline is running:
   ```bash
   curl -s http://localhost:8089/actuator/health | jq .status
   ```
2. Check Kafka has messages:
   Open http://localhost:8090 (Kafka UI) → Topics → look for `transaction.completed` and `risk.assessment.completed`.
3. Check data pipeline logs:
   ```bash
   # In the data-pipeline terminal, look for lines like:
   # "Flushing 3 transaction facts to ClickHouse"
   # The flush happens every 5 seconds with ≥1 records.
   ```

---

## 10. Grafana Dashboards

Open **http://localhost:3100** and log in with `admin / admin`.

Three dashboards are pre-provisioned automatically:

| Dashboard | Description |
|-----------|-------------|
| **AegisPay — Payment Operations** | Transaction counts, volume by currency, failure codes, hourly trends |
| **AegisPay — Fraud Intelligence** | Risk scores, decision breakdown, triggered rule flags |
| **AegisPay — SLA & Latency** | P95/P99 saga latency, slowest sagas, reconciliation breaks |

If dashboards don't appear:

```bash
# Check Grafana logs
docker compose logs grafana | tail -30

# Verify datasource connectivity
docker exec -it aegispay-grafana \
  curl -s "http://admin:admin@localhost:3100/api/datasources" | jq .[].name
```

To manually reload provisioning:
```bash
docker compose restart grafana
```

---

## 11. Notification Channels

### Email (SMTP / Gmail)

1. Create a Gmail **App Password** (Google Account → Security → 2-Step Verification → App passwords).
2. Set in `.env.local`:
   ```
   SMTP_USERNAME=your@gmail.com
   SMTP_PASSWORD=xxxx xxxx xxxx xxxx   # 16-char app password, spaces OK
   SMTP_FROM_ADDRESS=your@gmail.com
   ```
3. Restart notification-service and trigger a transaction. Check your inbox.

### Slack

1. Go to https://api.slack.com/apps → Create App → From scratch.
2. Enable **Incoming Webhooks** → Add to channel.
3. Copy the webhook URL and set:
   ```
   SLACK_WEBHOOK_URL=https://hooks.slack.com/services/XXX/YYY/ZZZ
   ```
4. Restart notification-service. Failed transactions will post to the channel.

### SMS (Fast2SMS)

1. Sign up at https://www.fast2sms.com and get an API key.
2. Set:
   ```
   FAST2SMS_API_KEY=your-api-key
   ```
3. Restart notification-service. Failed transactions will SMS the user's registered phone.

> **Notification triggers summary:**
> - Transaction **COMPLETED** → WebSocket (in-app badge) + Email
> - Transaction **FAILED** → WebSocket + Email + SMS + Slack

---

## 12. Mobile Apps (Android & iOS)

The mobile apps connect to the same local backend. The API Gateway runs on `localhost:8080` and Keycloak on `localhost:8180` — you need to expose these to your phone/emulator.

### Android

**Prerequisites:**
| Tool | Version |
|------|---------|
| Android Studio | Hedgehog 2023.1+ |
| JDK | 17+ (bundled with Android Studio) |
| Android SDK | API 31+ (target 35) |

**Run on emulator (recommended for local dev):**
1. Open `apps/android/` in Android Studio
2. Create an AVD: API 34, Pixel 8 Pro (or any x86_64 device)
3. In `apps/android/app/src/main/java/.../AppConfig.kt` set:
   ```kotlin
   const val BASE_URL = "http://10.0.2.2:8080"        // emulator → host localhost
   const val KEYCLOAK_ISSUER = "http://10.0.2.2:8180/realms/aegispay"
   ```
4. Run → the app opens on the emulator, sign in with Google SSO (Keycloak broker)

**Run on physical device:**
- Use `adb reverse tcp:8080 tcp:8080` and `adb reverse tcp:8180 tcp:8180` to forward ports, then use `http://localhost:8080` in AppConfig.
- Or point at the dev GCP VM hostname.

**Firebase Phone OTP (for phone number verification in Profile):**
1. Create a Firebase project → enable Phone Auth
2. Download `google-services.json` → place in `apps/android/app/`
3. Add your test phone number to Firebase Console → Authentication → Phone → Test phone numbers
4. The OTP flow is handled by Firebase; the verified number is then PATCHed to `PUT /api/v1/users/{userId}/phone`

---

### iOS

**Prerequisites:**
| Tool | Version |
|------|---------|
| Xcode | 15.2+ |
| macOS | Sonoma 14+ |
| iOS Simulator | iOS 16+ |

> iOS development requires macOS. On Windows, only Android is available locally.

**Run on simulator:**
1. Open `apps/ios/AegisPay.xcodeproj` in Xcode
2. In `apps/ios/AegisPay/App/AppConfig.swift` set:
   ```swift
   static let baseURL  = "http://localhost:8080"
   static let keycloakIssuer = URL(string: "http://localhost:8180/realms/aegispay")!
   ```
   (Simulator shares the host machine's network — `localhost` works directly)
3. Select an iPhone 15 simulator → Run (⌘R)

**Dependencies**: managed via Swift Package Manager — Xcode resolves them automatically on first build (AppAuth-iOS, KeychainAccess, Stripe iOS SDK).

**Firebase Phone OTP:**
1. Download `GoogleService-Info.plist` from your Firebase project
2. Add to `apps/ios/AegisPay/` in Xcode (check "Add to target: AegisPay")
3. Test numbers configured in Firebase Console work without a real SIM

---

## 13. Troubleshooting

### Keycloak fails to start

Keycloak needs the `aegispay_keycloak` database to exist. This is created by the `db-init` container automatically. If Keycloak still fails:
```bash
docker compose logs db-init
docker compose restart keycloak
```

### Port 5432 conflict (macOS — local PostgreSQL running)

The compose file maps Postgres to **5433** on the host for exactly this reason. If you're still seeing a conflict on another port, check:
```bash
lsof -i :5433
```

### Kafka topics not auto-creating

Set `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"` is already in docker-compose.yml. If topics are missing:
```bash
docker exec -it aegispay-kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

### Spring Boot service fails with "Connection refused" to Postgres

The services use `DB_PORT=5433` when running locally (host port). Ensure you've sourced `.env.local` in the same terminal.

### ClickHouse `start_period: 520s` — seems long

ClickHouse performs data recovery on first start which can take up to 8 minutes on slow disks. The `healthcheck` polls until `ping` returns `Ok`. You can monitor with:
```bash
docker compose logs -f clickhouse | grep -E "Ready|error|Exception"
```

### Maven build fails: "Module not found"

Build the shared libraries first:
```bash
mvn -pl libs/common-domain,libs/common-events install -DskipTests
```

Then start individual services.

### Windows: Docker Compose commands not found in WSL

Ensure Docker Desktop → Settings → Resources → WSL Integration has your Ubuntu distro checked, then restart WSL:
```powershell
wsl --shutdown
wsl
```

### Reset everything (nuclear option)

```bash
docker compose down -v          # removes all containers AND volumes
docker compose up -d            # fresh start — ClickHouse will re-init
```

---

*Last updated: May 2026 — AegisPay v1.1 (added iOS/Android local setup, fixed registration flow, Windows bat script)*
