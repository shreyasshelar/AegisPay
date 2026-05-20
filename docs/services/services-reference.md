# AegisPay — Services Reference

Quick reference for every microservice: port, DB, Kafka topics, and key responsibilities.

---

## API Gateway (port 8080)

**Tech**: Spring Cloud Gateway (reactive)  
**DB**: Redis (rate limits, idempotency keys, session cache)

### What it does
- Validates JWT signature using Keycloak's JWKS endpoint (cached)
- Enforces `Idempotency-Key` header for all POST/PUT requests
- Rate-limits per user via Redis sliding window
- Routes requests to downstream services based on path
- Circuit breaker (Resilience4j): if transaction-service returns 5xx on 60% of 20 calls, opens for 15s
- Retry: 2 retries on 502/503 for GET and POST
- Fallback: returns structured `ApiResponse { success: false, error: "Service temporarily unavailable" }` instead of raw 503

### Routes
| Path | Service |
|------|---------|
| `/api/v1/users/**` | user-service:8081 |
| `/api/v1/transactions/**` | transaction-service:8082 |
| `/api/v1/ledger/**` | ledger-service:8083 |
| `/api/v1/payments/**` | payment-orchestrator:8084 |
| `/api/v1/risk/**` | risk-engine:8085 |
| `/api/v1/ai/**` | ai-platform:8091 |
| `/ws/**` | notification-service:8086 (WebSocket upgrade) |

---

## User Service (port 8081)

**DB**: PostgreSQL `aegispay_users`  
**Kafka produced**: `user.registered`  
**Kafka consumed**: none

### What it does
- User registration with email + password, or social OAuth via Keycloak redirect
- Stores user profile, KYC status, role
- Publishes `UserRegisteredEvent` (with full email) to Kafka on registration
- Password reset flow via email OTP

### Kafka event: `UserRegisteredEvent`
```json
{
  "userId": "uuid",
  "email": "customer@example.com",
  "maskedEmail": "cu*****@example.com",
  "firstName": "Test",
  "lastName": "Customer",
  "phone": "+919000000001"
}
```

---

## Transaction Service (port 8082)

**DB**: PostgreSQL `aegispay_transactions` (write) + MongoDB `aegispay` (read models)  
**Kafka produced**: `transaction.initiated`, `transaction.completed`, `transaction.failed`  
**Kafka consumed**: `balance.reserved`, `balance.reservation.failed`, `risk.assessed`, `payment.completed`, `payment.failed`, `ledger.committed`

### What it does
- Creates transactions (PENDING) with Outbox event in single DB transaction
- Maintains the transaction state machine via Kafka event consumption
- Upserts `TransactionView` MongoDB documents for CQRS reads
- Exposes REST endpoints: `POST /transactions`, `GET /transactions`, `GET /transactions/{id}`
- Amount serialised as String in API responses to avoid floating-point precision loss

### State machine
```
PENDING → RESERVED → RISK_CLEARED → PROCESSING → COMPLETED
Any state → FAILED (with failureCode + failureReason)
```

### `failureCode` values
| Code | Meaning |
|------|---------|
| `INSUFFICIENT_FUNDS` | Payer balance < amount |
| `RISK_BLOCKED` | Risk engine decision = BLOCK |
| `amount_too_small` | Below Stripe minimum (INR ₹50, USD $0.50) |
| `SAGA_TIMEOUT` | Saga exceeded max duration |
| `STRIPE_ERROR` | Generic Stripe API error |

---

## Ledger Service (port 8083)

**DB**: PostgreSQL `aegispay_ledger`  
**Kafka produced**: `balance.reserved`, `balance.reservation.failed`, `ledger.committed`  
**Kafka consumed**: `transaction.initiated` (via `balance.reservation.requested`), `payment.completed`, `payment.failed`

### What it does
- Maintains `accounts` table (current balances with optimistic locking)
- Appends immutable `ledger_entries` (double-entry bookkeeping)
- Phase 1: reserve (`reserved_balance += amount`)
- Phase 2a: commit (debit payer, credit payee in same DB transaction)
- Phase 2b: release (`reserved_balance -= amount` on payment failure)
- Exposes balance query endpoint for dashboard

---

## Payment Orchestrator (port 8084)

**DB**: PostgreSQL `aegispay_sagas`  
**Kafka produced**: `payment.completed`, `payment.failed`  
**Kafka consumed**: `risk.assessed`  
**External calls**: Stripe API (synchronous)

