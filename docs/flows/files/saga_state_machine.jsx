import { useState } from "react";

// ─── State machine definition ─────────────────────────────────────────────────

const STATES = {
  STARTED:            { label: "STARTED",            color: "#6366f1", desc: "Saga created when transaction.initiated consumed. Saga row persisted to aegispay_sagas DB.", x: 300, y: 30  },
  FUNDS_RESERVED:     { label: "FUNDS_RESERVED",     color: "#0891b2", desc: "Ledger service successfully reserved funds. balance.reserved published. Payer's available_balance decremented, reserved_balance incremented.", x: 300, y: 120 },
  RISK_ASSESSED:      { label: "RISK_ASSESSED",      color: "#8b5cf6", desc: "Risk engine scored the transaction. Decision: ALLOW or REVIEW. RAG query complete. risk.assessed published with score + rule flags.", x: 300, y: 210 },
  PAYMENT_PROCESSING: { label: "PAYMENT_PROCESSING", color: "#d97706", desc: "Stripe API call in progress. POST /v1/payment_intents with transaction amount. Saga paused waiting for Stripe response.", x: 300, y: 300 },
  PAYMENT_COMPLETED:  { label: "PAYMENT_COMPLETED",  color: "#059669", desc: "Stripe returned success: { id: 'pi_3xxx', status: 'succeeded' }. payment.completed published to Kafka.", x: 300, y: 390 },
  LEDGER_COMMITTED:   { label: "LEDGER_COMMITTED",   color: "#059669", desc: "Ledger service wrote append-only DEBIT + CREDIT entries. Accounts updated. ledger.committed published. SUM(DEBIT) = SUM(CREDIT) verified.", x: 300, y: 480 },
  COMPLETED:          { label: "COMPLETED",          color: "#059669", desc: "Saga complete. Transaction status → COMPLETED. WebSocket push sent. Notifications dispatched.", x: 300, y: 570 },
  COMPENSATING:       { label: "COMPENSATING",       color: "#dc2626", desc: "Compensation chain executing. Releasing funds reservation. Possibly issuing Stripe refund. Every compensation is idempotent + logged.", x: 600, y: 300 },
  FUNDS_RELEASED:     { label: "FUNDS_RELEASED",     color: "#dc2626", desc: "Ledger reservation released. reserved_balance → 0, available_balance restored. Compensation successful.", x: 600, y: 440 },
  FAILED:             { label: "FAILED",             color: "#dc2626", desc: "Saga failed and compensated. Transaction status → FAILED with specific failureCode. User notified.", x: 600, y: 570 },
};

// Happy path transitions
const TRANSITIONS = [
  { from: "STARTED",            to: "FUNDS_RESERVED",     label: "balance.reserved",       path: "happy" },
  { from: "FUNDS_RESERVED",     to: "RISK_ASSESSED",      label: "risk.assessed (ALLOW)",  path: "happy" },
  { from: "RISK_ASSESSED",      to: "PAYMENT_PROCESSING", label: "Stripe API call",        path: "happy" },
  { from: "PAYMENT_PROCESSING", to: "PAYMENT_COMPLETED",  label: "Stripe success",         path: "happy" },
  { from: "PAYMENT_COMPLETED",  to: "LEDGER_COMMITTED",   label: "ledger.committed",       path: "happy" },
  { from: "LEDGER_COMMITTED",   to: "COMPLETED",          label: "saga done",              path: "happy" },
  // Failure transitions
  { from: "STARTED",            to: "FAILED",             label: "INSUFFICIENT_FUNDS",     path: "fail" },
  { from: "FUNDS_RESERVED",     to: "COMPENSATING",       label: "risk.assessed (BLOCK)",  path: "fail" },
  { from: "PAYMENT_PROCESSING", to: "COMPENSATING",       label: "Stripe error",           path: "fail" },
  { from: "PAYMENT_COMPLETED",  to: "COMPENSATING",       label: "Ledger commit failed",   path: "fail" },
  { from: "COMPENSATING",       to: "FUNDS_RELEASED",     label: "reservation released",   path: "fail" },
  { from: "FUNDS_RELEASED",     to: "FAILED",             label: "saga compensated",       path: "fail" },
];

