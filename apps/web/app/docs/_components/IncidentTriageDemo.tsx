'use client'

import { useState, useEffect, useRef } from "react";

const SCENARIO = {
  title: "payment-orchestrator failures spike",
  description: "47 saga failures/min at 14:31. Payment success rate dropped from 99.1% → 84.3%.",
};

const LOOP_STEPS = [
  {
    type: "think",
    label: "Reasoning",
    content: "There are saga failures in payment-orchestrator. I need to understand what errors are occurring. Let me read the recent logs first to identify the error type and timing.",
    tool: null as string | null,
    color: "#6366f1",
    bg: "#eef2ff",
    icon: "🧠",
    result: undefined as string | undefined,
  },
  {
    type: "act",
    label: "Tool call",
    content: 'readLogs(service="payment-orchestrator", since="15m ago")',
    tool: "readLogs",
    color: "#0891b2",
    bg: "#ecfeff",
    icon: "⚡",
    result: `[14:28:01] INFO  payment-orchestrator v2.3.1 started
[14:28:15] INFO  PaymentSagaCoordinator initialized
[14:31:02] ERROR NullPointerException at PaymentSagaCoordinator.java:247
[14:31:02] ERROR   at SagaStepExecutor.executeWithRetry(SagaStepExecutor.java:89)
[14:31:02] ERROR   at PaymentSagaCoordinator.processRiskEvent(PaymentSagaCoordinator.java:247)
[14:31:04] ERROR NullPointerException at PaymentSagaCoordinator.java:247
[14:31:06] ERROR NullPointerException at PaymentSagaCoordinator.java:247
[14:31:11] WARN  47 saga failures in last 60s — threshold exceeded`,
  },
  {
    type: "think",
    label: "Reasoning",
    content: "I can see NullPointerExceptions in PaymentSagaCoordinator at line 247, starting at 14:31:02. The service started at 14:28 — so failures began 3 minutes after startup. Let me check the metrics to see the exact failure rate and timing.",
    tool: null as string | null,
    color: "#6366f1",
    bg: "#eef2ff",
    icon: "🧠",
    result: undefined as string | undefined,
  },
  {
    type: "act",
    label: "Tool call",
    content: 'queryMetrics(metric="payment_saga_failures", window="20m")',
    tool: "queryMetrics",
    color: "#059669",
    bg: "#ecfdf5",
    icon: "⚡",
    result: `{
  "metric": "payment_saga_failures",
  "series": [
    {"ts": "14:28", "value": 0},
    {"ts": "14:29", "value": 0},
    {"ts": "14:30", "value": 0},
    {"ts": "14:31", "value": 47},
    {"ts": "14:32", "value": 51},
    {"ts": "14:33", "value": 49}
  ],
  "baseline_p99": 2
}`,
  },
  {
    type: "think",
    label: "Reasoning",
    content: "Failures jumped from 0 to 47/min at exactly 14:31. The service started at 14:28. This is a classic post-deployment regression — the new version worked for 3 minutes before external traffic triggered the NPE. Let me check recent deployments to confirm.",
    tool: null as string | null,
    color: "#6366f1",
    bg: "#eef2ff",
    icon: "🧠",
    result: undefined as string | undefined,
  },
  {
    type: "act",
    label: "Tool call",
    content: 'getRecentDeployments(service="payment-orchestrator", since="1h ago")',
    tool: "getRecentDeployments",
    color: "#d97706",
    bg: "#fffbeb",
    icon: "⚡",
    result: `[
  {
    "version": "v2.3.1",
    "deployed_at": "14:28:00",
    "deployed_by": "ci-pipeline",
    "git_sha": "a3f9c12",
    "commit_msg": "feat: add risk score caching to saga coordinator"
  },
  {
    "version": "v2.3.0",
    "deployed_at": "11:14:00",
    "deployed_by": "ci-pipeline",
    "note": "previous stable version"
  }
]`,
  },
  {
    type: "conclude",
    label: "Root cause",
    content: `**Root cause identified:** payment-orchestrator v2.3.1 was deployed at 14:28:00. NullPointerExceptions began at 14:31:02 in PaymentSagaCoordinator.java:247 — the new risk score caching feature introduced a null-check regression when processing RiskAssessedEvents with unexpected payload shapes.

**Affected:** payment success rate 99.1% → 84.3% (~847 failed transactions in 18 minutes)

**Immediate action:** Rollback to v2.3.0 via \`helm rollback payment-orchestrator 1\`
**Follow-up:** Add null safety to SagaStepExecutor.java:89 before re-deploying v2.3.1`,
    tool: null as string | null,
    color: "#dc2626",
    bg: "#fef2f2",
    icon: "🎯",
    result: undefined as string | undefined,
  },
];

