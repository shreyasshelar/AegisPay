# AegisPay — Observability & Monitoring

---

## Three Pillars

| Pillar | Technology | What it covers |
|--------|-----------|---------------|
| **Metrics** | Micrometer → Prometheus → Grafana (kube-prometheus-stack) | JVM health, HTTP error rates, Kafka lag, saga timeouts |
| **Tracing** | W3C `traceparent` header propagated across all services | Request journey from gateway to every downstream call |
| **Logs** | Structured JSON logs (Logback) with masked sensitive fields | Per-request context, error details, Kafka event processing |

---

## Metrics Collection

Every Spring Boot service auto-exposes `/actuator/prometheus` metrics via Micrometer. The API Gateway annotates pods with:

```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "<service_port>"
  prometheus.io/path: "/actuator/prometheus"
```

The kube-prometheus-stack Prometheus scrapes all pods with these annotations every 15s.

---

## Two Grafana Instances (by design)

### 1. kube-prometheus-stack Grafana (Prometheus-backed)
Covers infrastructure and application health:
- Spring Boot Observability (dashboard gnetId 17175) — HTTP rates, latencies, error counts
- JVM Micrometer (gnetId 4701) — GC pauses, heap usage, thread pools
- Kafka Overview (gnetId 7589) — consumer group lag, broker throughput
- Kubernetes Workloads (gnetId 15760) — pod restarts, resource usage

### 2. AegisPay Grafana (ClickHouse-backed, port 3100)
Covers business analytics:
- **Payment Operations** — transaction volume, failure rates, failure codes
- **Fraud Intelligence** — risk score distribution, decision breakdown, rule flags
- **SLA & Latency** — P50/P95/P99 saga latency, reconciliation breaks

---

## Alerting (PrometheusRules)

All rules are in `infra/helm/aegispay/templates/prometheusrules.yaml` and sent to Alertmanager.

### Saga Alerts
| Alert | Expr | Severity |
|-------|------|---------|
| `SagaTimeoutRateHigh` | `sum(rate(saga_timeout_total[5m])) > 0.1` | warning |
| `SagaCompensatingRateHigh` | `sum(rate(saga_compensating_total[5m])) > 0.5` | critical |

### Kafka Alerts
| Alert | Expr | Severity |
|-------|------|---------|
| `DlqDepthNonZero` | `sum(kafka_consumer_group_lag{topic=~".*\\.DLQ"}) > 0` | critical |
| `KafkaConsumerLagHigh` | consumer lag > 5000 | warning |

### Ledger Alerts
| Alert | Expr | Severity |
|-------|------|---------|
| `BalanceNegative` | `min(ledger_account_available_balance) < 0` | critical (fires immediately) |
| `LedgerReservationFailureRateHigh` | reservation failure rate > 10% | warning |

### Notification Alerts
| Alert | Expr | Severity |
|-------|------|---------|
| `NotificationDeliveryFailureHigh` | delivery failure rate > 0.5/min | warning |
| `NotificationServiceDown` | `up{job="notification-service"} == 0` for 2m | critical |

### Data Pipeline Alerts
| Alert | Expr | Severity |
|-------|------|---------|
| `DataPipelineSinkErrorHigh` | ClickHouse write error rate > 0.2/min | warning |
| `DataPipelineSinkBacklogHigh` | pending records > 1000 | warning |

### Reconciliation Alerts
| Alert | Expr | Severity |
|-------|------|---------|
| `ReconciliationBreakCountHigh` | unresolved breaks > 5 for 15m | critical |
| `ReconciliationJobFailed` | kube_job_failed for reconciliation jobs > 0 | critical |

---

## Alertmanager Routing

```
All alerts → Slack #aegispay-alerts
Critical alerts → Slack + Email
Warning alerts → Slack only
```

Slack and SMTP credentials are injected via mounted secret files (not env vars) — avoids credentials appearing in `kubectl describe pod` output.

---

## Distributed Tracing

Every service propagates the W3C `traceparent` header:

```
traceparent: 00-{traceId}-{spanId}-{flags}
```

The correlation ID is also added as `X-Correlation-Id` for log correlation.

In logs, every line includes:
```json
{
  "traceId": "0af7651916cd43dd8448eb211c80319c",
  "spanId": "b7ad6b7169203331",
  "correlationId": "req-abc-123",
  "userId": "59295e61-...",   ← from JWT, added by gateway
  "service": "transaction-service",
  "level": "INFO",
  "message": "Transaction created: id=550e8400-..."
}
```

---

## Sensitive Data Masking

Logback configuration applies masking patterns before any log is written:

| Field | Raw | Masked |
|-------|-----|--------|
| Email | `customer@aegispay.local` | `cu*****@aegispay.local` |
| Phone | `+919000000001` | `+91900*****01` |
| Card number | `4242424242424242` | `4242****4242` |
| JWT token | `eyJhbGci...` | `[REDACTED]` |

API responses use `maskedEmail` / `maskedPhone` fields — the full values are only stored in `UserContactDocument` (accessible only to Notification Service, never returned to frontend).

---

## Health Checks

Every service exposes Spring Boot Actuator health endpoints:

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health/liveness` | Is the JVM alive? (Kubernetes liveness probe) |
| `/actuator/health/readiness` | Are all dependencies (DB, Redis, Kafka) ready? (readiness probe) |
| `/actuator/health` | Full composite health |
| `/actuator/prometheus` | Prometheus scrape endpoint |

Kubernetes liveness probe: initial delay 40s (JVM startup), period 15s, 3 failures → pod restart  
Readiness probe: initial delay 25s, period 10s, 3 failures → pod removed from Service endpoints (no traffic)
