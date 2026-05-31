import { useState, useEffect } from "react";

// ─── KYC OCR GATE SIMULATOR ───────────────────────────────────────────────
const SCENARIOS = [
  {
    id: "good",
    label: "✓ Clean Aadhaar",
    image: "🪪",
    quality: { score: 0.91, issues: [], acceptable: true },
    tampering: { tampered: false, confidence: 0.97, indicators: [], verdict: "authentic" },
    extraction: {
      documentType: "AADHAAR",
      documentNumber: "XXXX XXXX 4821",
      fullName: "Shreyas Shelar",
      dateOfBirth: "1998-03-14",
      gender: "M",
      address: "Flat 4B, Kalyani Nagar, Pune, Maharashtra 411014",
    },
  },
  {
    id: "blurry",
    label: "✗ Blurry photo",
    image: "🌫️",
    quality: { score: 0.38, issues: ["severe blur", "low contrast", "text illegible"], acceptable: false },
    tampering: null,
    extraction: null,
  },
  {
    id: "tampered",
    label: "✗ Tampered doc",
    image: "⚠️",
    quality: { score: 0.88, issues: ["slight glare"], acceptable: true },
    tampering: {
      tampered: true,
      confidence: 0.93,
      indicators: ["font inconsistency in name field", "pixel artifacts around DOB", "misaligned hologram"],
      verdict: "TAMPERED",
    },
    extraction: null,
  },
  {
    id: "passport",
    label: "✓ Passport",
    image: "📘",
    quality: { score: 0.95, issues: [], acceptable: true },
    tampering: { tampered: false, confidence: 0.98, indicators: [], verdict: "authentic" },
    extraction: {
      documentType: "PASSPORT",
      documentNumber: "P1234567",
      fullName: "Shreyas Shelar",
      dateOfBirth: "1998-03-14",
      gender: "M",
      address: null,
      expiryDate: "2034-03-13",
      issueDate: "2024-03-14",
    },
  },
];

const GATES = [
  { id: "quality", label: "Gate 1 — Quality", sub: "Is the image usable?", icon: "🔍", color: "#0891b2" },
  { id: "tampering", label: "Gate 2 — Authenticity", sub: "Is the document real?", icon: "🛡️", color: "#7c3aed" },
  { id: "extraction", label: "Gate 3 — Extraction", sub: "Extract structured fields", icon: "📋", color: "#059669" },
];

function ScoreBar({ score, color }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
      <div style={{ flex: 1, height: 6, background: "#e2e8f0", borderRadius: 3 }}>
        <div
          style={{
            width: `${score * 100}%`,
            height: "100%",
            borderRadius: 3,
            background: score > 0.7 ? "#10b981" : score > 0.5 ? "#f59e0b" : "#ef4444",
            transition: "width 0.5s ease",
          }}
        />
      </div>
      <span style={{ fontSize: 11, fontFamily: "monospace", minWidth: 32, color }}>
        {score.toFixed(2)}
      </span>
    </div>
  );
}

function QualityResult({ result, passed }) {
  return (
    <div>
      <ScoreBar score={result.score} color={passed ? "#059669" : "#dc2626"} />
      <div style={{ marginTop: 8, fontSize: 12, color: "#374151" }}>
        {result.issues.length === 0 ? (
          <span style={{ color: "#059669" }}>✓ No quality issues detected</span>
        ) : (
          result.issues.map((issue) => (
            <div key={issue} style={{ color: "#dc2626" }}>✗ {issue}</div>
          ))
        )}
      </div>
      <div
        style={{
          marginTop: 8,
          padding: "6px 10px",
          background: passed ? "#f0fdf4" : "#fef2f2",
          borderRadius: 6,
          fontSize: 11,
          fontFamily: "monospace",
          color: passed ? "#059669" : "#dc2626",
          fontWeight: 600,
        }}
      >
        {passed ? "→ PASS: proceed to authenticity check" : "→ FAIL: reject — request retake"}
      </div>
    </div>
  );
}