// Scenarios
const SCENARIOS = [
  {
    id: "happy",
    label: "✅ Happy Path",
    color: "#059669",
    path: ["STARTED", "FUNDS_RESERVED", "RISK_ASSESSED", "PAYMENT_PROCESSING", "PAYMENT_COMPLETED", "LEDGER_COMMITTED", "COMPLETED"],
    desc: "Payment flows through all steps without errors.",
  },
  {
    id: "insuf",
    label: "💸 Insufficient Funds",
    color: "#dc2626",
    path: ["STARTED", "FAILED"],
    desc: "Ledger rejects reservation immediately. Saga fails at step 1 — no compensation needed.",
  },
  {
    id: "risk",
    label: "🚨 Risk Blocked",
    color: "#dc2626",
    path: ["STARTED", "FUNDS_RESERVED", "COMPENSATING", "FUNDS_RELEASED", "FAILED"],
    desc: "Risk engine issues BLOCK (score ≥ 80). Orchestrator releases reservation as compensation.",
  },
  {
    id: "stripe",
    label: "💳 Stripe Error",
    color: "#d97706",
    path: ["STARTED", "FUNDS_RESERVED", "RISK_ASSESSED", "PAYMENT_PROCESSING", "COMPENSATING", "FUNDS_RELEASED", "FAILED"],
    desc: "Stripe returns error (card_declined, amount_too_small). Reservation released. No ledger entries written.",
  },
  {
    id: "timeout",
    label: "⏱ Saga Timeout",
    color: "#d97706",
    path: ["STARTED", "FUNDS_RESERVED", "RISK_ASSESSED", "PAYMENT_PROCESSING", "COMPENSATING", "FUNDS_RELEASED", "FAILED"],
    desc: "Saga exceeds 5-min timeout. SagaTimeoutAlert fires. Compensation chain triggered automatically.",
  },
];

const COMPENSATION_STEPS = {
  COMPENSATING: [
    { step: "Check which saga step failed", icon: "🔍" },
    { step: "If funds reserved → release_reservation(txId)", icon: "💰" },
    { step: "If Stripe charged → issue stripe.refund(pi_xxx)", icon: "↩" },
    { step: "Log compensation entry to ledger for audit", icon: "📋" },
    { step: "Mark saga FAILED + persist to aegispay_sagas", icon: "💾" },
  ],
};