function MetricSparkline({ active }: { active: boolean }) {
  const data = [0, 0, 0, 47, 51, 49];
  const max = 60;
  const w = 160, h = 50;
  const pts = data.map((v, i) => `${(i / (data.length - 1)) * w},${h - (v / max) * h}`).join(" ");
  return (
    <svg width={w} height={h + 16} style={{ display: "block" }}>
      <polyline points={pts} fill="none" stroke={active ? "#dc2626" : "#e2e8f0"} strokeWidth={1.5} />
      {data.map((v, i) => (
        <circle
          key={i}
          cx={(i / (data.length - 1)) * w}
          cy={h - (v / max) * h}
          r={v > 0 ? 3 : 2}
          fill={v > 0 ? "#dc2626" : "#94a3b8"}
        />
      ))}
      {["14:28", "14:29", "14:30", "14:31", "14:32", "14:33"].map((t, i) => (
        <text key={i} x={(i / 5) * w} y={h + 14} textAnchor="middle" fontSize={7} fill="#94a3b8" fontFamily="monospace">
          {i % 2 === 0 ? t : ""}
        </text>
      ))}
    </svg>
  );
}

export default function IncidentTriageDemo() {
  const [runState, setRunState] = useState("idle");
  const [currentStep, setCurrentStep] = useState(-1);
  const [shownSteps, setShownSteps] = useState<number[]>([]);
  const [typedContent, setTypedContent] = useState("");
  const [typingDone, setTypingDone] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  const reset = () => {
    setRunState("idle");
    setCurrentStep(-1);
    setShownSteps([]);
    setTypedContent("");
    setTypingDone(false);
  };

  const run = () => {
    reset();
    setTimeout(() => setRunState("running"), 100);
  };

  useEffect(() => {
    if (runState !== "running") return;
    if (currentStep === -1) {
      setTimeout(() => setCurrentStep(0), 400);
      return;
    }
    if (currentStep >= LOOP_STEPS.length) {
      setRunState("done");
    }
  }, [runState, currentStep]);

  useEffect(() => {
    if (currentStep < 0 || currentStep >= LOOP_STEPS.length) return;
    const step = LOOP_STEPS[currentStep];
    setTypedContent("");
    setTypingDone(false);
    let i = 0;
    const words = step.content.split(" ");
    const interval = setInterval(() => {
      i++;
      setTypedContent(words.slice(0, i).join(" "));
      if (i >= words.length) {
        clearInterval(interval);
        setTypingDone(true);
      }
    }, 30);
    return () => clearInterval(interval);
  }, [currentStep]);

  useEffect(() => {
    if (!typingDone || currentStep < 0 || currentStep >= LOOP_STEPS.length) return;
    const step = LOOP_STEPS[currentStep];
    const delay = step.type === "act" ? 1200 : step.type === "conclude" ? 0 : 600;
    const t = setTimeout(() => {
      setShownSteps((s) => [...s, currentStep]);
      if (step.type !== "conclude") {
        setCurrentStep((c) => c + 1);
      } else {
        setRunState("done");
      }
    }, delay);
    return () => clearTimeout(t);
  }, [typingDone, currentStep]);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [shownSteps, typedContent]);

  const toolCallCount = shownSteps.filter((i) => LOOP_STEPS[i].type === "act").length;
  const thinkCount = shownSteps.filter((i) => LOOP_STEPS[i].type === "think").length;

  const renderContent = (text: string) => {
    return text.split("**").map((part, i) =>
      i % 2 === 1 ? (
        <strong key={i} style={{ color: "#0f172a" }}>{part}</strong>
      ) : (
        part
      )
    );
  };

  return (
    <div style={{ fontFamily: "'IBM Plex Sans', system-ui, sans-serif", padding: "20px", maxWidth: 780, margin: "0 auto" }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600&family=JetBrains+Mono&display=swap');
      @keyframes blink { 0%,100%{opacity:1}50%{opacity:0.2} }
      `}</style>

      <div style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: "0.1em", color: "#94a3b8", textTransform: "uppercase", marginBottom: 4 }}>
          AegisPay · AI Platform · Incident Triage Agent
        </div>
        <div style={{ fontSize: 20, fontWeight: 600, color: "#0f172a" }}>ReAct loop — Think → Act → Observe → Repeat</div>
        <div style={{ fontSize: 13, color: "#64748b", marginTop: 2 }}>
          The LLM decides which tools to call, in what order, based on what it discovers — no hardcoded sequence
        </div>
      </div>

      <div
        style={{
          background: "#fef2f2",
          border: "1px solid #fca5a5",
          borderRadius: 10,
          padding: "12px 16px",
          marginBottom: 16,
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          flexWrap: "wrap",
          gap: 12,
        }}
      >
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 4 }}>
            <span style={{ fontSize: 14 }}>🚨</span>
            <span style={{ fontSize: 13, fontWeight: 600, color: "#dc2626" }}>INCIDENT TRIGGERED</span>
          </div>
          <div style={{ fontSize: 12, color: "#374151", fontWeight: 500 }}>{SCENARIO.title}</div>
          <div style={{ fontSize: 11, color: "#64748b" }}>{SCENARIO.description}</div>
        </div>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <MetricSparkline active={runState !== "idle"} />
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            <button
              onClick={run}
              disabled={runState === "running"}
              style={{
                background: runState === "running" ? "#e2e8f0" : "#dc2626",
                color: runState === "running" ? "#94a3b8" : "#fff",
                border: "none",
                borderRadius: 8,
                padding: "8px 16px",
                fontSize: 13,
                fontWeight: 600,
                cursor: runState === "running" ? "not-allowed" : "pointer",
              }}
            >
              {runState === "idle" ? "▶ Run triage" : runState === "running" ? "⟳ Triaging..." : "✓ Done"}
            </button>
            {runState !== "idle" && (
              <button
                onClick={reset}
                style={{
                  background: "none",
                  border: "1px solid #e2e8f0",
                  borderRadius: 6,
                  padding: "4px 12px",
                  fontSize: 11,
                  cursor: "pointer",
                  color: "#64748b",
                }}
              >
                ↺ reset
              </button>
            )}
          </div>
        </div>
      </div>

      {runState !== "idle" && (
        <div style={{ display: "flex", gap: 12, marginBottom: 12 }}>
          {[
            { label: "Reasoning steps", value: thinkCount, color: "#6366f1" },
            { label: "Tool calls", value: toolCallCount, color: "#0891b2" },
            { label: "Tools available", value: 3, color: "#059669" },
          ].map((stat) => (
            <div
              key={stat.label}
              style={{
                flex: 1,
                background: "#f8fafc",
                border: "1px solid #e2e8f0",
                borderRadius: 8,
                padding: "8px 12px",
                textAlign: "center",
              }}
            >
              <div style={{ fontSize: 20, fontWeight: 600, color: stat.color }}>{stat.value}</div>
              <div style={{ fontSize: 10, color: "#64748b" }}>{stat.label}</div>
            </div>
          ))}
        </div>
      )}

      <div
        ref={scrollRef}
        style={{
          background: "#0f172a",
          borderRadius: 12,
          padding: 16,
          minHeight: 200,
          maxHeight: 480,
          overflowY: "auto",
          fontFamily: "'JetBrains Mono', monospace",
          fontSize: 11,
        }}
      >
        {runState === "idle" && (
          <div style={{ color: "#475569", textAlign: "center", paddingTop: 40 }}>
            Click &quot;Run triage&quot; to start the agent loop ↑
          </div>
        )}

        {shownSteps.map((i) => {
          const step = LOOP_STEPS[i];
          return (
            <div key={i} style={{ marginBottom: 16 }}>
              <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 6 }}>
                <span style={{ fontSize: 12 }}>{step.icon}</span>
                <span
                  style={{
                    fontSize: 9,
                    fontWeight: 700,
                    color: step.color,
                    border: `1px solid ${step.color}44`,
                    padding: "1px 6px",
                    borderRadius: 4,
                    textTransform: "uppercase",
                    letterSpacing: "0.08em",
                  }}
                >
                  {step.type === "think" ? "reasoning" : step.type === "act" ? "tool call" : "conclusion"}
                </span>
              </div>
              <div
                style={{
                  color: step.type === "think" ? "#94a3b8" : step.type === "act" ? "#7dd3fc" : "#fbbf24",
                  lineHeight: 1.6,
                  marginBottom: step.result ? 8 : 0,
                }}
              >
                {step.type === "act" ? `> ${step.content}` : renderContent(step.content)}
              </div>
              {step.result && (
                <div
                  style={{
                    background: "#1e293b",
                    borderRadius: 6,
                    padding: "8px 12px",
                    color: "#86efac",
                    fontSize: 10,
                    whiteSpace: "pre",
                    overflowX: "auto",
                    borderLeft: `2px solid ${step.color}`,
                  }}
                >
                  {step.result}
                </div>
              )}
            </div>
          );
        })}

        {currentStep >= 0 && currentStep < LOOP_STEPS.length && !shownSteps.includes(currentStep) && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 6 }}>
              <span style={{ fontSize: 12 }}>{LOOP_STEPS[currentStep].icon}</span>
              <span
                style={{
                  fontSize: 9,
                  fontWeight: 700,
                  color: LOOP_STEPS[currentStep].color,
                  border: `1px solid ${LOOP_STEPS[currentStep].color}44`,
                  padding: "1px 6px",
                  borderRadius: 4,
                  textTransform: "uppercase",
                  letterSpacing: "0.08em",
                }}
              >
                {LOOP_STEPS[currentStep].type === "think" ? "reasoning" : LOOP_STEPS[currentStep].type === "act" ? "tool call" : "conclusion"}
              </span>
              <span
                style={{
                  width: 6,
                  height: 6,
                  borderRadius: "50%",
                  background: LOOP_STEPS[currentStep].color,
                  animation: "blink 1s infinite",
                  display: "inline-block",
                }}
              />
            </div>
            <div
              style={{
                color: LOOP_STEPS[currentStep].type === "think" ? "#94a3b8" : LOOP_STEPS[currentStep].type === "act" ? "#7dd3fc" : "#fbbf24",
                lineHeight: 1.6,
              }}
            >
              {LOOP_STEPS[currentStep].type === "act" ? `> ${typedContent}` : renderContent(typedContent)}
              <span style={{ animation: "blink 0.7s infinite", color: "#6366f1" }}>▋</span>
            </div>
          </div>
        )}
      </div>

      <div style={{ marginTop: 16 }}>
        <div style={{ fontSize: 11, fontWeight: 600, color: "#64748b", marginBottom: 8, textTransform: "uppercase", letterSpacing: "0.05em" }}>
          @Tool methods registered with Spring AI ChatClient
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
          {[
            { name: "readLogs()", desc: "Log aggregation · service + window", called: shownSteps.some((i) => LOOP_STEPS[i].tool === "readLogs"), color: "#0891b2" },
            { name: "queryMetrics()", desc: "Prometheus/ClickHouse · metric + window", called: shownSteps.some((i) => LOOP_STEPS[i].tool === "queryMetrics"), color: "#059669" },
            { name: "getRecentDeployments()", desc: "CI/CD history · service + lookback", called: shownSteps.some((i) => LOOP_STEPS[i].tool === "getRecentDeployments"), color: "#d97706" },
          ].map((tool) => (
            <div
              key={tool.name}
              style={{
                background: tool.called ? tool.color + "11" : "#f8fafc",
                border: `1px solid ${tool.called ? tool.color + "44" : "#e2e8f0"}`,
                borderRadius: 8,
                padding: "10px 12px",
                transition: "all 0.3s",
              }}
            >
              <div style={{ fontSize: 11, fontWeight: 600, fontFamily: "monospace", color: tool.called ? tool.color : "#94a3b8" }}>
                {tool.called ? "✓ " : ""}{tool.name}
              </div>
              <div style={{ fontSize: 10, color: "#64748b", marginTop: 2 }}>{tool.desc}</div>
            </div>
          ))}
        </div>
      </div>

      <div style={{ marginTop: 12, padding: "10px 14px", background: "#f8fafc", borderRadius: 8, border: "1px solid #e2e8f0", fontSize: 11, color: "#64748b" }}>
        <strong style={{ color: "#374151" }}>Why ReAct vs hardcoded sequence:</strong> If logs show an infrastructure timeout instead of an NPE, the agent skips
        the deployment check and queries network metrics instead. The tool-use loop adapts to what it discovers — a hardcoded
        sequence can&apos;t do that.
      </div>
    </div>
  );
}
