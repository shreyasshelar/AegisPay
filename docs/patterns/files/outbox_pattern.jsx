import { useState, useEffect, useRef } from "react";

// ─── Scenarios ────────────────────────────────────────────────────────────────

const BAD_STEPS = [
  { id: 1, actor: "Transaction Service", action: "INSERT INTO transactions (status=PENDING)", result: "✅ Row written to Postgres", good: true  },
  { id: 2, actor: "Transaction Service", action: "💥 SERVICE CRASHES HERE",                   result: "❌ Crash before Kafka publish!", good: false, crash: true },
  { id: 3, actor: "Kafka",               action: "transaction.initiated never published",     result: "❌ No event — payment flow stalls forever", good: false },
  { id: 4, actor: "System",              action: "Ghost transaction stuck in PENDING",        result: "❌ User never notified. Ledger never reserved.", good: false },
];

const GOOD_STEPS = [
  { id: 1, actor: "Transaction Service", action: "BEGIN TRANSACTION",                         result: "", good: true  },
  { id: 2, actor: "Postgres",            action: "INSERT INTO transactions (status=PENDING)", result: "✅ Domain entity written", good: true },
  { id: 3, actor: "Postgres",            action: "INSERT INTO outbox_events (topic='transaction.initiated', payload=...)", result: "✅ Event row in SAME transaction", good: true },
  { id: 4, actor: "Transaction Service", action: "COMMIT;",                                   result: "✅ Both rows committed atomically — or neither!", good: true },
  { id: 5, actor: "Outbox Relay",        action: "SELECT * FROM outbox_events WHERE published=false", result: "✅ Relay polls every ~1s", good: true },
  { id: 6, actor: "Outbox Relay",        action: "kafka.send('transaction.initiated', payload)", result: "✅ Event published to Kafka broker", good: true },
  { id: 7, actor: "Outbox Relay",        action: "UPDATE outbox_events SET published=true",   result: "✅ Marked as delivered", good: true },
  { id: 8, actor: "Kafka Consumers",     action: "Ledger, Risk, Pipeline consume event",      result: "✅ Payment flow continues normally", good: true },
];

const CRASH_STEPS = [
  { id: 1, actor: "Transaction Service", action: "BEGIN → INSERT both rows → COMMIT",         result: "✅ Atomic — both rows in DB", good: true },
  { id: 2, actor: "Outbox Relay",        action: "kafka.send('transaction.initiated', ...)",  result: "✅ Published to Kafka", good: true },
  { id: 3, actor: "Outbox Relay",        action: "💥 RELAY CRASHES before UPDATE outbox_events", result: "⚠️ Crash! Row still shows published=false", good: false, crash: true },
  { id: 4, actor: "Outbox Relay",        action: "Service restarts — re-queries unpublished rows", result: "⚠️ Duplicate publish to Kafka!", good: false },
  { id: 5, actor: "Kafka Consumers",     action: "Check: already processed tx-id in MongoDB?","result": "✅ Idempotency: ON CONFLICT DO NOTHING", good: true },
  { id: 6, actor: "System",              action: "Duplicate safely ignored",                  result: "✅ Exactly-once processing preserved", good: true },
];

const SCENARIOS = [
  { id: "bad",   label: "❌ Without Outbox",          color: "#dc2626", steps: BAD_STEPS },
  { id: "good",  label: "✅ With Outbox Pattern",     color: "#059669", steps: GOOD_STEPS },
  { id: "crash", label: "⚠️ Relay Crash (duplicate?)", color: "#d97706", steps: CRASH_STEPS },
];

const ACTOR_COLORS = {
  "Transaction Service": "#6366f1",
  "Postgres":            "#3b82f6",
  "Outbox Relay":        "#8b5cf6",
  "Kafka":               "#dc2626",
  "Kafka Consumers":     "#059669",
  "System":              "#64748b",
};

