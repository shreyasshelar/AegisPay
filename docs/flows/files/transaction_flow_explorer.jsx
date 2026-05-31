import { useState, useEffect, useCallback } from "react";

// ─── Steps ────────────────────────────────────────────────────────────────────

const STEPS = [
  {
    id: 1, label: "User submits payment",
    service: "Next.js Web",       tier: "client",
    state: "—",
    event: null,
    detail: "User fills 'Send Money' form (amount, payee, note) and taps Send. Next.js calls POST /api/v1/transactions with an Idempotency-Key UUID header.",
    code: `POST /api/v1/transactions\nAuthorization: Bearer <JWT>\nIdempotency-Key: a3c5e7b9-...\n\n{ recipientId, amount: "500.00", currency: "INR", note: "Lunch split" }`,
    path: "happy",
  },
  {
    id: 2, label: "Gateway validates JWT & rate-limits",
    service: "API Gateway",       tier: "gateway",
    state: "—",
    event: null,
    detail: "Gateway fetches Keycloak JWKS (cached). Verifies JWT signature, expiry, issuer. Checks Idempotency-Key in Redis (SET NX PX 86400000). Applies rate-limit sliding window counter. Forwards to Transaction Service.",
    code: `Redis: SET idempotency:a3c5e7b9 "pending" NX PX 86400000\nRedis: INCR rate:limit:{userId}:tx  → 1 / 100 allowed\nKeycloak JWKS: signature ✓  expiry ✓  issuer ✓`,
    path: "happy",
  },
  {
    id: 3, label: "Transaction Service writes atomically",
    service: "Transaction Service", tier: "core",
    state: "PENDING",
    event: "transaction.initiated",
    detail: "Transaction Service extracts userId from JWT claim aegispay_user_id. Atomically writes: INSERT INTO transactions (status=PENDING) + INSERT INTO outbox_events. Both rows commit or neither does — the event can never be lost.",
    code: `BEGIN;\n  INSERT INTO transactions (id, payer_id, payee_id, amount, status)\n  VALUES (gen_uuid(), :userId, :payeeId, 500.00, 'PENDING');\n\n  INSERT INTO outbox_events (topic, payload)\n  VALUES ('transaction.initiated', '{"transactionId":...}');\nCOMMIT;`,
    path: "happy",
  },
  {
    id: 4, label: "Outbox Relay publishes to Kafka",
    service: "Outbox Relay",       tier: "infra",
    state: "PENDING",
    event: "transaction.initiated",
    detail: "The outbox relay polls outbox_events WHERE published=false every ~1s. Publishes to Kafka, then marks the row published. If the relay crashes between publish and mark, it re-publishes — consumers are idempotent.",
    code: `SELECT * FROM outbox_events WHERE published = false\nORDER BY created_at LIMIT 100;\n\nkafka.send("transaction.initiated", payload)\nUPDATE outbox_events SET published=true, published_at=now()`,
    path: "happy",
  },
  {
    id: 5, label: "Ledger Service reserves funds",
    service: "Ledger Service",     tier: "core",
    state: "RESERVED",
    event: "balance.reserved",
    detail: "Ledger consumes transaction.initiated. Acquires FOR UPDATE row lock on payer account. If available_balance >= amount: decrements available_balance, increments reserved_balance. Publishes balance.reserved. Optimistic locking (version column) prevents concurrent over-reservation.",
    code: `SELECT available_balance FROM accounts\nWHERE user_id=:payerId FOR UPDATE;\n\n-- available_balance: 50000 ≥ 500 ✓\nUPDATE accounts\n  SET available_balance = available_balance - 500,\n      reserved_balance  = reserved_balance  + 500,\n      version           = version + 1\n  WHERE user_id=:payerId AND version=:expectedVersion;`,
    path: "happy",
  },
  {
    id: 6, label: "Risk Engine scores the transaction",
    service: "Risk Engine",        tier: "core",
    state: "RESERVED",
    event: "risk.assessed",
    detail: "Risk Engine consumes balance.reserved. Runs 10 ML-style rules: velocity (txCount24h), device fingerprint, geography, NEW_PAYEE, HIGH_VALUE, SELF_TRANSFER, etc. Also fires a RAG query to AI Platform for similar historical fraud patterns. Emits risk.assessed {score, decision: ALLOW | REVIEW | BLOCK}.",
    code: `riskScore = ruleEngine.evaluate(tx, userHistory)\n// Rules: HIGH_VELOCITY, NEW_DEVICE, GEO_ANOMALY...\n\nragContext = aiPlatform.query(\n  embedding(tx.description), topK=3\n)\n\npublish("risk.assessed", { score: 28, decision: "ALLOW",\n  ruleFlags: ["NEW_PAYEE"] })`,
    path: "happy",
  },
  {
    id: 7, label: "Payment Orchestrator runs Saga",
    service: "Payment Orchestrator", tier: "core",
    state: "RISK_CLEARED",
    event: "payment.completed",
    detail: "Orchestrator consumes risk.assessed (ALLOW). Starts/resumes the saga for this transactionId. Calls Stripe synchronously: POST /v1/payment_intents. Saga state is persisted to aegispay_sagas DB — survives restarts.",
    code: `saga = sagaRepository.findByTransactionId(txId);\nsaga.transitionTo(PAYMENT_PROCESSING);\n\nstripeResult = stripe.paymentIntents.create({\n  amount: 50000,   // INR paisa\n  currency: "inr",\n  metadata: { transactionId: txId }\n});\n// → { id: "pi_3xxx", status: "succeeded" }`,
    path: "happy",
  },
  {
    id: 8, label: "Ledger commits double-entry entries",
    service: "Ledger Service",     tier: "core",
    state: "PROCESSING",
    event: "ledger.committed",
    detail: "Ledger consumes payment.completed. Opens one transaction: inserts DEBIT for payer and CREDIT for payee (append-only). Updates account balances — reserved_balance back to 0, available_balance decremented for payer, incremented for payee. Net sum always = 0.",
    code: `BEGIN;\n  INSERT INTO ledger_entries VALUES\n    (txId, payerAcctId, 'DEBIT',  500, 'INR'),\n    (txId, payeeAcctId, 'CREDIT', 500, 'INR');\n\n  UPDATE accounts SET\n    reserved_balance  = reserved_balance  - 500,  -- payer\n    available_balance = available_balance - 500   -- payer\n  WHERE id = :payerAcctId;\n\n  UPDATE accounts SET\n    available_balance = available_balance + 500   -- payee\n  WHERE id = :payeeAcctId;\nCOMMIT;`,
    path: "happy",
  },
  {
    id: 9, label: "Transaction status → COMPLETED",
    service: "Transaction Service", tier: "core",
    state: "COMPLETED",
    event: "transaction.completed",
    detail: "Transaction Service consumes ledger.committed. Updates transaction status to COMPLETED. Updates MongoDB read model (TransactionView document) for fast CQRS queries. Publishes transaction.completed to Kafka.",
    code: `UPDATE transactions SET status='COMPLETED'\nWHERE id=:txId;\n\n// MongoDB CQRS update\ntransactionViewRepo.save(TransactionView.builder()\n  .id(txId).status("COMPLETED")\n  .completedAt(now())\n  .build());`,
    path: "happy",
  },
  {
    id: 10, label: "WebSocket push — status badge updates",
    service: "Notification Service", tier: "core",
    state: "COMPLETED",
    event: null,
    detail: "Notification Service consumes transaction.completed. Resolves payerId → email from MongoDB UserContactDocument. Dispatches all channels in parallel: STOMP WebSocket push to /user/queue/transactions, Email via Gmail SMTP. The Web App receives the push and updates the status badge to COMPLETED ✅.",
    code: `// WebSocket push\nmessagingTemplate.convertAndSendToUser(\n  userId, "/queue/transactions",\n  TransactionStatusEvent { txId, status: "COMPLETED" }\n);\n\n// Email async\nexecutor.submit(() -> emailService.send(\n  contact.email, "Payment successful ₹500"\n));`,
    path: "happy",
  },
  {
    id: 11, label: "Analytics: Kafka → ClickHouse",
    service: "Data Pipeline",      tier: "analytics",
    state: "COMPLETED",
    event: null,
    detail: "Data Pipeline consumes transaction.completed + risk.assessed events via Kafka Streams. Buffers in TransactionMetricsStream and RiskAnalyticsStream in-memory. Flushes to ClickHouse every 5 seconds (or at 500 records). Grafana dashboards auto-refresh from ClickHouse.",
    code: `// Kafka Streams topology\nbuilder.stream("transaction.completed")\n  .filter((k,v) -> v != null)\n  .mapValues(v -> toFactRecord(v))\n  .foreach((k,v) -> clickHouseSink.writeTransactionFact(v));\n\n// Flush every 5s\n// → transaction_facts, risk_assessments, saga_latencies`,
    path: "happy",
  },
];