function TamperingResult({ result, passed }) {
  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 8 }}>
        <span style={{ fontSize: 20 }}>{result.tampered ? "⚠️" : "✅"}</span>
        <div>
          <div style={{ fontSize: 13, fontWeight: 600, color: result.tampered ? "#dc2626" : "#059669" }}>
            {result.verdict}
          </div>
          <div style={{ fontSize: 11, color: "#64748b" }}>confidence: {result.confidence.toFixed(2)}</div>
        </div>
      </div>
      {result.indicators.length > 0 && (
        <div>
          {result.indicators.map((ind) => (
            <div key={ind} style={{ fontSize: 11, color: "#dc2626", marginBottom: 2 }}>⚠ {ind}</div>
          ))}
        </div>
      )}
      <div
        style={{
          marginTop: 8,
          padding: "6px 10px",
          background: passed ? "#f0fdf4" : "#fef2f2",
          borderRadius: 6,
          fontSize: 11,
          fontFamily: "monospace",
          color: passed ? "#059669" : "#dc2626",
          fontWeight: 600,
        }}
      >
        {passed ? "→ PASS: proceed to data extraction" : "→ FAIL: reject — flag for review"}
      </div>
    </div>
  );
}

function ExtractionResult({ result }) {
  const fields = Object.entries(result).filter(([, v]) => v != null);
  return (
    <div>
      <div
        style={{
          background: "#0f172a",
          borderRadius: 8,
          padding: 12,
          fontFamily: "monospace",
          fontSize: 10,
          color: "#94a3b8",
          lineHeight: 1.8,
        }}
      >
        <span style={{ color: "#64748b" }}>// structured output — parsed from Claude JSON response{"\n"}</span>
        {"{\n"}
        {fields.map(([k, v]) => (
          <span key={k} style={{ display: "block" }}>
            {"  "}
            <span style={{ color: "#fbbf24" }}>"{k}"</span>
            {": "}
            <span style={{ color: "#86efac" }}>"{v}"</span>
            {","}
          </span>
        ))}
        {"}"}
      </div>
      <div
        style={{
          marginTop: 8,
          padding: "6px 10px",
          background: "#f0fdf4",
          borderRadius: 6,
          fontSize: 11,
          fontFamily: "monospace",
          color: "#059669",
          fontWeight: 600,
        }}
      >
        → KYC_PASS: update user.kyc_status = VERIFIED
      </div>
    </div>
  );
}

