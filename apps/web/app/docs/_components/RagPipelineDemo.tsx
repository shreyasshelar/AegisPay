'use client'

import { useState, useEffect } from "react";

const STEPS = [
  { id: 0, label: "Transaction context", sublabel: "Input arrives from Risk Engine", color: "#6366f1", bg: "#eef2ff" },
  { id: 1, label: "Embed query", sublabel: "text-embedding-3-small → float[1536]", color: "#0891b2", bg: "#ecfeff" },
  { id: 2, label: "Vector search", sublabel: "pgvector HNSW cosine similarity", color: "#059669", bg: "#ecfdf5" },
  { id: 3, label: "Assemble prompt", sublabel: "Context + retrieved docs + question", color: "#d97706", bg: "#fffbeb" },
  { id: 4, label: "Claude generates", sublabel: "claude-sonnet-4-6 → explanation", color: "#dc2626", bg: "#fef2f2" },
  { id: 5, label: "Audit + respond", sublabel: "ai_audit_log → caller", color: "#7c3aed", bg: "#f5f3ff" },
];

const TRANSACTION = {
  transactionId: "txn_abc123", amount: "15000", currency: "INR", riskScore: 78,
  decision: "BLOCK", ruleFlags: ["HIGH_VELOCITY", "NEW_DEVICE"], dailyTxCount: 7,
  deviceFingerprint: "fp_xyz_991", isNewDevice: true, payeeId: "pay_456", isFirstPayee: true,
};

const EMBEDDING_SAMPLE = Array.from({ length: 64 }, (_, i) =>
  parseFloat((Math.sin(i * 0.4 + 1.2) * 0.6 + Math.cos(i * 0.9) * 0.3).toFixed(3))
);

const RETRIEVED_DOCS = [
  { score: 0.94, category: "fraud_pattern", content: "HIGH_VELOCITY + NEW_DEVICE: 5+ tx in 10 min from unknown device → 78% fraud rate in 23 confirmed cases. Risk contribution: +35 pts." },
  { score: 0.87, category: "fraud_pattern", content: "NEW_PAYEE + HIGH_VALUE: First transaction to a new recipient over ₹10,000 carries elevated risk. Fraudsters exfiltrate via new payees." },
  { score: 0.81, category: "fraud_pattern", content: "VELOCITY BURST pattern: >5 tx in any 10-min window is a hard trigger. Combined with device anomaly, escalate to REVIEW or BLOCK." },
  { score: 0.72, category: "fraud_pattern", content: "DEVICE FINGERPRINT CHANGE mid-session: if user switches device mid-payment flow, treat as suspicious. Require step-up auth." },
  { score: 0.68, category: "fraud_pattern", content: "ACCOUNT TAKEOVER signature: high velocity + new device + new payee is the classic ATO pattern. Block and notify via SMS immediately." },
];

const CLAUDE_RESPONSE = `This transaction matches the **HIGH_VELOCITY + NEW_DEVICE** fraud pattern documented in 23 confirmed historical cases.

The payer made 7 transactions today with recent ones in rapid succession from a device first seen today — exactly the pattern that carries a **78% historical fraud rate** in AegisPay's dataset.

The additional factor of this being the **first transaction to this payee** (pay_456) elevates risk further: fraudsters who compromise accounts typically transact with new recipients to exfiltrate funds before the account owner notices.

**Recommended action:** Request OTP step-up verification via registered phone number before processing. Do not automatically approve. Confidence: HIGH.`;