export default function SagaStateMachine() {
  const [scenario, setScenario] = useState("happy");
  const [stepIdx,  setStepIdx]  = useState(0);
  const [hovered,  setHovered]  = useState(null);

  const scen    = SCENARIOS.find(s => s.id === scenario);
  const path    = scen.path;
  const current = path[stepIdx] || path[0];
  const curState = STATES[current];

  const next  = () => setStepIdx(i => Math.min(path.length - 1, i + 1));
  const prev  = () => setStepIdx(i => Math.max(0, i - 1));
  const reset = () => setStepIdx(0);

  // Compact vertical flow diagram
  const renderFlow = () => {
    return (
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 0 }}>
        {path.map((sid, i) => {
          const s       = STATES[sid];
          const isActive = sid === current;
          const isDone   = i < stepIdx;
          const isFail   = s.color === "#dc2626" && sid !== "COMPENSATING";
          const isComp   = sid === "COMPENSATING";

          return (
            <div key={sid} style={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
              <div
                onClick={() => setStepIdx(i)}
                onMouseEnter={() => setHovered(sid)}
                onMouseLeave={() => setHovered(null)}
                style={{
                  padding:     "8px 20px",
                  borderRadius: 10,
                  border:      `2px solid ${isActive ? s.color : (isDone ? s.color + "80" : "#e2e8f0")}`,
                  background:  isActive ? s.color : (isDone ? s.color + "15" : "#fff"),
                  color:       isActive ? "#fff" : (isDone ? s.color : "#94a3b8"),
                  fontSize:    12,
                  fontWeight:  isActive ? 700 : 500,
                  fontFamily:  "monospace",
                  cursor:      "pointer",
                  transition:  "all 0.15s",
                  minWidth:    180,
                  textAlign:   "center",
                  boxShadow:   isActive ? `0 2px 12px ${s.color}40` : "none",
                }}
              >
                {isDone ? "✓ " : (isActive ? "● " : "○ ")}{sid.replace(/_/g, "_​")}
              </div>
              {i < path.length - 1 && (
                <div style={{ display: "flex", flexDirection: "column", alignItems: "center", margin: "2px 0" }}>
                  <div style={{ width: 2, height: 10, background: isDone ? STATES[path[i + 1]]?.color + "60" : "#e2e8f0" }} />
                  <div style={{ fontSize: 9, color: "#94a3b8", fontFamily: "monospace", padding: "1px 0", maxWidth: 200, textAlign: "center" }}>
                    {TRANSITIONS.find(t => t.from === sid && t.to === path[i + 1])?.label || "→"}
                  </div>
                  <div style={{ width: 2, height: 10, background: isDone ? STATES[path[i + 1]]?.color + "60" : "#e2e8f0" }} />
                </div>
              )}
            </div>
          );
        })}
      </div>
    );
  };

  return (
    <div style={{ fontFamily: "'IBM Plex Sans', system-ui, sans-serif", maxWidth: 860, margin: "0 auto", padding: 24 }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap');`}</style>

      {/* Header */}
      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 10, fontWeight: 700, color: "#94a3b8", letterSpacing: "0.12em", textTransform: "uppercase", marginBottom: 4 }}>AegisPay · Saga Pattern</div>
        <div style={{ fontSize: 24, fontWeight: 700, color: "#0f172a", letterSpacing: "-0.02em" }}>Saga State Machine Explorer</div>
        <div style={{ fontSize: 13, color: "#64748b", marginTop: 2 }}>Orchestrated saga — step through every state transition, including compensation paths</div>
      </div>

      {/* Scenario picker */}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 20 }}>
        {SCENARIOS.map(s => (
          <button
            key={s.id}
            onClick={() => { setScenario(s.id); setStepIdx(0); }}
            style={{ padding: "6px 14px", borderRadius: 8, border: `2px solid ${scenario === s.id ? s.color : "#e2e8f0"}`, background: scenario === s.id ? s.color : "#fff", color: scenario === s.id ? "#fff" : "#475569", fontSize: 11, fontWeight: 600, cursor: "pointer" }}
          >
            {s.label}
          </button>
        ))}
      </div>

      {/* Scenario description */}
      <div style={{ background: scen.color + "10", border: `1px solid ${scen.color}33`, borderRadius: 8, padding: "10px 14px", marginBottom: 20, fontSize: 12, color: "#374151" }}>
        <strong style={{ color: scen.color }}>{scen.label}</strong> — {scen.desc}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "220px 1fr", gap: 20 }}>
        {/* Flow diagram */}
        <div style={{ background: "#f8fafc", borderRadius: 12, padding: 16, border: "1px solid #e2e8f0" }}>
          <div style={{ fontSize: 10, fontWeight: 700, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: 12, textAlign: "center" }}>State Path</div>
          {renderFlow()}
        </div>

        {/* State detail */}
        <div>
          <div style={{ background: "#fff", borderRadius: 12, border: `2px solid ${curState.color}`, padding: 20, marginBottom: 14 }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: curState.color, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 6 }}>
              Current State · Step {stepIdx + 1} of {path.length}
            </div>
            <div style={{ fontSize: 20, fontWeight: 700, color: "#0f172a", fontFamily: "monospace", marginBottom: 12 }}>
              {current}
            </div>
            <div style={{ fontSize: 13, color: "#374151", lineHeight: 1.7 }}>{curState.desc}</div>

            {/* Compensation detail */}
            {current === "COMPENSATING" && (
              <div style={{ marginTop: 14 }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#dc2626", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 8 }}>Compensation Steps</div>
                {COMPENSATION_STEPS.COMPENSATING.map((cs, i) => (
                  <div key={i} style={{ display: "flex", alignItems: "flex-start", gap: 8, marginBottom: 6, padding: "7px 10px", background: "#fef2f2", borderRadius: 6 }}>
                    <span style={{ fontSize: 14 }}>{cs.icon}</span>
                    <span style={{ fontSize: 11, color: "#374151" }}>{cs.step}</span>
                  </div>
                ))}
              </div>
            )}

            {/* Happy path detail boxes */}
            {current === "LEDGER_COMMITTED" && (
              <div style={{ marginTop: 14, background: "#f0fdf4", borderRadius: 8, padding: "10px 12px", border: "1px solid #bbf7d0" }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#166534", marginBottom: 6 }}>Double-Entry Entries Written</div>
                <div style={{ fontFamily: "monospace", fontSize: 11, color: "#374151", lineHeight: 1.8 }}>
                  <div>DEBIT   payer_account  ₹500   (money leaves payer)</div>
                  <div>CREDIT  payee_account  ₹500   (money arrives at payee)</div>
                  <div style={{ color: "#059669", marginTop: 4 }}>∑ all entries = ₹0   ← invariant always holds</div>
                </div>
              </div>
            )}

            {current === "FUNDS_RESERVED" && (
              <div style={{ marginTop: 14, background: "#f0f9ff", borderRadius: 8, padding: "10px 12px", border: "1px solid #bae6fd" }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#0369a1", marginBottom: 6 }}>Balance Change</div>
                <div style={{ fontFamily: "monospace", fontSize: 11, color: "#374151", lineHeight: 1.8 }}>
                  <div>available_balance: ₹50,000 → ₹49,500  ⬇</div>
                  <div>reserved_balance:       ₹0 → ₹500      ⬆</div>
                  <div style={{ color: "#0369a1", marginTop: 4 }}>Funds on hold — NOT yet moved to payee</div>
                </div>
              </div>
            )}
          </div>

          {/* Saga persistence note */}
          <div style={{ background: "#faf5ff", borderRadius: 10, padding: "12px 14px", border: "1px solid #e9d5ff", marginBottom: 14 }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: "#7e22ce", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 6 }}>🔐 Crash Recovery</div>
            <div style={{ fontSize: 11, color: "#374151", lineHeight: 1.6 }}>
              Every state transition persists to <span style={{ fontFamily: "monospace", color: "#7e22ce" }}>aegispay_sagas</span> DB. On restart, Orchestrator queries for <span style={{ fontFamily: "monospace" }}>status='RUNNING'</span> sagas and resumes from the last committed step. <strong>No payment is ever lost or doubled during a crash.</strong>
            </div>
          </div>

          {/* Navigation */}
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <button onClick={reset} style={{ padding: "7px 14px", borderRadius: 8, border: "1.5px solid #e2e8f0", background: "#fff", color: "#64748b", fontSize: 12, cursor: "pointer" }}>↺ Reset</button>
            <button onClick={prev} disabled={stepIdx === 0} style={{ padding: "7px 16px", borderRadius: 8, border: "1.5px solid #e2e8f0", background: "#fff", color: stepIdx === 0 ? "#cbd5e1" : "#374151", fontSize: 12, cursor: stepIdx === 0 ? "not-allowed" : "pointer" }}>← Prev</button>
            <button onClick={next} disabled={stepIdx === path.length - 1} style={{ padding: "7px 16px", borderRadius: 8, border: `1.5px solid ${stepIdx === path.length - 1 ? "#e2e8f0" : curState.color}`, background: stepIdx === path.length - 1 ? "#fff" : curState.color, color: stepIdx === path.length - 1 ? "#cbd5e1" : "#fff", fontSize: 12, cursor: stepIdx === path.length - 1 ? "not-allowed" : "pointer", fontWeight: 600 }}>
              Next →
            </button>
            <div style={{ marginLeft: "auto", fontSize: 11, color: "#94a3b8", fontFamily: "monospace" }}>{stepIdx + 1} / {path.length}</div>
          </div>
        </div>
      </div>

      {/* Why not 2PC callout */}
      <div style={{ marginTop: 20, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <div style={{ background: "#fef2f2", borderRadius: 10, padding: "12px 14px", border: "1px solid #fecaca" }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: "#dc2626", marginBottom: 6 }}>❌ Why not 2-Phase Commit?</div>
          <div style={{ fontSize: 11, color: "#374151", lineHeight: 1.6 }}>2PC holds locks on all participants while the coordinator decides. If the coordinator crashes, every service holds its lock indefinitely — a distributed deadlock. Ledger would be unavailable to all other payments.</div>
        </div>
        <div style={{ background: "#f0fdf4", borderRadius: 10, padding: "12px 14px", border: "1px solid #bbf7d0" }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: "#166534", marginBottom: 6 }}>✅ Saga + Compensating Transactions</div>
          <div style={{ fontSize: 11, color: "#374151", lineHeight: 1.6 }}>No distributed locks. Each step is independent. Failures trigger compensating transactions that undo previous steps. Compensation is idempotent — safe to replay. Every compensation is audit-logged.</div>
        </div>
      </div>

      <div style={{ marginTop: 12, fontSize: 10, color: "#cbd5e1", textAlign: "center" }}>AegisPay · docs/flows/files/saga_state_machine.jsx</div>
    </div>
  );
}