function KycOcr() {
  const [scenario, setScenario] = useState(SCENARIOS[0]);
  const [activeGate, setActiveGate] = useState(0);
  const [running, setRunning] = useState(false);
  const [gateState, setGateState] = useState({ quality: null, tampering: null, extraction: null }); // null|'running'|'pass'|'fail'

  const runScenario = () => {
    setRunning(true);
    setActiveGate(0);
    setGateState({ quality: null, tampering: null, extraction: null });

    // Gate 1 — quality
    setTimeout(() => {
      setGateState((g) => ({ ...g, quality: "running" }));
    }, 300);
    setTimeout(() => {
      const pass = scenario.quality.acceptable;
      setGateState((g) => ({ ...g, quality: pass ? "pass" : "fail" }));
      if (!pass) { setRunning(false); return; }

      // Gate 2 — tampering
      setTimeout(() => {
        setActiveGate(1);
        setGateState((g) => ({ ...g, tampering: "running" }));
        setTimeout(() => {
          const pass2 = !scenario.tampering?.tampered;
          setGateState((g) => ({ ...g, tampering: pass2 ? "pass" : "fail" }));
          if (!pass2) { setRunning(false); return; }

          // Gate 3 — extraction
          setTimeout(() => {
            setActiveGate(2);
            setGateState((g) => ({ ...g, extraction: "running" }));
            setTimeout(() => {
              setGateState((g) => ({ ...g, extraction: scenario.extraction ? "pass" : "fail" }));
              setRunning(false);
            }, 1200);
          }, 400);
        }, 1000);
      }, 400);
    }, 1000);
  };

  const gateStatus = (key) => gateState[key];
  const gateColor = (status) =>
    status === "pass" ? "#059669" : status === "fail" ? "#dc2626" : status === "running" ? "#6366f1" : "#94a3b8";

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 11, fontWeight: 600, color: "#94a3b8", letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 4 }}>
          KYC OCR · Multimodal · Sequential Gates
        </div>
        <div style={{ fontSize: 18, fontWeight: 600, color: "#0f172a" }}>Three-gate document verification</div>
        <div style={{ fontSize: 12, color: "#64748b", marginTop: 2 }}>Each gate is a separate Claude vision API call · fail-fast prevents wasted API spend</div>
      </div>

      <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap" }}>
        {SCENARIOS.map((s) => (
          <button
            key={s.id}
            onClick={() => { setScenario(s); setGateState({ quality: null, tampering: null, extraction: null }); setActiveGate(0); }}
            style={{
              background: scenario.id === s.id ? "#0f172a" : "none",
              color: scenario.id === s.id ? "#fff" : "#475569",
              border: "1px solid",
              borderColor: scenario.id === s.id ? "#0f172a" : "#e2e8f0",
              borderRadius: 8,
              padding: "6px 12px",
              fontSize: 12,
              cursor: "pointer",
              fontWeight: scenario.id === s.id ? 600 : 400,
            }}
          >
            {s.label}
          </button>
        ))}
        <button
          onClick={runScenario}
          disabled={running}
          style={{
            marginLeft: "auto",
            background: running ? "#e2e8f0" : "#6366f1",
            color: running ? "#94a3b8" : "#fff",
            border: "none",
            borderRadius: 8,
            padding: "6px 16px",
            fontSize: 12,
            fontWeight: 600,
            cursor: running ? "not-allowed" : "pointer",
          }}
        >
          {running ? "⟳ Processing..." : "▶ Run OCR"}
        </button>
      </div>

      {/* Gate pipeline */}
      <div style={{ display: "flex", gap: 0, marginBottom: 16, alignItems: "stretch" }}>
        {/* Document input */}
        <div
          style={{
            background: "#f8fafc",
            border: "1px solid #e2e8f0",
            borderRadius: "8px 0 0 8px",
            padding: "12px 16px",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            gap: 4,
            minWidth: 80,
          }}
        >
          <span style={{ fontSize: 32 }}>{scenario.image}</span>
          <span style={{ fontSize: 9, color: "#94a3b8", textAlign: "center" }}>document<br />image</span>
        </div>

        {GATES.map((gate, i) => {
          const status = gateStatus(gate.id);
          return (
            <div key={gate.id} style={{ display: "flex", alignItems: "center" }}>
              <div style={{ width: 24, height: 2, background: "#e2e8f0" }} />
              <div
                style={{
                  border: "1px solid",
                  borderColor: status ? gateColor(status) : "#e2e8f0",
                  borderRadius: i === GATES.length - 1 ? "0 8px 8px 0" : 0,
                  padding: "12px 14px",
                  background: status === "pass" ? gate.color + "11" : status === "fail" ? "#fef2f2" : status === "running" ? "#eef2ff" : "#f8fafc",
                  minWidth: 130,
                  transition: "all 0.3s",
                }}
              >
                <div style={{ fontSize: 14, marginBottom: 4 }}>{gate.icon}</div>
                <div style={{ fontSize: 11, fontWeight: 600, color: gateColor(status) }}>{gate.label}</div>
                <div style={{ fontSize: 10, color: "#64748b" }}>{gate.sub}</div>
                {status && (
                  <div
                    style={{
                      marginTop: 6,
                      fontSize: 10,
                      fontWeight: 700,
                      color: gateColor(status),
                      fontFamily: "monospace",
                    }}
                  >
                    {status === "running" ? "⟳ Claude API call..." : status === "pass" ? "✓ PASS" : "✗ FAIL — stop"}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Detailed results */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10 }}>
        {GATES.map((gate) => {
          const status = gateStatus(gate.id);
          const data =
            gate.id === "quality" ? scenario.quality :
            gate.id === "tampering" ? scenario.tampering :
            scenario.extraction;

          return (
            <div
              key={gate.id}
              style={{
                border: "1px solid",
                borderColor: status ? gateColor(status) + "66" : "#e2e8f0",
                borderRadius: 8,
                padding: 12,
                opacity: status ? 1 : 0.4,
                transition: "opacity 0.3s",
              }}
            >
              <div style={{ fontSize: 11, fontWeight: 600, color: gateColor(status) || "#94a3b8", marginBottom: 8 }}>
                {gate.icon} {gate.label}
              </div>
              {status === "running" && (
                <div style={{ fontSize: 11, color: "#6366f1", fontFamily: "monospace" }}>
                  Calling claude-sonnet-4-6 vision…
                </div>
              )}
              {status === "pass" && data && gate.id === "quality" && (
                <QualityResult result={data} passed={true} />
              )}
              {status === "fail" && data && gate.id === "quality" && (
                <QualityResult result={data} passed={false} />
              )}
              {status === "pass" && data && gate.id === "tampering" && (
                <TamperingResult result={data} passed={true} />
              )}
              {status === "fail" && data && gate.id === "tampering" && (
                <TamperingResult result={data} passed={false} />
              )}
              {status === "pass" && data && gate.id === "extraction" && (
                <ExtractionResult result={data} />
              )}
              {!status && (
                <div style={{ fontSize: 10, color: "#94a3b8", fontFamily: "monospace" }}>
                  waiting for gate {GATES.indexOf(gate)} to pass…
                </div>
              )}
            </div>
          );
        })}
      </div>

      <div style={{ marginTop: 12, fontSize: 11, color: "#64748b", padding: "8px 12px", background: "#f8fafc", borderRadius: 6, border: "1px solid #e2e8f0" }}>
        <strong style={{ color: "#374151" }}>Why sequential, not parallel:</strong> Gate 2 (tampering) on a blurry image wastes an API call that will be rejected anyway.
        Gate 3 (extraction) on a tampered doc returns fraudulent data. Fail-fast saves ~67% API cost on rejected documents.
      </div>
    </div>
  );
}

// ─── FULL PLATFORM EXPLORER ───────────────────────────────────────────────
const SERVICES = [
  {
    id: "fraud",
    name: "Fraud Copilot",
    type: "RAG",
    caller: "Risk Engine :8085",
    input: "transactionId + ruleFlags + riskScore",
    output: "explanation + matchedPatterns + suggestedAction",
    color: "#dc2626",
    bg: "#fef2f2",
    category: "fraud_pattern",
    model: "claude-sonnet-4-6",
    embedding: "text-embedding-3-small",
    topK: 5,
    threshold: 0.7,
    temp: 0.1,
  },
  {
    id: "error",
    name: "Error Resolution",
    type: "RAG",
    caller: "Frontend + Transaction Svc",
    input: "transactionId + failureCode",
    output: "explanation + suggestedAction + confidence",
    color: "#d97706",
    bg: "#fffbeb",
    category: "error_resolution",
    model: "claude-sonnet-4-6",
    embedding: "text-embedding-3-small",
    topK: 3,
    threshold: 0.65,
    temp: 0.1,
  },
  {
    id: "triage",
    name: "Incident Triage",
    type: "Agentic",
    caller: "SRE / Ops team",
    input: "incident description",
    output: "root cause + affected services + action",
    color: "#059669",
    bg: "#ecfdf5",
    category: "N/A (tool-use loop)",
    model: "claude-sonnet-4-6",
    embedding: "N/A",
    topK: "N/A",
    threshold: "N/A",
    temp: 0.2,
  },
  {
    id: "kyc",
    name: "KYC OCR",
    type: "Multimodal",
    caller: "User Service :8081",
    input: "base64 document image",
    output: "extracted fields + quality + tampering verdict",
    color: "#7c3aed",
    bg: "#f5f3ff",
    category: "kyc_guidance",
    model: "claude-sonnet-4-6",
    embedding: "N/A",
    topK: "N/A",
    threshold: "N/A",
    temp: 0.0,
  },
];

function ServiceCard({ svc, selected, onSelect }) {
  return (
    <div
      onClick={() => onSelect(svc.id)}
      style={{
        border: `1px solid ${selected ? svc.color : "#e2e8f0"}`,
        borderRadius: 10,
        padding: "12px 14px",
        cursor: "pointer",
        background: selected ? svc.bg : "#f8fafc",
        transition: "all 0.15s",
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 6 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: selected ? "#0f172a" : "#475569" }}>{svc.name}</div>
        <span
          style={{
            fontSize: 9,
            fontWeight: 700,
            color: svc.color,
            background: svc.bg,
            border: `1px solid ${svc.color}44`,
            padding: "1px 6px",
            borderRadius: 4,
          }}
        >
          {svc.type}
        </span>
      </div>
      <div style={{ fontSize: 11, color: "#64748b" }}>caller: {svc.caller}</div>
    </div>
  );
}

function ServiceDetail({ svc }) {
  const rows = [
    ["Model", svc.model],
    ["Embedding", svc.embedding],
    ["KB category", svc.category],
    ["top-K", svc.topK],
    ["sim threshold", svc.threshold],
    ["temperature", svc.temp],
  ];

  return (
    <div
      style={{
        border: `1px solid ${svc.color}44`,
        borderRadius: 10,
        padding: 16,
        background: svc.bg,
      }}
    >
      <div style={{ fontSize: 14, fontWeight: 600, color: "#0f172a", marginBottom: 12 }}>{svc.name} — configuration</div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 12 }}>
        <div style={{ background: "white", borderRadius: 6, padding: "8px 12px", border: "1px solid #e2e8f0" }}>
          <div style={{ fontSize: 10, color: "#94a3b8", marginBottom: 2 }}>INPUT</div>
          <div style={{ fontSize: 11, color: "#374151" }}>{svc.input}</div>
        </div>
        <div style={{ background: "white", borderRadius: 6, padding: "8px 12px", border: "1px solid #e2e8f0" }}>
          <div style={{ fontSize: 10, color: "#94a3b8", marginBottom: 2 }}>OUTPUT</div>
          <div style={{ fontSize: 11, color: "#374151" }}>{svc.output}</div>
        </div>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 6 }}>
        {rows.map(([k, v]) => (
          <div key={k} style={{ background: "white", borderRadius: 6, padding: "6px 10px", border: "1px solid #e2e8f0" }}>
            <div style={{ fontSize: 9, color: "#94a3b8", marginBottom: 1 }}>{k}</div>
            <div style={{ fontSize: 11, fontFamily: "monospace", color: svc.color, fontWeight: 600 }}>{v}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── MAIN COMPONENT ───────────────────────────────────────────────────────
export default function App() {
  const [tab, setTab] = useState("kyc"); // kyc | platform
  const [selectedSvc, setSelectedSvc] = useState("fraud");

  return (
    <div style={{ fontFamily: "'IBM Plex Sans', system-ui, sans-serif", padding: "20px", maxWidth: 780, margin: "0 auto" }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600&family=JetBrains+Mono&display=swap');`}</style>

      {/* Tab bar */}
      <div style={{ display: "flex", gap: 4, marginBottom: 20, background: "#f1f5f9", borderRadius: 8, padding: 4 }}>
        {[
          { id: "kyc", label: "🪪 KYC OCR — three-gate flow" },
          { id: "platform", label: "🗺 AI Platform — service map" },
        ].map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            style={{
              flex: 1,
              background: tab === t.id ? "#fff" : "none",
              border: "none",
              borderRadius: 6,
              padding: "8px 12px",
              fontSize: 13,
              fontWeight: tab === t.id ? 600 : 400,
              color: tab === t.id ? "#0f172a" : "#64748b",
              cursor: "pointer",
              boxShadow: tab === t.id ? "0 1px 3px rgba(0,0,0,0.08)" : "none",
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "kyc" && <KycOcr />}

      {tab === "platform" && (
        <div>
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: "#94a3b8", letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 4 }}>
              AI Platform :8091 · Four services
            </div>
            <div style={{ fontSize: 18, fontWeight: 600, color: "#0f172a" }}>All AI services — click to inspect</div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 16 }}>
            {SERVICES.map((svc) => (
              <ServiceCard key={svc.id} svc={svc} selected={selectedSvc === svc.id} onSelect={setSelectedSvc} />
            ))}
          </div>

          {selectedSvc && <ServiceDetail svc={SERVICES.find((s) => s.id === selectedSvc)} />}

          {/* Spring AI abstraction */}
          <div
            style={{
              marginTop: 16,
              border: "1px solid #bfdbfe",
              borderRadius: 10,
              padding: 14,
              background: "#eff6ff",
            }}
          >
            <div style={{ fontSize: 12, fontWeight: 600, color: "#1d4ed8", marginBottom: 8 }}>
              Spring AI abstraction — what lets you swap models without changing service code
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
              {[
                { iface: "ChatModel", impl: "AnthropicChatModel", desc: "→ claude-sonnet-4-6" },
                { iface: "EmbeddingModel", impl: "OpenAiEmbeddingModel", desc: "→ text-emb-3-small" },
                { iface: "VectorStore", impl: "PgVectorStore", desc: "→ pgvector HNSW" },
              ].map((a) => (
                <div key={a.iface} style={{ background: "white", borderRadius: 6, padding: "8px 10px", border: "1px solid #bfdbfe" }}>
                  <div style={{ fontSize: 10, fontFamily: "monospace", color: "#1d4ed8", fontWeight: 600 }}>{a.iface}</div>
                  <div style={{ fontSize: 10, fontFamily: "monospace", color: "#64748b" }}>{a.impl}</div>
                  <div style={{ fontSize: 10, color: "#94a3b8" }}>{a.desc}</div>
                </div>
              ))}
            </div>
            <div style={{ marginTop: 8, fontSize: 11, color: "#475569" }}>
              On-prem: change <code style={{ background: "#dbeafe", padding: "0 4px", borderRadius: 3 }}>spring.ai.anthropic.base-url</code> to OpenRouter — zero service code changes
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
