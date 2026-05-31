import { useState, useEffect, useRef, useCallback } from "react";

const W = 560, H = 420;

const CATEGORIES = {
  fraud_pattern: { color: "#dc2626", bg: "#fef2f2", label: "fraud_pattern" },
  error_resolution: { color: "#d97706", bg: "#fffbeb", label: "error_resolution" },
  kyc_guidance: { color: "#7c3aed", bg: "#f5f3ff", label: "kyc_guidance" },
};

const DOCS = [
  { id: 0, x: 120, y: 160, cat: "fraud_pattern", label: "HIGH_VELOCITY+\nNEW_DEVICE", short: "Velocity+Device" },
  { id: 1, x: 160, y: 220, cat: "fraud_pattern", label: "NEW_PAYEE+\nHIGH_VALUE", short: "New payee" },
  { id: 2, x: 90, y: 230, cat: "fraud_pattern", label: "VELOCITY BURST\npattern", short: "Burst" },
  { id: 3, x: 145, y: 300, cat: "fraud_pattern", label: "DEVICE CHANGE\nmid-session", short: "Device change" },
  { id: 4, x: 210, y: 175, cat: "fraud_pattern", label: "ACCOUNT TAKEOVER\nsignature", short: "ATO" },
  { id: 5, x: 80, y: 320, cat: "fraud_pattern", label: "GEOGRAPHIC\nanomaly", short: "Geo anomaly" },
  { id: 6, x: 380, y: 120, cat: "error_resolution", label: "amount_too_small\nINR ₹50 min", short: "amount_too_small" },
  { id: 7, x: 440, y: 170, cat: "error_resolution", label: "INSUFFICIENT\nFUNDS", short: "Insuf. funds" },
  { id: 8, x: 360, y: 200, cat: "error_resolution", label: "STRIPE_ERROR\ncodes", short: "Stripe err" },
  { id: 9, x: 420, y: 250, cat: "error_resolution", label: "RISK_BLOCKED\nexplanation", short: "Risk blocked" },
  { id: 10, x: 350, y: 290, cat: "error_resolution", label: "SAGA_TIMEOUT\nfailure", short: "Saga timeout" },
  { id: 11, x: 260, y: 330, cat: "kyc_guidance", label: "Aadhaar OCR\nfields", short: "Aadhaar" },
  { id: 12, x: 310, y: 360, cat: "kyc_guidance", label: "PAN card\nextraction", short: "PAN" },
  { id: 13, x: 220, y: 370, cat: "kyc_guidance", label: "Tampering\ndetection cues", short: "Tampering" },
  { id: 14, x: 350, y: 360, cat: "kyc_guidance", label: "Passport MRZ\nparsing", short: "Passport" },
];

// HNSW edges (simplified - connects nearby same-cat docs)
const HNSW_EDGES = [
  [0, 1], [0, 2], [1, 4], [2, 3], [1, 2], [3, 5], [0, 4],
  [6, 7], [6, 8], [7, 9], [8, 10], [9, 10], [6, 9],
  [11, 12], [12, 14], [11, 13], [13, 14],
  [4, 9], // cross-category bridge edge
];

function cosineSimFake(doc, query) {
  const dx = doc.x - query.x;
  const dy = doc.y - query.y;
  const dist = Math.sqrt(dx * dx + dy * dy);
  const maxDist = Math.sqrt(W * W + H * H);
  return parseFloat((1 - dist / maxDist).toFixed(3));
}