function QueryBuilder() {
  const query = `Transaction flagged with risk score ${TRANSACTION.riskScore}, decision ${TRANSACTION.decision}.
Rule flags triggered: ${TRANSACTION.ruleFlags.join(", ")}.
Amount: ${TRANSACTION.amount} ${TRANSACTION.currency}.
Payer made ${TRANSACTION.dailyTxCount} transactions today.
Device: ${TRANSACTION.deviceFingerprint} (first seen: today).
Payee: ${TRANSACTION.payeeId} (first transaction: true).
Why is this suspicious? What historical fraud patterns match?`;
  return (
    <div style={{ fontFamily: "'JetBrains Mono', 'Fira Code', monospace", fontSize: 12, lineHeight: 1.7 }}>
      <div style={{ marginBottom: 12, display: "flex", gap: 8, flexWrap: "wrap" }}>
        {TRANSACTION.ruleFlags.map((f) => (
          <span key={f} style={{ background: "#fef2f2", color: "#dc2626", border: "1px solid #fca5a5", borderRadius: 4, padding: "2px 8px", fontSize: 11, fontWeight: 600 }}>{f}</span>
        ))}
        <span style={{ background: "#fffbeb", color: "#d97706", border: "1px solid #fcd34d", borderRadius: 4, padding: "2px 8px", fontSize: 11, fontWeight: 600 }}>score: {TRANSACTION.riskScore}</span>
      </div>
      <pre style={{ background: "#0f172a", color: "#94a3b8", borderRadius: 8, padding: 16, margin: 0, fontSize: 11, overflowX: "auto", whiteSpace: "pre-wrap" }}>
        <span style={{ color: "#64748b" }}>{"// query text sent to embedding model\n"}</span>
        {query.split("\n").map((line, i) => (
          <span key={i} style={{ display: "block", color: i === 0 ? "#f1f5f9" : "#94a3b8" }}>{line}</span>
        ))}
      </pre>
    </div>
  );
}

function EmbeddingViz() {
  return (
    <div>
      <div style={{ display: "flex", gap: 2, flexWrap: "wrap", marginBottom: 12 }}>
        {EMBEDDING_SAMPLE.map((v, i) => {
          const norm = (v + 1) / 2;
          const r = norm > 0.5 ? Math.round(230 * (norm - 0.5) * 2) : 0;
          const b = norm < 0.5 ? Math.round(230 * (1 - norm * 2)) : 0;
          const g = Math.round(80 * (1 - Math.abs(norm - 0.5) * 2));
          return <div key={i} title={`dim[${i}] = ${v}`} style={{ width: 14, height: 28, borderRadius: 2, background: `rgb(${r},${g},${b})`, opacity: 0.85, cursor: "default" }} />;
        })}
        <div style={{ fontSize: 11, color: "#64748b", marginTop: 4, width: "100%" }}>… 1536 dimensions total — showing first 64 · hover for value · red = positive, blue = negative</div>
      </div>
      <div style={{ background: "#0f172a", borderRadius: 8, padding: 12, fontFamily: "monospace", fontSize: 11, color: "#94a3b8" }}>
        <span style={{ color: "#64748b" }}>{"// float[1536] vector — 6144 bytes stored in pgvector\n"}</span>
        <span style={{ color: "#f1f5f9" }}>embedding</span>{" = ["}
        <span style={{ color: "#34d399" }}>{EMBEDDING_SAMPLE.slice(0, 8).map((v) => v.toFixed(3)).join(", ")}</span>
        <span style={{ color: "#94a3b8" }}>{", … +1528 more]"}</span>
      </div>
    </div>
  );
}