const FAIL_STEPS = [
  {
    id: "F1", label: "INSUFFICIENT_FUNDS — ledger rejects",
    service: "Ledger Service",     tier: "core",
    state: "FAILED",
    event: "balance.reservation.failed",
    detail: "Ledger checks available_balance < amount. Publishes balance.reservation.failed. Orchestrator marks saga FAILED. Transaction Service sets status=FAILED with failureCode=INSUFFICIENT_FUNDS.",
    code: `-- available_balance: 200 < 500 ✗\npublish("balance.reservation.failed",\n  { txId, failureCode: "INSUFFICIENT_FUNDS" });\n\nUPDATE transactions SET\n  status='FAILED',\n  failure_code='INSUFFICIENT_FUNDS';`,
    path: "fail",
  },
  {
    id: "F2", label: "RISK_BLOCKED — fraud detected",
    service: "Risk Engine",        tier: "core",
    state: "FAILED",
    event: "risk.assessed (BLOCK)",
    detail: "Risk Engine issues BLOCK decision (score=95, rules=[HIGH_VELOCITY, NEW_DEVICE]). Orchestrator releases the ledger reservation (compensating transaction). Transaction → FAILED with failureCode=RISK_BLOCKED.",
    code: `publish("risk.assessed", {\n  score: 95,\n  decision: "BLOCK",\n  ruleFlags: ["HIGH_VELOCITY", "NEW_DEVICE"]\n});\n\n// Orchestrator compensates:\nledger.releaseReservation(txId);\nUPDATE transactions SET status='FAILED',\n  failure_code='RISK_BLOCKED';`,
    path: "fail",
  },
  {
    id: "F3", label: "STRIPE_ERROR — payment declined",
    service: "Payment Orchestrator", tier: "core",
    state: "FAILED",
    event: "payment.failed",
    detail: "Stripe returns an error (e.g. amount_too_small, card_declined). Orchestrator maps the Stripe error code to an internal failureCode, releases the ledger reservation, publishes payment.failed.",
    code: `// Stripe error response\n{ error: { code: "amount_too_small",\n    message: "Amount must be at least ₹50" } }\n\n// Compensation\nledger.releaseReservation(txId);\npublish("transaction.failed", {\n  txId, failureCode: "amount_too_small"\n});`,
    path: "fail",
  },
];