export default function OutboxPattern() {
  const [scenario, setScenario]   = useState("bad");
  const [stepIdx,  setStepIdx]    = useState(0);
  const [playing,  setPlaying]    = useState(false);

  const scen  = SCENARIOS.find(s => s.id === scenario);
  const steps = scen.steps;
  const step  = steps[stepIdx] || steps[0];
  const maxStep = steps.length - 1;

  useEffect(() => {
    if (!playing) return;
    if (stepIdx >= maxStep) { setPlaying(false); return; }
    const delay = step.crash ? 1200 : 1400;
    const t = setTimeout(() => setStepIdx(i => i + 1), delay);
    return () => clearTimeout(t);
  }, [playing, stepIdx, maxStep, step]);

  const switchScenario = (id) => { setScenario(id); setStepIdx(0); setPlaying(false); };
  const next  = () => setStepIdx(i => Math.min(maxStep, i + 1));
  const prev  = () => setStepIdx(i => Math.max(0, i - 1));
  const reset = () => { setStepIdx(0); setPlaying(false); };

  // DB state simulator
  const dbRows = steps.slice(0, stepIdx + 1).filter(s => s.good || s.id <= stepIdx);

  return (
    <div style={{ fontFamily: "'IBM Plex Sans', system-ui, sans-serif", maxWidth: 860, margin: "0 auto", padding: 24 }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap');`}</style>

      {/* Header */}
      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 10, fontWeight: 700, color: "#94a3b8", letterSpacing: "0.12em", textTransform: "uppercase", marginBottom: 4 }}>AegisPay · Patterns</div>
        <div style={{ fontSize: 24, fontWeight: 700, color: "#0f172a", letterSpacing: "-0.02em" }}>Transactional Outbox Pattern</div>
        <div style={{ fontSize: 13, color: "#64748b", marginTop: 2 }}>The only safe way to write to both Postgres and Kafka without losing events</div>
      </div>

      {/* The Problem callout */}
      <div style={{ background: "#fef2f2", borderRadius: 10, padding: "12px 16px", border: "1px solid #fecaca", marginBottom: 20 }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: "#dc2626", marginBottom: 6 }}>⚠️ The Core Problem</div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <div style={{ fontSize: 11, color: "#374151", lineHeight: 1.6 }}>
            <strong>Order A:</strong> Write DB first, then publish to Kafka<br/>
            → Crash between write and publish → event lost → ghost transaction stuck PENDING forever
          </div>
          <div style={{ fontSize: 11, color: "#374151", lineHeight: 1.6 }}>
            <strong>Order B:</strong> Publish to Kafka first, then write DB<br/>
            → Crash after publish → DB write never happens → consumers process event for non-existent transaction
          </div>
        </div>
        <div style={{ marginTop: 8, fontSize: 12, fontWeight: 600, color: "#dc2626" }}>Neither order is safe. You cannot atomically write to two different systems.</div>
      </div>

      {/* Scenario tabs */}
      <div style={{ display: "flex", gap: 8, marginBottom: 20 }}>
        {SCENARIOS.map(s => (
          <button
            key={s.id}
            onClick={() => switchScenario(s.id)}
            style={{ padding: "7px 16px", borderRadius: 8, border: `2px solid ${scenario === s.id ? s.color : "#e2e8f0"}`, background: scenario === s.id ? s.color : "#fff", color: scenario === s.id ? "#fff" : "#475569", fontSize: 12, fontWeight: 600, cursor: "pointer" }}
          >
            {s.label}
          </button>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 340px", gap: 16 }}>
        {/* Step list */}
        <div>
          <div style={{ background: "#f8fafc", borderRadius: 12, padding: 16, border: "1px solid #e2e8f0", marginBottom: 14 }}>
            {steps.map((s, i) => {
              const isActive  = i === stepIdx;
              const isDone    = i < stepIdx;
              const actorCol  = ACTOR_COLORS[s.actor] || "#64748b";
              return (
                <div
                  key={s.id}
                  onClick={() => { setStepIdx(i); setPlaying(false); }}
                  style={{ display: "flex", gap: 12, padding: "10px 12px", borderRadius: 8, marginBottom: 6, cursor: "pointer", background: isActive ? (s.crash ? "#fef2f2" : "#f0fdf4") : (isDone ? "#f8fafc" : "#fff"), border: `1.5px solid ${isActive ? (s.crash ? "#dc2626" : "#059669") : "#e2e8f0"}`, transition: "all 0.15s" }}
                >
                  <div style={{ width: 22, height: 22, borderRadius: "50%", background: isDone ? actorCol : (isActive ? actorCol : "#e2e8f0"), color: isDone || isActive ? "#fff" : "#94a3b8", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 9, fontWeight: 700, flexShrink: 0, marginTop: 1 }}>
                    {isDone ? "✓" : s.id}
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", gap: 8, alignItems: "baseline", marginBottom: 2 }}>
                      <span style={{ fontSize: 10, fontWeight: 700, color: actorCol }}>{s.actor}</span>
                    </div>
                    <div style={{ fontSize: 11, fontFamily: "monospace", color: s.crash ? "#dc2626" : (isDone ? "#64748b" : "#0f172a"), fontWeight: s.crash ? 700 : 400 }}>{s.action}</div>
                    {(isActive || isDone) && s.result && (
                      <div style={{ fontSize: 11, color: s.good ? "#059669" : "#dc2626", marginTop: 3 }}>{s.result}</div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>

          {/* Controls */}
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <button onClick={reset} style={{ padding: "7px 14px", borderRadius: 8, border: "1.5px solid #e2e8f0", background: "#fff", color: "#64748b", fontSize: 12, cursor: "pointer" }}>↺ Reset</button>
            <button onClick={prev} disabled={stepIdx === 0} style={{ padding: "7px 14px", borderRadius: 8, border: "1.5px solid #e2e8f0", background: "#fff", color: stepIdx === 0 ? "#cbd5e1" : "#374151", fontSize: 12, cursor: stepIdx === 0 ? "not-allowed" : "pointer" }}>← Prev</button>
            <button onClick={next} disabled={stepIdx === maxStep} style={{ padding: "7px 14px", borderRadius: 8, border: `1.5px solid ${stepIdx === maxStep ? "#e2e8f0" : scen.color}`, background: stepIdx === maxStep ? "#fff" : scen.color, color: stepIdx === maxStep ? "#cbd5e1" : "#fff", fontSize: 12, fontWeight: 600, cursor: stepIdx === maxStep ? "not-allowed" : "pointer" }}>Next →</button>
            <button onClick={() => setPlaying(p => !p)} style={{ padding: "7px 14px", borderRadius: 8, border: "1.5px solid #e2e8f0", background: playing ? "#fef3c7" : "#fff", color: "#374151", fontSize: 12, cursor: "pointer" }}>
              {playing ? "⏸ Pause" : "▶ Auto"}
            </button>
            <div style={{ marginLeft: "auto", fontSize: 11, color: "#94a3b8", fontFamily: "monospace" }}>{stepIdx + 1} / {steps.length}</div>
          </div>
        </div>

        {/* Right panel — live system state */}
        <div>
          <div style={{ background: "#f8fafc", borderRadius: 12, padding: 14, border: "1px solid #e2e8f0", marginBottom: 12 }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 10 }}>🗄 Postgres State</div>

            <div style={{ marginBottom: 8 }}>
              <div style={{ fontSize: 10, color: "#94a3b8", fontFamily: "monospace", marginBottom: 4 }}>transactions</div>
              {scenario !== "bad" || stepIdx >= 0 ? (
                <div style={{ background: "#fff", borderRadius: 6, padding: "6px 8px", border: "1px solid #e2e8f0", fontFamily: "monospace", fontSize: 10 }}>
                  <div style={{ color: "#059669" }}>id: tx-001 | status: PENDING</div>
                </div>
              ) : (
                <div style={{ fontSize: 10, color: "#94a3b8" }}>empty</div>
              )}
            </div>

            <div>
              <div style={{ fontSize: 10, color: "#94a3b8", fontFamily: "monospace", marginBottom: 4 }}>outbox_events</div>
              {scenario === "bad" ? (
                <div style={{ fontSize: 10, color: "#dc2626", background: "#fef2f2", borderRadius: 6, padding: "6px 8px" }}>❌ No outbox table — pattern not implemented!</div>
              ) : stepIdx >= 3 ? (
                <div style={{ background: "#fff", borderRadius: 6, padding: "6px 8px", border: "1px solid #e2e8f0", fontFamily: "monospace", fontSize: 10 }}>
                  <div style={{ color: stepIdx >= 7 ? "#059669" : "#d97706" }}>
                    id: ev-001 | topic: transaction.initiated<br/>
                    published: {scenario === "crash" && stepIdx >= 3 && stepIdx <= 4 ? "false (CRASH!)" : (stepIdx >= 7 ? "true" : "false")}
                  </div>
                </div>
              ) : stepIdx >= 1 ? (
                <div style={{ background: "#fff", borderRadius: 6, padding: "6px 8px", border: "1px solid #e2e8f0", fontFamily: "monospace", fontSize: 10 }}>
                  <div style={{ color: "#d97706" }}>id: ev-001 | topic: transaction.initiated<br/>published: false</div>
                </div>
              ) : (
                <div style={{ fontSize: 10, color: "#94a3b8" }}>empty</div>
              )}
            </div>
          </div>

          <div style={{ background: "#f8fafc", borderRadius: 12, padding: 14, border: "1px solid #e2e8f0", marginBottom: 12 }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 10 }}>⚡ Kafka Broker</div>
            {((scenario === "good" && stepIdx >= 6) || (scenario === "crash" && (stepIdx === 2 || stepIdx >= 4))) ? (
              <div style={{ background: "#f0fdf4", borderRadius: 6, padding: "6px 8px", border: "1px solid #bbf7d0", fontFamily: "monospace", fontSize: 10, color: "#059669" }}>
                ✓ transaction.initiated<br/>
                {scenario === "crash" && stepIdx >= 4 ? "⚠️ DUPLICATE (safe)" : ""}
              </div>
            ) : scenario === "bad" && stepIdx >= 2 ? (
              <div style={{ background: "#fef2f2", borderRadius: 6, padding: "6px 8px", border: "1px solid #fecaca", fontFamily: "monospace", fontSize: 10, color: "#dc2626" }}>
                ❌ No events received
              </div>
            ) : (
              <div style={{ fontSize: 10, color: "#94a3b8" }}>No events yet</div>
            )}
          </div>

          {/* Current step explanation */}
          <div style={{ background: step.crash ? "#fef2f2" : (step.good ? "#f0fdf4" : "#fff7ed"), borderRadius: 10, padding: "12px 14px", border: `1px solid ${step.crash ? "#fecaca" : (step.good ? "#bbf7d0" : "#fed7aa")}` }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: step.crash ? "#dc2626" : (step.good ? "#166534" : "#c2410c"), textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 6 }}>
              {step.crash ? "💥 Crash!" : (step.good ? "✅ Success" : "⚠️ Problem")}
            </div>
            <div style={{ fontSize: 11, color: "#374151", lineHeight: 1.6 }}>
              {step.crash
                ? "Service crashes. Depending on the pattern used, the outcome is either catastrophic data loss or safe idempotent replay."
                : step.good
                ? "This step executes successfully. The Outbox pattern ensures atomicity — both domain entity and event are persisted together."
                : "Without the Outbox pattern, a crash here leaves the system in an inconsistent state with no recovery path."}
            </div>
          </div>
        </div>
      </div>

      {/* Guarantees table */}
      <div style={{ marginTop: 20, background: "#f8fafc", borderRadius: 12, padding: 16, border: "1px solid #e2e8f0" }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: "#0f172a", marginBottom: 12 }}>Outbox Pattern Guarantees</div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: 10 }}>
          {[
            { scenario: "DB write succeeds, broker publish fails", outcome: "✅ Event stays in outbox; relay retries on next poll", color: "#059669" },
            { scenario: "Relay crashes before marking published",   outcome: "✅ Event replayed on restart (consumer idempotency handles duplicate)", color: "#059669" },
            { scenario: "DB transaction rolls back",                outcome: "✅ Outbox row also rolls back — event never published", color: "#059669" },
            { scenario: "Without outbox: crash after DB write",     outcome: "❌ Event never published — ghost transaction forever", color: "#dc2626" },
          ].map((row, i) => (
            <div key={i} style={{ padding: "10px 12px", background: "#fff", borderRadius: 8, borderLeft: `3px solid ${row.color}` }}>
              <div style={{ fontSize: 11, color: "#64748b", marginBottom: 4 }}>{row.scenario}</div>
              <div style={{ fontSize: 11, fontWeight: 700, color: row.color }}>{row.outcome}</div>
            </div>
          ))}
        </div>
        <div style={{ marginTop: 12, padding: "8px 12px", background: "#f0f9ff", borderRadius: 8, fontSize: 11, color: "#0369a1" }}>
          <strong>Key insight:</strong> At-least-once delivery + idempotent consumers = effectively-once processing. Never exactly-once without consumer idempotency.
        </div>
      </div>

      <div style={{ marginTop: 12, fontSize: 10, color: "#cbd5e1", textAlign: "center" }}>AegisPay · docs/patterns/files/outbox_pattern.jsx</div>
    </div>
  );
}