export default function HnswViz() {
  const svgRef = useRef(null);
  const [query, setQuery] = useState({ x: 130, y: 190 });
  const [dragging, setDragging] = useState(false);
  const [hoveredDoc, setHoveredDoc] = useState(null);
  const [showHnsw, setShowHnsw] = useState(true);
  const [topK, setTopK] = useState(3);
  const [mode, setMode] = useState("drag"); // drag | categories

  const sorted = [...DOCS]
    .map((d) => ({ ...d, sim: cosineSimFake(d, query) }))
    .sort((a, b) => b.sim - a.sim);

  const topDocs = new Set(sorted.slice(0, topK).map((d) => d.id));

  const getSVGCoords = (e) => {
    const svg = svgRef.current;
    if (!svg) return { x: 0, y: 0 };
    const rect = svg.getBoundingClientRect();
    const scaleX = W / rect.width;
    const scaleY = H / rect.height;
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    const clientY = e.touches ? e.touches[0].clientY : e.clientY;
    return {
      x: Math.max(20, Math.min(W - 20, (clientX - rect.left) * scaleX)),
      y: Math.max(20, Math.min(H - 20, (clientY - rect.top) * scaleY)),
    };
  };

  const onMouseDown = (e) => {
    if (mode !== "drag") return;
    setDragging(true);
    setQuery(getSVGCoords(e));
  };
  const onMouseMove = (e) => {
    if (!dragging) return;
    setQuery(getSVGCoords(e));
  };
  const onMouseUp = () => setDragging(false);

  const queryCategory = sorted[0]?.cat;

  return (
    <div style={{ fontFamily: "'IBM Plex Sans', system-ui, sans-serif", padding: "20px", maxWidth: 760, margin: "0 auto" }}>
      <style>
        {`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600&family=JetBrains+Mono:wght@400&display=swap');`}
      </style>

      <div style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: "0.1em", color: "#94a3b8", textTransform: "uppercase", marginBottom: 4 }}>
          AegisPay · pgvector
        </div>
        <div style={{ fontSize: 20, fontWeight: 600, color: "#0f172a" }}>HNSW Vector Space — live similarity search</div>
        <div style={{ fontSize: 13, color: "#64748b", marginTop: 2 }}>
          Drag the query point ✦ — watch the top-{topK} nearest neighbors update in real time
        </div>
      </div>

      {/* Controls */}
      <div style={{ display: "flex", gap: 12, marginBottom: 16, flexWrap: "wrap", alignItems: "center" }}>
        <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "#475569" }}>
          <input
            type="checkbox"
            checked={showHnsw}
            onChange={(e) => setShowHnsw(e.target.checked)}
          />
          Show HNSW graph edges
        </label>
        <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "#475569" }}>
          top-K:
          <input
            type="range"
            min={1}
            max={7}
            value={topK}
            onChange={(e) => setTopK(Number(e.target.value))}
            style={{ width: 80 }}
          />
          <strong style={{ minWidth: 16 }}>{topK}</strong>
        </label>
        <div style={{ display: "flex", gap: 8 }}>
          {[
            { id: "fraud", label: "⬤ fraud query", x: 130, y: 190 },
            { id: "error", label: "⬤ error query", x: 395, y: 185 },
            { id: "kyc", label: "⬤ KYC query", x: 285, y: 355 },
          ].map((preset) => (
            <button
              key={preset.id}
              onClick={() => setQuery({ x: preset.x, y: preset.y })}
              style={{
                background: "none",
                border: "1px solid #e2e8f0",
                borderRadius: 6,
                padding: "3px 10px",
                fontSize: 11,
                cursor: "pointer",
                color: "#475569",
              }}
            >
              {preset.label}
            </button>
          ))}
        </div>
      </div>

      {/* SVG canvas */}
      <div
        style={{
          border: "1px solid #e2e8f0",
          borderRadius: 12,
          overflow: "hidden",
          cursor: dragging ? "grabbing" : "crosshair",
          userSelect: "none",
        }}
      >
        <svg
          ref={svgRef}
          viewBox={`0 0 ${W} ${H}`}
          width="100%"
          onMouseDown={onMouseDown}
          onMouseMove={onMouseMove}
          onMouseUp={onMouseUp}
          onMouseLeave={onMouseUp}
          style={{ display: "block", background: "#f8fafc" }}
        >
          {/* Grid */}
          {Array.from({ length: 6 }, (_, i) => (
            <line key={`gx${i}`} x1={i * 100 + 20} y1={0} x2={i * 100 + 20} y2={H} stroke="#e2e8f0" strokeWidth={0.5} />
          ))}
          {Array.from({ length: 5 }, (_, i) => (
            <line key={`gy${i}`} x1={0} y1={i * 90 + 20} x2={W} y2={i * 90 + 20} stroke="#e2e8f0" strokeWidth={0.5} />
          ))}

          {/* HNSW graph edges */}
          {showHnsw &&
            HNSW_EDGES.map(([a, b], i) => {
              const da = DOCS[a], db = DOCS[b];
              const sameCluster = da.cat === db.cat;
              return (
                <line
                  key={`edge${i}`}
                  x1={da.x} y1={da.y} x2={db.x} y2={db.y}
                  stroke={sameCluster ? CATEGORIES[da.cat].color : "#94a3b8"}
                  strokeWidth={sameCluster ? 0.8 : 0.5}
                  strokeOpacity={sameCluster ? 0.25 : 0.15}
                  strokeDasharray={sameCluster ? "none" : "3 3"}
                />
              );
            })}

          {/* Distance lines to top-K */}
          {sorted.slice(0, topK).map((doc) => (
            <line
              key={`dist${doc.id}`}
              x1={query.x} y1={query.y}
              x2={doc.x} y2={doc.y}
              stroke={CATEGORIES[doc.cat].color}
              strokeWidth={1.5}
              strokeOpacity={0.5}
              strokeDasharray="4 3"
            />
          ))}

          {/* Similarity radius ring */}
          {topK > 0 && (() => {
            const kthDoc = sorted[topK - 1];
            const dx = kthDoc.x - query.x;
            const dy = kthDoc.y - query.y;
            const r = Math.sqrt(dx * dx + dy * dy);
            return (
              <circle
                cx={query.x} cy={query.y} r={r}
                fill="none"
                stroke="#6366f1"
                strokeWidth={0.8}
                strokeOpacity={0.2}
                strokeDasharray="5 3"
              />
            );
          })()}

          {/* Document nodes */}
          {DOCS.map((doc) => {
            const cat = CATEGORIES[doc.cat];
            const isTop = topDocs.has(doc.id);
            const rank = sorted.findIndex((d) => d.id === doc.id);
            const sim = sorted.find((d) => d.id === doc.id)?.sim ?? 0;
            return (
              <g
                key={doc.id}
                onMouseEnter={() => setHoveredDoc(doc.id)}
                onMouseLeave={() => setHoveredDoc(null)}
                style={{ cursor: "pointer" }}
              >
                <circle
                  cx={doc.x} cy={doc.y}
                  r={isTop ? 10 : 7}
                  fill={isTop ? cat.color : cat.bg}
                  stroke={cat.color}
                  strokeWidth={isTop ? 2 : 1}
                  opacity={isTop ? 1 : 0.6}
                />
                {isTop && (
                  <text
                    x={doc.x} y={doc.y}
                    textAnchor="middle"
                    dominantBaseline="central"
                    fontSize={8}
                    fontWeight={700}
                    fill="#fff"
                    fontFamily="monospace"
                  >
                    {rank + 1}
                  </text>
                )}
                {(isTop || hoveredDoc === doc.id) && (
                  <text
                    x={doc.x + 14} y={doc.y - 4}
                    fontSize={9}
                    fill={cat.color}
                    fontWeight={600}
                    fontFamily="'IBM Plex Sans', sans-serif"
                  >
                    {doc.short}
                  </text>
                )}
                {(isTop || hoveredDoc === doc.id) && (
                  <text
                    x={doc.x + 14} y={doc.y + 8}
                    fontSize={8}
                    fill="#64748b"
                    fontFamily="monospace"
                  >
                    sim={sim.toFixed(3)}
                  </text>
                )}
              </g>
            );
          })}

          {/* Query point */}
          <g>
            <circle
              cx={query.x} cy={query.y} r={14}
              fill="#6366f1"
              fillOpacity={0.15}
              stroke="#6366f1"
              strokeWidth={1.5}
              strokeDasharray="4 2"
            />
            <text x={query.x} y={query.y} textAnchor="middle" dominantBaseline="central" fontSize={14} fill="#6366f1">
              ✦
            </text>
            <text
              x={query.x} y={query.y - 20}
              textAnchor="middle"
              fontSize={9}
              fontWeight={600}
              fill="#6366f1"
              fontFamily="monospace"
            >
              query vector
            </text>
          </g>

          {/* Legend */}
          {Object.entries(CATEGORIES).map(([k, v], i) => (
            <g key={k}>
              <circle cx={16} cy={H - 60 + i * 18} r={5} fill={v.color} />
              <text x={26} y={H - 60 + i * 18} dominantBaseline="central" fontSize={9} fill="#475569" fontFamily="'IBM Plex Sans', sans-serif">
                {v.label}
              </text>
            </g>
          ))}
          {showHnsw && (
            <g>
              <line x1={10} y1={H - 14} x2={20} y2={H - 14} stroke="#94a3b8" strokeWidth={1} strokeDasharray="2 2" />
              <text x={26} y={H - 14} dominantBaseline="central" fontSize={9} fill="#94a3b8" fontFamily="'IBM Plex Sans', sans-serif">
                HNSW edges (m=16)
              </text>
            </g>
          )}
        </svg>
      </div>

      {/* Results panel */}
      <div style={{ marginTop: 16, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <div style={{ background: "#f8fafc", borderRadius: 8, padding: 12, border: "1px solid #e2e8f0" }}>
          <div style={{ fontSize: 11, fontWeight: 600, color: "#64748b", marginBottom: 8, textTransform: "uppercase", letterSpacing: "0.05em" }}>
            Top-{topK} results (ORDER BY distance ASC)
          </div>
          {sorted.slice(0, topK).map((doc, i) => (
            <div key={doc.id} style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
              <span
                style={{
                  width: 18,
                  height: 18,
                  borderRadius: "50%",
                  background: CATEGORIES[doc.cat].color,
                  color: "#fff",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontSize: 9,
                  fontWeight: 700,
                  flexShrink: 0,
                }}
              >
                {i + 1}
              </span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 11, fontWeight: 600, color: "#0f172a", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                  {doc.short}
                </div>
                <div style={{ height: 3, background: "#e2e8f0", borderRadius: 2, marginTop: 2 }}>
                  <div
                    style={{
                      width: `${doc.sim * 100}%`,
                      height: "100%",
                      borderRadius: 2,
                      background: CATEGORIES[doc.cat].color,
                    }}
                  />
                </div>
              </div>
              <span style={{ fontSize: 10, fontFamily: "monospace", color: "#475569", flexShrink: 0 }}>
                {doc.sim.toFixed(3)}
              </span>
            </div>
          ))}
        </div>

        <div style={{ background: "#f8fafc", borderRadius: 8, padding: 12, border: "1px solid #e2e8f0" }}>
          <div style={{ fontSize: 11, fontWeight: 600, color: "#64748b", marginBottom: 8, textTransform: "uppercase", letterSpacing: "0.05em" }}>
            Why HNSW beats full scan
          </div>
          <div style={{ fontFamily: "monospace", fontSize: 11, color: "#475569", lineHeight: 1.8 }}>
            <div><span style={{ color: "#dc2626" }}>full scan</span>  O(n) — checks all 15 docs</div>
            <div><span style={{ color: "#059669" }}>HNSW     </span>  O(log n) — ~99% recall</div>
            <div style={{ marginTop: 6, color: "#94a3b8" }}>m=16  → 16 edges per node</div>
            <div style={{ color: "#94a3b8" }}>ef_construction=64 → build quality</div>
            <div style={{ marginTop: 6, color: "#0f172a", fontSize: 10 }}>
              For 10k docs: full scan ~60ms → HNSW ~2ms
            </div>
          </div>
          <div style={{ marginTop: 10, padding: "8px 10px", background: "#ecfdf5", borderRadius: 6, border: "1px solid #6ee7b7" }}>
            <div style={{ fontSize: 10, color: "#059669", fontWeight: 600 }}>HNSW search path</div>
            <div style={{ fontSize: 10, color: "#475569", marginTop: 2, fontFamily: "monospace" }}>
              entry node → follow {topK} best edges → converge near query
            </div>
          </div>
        </div>
      </div>

      <div style={{ marginTop: 12, fontSize: 11, color: "#94a3b8", textAlign: "center" }}>
        similarity threshold = 0.7 · documents below threshold are excluded · {"<=>"}  operator = cosine distance
      </div>
    </div>
  );
}
