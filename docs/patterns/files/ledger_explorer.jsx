import { useState } from "react";

// ─── Initial state ────────────────────────────────────────────────────────────

const INITIAL = {
  payer:  { userId: "Priya (payer)",  available: 50000, reserved: 0,    version: 1 },
  payee:  { userId: "Arjun (payee)",  available: 5000,  reserved: 0,    version: 1 },
};

const INITIAL_ENTRIES = [];

const PHASES = {
  idle:     { label: "Idle",     color: "#94a3b8" },
  reserved: { label: "Reserved", color: "#0891b2" },
  committed:{ label: "Committed",color: "#059669" },
  released: { label: "Released", color: "#d97706" },
  failed:   { label: "Failed",   color: "#dc2626" },
};

const AMOUNTS = [500, 1000, 2000, 5000];

export default function LedgerExplorer() {
  const [accounts,  setAccounts]  = useState({ ...INITIAL });
  const [entries,   setEntries]   = useState([]);
  const [phase,     setPhase]     = useState("idle");
  const [amount,    setAmount]    = useState(500);
  const [txId,      setTxId]      = useState(1);
  const [log,       setLog]       = useState([]);
  const [history,   setHistory]   = useState([]);

  const addLog = (msg, color="#374151") =>
    setLog(l => [{ msg, color, ts: new Date().toLocaleTimeString() }, ...l].slice(0, 12));

  // ── Phase 1: RESERVE ──────────────────────────────────────────────────────
  const reserve = () => {
    const payer = accounts.payer;
    if (payer.available < amount) {
      addLog(`❌ INSUFFICIENT_FUNDS — available ₹${payer.available} < ₹${amount}`, "#dc2626");
      setPhase("failed");
      return;
    }
    setAccounts(a => ({
      ...a,
      payer: {
        ...a.payer,
        available: a.payer.available - amount,
        reserved:  a.payer.reserved  + amount,
        version:   a.payer.version   + 1,
      },
    }));
    addLog(`✅ Phase 1 RESERVE — ₹${amount} moved from available → reserved (tx-${txId})`, "#0891b2");
    setPhase("reserved");
  };

  // ── Phase 2a: COMMIT ─────────────────────────────────────────────────────
  const commit = () => {
    const newEntryDebit  = { id: entries.length + 1, txId, account: "payer", type: "DEBIT",  amount, currency: "INR" };
    const newEntryCredit = { id: entries.length + 2, txId, account: "payee", type: "CREDIT", amount, currency: "INR" };
    setEntries(e => [...e, newEntryDebit, newEntryCredit]);
    setAccounts(a => ({
      payer: {
        ...a.payer,
        reserved:  a.payer.reserved  - amount,
        version:   a.payer.version   + 1,
      },
      payee: {
        ...a.payee,
        available: a.payee.available + amount,
        version:   a.payee.version   + 1,
      },
    }));
    setHistory(h => [...h, { txId, amount, type: "COMPLETED", ts: new Date().toLocaleTimeString() }]);
    addLog(`✅ Phase 2a COMMIT — DEBIT payer ₹${amount} + CREDIT payee ₹${amount} (tx-${txId})`, "#059669");
    setPhase("committed");
    setTxId(id => id + 1);
  };

  // ── Phase 2b: RELEASE ────────────────────────────────────────────────────
  const release = () => {
    setAccounts(a => ({
      ...a,
      payer: {
        ...a.payer,
        available: a.payer.available + amount,
        reserved:  a.payer.reserved  - amount,
        version:   a.payer.version   + 1,
      },
    }));
    addLog(`🔁 Phase 2b RELEASE — ₹${amount} returned to available_balance (tx-${txId})`, "#d97706");
    setPhase("released");
    setTxId(id => id + 1);
  };

  // ── Reset next transaction ───────────────────────────────────────────────
  const next = () => { setPhase("idle"); };

  // ── Full reset ────────────────────────────────────────────────────────────
  const fullReset = () => {
    setAccounts({ ...INITIAL });
    setEntries([]);
    setPhase("idle");
    setTxId(1);
    setLog([]);
    setHistory([]);
  };

  // ── Invariant check ──────────────────────────────────────────────────────
  const totalDebit  = entries.filter(e => e.type === "DEBIT").reduce((s, e) => s + e.amount, 0);
  const totalCredit = entries.filter(e => e.type === "CREDIT").reduce((s, e) => s + e.amount, 0);
  const invariantOk = totalDebit === totalCredit;

  // ── Balance bar ──────────────────────────────────────────────────────────
  const BalanceBar = ({ account, name }) => {
    const total = account.available + account.reserved;
    const maxTotal = name === "payer" ? 50000 : 10000;
    const avPct = Math.round((account.available / maxTotal) * 100);
    const rePct = Math.round((account.reserved  / maxTotal) * 100);
    return (
      <div style={{ background: "#fff", borderRadius: 10, padding: 16, border: "1.5px solid #e2e8f0" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: "#0f172a" }}>{account.userId}</div>
          <div style={{ fontSize: 10, fontFamily: "monospace", color: "#94a3b8" }}>v{account.version}</div>
        </div>
        <div style={{ display: "flex", gap: 16, marginBottom: 12 }}>
          <div>
            <div style={{ fontSize: 10, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.06em" }}>Available</div>
            <div style={{ fontSize: 20, fontWeight: 700, color: "#059669", fontFamily: "monospace" }}>₹{account.available.toLocaleString()}</div>
          </div>
          {account.reserved > 0 && (
            <div>
              <div style={{ fontSize: 10, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.06em" }}>Reserved</div>
              <div style={{ fontSize: 20, fontWeight: 700, color: "#0891b2", fontFamily: "monospace" }}>₹{account.reserved.toLocaleString()}</div>
            </div>
          )}
        </div>
        <div style={{ height: 10, borderRadius: 5, background: "#f1f5f9", overflow: "hidden", display: "flex" }}>
          <div style={{ width: `${avPct}%`, background: "#059669", transition: "width 0.4s ease" }} />
          <div style={{ width: `${rePct}%`, background: "#0891b2", transition: "width 0.4s ease" }} />
        </div>
        <div style={{ display: "flex", gap: 12, marginTop: 5 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
            <div style={{ width: 8, height: 8, borderRadius: 2, background: "#059669" }} />
            <span style={{ fontSize: 9, color: "#64748b" }}>Available</span>
          </div>
          {account.reserved > 0 && (
            <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
              <div style={{ width: 8, height: 8, borderRadius: 2, background: "#0891b2" }} />
              <span style={{ fontSize: 9, color: "#64748b" }}>Reserved</span>
            </div>
          )}
        </div>
      </div>
    );
  };

  return (
    <div style={{ fontFamily: "'IBM Plex Sans', system-ui, sans-serif", maxWidth: 900, margin: "0 auto", padding: 24 }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap');`}</style>

      {/* Header */}
      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 10, fontWeight: 700, color: "#94a3b8", letterSpacing: "0.12em", textTransform: "uppercase", marginBottom: 4 }}>AegisPay · Patterns</div>
        <div style={{ fontSize: 24, fontWeight: 700, color: "#0f172a", letterSpacing: "-0.02em" }}>Ledger Explorer — Double-Entry Bookkeeping</div>
        <div style={{ fontSize: 13, color: "#64748b", marginTop: 2 }}>Simulate balance reservation → commit → release. Watch the append-only entries grow.</div>
      </div>

      {/* Amount picker */}
      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 18, flexWrap: "wrap" }}>
        <span style={{ fontSize: 12, color: "#64748b" }}>Payment amount:</span>
        {AMOUNTS.map(a => (
          <button key={a} onClick={() => { if (phase === "idle") setAmount(a); }} style={{ padding: "6px 14px", borderRadius: 8, border: `2px solid ${amount === a ? "#6366f1" : "#e2e8f0"}`, background: amount === a ? "#6366f1" : "#fff", color: amount === a ? "#fff" : "#475569", fontSize: 12, fontWeight: 600, cursor: phase === "idle" ? "pointer" : "not-allowed", opacity: phase !== "idle" ? 0.6 : 1 }}>₹{a.toLocaleString()}</button>
        ))}
        <button onClick={fullReset} style={{ marginLeft: "auto", padding: "6px 14px", borderRadius: 8, border: "1.5px solid #e2e8f0", background: "#fff", color: "#64748b", fontSize: 12, cursor: "pointer" }}>↺ Full Reset</button>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, marginBottom: 16 }}>
        <BalanceBar account={accounts.payer} name="payer" />
        <BalanceBar account={accounts.payee} name="payee" />
      </div>

      {/* Phase controls */}
      <div style={{ background: "#f8fafc", borderRadius: 12, padding: 16, border: "1px solid #e2e8f0", marginBottom: 16 }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 12 }}>
          Transaction tx-{txId} · Phase: <span style={{ color: PHASES[phase]?.color }}>{PHASES[phase]?.label}</span>
        </div>

        <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
          {/* Phase 1: Reserve */}
          <div style={{ flex: 1, minWidth: 200, background: "#fff", borderRadius: 10, padding: "12px 14px", border: phase === "idle" ? "2px solid #0891b2" : "2px solid #e2e8f0" }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: "#0891b2", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 4 }}>Phase 1</div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0f172a", marginBottom: 4 }}>Reserve Funds</div>
            <div style={{ fontSize: 11, color: "#64748b", marginBottom: 10, lineHeight: 1.5 }}>available -= ₹{amount}<br/>reserved += ₹{amount}</div>
            <button
              onClick={reserve}
              disabled={phase !== "idle"}
              style={{ width: "100%", padding: "8px", borderRadius: 8, border: "none", background: phase === "idle" ? "#0891b2" : "#e2e8f0", color: phase === "idle" ? "#fff" : "#94a3b8", fontSize: 12, fontWeight: 700, cursor: phase === "idle" ? "pointer" : "not-allowed" }}
            >
              Reserve ₹{amount.toLocaleString()}
            </button>
          </div>

          {/* Phase 2a: Commit */}
          <div style={{ flex: 1, minWidth: 200, background: "#fff", borderRadius: 10, padding: "12px 14px", border: phase === "reserved" ? "2px solid #059669" : "2px solid #e2e8f0" }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: "#059669", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 4 }}>Phase 2a — Success</div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0f172a", marginBottom: 4 }}>Commit to Ledger</div>
            <div style={{ fontSize: 11, color: "#64748b", marginBottom: 10, lineHeight: 1.5 }}>DEBIT payer ₹{amount}<br/>CREDIT payee ₹{amount}</div>
            <button
              onClick={commit}
              disabled={phase !== "reserved"}
              style={{ width: "100%", padding: "8px", borderRadius: 8, border: "none", background: phase === "reserved" ? "#059669" : "#e2e8f0", color: phase === "reserved" ? "#fff" : "#94a3b8", fontSize: 12, fontWeight: 700, cursor: phase === "reserved" ? "pointer" : "not-allowed" }}
            >
              Commit (Stripe ✓)
            </button>
          </div>

          {/* Phase 2b: Release */}
          <div style={{ flex: 1, minWidth: 200, background: "#fff", borderRadius: 10, padding: "12px 14px", border: phase === "reserved" ? "2px solid #d97706" : "2px solid #e2e8f0" }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: "#d97706", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 4 }}>Phase 2b — Failure</div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0f172a", marginBottom: 4 }}>Release Reservation</div>
            <div style={{ fontSize: 11, color: "#64748b", marginBottom: 10, lineHeight: 1.5 }}>reserved -= ₹{amount}<br/>available += ₹{amount}</div>
            <button
              onClick={release}
              disabled={phase !== "reserved"}
              style={{ width: "100%", padding: "8px", borderRadius: 8, border: "none", background: phase === "reserved" ? "#d97706" : "#e2e8f0", color: phase === "reserved" ? "#fff" : "#94a3b8", fontSize: 12, fontWeight: 700, cursor: phase === "reserved" ? "pointer" : "not-allowed" }}
            >
              Release (Stripe ✗)
            </button>
          </div>
        </div>

        {(phase === "committed" || phase === "released" || phase === "failed") && (
          <button onClick={next} style={{ marginTop: 12, padding: "8px 20px", borderRadius: 8, border: "1.5px solid #6366f1", background: "#6366f1", color: "#fff", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>
            + New Transaction →
          </button>
        )}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
        {/* Ledger entries */}
        <div style={{ background: "#f8fafc", borderRadius: 12, padding: 14, border: "1px solid #e2e8f0" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.06em" }}>
              Ledger Entries (append-only)
            </div>
            <div style={{ fontSize: 10, fontFamily: "monospace", padding: "3px 8px", borderRadius: 12, background: invariantOk ? "#f0fdf4" : "#fef2f2", color: invariantOk ? "#059669" : "#dc2626", border: `1px solid ${invariantOk ? "#bbf7d0" : "#fecaca"}` }}>
              {invariantOk ? "∑ DEBIT = ∑ CREDIT ✓" : "INVARIANT VIOLATED ❌"}
            </div>
          </div>
          {entries.length === 0 ? (
            <div style={{ fontSize: 12, color: "#94a3b8", textAlign: "center", padding: "20px 0" }}>No entries yet — commit a transaction</div>
          ) : (
            <div style={{ maxHeight: 240, overflow: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 11, fontFamily: "monospace" }}>
                <thead>
                  <tr style={{ borderBottom: "1px solid #e2e8f0" }}>
                    {["#", "tx-id", "Account", "Type", "Amount"].map(h => (
                      <th key={h} style={{ textAlign: "left", padding: "4px 6px", color: "#94a3b8", fontWeight: 600, fontSize: 10, textTransform: "uppercase" }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {[...entries].reverse().map(e => (
                    <tr key={e.id} style={{ borderBottom: "1px solid #f1f5f9" }}>
                      <td style={{ padding: "5px 6px", color: "#94a3b8" }}>{e.id}</td>
                      <td style={{ padding: "5px 6px", color: "#475569" }}>tx-{e.txId}</td>
                      <td style={{ padding: "5px 6px", color: "#0f172a" }}>{e.account}</td>
                      <td style={{ padding: "5px 6px" }}>
                        <span style={{ background: e.type === "DEBIT" ? "#fef2f2" : "#f0fdf4", color: e.type === "DEBIT" ? "#dc2626" : "#059669", borderRadius: 4, padding: "1px 6px", fontWeight: 700, fontSize: 10 }}>{e.type}</span>
                      </td>
                      <td style={{ padding: "5px 6px", fontWeight: 700, color: e.type === "DEBIT" ? "#dc2626" : "#059669" }}>₹{e.amount.toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {entries.length > 0 && (
                <div style={{ marginTop: 8, padding: "6px 6px", background: "#fff", borderRadius: 6, display: "flex", gap: 16 }}>
                  <span style={{ fontSize: 10, color: "#dc2626" }}>∑ DEBIT: ₹{totalDebit.toLocaleString()}</span>
                  <span style={{ fontSize: 10, color: "#059669" }}>∑ CREDIT: ₹{totalCredit.toLocaleString()}</span>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Log + history */}
        <div>
          <div style={{ background: "#f8fafc", borderRadius: 12, padding: 14, border: "1px solid #e2e8f0", marginBottom: 12 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 8 }}>Event Log</div>
            {log.length === 0 ? (
              <div style={{ fontSize: 12, color: "#94a3b8" }}>No events yet</div>
            ) : log.map((l, i) => (
              <div key={i} style={{ fontSize: 11, color: l.color, lineHeight: 1.6, marginBottom: 2 }}>
                <span style={{ color: "#94a3b8", fontFamily: "monospace" }}>{l.ts} </span>{l.msg}
              </div>
            ))}
          </div>

          {history.length > 0 && (
            <div style={{ background: "#f8fafc", borderRadius: 12, padding: 14, border: "1px solid #e2e8f0" }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 8 }}>Completed Transactions</div>
              {history.map((h, i) => (
                <div key={i} style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                  <span style={{ width: 8, height: 8, borderRadius: "50%", background: "#059669", flexShrink: 0 }} />
                  <span style={{ fontFamily: "monospace", fontSize: 11, color: "#0f172a" }}>tx-{h.txId}</span>
                  <span style={{ fontSize: 11, color: "#059669", fontWeight: 700 }}>₹{h.amount.toLocaleString()}</span>
                  <span style={{ fontSize: 10, color: "#94a3b8" }}>{h.ts}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <div style={{ marginTop: 16, background: "#fffbeb", borderRadius: 10, padding: "12px 14px", border: "1px solid #fde68a" }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: "#b45309", marginBottom: 6 }}>⚡ Why 2-Phase Balance?</div>
        <div style={{ fontSize: 11, color: "#374151", lineHeight: 1.6 }}>
          If we deducted immediately and Stripe failed, we'd have to "un-deduct" — a reversal that can race with concurrent transactions.
          <strong> Reservation</strong> puts funds on hold without moving them. <strong>Commit</strong> finalizes with append-only ledger entries (never updated, never deleted). <strong>Release</strong> restores everything cleanly. Result: no double-spend, no partial states.
        </div>
      </div>

      <div style={{ marginTop: 12, fontSize: 10, color: "#cbd5e1", textAlign: "center" }}>AegisPay · docs/patterns/files/ledger_explorer.jsx</div>
    </div>
  );
}
