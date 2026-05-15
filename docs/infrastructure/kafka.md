# AegisPay — Kafka Topics & Event Contracts

---

## Topic Inventory

| Topic | Producer | Consumers | Retention |
|-------|----------|-----------|-----------|
| `transaction.initiated` | Transaction Service (Outbox) | Ledger Service | 7d |
| `balance.reserved` | Ledger Service | Transaction Service, Risk Engine, Payment Orchestrator | 7d |
| `balance.reservation.failed` | Ledger Service | Transaction Service, Payment Orchestrator | 7d |
| `risk.assessed` | Risk Engine | Transaction Service, Payment Orchestrator | 7d |
| `risk.assessment.completed` | Risk Engine | Data Pipeline | 7d |
| `payment.completed` | Payment Orchestrator | Transaction Service, Ledger Service | 7d |
| `payment.failed` | Payment Orchestrator | Transaction Service, Ledger Service | 7d |
| `ledger.committed` | Ledger Service | Transaction Service | 7d |
| `transaction.completed` | Transaction Service | Notification Service, Data Pipeline | 7d |
| `transaction.failed` | Transaction Service | Notification Service, Data Pipeline | 7d |
| `user.registered` | User Service | Notification Service | 30d |
| `*.DLQ` | Spring Kafka (auto) | Manual inspection | 30d |

---

## Partition Strategy

`transaction.completed` and `transaction.initiated` are partitioned by **`userId`**. This ensures:
- All events for a user are processed in order by the same consumer thread
- No cross-user interference
- `NUM_PARTITIONS: 3` (configurable per topic)

---

## Dead Letter Queue (DLQ)

If a consumer throws an exception after the configured number of retries, Spring Kafka publishes the failed message to `{topic}.DLQ`.

The `DlqDepthNonZero` PrometheusRule fires immediately when any DLQ receives a message — requires manual investigation and replay.

To replay from DLQ:
```bash
# Check DLQ depth
docker exec aegispay-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group aegispay-dlq-group

# Consume and inspect
docker exec aegispay-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic transaction.initiated.DLQ \
  --from-beginning --max-messages 10
```

---

## Event Schema (Key Events)

### `TransactionInitiatedEvent`
```json
{
  "eventId": "uuid",
  "transactionId": "uuid",
  "payerId": "uuid",
  "payeeId": "uuid",
  "amount": "500.00",
  "currency": "INR",
  "note": "Lunch split",
  "idempotencyKey": "uuid",
  "initiatedAt": "2026-05-16T10:30:00Z"
}
```

### `RiskAssessedEvent`
```json
{
  "eventId": "uuid",
  "transactionId": "uuid",
  "userId": "uuid",
  "riskScore": 25,
  "decision": "ALLOW",
  "ruleFlags": ["FIRST_TIME_PAYEE"],
  "assessedAt": "2026-05-16T10:30:01Z"
}
```

### `TransactionStatusChangedEvent` (WebSocket push)
```json
{
  "transactionId": "uuid",
  "status": "COMPLETED",
  "failureCode": null,
  "failureReason": null,
  "updatedAt": "2026-05-16T10:30:03Z"
}
```

---

## Kafka Configuration (docker-compose)

```yaml
KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
KAFKA_DEFAULT_REPLICATION_FACTOR: 1      # single-node dev
KAFKA_NUM_PARTITIONS: 3
KAFKA_LOG_RETENTION_HOURS: 168           # 7 days
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0  # dev only: skip rebalance wait
CLUSTER_ID: "MkU3OEVBNTcwNTJENDM2Qk"    # fixed: volumes survive restarts
```

**KRaft mode** (no ZooKeeper): the broker is both a broker and a controller. Single-node dev. In production, use 3 brokers + 3 controllers (separate roles).

---

## Consumer Groups

| Group ID | Service | Topics consumed |
|----------|---------|----------------|
| `ledger-service` | Ledger Service | `transaction.initiated` |
| `risk-engine` | Risk Engine | `balance.reserved` |
| `payment-orchestrator` | Payment Orchestrator | `risk.assessed` |
| `transaction-service` | Transaction Service | `balance.reserved`, `risk.assessed`, `payment.completed`, `payment.failed`, `ledger.committed` |
| `notification-service` | Notification Service | `transaction.completed`, `transaction.failed`, `user.registered` |
| `data-pipeline` | Data Pipeline | `transaction.completed`, `transaction.failed`, `risk.assessment.completed` |