const TIER_COLORS  = { client: "#0ea5e9", gateway: "#8b5cf6", core: "#059669", infra: "#dc2626", analytics: "#d97706" };
const STATE_COLORS = { "—": "#94a3b8", PENDING: "#d97706", RESERVED: "#0891b2", RISK_CLEARED: "#8b5cf6", PROCESSING: "#6366f1", COMPLETED: "#059669", FAILED: "#dc2626" };

export default function TransactionFlowExplorer() {
  const [currentStep, setCurrentStep] = useState(0);
  const [mode, setMode]               = useState("happy"); // happy | fail
  const [failStep, setFailStep]       = useState(0);
  const [playing, setPlaying]         = useState(false);

  const steps    = mode === "happy" ? STEPS : FAIL_STEPS;
  const step     = steps[currentStep] || steps[0];
  const maxStep  = steps.length - 1;

  useEffect(() => {
    if (!playing) return;
    if (currentStep >= maxStep) { setPlaying(false); return; }
    const t = setTimeout(() => setCurrentStep(s => s + 1), 1800);
    return () => clearTimeout(t);
  }, [playing, currentStep, maxStep]);

  const prev = () => setCurrentStep(s => Math.max(0, s - 1));
  const next = () => setCurrentStep(s => Math.min(maxStep, s + 1));
  const reset = () => { setCurrentStep(0); setPlaying(false); };

  const switchMode = (m) => { setMode(m); setCurrentStep(0); setPlaying(false); };

  const txState = step.state;
  const txColor = STATE_COLORS[txState] || "#94a3b8";

  return (
    <div style={{ fontFamily: "'IBM Plex Sans', system-ui, sans-serif", maxWidth: 880, margin: "0 auto", padding: 24 }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap');`}</style>

      {/* Header */}
      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 10, fontWeight: 700, color: "#94a3b8", letterSpacing: "0.12em", textTransform: "uppercase", marginBottom: 4 }}>AegisPay · Flows</div>
        <div style={{ fontSize: 24, fontWeight: 700, color: "#0f172a", letterSpacing: "-0.02em" }}>Transaction Flow Explorer</div>
        <div style={{ fontSize: 13, color: "#64748b", marginTop: 2 }}>Step through the full payment lifecycle — from user tap to ledger commit</div>
      </div>

      {/* Mode switcher */}
      <div style={{ display: "flex", gap: 8, marginBottom: 20 }}>
        {[{ id: "happy", label: "✅ Happy Path (11 steps)", color: "#059669" }, { id: "fail", label: "❌ Failure Scenarios (3)", color: "#dc2626" }].map(m => (
          <button key={m.id} onClick={() => switchMode(m.id)} style={{ padding: "7px 16px", borderRadius: 8, border: `2px solid ${mode === m.id ? m.color : "#e2e8f0"}`, background: mode === m.id ? m.color : "#fff", color: mode === m.id ? "#fff" : "#475569", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
            {m.label}
          </button>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "220px 1fr", gap: 16 }}>
        {/* Step list */}
        <div style={{ background: "#f8fafc", borderRadius: 12, padding: 12, border: "1px solid #e2e8f0", height: "fit-content" }}>
          <div style={{ fontSize: 10, fontWeight: 700, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: 10 }}>Steps</div>
          {steps.map((s, i) => {
            const isActive = i === currentStep;
            const isDone   = i < currentStep;
            const col      = s.path === "fail" ? "#dc2626" : TIER_COLORS[s.tier] || "#64748b";
            return (
              <div
                key={s.id}
                onClick={() => { setCurrentStep(i); setPlaying(false); }}
                style={{ display: "flex", alignItems: "center", gap: 8, padding: "7px 8px", borderRadius: 7, marginBottom: 3, cursor: "pointer", background: isActive ? col + "18" : "transparent", border: isActive ? `1.5px solid ${col}` : "1.5px solid transparent", transition: "all 0.12s" }}
              >
                <span style={{ width: 20, height: 20, borderRadius: "50%", background: isDone ? col : (isActive ? col : "#e2e8f0"), color: isDone || isActive ? "#fff" : "#94a3b8", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 9, fontWeight: 700, flexShrink: 0 }}>
                  {isDone ? "✓" : s.id}
                </span>
                <span style={{ fontSize: 10, fontWeight: isActive ? 700 : 400, color: isActive ? "#0f172a" : (isDone ? "#64748b" : "#94a3b8"), lineHeight: 1.3 }}>{s.label}</span>
              </div>
            );
          })}
        </div>

        {/* Main panel */}
        <div>
          {/* Transaction state badge */}
          <div style={{ display: "flex", gap: 12, marginBottom: 14, alignItems: "center" }}>
            <div style={{ fontSize: 11, color: "#64748b" }}>Transaction state:</div>
            <div style={{ background: txColor + "18", border: `1.5px solid ${txColor}`, borderRadius: 20, padding: "3px 12px", fontSize: 12, fontWeight: 700, color: txColor, fontFamily: "monospace" }}>
              {txState}
            </div>
            {step.event && (
              <>
                <div style={{ fontSize: 11, color: "#94a3b8" }}>event →</div>
                <div style={{ background: "#f1f5f9", borderRadius: 20, padding: "3px 12px", fontSize: 11, color: "#475569", fontFamily: "monospace" }}>
                  {step.event}
                </div>
              </>
            )}
          </div>

          {/* Step card */}
          <div style={{ background: "#fff", borderRadius: 12, border: `2px solid ${TIER_COLORS[step.tier] || "#dc2626"}`, padding: 20, marginBottom: 14 }}>
            <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", marginBottom: 12 }}>
              <div>
                <div style={{ fontSize: 10, fontWeight: 700, color: TIER_COLORS[step.tier] || "#dc2626", letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 4 }}>
                  Step {step.id} · {step.service}
                </div>
                <div style={{ fontSize: 18, fontWeight: 700, color: "#0f172a" }}>{step.label}</div>
              </div>
              <span style={{ background: (TIER_COLORS[step.tier] || "#dc2626") + "20", border: `1px solid ${TIER_COLORS[step.tier] || "#dc2626"}33`, borderRadius: 6, padding: "4px 10px", fontSize: 10, fontWeight: 600, color: TIER_COLORS[step.tier] || "#dc2626", whiteSpace: "nowrap" }}>
                {step.tier}
              </span>
            </div>

            <div style={{ fontSize: 13, color: "#374151", lineHeight: 1.7, marginBottom: 14 }}>{step.detail}</div>

            <div style={{ background: "#0f172a", borderRadius: 8, padding: "12px 14px" }}>
              <div style={{ fontSize: 9, color: "#64748b", fontWeight: 700, letterSpacing: "0.08em", textTransform: "uppercase", marginBottom: 6 }}>Code / Query</div>
              <pre style={{ margin: 0, fontSize: 11, color: "#e2e8f0", fontFamily: "'JetBrains Mono', monospace", lineHeight: 1.7, whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{step.code}</pre>
            </div>
          </div>

          {/* Controls */}
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <button onClick={reset}  style={{ padding: "7px 14px", borderRadius: 8, border: "1.5px solid #e2e8f0", background: "#fff", color: "#64748b", fontSize: 12, cursor: "pointer" }}>↺ Reset</button>
            <button onClick={prev}   disabled={currentStep === 0} style={{ padding: "7px 16px", borderRadius: 8, border: "1.5px solid #e2e8f0", background: "#fff", color: currentStep === 0 ? "#cbd5e1" : "#374151", fontSize: 12, cursor: currentStep === 0 ? "not-allowed" : "pointer" }}>← Prev</button>
            <button onClick={next}   disabled={currentStep === maxStep} style={{ padding: "7px 16px", borderRadius: 8, border: `1.5px solid ${currentStep === maxStep ? "#e2e8f0" : "#6366f1"}`, background: currentStep === maxStep ? "#fff" : "#6366f1", color: currentStep === maxStep ? "#cbd5e1" : "#fff", fontSize: 12, cursor: currentStep === maxStep ? "not-allowed" : "pointer", fontWeight: 600 }}>
              Next →
            </button>
            <button onClick={() => setPlaying(p => !p)} style={{ padding: "7px 16px", borderRadius: 8, border: "1.5px solid #e2e8f0", background: playing ? "#fef3c7" : "#fff", color: "#374151", fontSize: 12, cursor: "pointer" }}>
              {playing ? "⏸ Pause" : "▶ Auto-play"}
            </button>
            <div style={{ marginLeft: "auto", fontSize: 11, color: "#94a3b8", fontFamily: "monospace" }}>
              {currentStep + 1} / {steps.length}
            </div>
          </div>

          {/* Progress bar */}
          <div style={{ marginTop: 10, height: 4, background: "#f1f5f9", borderRadius: 4, overflow: "hidden" }}>
            <div style={{ height: "100%", width: `${((currentStep + 1) / steps.length) * 100}%`, background: mode === "happy" ? "#059669" : "#dc2626", borderRadius: 4, transition: "width 0.3s ease" }} />
          </div>
        </div>
      </div>

      {/* State machine legend */}
      {mode === "happy" && (
        <div style={{ marginTop: 20, background: "#f8fafc", borderRadius: 12, padding: 16, border: "1px solid #e2e8f0" }}>
          <div style={{ fontSize: 11, fontWeight: 600, color: "#64748b", marginBottom: 10, textTransform: "uppercase", letterSpacing: "0.06em" }}>Transaction State Machine</div>
          <div style={{ display: "flex", alignItems: "center", gap: 6, flexWrap: "wrap" }}>
            {["—", "PENDING", "RESERVED", "RISK_CLEARED", "PROCESSING", "COMPLETED"].map((s, i, arr) => (
              <div key={s} style={{ display: "flex", alignItems: "center", gap: 6 }}>
                <span style={{ padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 600, background: STATE_COLORS[s] + "18", color: STATE_COLORS[s], border: `1.5px solid ${STATE_COLORS[s]}`, fontFamily: "monospace" }}>{s}</span>
                {i < arr.length - 1 && <span style={{ color: "#cbd5e1", fontSize: 16 }}>→</span>}
              </div>
            ))}
            <span style={{ color: "#cbd5e1", fontSize: 14, margin: "0 4px" }}>·</span>
            <span style={{ padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 600, background: "#fef2f2", color: "#dc2626", border: "1.5px solid #dc2626", fontFamily: "monospace" }}>FAILED (any stage)</span>
          </div>
        </div>
      )}

      <div style={{ marginTop: 12, fontSize: 10, color: "#cbd5e1", textAlign: "center" }}>
        AegisPay · docs/flows/files/transaction_flow_explorer.jsx
      </div>
    </div>
  );
}