### What it does
- Drives the Saga: risk.assessed (ALLOW) → Stripe call → publish result
- Pre-flight amount validation vs Stripe minimums before calling Stripe
- Maps Stripe error codes to AegisPay `failureCode` values
- Persists saga state so restart recovers in-progress sagas
- On BLOCK decision: triggers compensation (releases reservation)

### Stripe minimum amounts (pre-flight check)
| Currency | Minimum |
|----------|---------|
| INR | ₹50 |
| USD | $0.50 |
| EUR | €0.50 |
| GBP | £0.30 |

---

## Risk Engine (port 8085)

**DB**: PostgreSQL `aegispay_risk`, Redis (velocity counters)  
**Kafka produced**: `risk.assessed`  
**Kafka consumed**: `balance.reserved`  
**External calls**: AI Platform (RAG query)

### What it does
- Rule-based evaluation (velocity limits, amount thresholds, device fingerprint, geography)
- RAG query to AI Platform for pattern matching against historical fraud cases
- Issues `ALLOW`, `REVIEW`, or `BLOCK` decision with numeric `riskScore` (0-100)
- Publishes `rule_flags` array listing which rules fired (stored in ClickHouse for analytics)

### Decision thresholds (configurable)
| Score | Decision |
|-------|---------|
| 0-49 | ALLOW |
| 50-74 | REVIEW |
| 75-100 | BLOCK |

---

## Notification Service (port 8086)

**DB**: MongoDB `aegispay` (user contacts)  
**Kafka consumed**: `transaction.completed`, `transaction.failed`, `user.registered`  
**External calls**: Gmail SMTP, Fast2SMS, Slack Incoming Webhook  
**WebSocket**: STOMP over SockJS at `/ws`

### What it does
- Consumes `user.registered` → stores contact document (email, phone) in MongoDB
- Consumes `transaction.completed` → sends WebSocket + Email
- Consumes `transaction.failed` → sends WebSocket + Email + SMS + Slack
- STOMP auth via `StompAuthChannelInterceptor` — validates JWT from CONNECT frame headers
- Slack and SMS are optional (stub mode when keys not configured)

---

## Reconciliation Service (port 8087)

**DB**: PostgreSQL `aegispay_ledger` (read), ClickHouse `reconciliation_breaks` (write)  
**Kafka**: none  
**External calls**: Stripe API (list payment intents)  
**Schedule**: Spring Batch job (daily, configurable)

### What it does
- Fetches all ledger entries for a date range
- Fetches corresponding Stripe PaymentIntents
- Diffs the two datasets: finds MISSING_IN_STRIPE, MISSING_IN_LEDGER, AMOUNT_MISMATCH
- Writes breaks to ClickHouse for Grafana visibility
- Wait-for-ledger init-container ensures it doesn't start before ledger schema exists

---

## Data Pipeline (port 8089)

**DB**: ClickHouse (write only)  
**Kafka consumed**: `transaction.completed`, `transaction.failed`, `risk.assessment.completed`  
**Batch flush**: every 5 seconds

### What it does
- `TransactionMetricsStream`: maps transaction events → `TransactionFactRecord` → batch to `transaction_facts`
- `RiskAnalyticsStream`: maps risk events → `RiskRecord` → batch to `risk_assessments`
- `writeSagaLatency()`: computes `latency_ms = completed_at - started_at` → batch to `saga_latencies`
- `ClickHouseSink`: thread-safe batch accumulator, flushes on interval or count threshold

---

## AI Platform (port 8091)

**DB**: PostgreSQL `aegispay_ai` (pgvector for embeddings), MongoDB (conversation history)  
**Kafka**: none (called via REST from other services and frontend)  
**External calls**: Anthropic Claude API (prod) or OpenRouter free tier (dev)

### What it does
- **Fraud Copilot**: RAG query over historical fraud case embeddings → explain why a transaction was flagged
- **Error Explanation Agent**: translate machine error codes (`amount_too_small`) into user-friendly plain English with suggested action
- **Incident Triage Agent**: agentic loop reading logs + metrics → suggest root cause
- **KYC OCR**: multimodal image analysis for document extraction and tampering detection
- Embeddings stored via pgvector (`vector(1536)` column) — queried with cosine similarity `<=>` operator