function VectorSearchViz() {
  return (
    <div>
      <div style={{ background: "#0f172a", borderRadius: 8, padding: 12, fontFamily: "monospace", fontSize: 11, color: "#94a3b8", marginBottom: 12 }}>
        <span style={{ color: "#64748b" }}>{"-- pgvector cosine similarity query\n"}</span>
        <span style={{ color: "#7dd3fc" }}>SELECT </span><span style={{ color: "#f1f5f9" }}>content, metadata, </span><span style={{ color: "#fbbf24" }}>{"embedding <=> $queryVec"}</span><span style={{ color: "#f1f5f9" }}>{" AS distance\n"}</span>
        <span style={{ color: "#7dd3fc" }}>FROM </span><span style={{ color: "#f1f5f9" }}>{"knowledge_base\n"}</span>
        <span style={{ color: "#7dd3fc" }}>WHERE </span><span style={{ color: "#f1f5f9" }}>category = </span><span style={{ color: "#86efac" }}>{`'fraud_pattern'\n`}</span>
        <span style={{ color: "#7dd3fc" }}>ORDER BY </span><span style={{ color: "#fbbf24" }}>{"distance ASC\n"}</span>
        <span style={{ color: "#7dd3fc" }}>LIMIT </span><span style={{ color: "#fbbf24" }}>5</span><span style={{ color: "#94a3b8" }}>{"; \n"}</span>
        <span style={{ color: "#64748b" }}>-- HNSW index: O(log n), ~99% recall vs exact search</span>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        {RETRIEVED_DOCS.map((doc, i) => (
          <div key={i} style={{ border: "1px solid", borderColor: doc.score > 0.85 ? "#6ee7b7" : doc.score > 0.75 ? "#fcd34d" : "#e2e8f0", borderRadius: 8, padding: "10px 14px", background: doc.score > 0.85 ? "#f0fdf4" : doc.score > 0.75 ? "#fffbeb" : "#f8fafc" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
              <span style={{ fontSize: 11, fontWeight: 600, color: "#64748b", fontFamily: "monospace" }}>doc[{i}] · {doc.category}</span>
              <span style={{ fontSize: 11, fontWeight: 700, color: doc.score > 0.85 ? "#059669" : doc.score > 0.75 ? "#d97706" : "#64748b", fontFamily: "monospace" }}>similarity: {doc.score.toFixed(2)}</span>
            </div>
            <div style={{ fontSize: 12, color: "#374151", lineHeight: 1.5 }}>{doc.content}</div>
            <div style={{ marginTop: 6, height: 3, borderRadius: 2, background: "#e2e8f0" }}>
              <div style={{ width: `${doc.score * 100}%`, height: "100%", borderRadius: 2, background: doc.score > 0.85 ? "#10b981" : doc.score > 0.75 ? "#f59e0b" : "#94a3b8" }} />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function PromptViz() {
  return (
    <div style={{ background: "#0f172a", borderRadius: 8, padding: 16, fontFamily: "monospace", fontSize: 11, color: "#94a3b8", lineHeight: 1.7 }}>
      <div style={{ marginBottom: 10 }}><span style={{ background: "#1e293b", color: "#7dd3fc", padding: "2px 8px", borderRadius: 4, fontSize: 10, fontWeight: 600 }}>SYSTEM</span></div>
      <div style={{ color: "#94a3b8", marginBottom: 14 }}>You are AegisPay&apos;s financial assistant. Use ONLY the provided context. Do not hallucinate. Cite specific patterns. Always give a recommended action.</div>
      <div style={{ marginBottom: 10 }}><span style={{ background: "#1e293b", color: "#86efac", padding: "2px 8px", borderRadius: 4, fontSize: 10, fontWeight: 600 }}>USER — RETRIEVED CONTEXT</span></div>
      {RETRIEVED_DOCS.slice(0, 3).map((doc, i) => (
        <div key={i} style={{ color: "#64748b", marginBottom: 8, paddingLeft: 12, borderLeft: "2px solid #1e293b" }}>[{i}] {doc.content}</div>
      ))}
      <div style={{ color: "#94a3b8", marginTop: 8 }}>
        <span style={{ color: "#fbbf24" }}>Question: </span>
        Transaction flagged riskScore=78, flags=HIGH_VELOCITY,NEW_DEVICE, amount=15000 INR... Why is this suspicious?
      </div>
    </div>
  );
}

function ClaudeResponseViz() {
  const [shown, setShown] = useState(0);
  const words = CLAUDE_RESPONSE.split(" ");

  useEffect(() => {
    if (shown < words.length) {
      const t = setTimeout(() => setShown((s) => s + 1), 25);
      return () => clearTimeout(t);
    }
  }, [shown, words.length]);

  const text = words.slice(0, shown).join(" ");

  const formatLine = (line: string, idx: number) => {
    const isBold = line.includes("**");
    if (isBold) {
      const parts = line.split(/\*\*(.*?)\*\*/g);
      return (
        <span key={idx}>
          {parts.map((p, j) => j % 2 === 1 ? <strong key={j} style={{ color: "#1e293b" }}>{p}</strong> : p)}
          <br />
        </span>
      );
    }
    return <span key={idx}>{line}<br /></span>;
  };

  return (
    <div>
      <div style={{ background: "#f0fdf4", border: "1px solid #86efac", borderRadius: 8, padding: 16, fontSize: 13, lineHeight: 1.7, color: "#374151", marginBottom: 12, minHeight: 120 }}>
        <div style={{ display: "flex", gap: 8, marginBottom: 10, alignItems: "center" }}>
          <div style={{ width: 8, height: 8, borderRadius: "50%", background: "#10b981" }} />
          <span style={{ fontSize: 11, fontWeight: 600, color: "#059669" }}>claude-sonnet-4-6 generating…</span>
        </div>
        {text.split("\n").filter((l) => l).map((line, i) => formatLine(line, i))}
        {shown < words.length && <span style={{ animation: "pulse 1s infinite", color: "#10b981" }}>▋</span>}
      </div>
      <button onClick={() => setShown(0)} style={{ background: "none", border: "1px solid #e2e8f0", borderRadius: 6, padding: "4px 12px", fontSize: 12, cursor: "pointer", color: "#64748b" }}>↺ replay</button>
    </div>
  );
}

function AuditViz() {
  return (
    <div>
      <div style={{ background: "#0f172a", borderRadius: 8, padding: 16, fontFamily: "monospace", fontSize: 11, color: "#94a3b8", marginBottom: 12 }}>
        <span style={{ color: "#64748b" }}>{`// ai_audit_log — written in finally{} block\n`}</span>
        {"{\n"}{"  "}<span style={{ color: "#fbbf24" }}>"id"</span>{`: `}<span style={{ color: "#86efac" }}>"aud_9f3c..."</span>{",\n"}
        {"  "}<span style={{ color: "#fbbf24" }}>"request_type"</span>{`: `}<span style={{ color: "#86efac" }}>"fraud_copilot"</span>{",\n"}
        {"  "}<span style={{ color: "#fbbf24" }}>"model_id"</span>{`: `}<span style={{ color: "#86efac" }}>"claude-sonnet-4-6"</span>{",\n"}
        {"  "}<span style={{ color: "#fbbf24" }}>"masked_input"</span>{`: `}<span style={{ color: "#86efac" }}>"txn_abc123 · score=78 · [MASKED]"</span>{",\n"}
        {"  "}<span style={{ color: "#fbbf24" }}>"latency_ms"</span>{`: `}<span style={{ color: "#7dd3fc" }}>412</span>{",\n"}
        {"  "}<span style={{ color: "#fbbf24" }}>"token_count"</span>{`: `}<span style={{ color: "#7dd3fc" }}>847</span>{",\n"}
        {"  "}<span style={{ color: "#fbbf24" }}>"created_at"</span>{`: `}<span style={{ color: "#86efac" }}>"2025-05-17T14:32:11Z"</span>{"\n"}
        {"}"}
      </div>
      <div style={{ background: "#f5f3ff", border: "1px solid #c4b5fd", borderRadius: 8, padding: 14, fontSize: 12, color: "#374151" }}>
        <div style={{ fontWeight: 600, color: "#7c3aed", marginBottom: 6 }}>↩ Response to Risk Engine (8085)</div>
        <div style={{ fontFamily: "monospace", fontSize: 11, color: "#64748b" }}>
          {`{ "explanation": "Matches HIGH_VELOCITY+NEW_DEVICE pattern...",`}<br />
          {`  "confidence": "HIGH", "matchedPatterns": 5,`}<br />
          {`  "suggestedAction": "OTP step-up before processing" }`}
        </div>
      </div>
    </div>
  );
}

const STEP_CONTENT = [
  <QueryBuilder key="0" />,
  <EmbeddingViz key="1" />,
  <VectorSearchViz key="2" />,
  <PromptViz key="3" />,
  <ClaudeResponseViz key="4" />,
  <AuditViz key="5" />,
];

export default function RagPipelineDemo() {
  const [active, setActive] = useState(0);
  const [visited, setVisited] = useState(new Set([0]));

  const go = (i: number) => { setActive(i); setVisited((v) => new Set([...v, i])); };
  const step = STEPS[active];

  return (
    <div style={{ fontFamily: "'IBM Plex Sans', system-ui, sans-serif", padding: "24px 20px", maxWidth: 780, margin: "0 auto" }}>
      <style>{`@keyframes pulse { 0%,100%{opacity:1}50%{opacity:0.3} } @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600&family=JetBrains+Mono&display=swap');`}</style>

      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: "0.1em", color: "#94a3b8", textTransform: "uppercase", marginBottom: 4 }}>AegisPay · AI Platform</div>
        <div style={{ fontSize: 20, fontWeight: 600, color: "#0f172a" }}>RAG Pipeline — interactive walkthrough</div>
        <div style={{ fontSize: 13, color: "#64748b", marginTop: 2 }}>Fraud Copilot · follows this exact path for every flagged transaction</div>
      </div>

      <div style={{ display: "flex", gap: 0, marginBottom: 24, overflowX: "auto" }}>
        {STEPS.map((s, i) => (
          <button key={s.id} onClick={() => go(i)} style={{ flex: 1, minWidth: 100, border: "none", borderBottom: `3px solid ${active === i ? s.color : visited.has(i) ? "#e2e8f0" : "#f1f5f9"}`, background: active === i ? s.bg : "transparent", padding: "10px 8px", cursor: "pointer", textAlign: "left", transition: "all 0.15s" }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: active === i ? s.color : "#94a3b8", marginBottom: 2 }}>{String(i + 1).padStart(2, "0")}</div>
            <div style={{ fontSize: 11, fontWeight: 600, color: active === i ? "#0f172a" : "#475569" }}>{s.label}</div>
          </button>
        ))}
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 16, padding: "12px 16px", background: step.bg, borderRadius: 10, border: `1px solid ${step.color}22` }}>
        <div style={{ width: 36, height: 36, borderRadius: "50%", background: step.color, color: "#fff", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 14, fontWeight: 700, flexShrink: 0 }}>{active + 1}</div>
        <div>
          <div style={{ fontSize: 15, fontWeight: 600, color: "#0f172a" }}>{step.label}</div>
          <div style={{ fontSize: 12, color: "#64748b" }}>{step.sublabel}</div>
        </div>
        <div style={{ marginLeft: "auto", display: "flex", gap: 8 }}>
          <button onClick={() => go(Math.max(0, active - 1))} disabled={active === 0} style={{ background: "none", border: "1px solid #e2e8f0", borderRadius: 6, padding: "4px 12px", cursor: active === 0 ? "not-allowed" : "pointer", opacity: active === 0 ? 0.4 : 1, fontSize: 13 }}>← prev</button>
          <button onClick={() => go(Math.min(STEPS.length - 1, active + 1))} disabled={active === STEPS.length - 1} style={{ background: step.color, border: "none", borderRadius: 6, padding: "4px 12px", cursor: active === STEPS.length - 1 ? "not-allowed" : "pointer", opacity: active === STEPS.length - 1 ? 0.4 : 1, color: "#fff", fontSize: 13, fontWeight: 500 }}>next →</button>
        </div>
      </div>

      <div style={{ minHeight: 300 }}>{STEP_CONTENT[active]}</div>

      <div style={{ marginTop: 20, padding: "12px 16px", background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, fontSize: 12, color: "#475569" }}>
        {active === 0 && "The richer the query text (transaction amount, device info, velocity count), the better the embedding captures the semantic meaning — and the more relevant the retrieved docs."}
        {active === 1 && "text-embedding-3-small produces float[1536]. Each dimension captures a different aspect of semantic meaning. Fraud-related velocity text will cluster close to other fraud-velocity documents in this 1536-dimensional space."}
        {active === 2 && "Cosine similarity ignores magnitude — only direction matters. The <=> operator in pgvector computes 1 - cosine_similarity. HNSW navigates a graph to find approximate nearest neighbors in O(log n) rather than O(n) full scan."}
        {active === 3 && "The system prompt pins Claude to AegisPay's domain. The user message injects retrieved docs as grounding context. Claude is instructed: only use this context, don't hallucinate, cite specific patterns."}
        {active === 4 && "temperature=0.1 ensures near-deterministic output for financial explanations. max_tokens=2048 caps cost and latency. The finally{} block guarantees the audit log is written even if Claude throws an exception."}
        {active === 5 && "Every LLM call is logged: masked input (PII stripped), raw output, model ID, latency, token count. This is a regulatory requirement for AI-assisted financial decisions. The caller (Risk Engine) gets a structured JSON response."}
      </div>

      <div style={{ marginTop: 16, display: "flex", alignItems: "center", gap: 4, fontSize: 11, color: "#94a3b8" }}>
        {STEPS.map((s, i) => (
          <span key={i} style={{ display: "flex", alignItems: "center", gap: 4 }}>
            <span style={{ width: 20, height: 20, borderRadius: "50%", background: visited.has(i) ? s.color : "#e2e8f0", color: visited.has(i) ? "#fff" : "#94a3b8", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 10, fontWeight: 700, cursor: "pointer" }} onClick={() => go(i)}>{i + 1}</span>
            {i < STEPS.length - 1 && <span style={{ color: "#e2e8f0" }}>──</span>}
          </span>
        ))}
        <span style={{ marginLeft: 8 }}>{visited.size}/{STEPS.length} steps explored</span>
      </div>
    </div>
  );
}
