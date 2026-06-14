'use client'

import { useState } from "react";

const SERVICES = {
  web:       { id: "web",       label: "Next.js Web",          tier: "client",    port: 3000,  tech: "Next.js 14 / TypeScript / NextAuth.js",        desc: "Customer dashboard, send-money flow, back-office triage. STOMP WebSocket for real-time status updates.", color: "#0ea5e9", bg: "#f0f9ff" },
  ios:       { id: "ios",       label: "iOS App",              tier: "client",    port: null,  tech: "SwiftUI / Combine / Swift 5.9",                 desc: "Native iOS app with FaceID/TouchID biometrics, offline queue with CoreData sync, Stripe payment sheet.", color: "#0ea5e9", bg: "#f0f9ff" },
  android:   { id: "android",   label: "Android App",          tier: "client",    port: null,  tech: "Jetpack Compose / Hilt / WorkManager",          desc: "Native Android with biometric auth, WorkManager offline sync, Room local DB, ProGuard hardened.", color: "#0ea5e9", bg: "#f0f9ff" },
  gateway:   { id: "gateway",   label: "API Gateway",          tier: "gateway",   port: 8080,  tech: "Spring Cloud Gateway / Resilience4j / Redis",   desc: "Single entry point. JWT validation via Keycloak JWKS. Rate limiting (Redis token bucket). Idempotency-Key enforcement. Circuit breaker + retry (502/503). Routes to downstream services.", color: "#8b5cf6", bg: "#f5f3ff" },
  keycloak:  { id: "keycloak",  label: "Keycloak IAM",         tier: "gateway",   port: 8180,  tech: "Keycloak 24 / OIDC / JWT",                     desc: "Identity Provider. Issues JWTs with aegispay_user_id claim. Federates Google, Microsoft, GitHub, Apple. Protocol mappers add custom claims.", color: "#8b5cf6", bg: "#f5f3ff" },
  user:      { id: "user",      label: "User Service",         tier: "core",      port: 8081,  tech: "Spring Boot 3.2 / PostgreSQL / Redis",          desc: "User registration & profile management. KYC status tracking. Source of truth for identity. Publishes UserRegisteredEvent to Kafka.", color: "#059669", bg: "#ecfdf5" },
  tx:        { id: "tx",        label: "Transaction Service",  tier: "core",      port: 8082,  tech: "Spring Boot 3.2 / PostgreSQL / Outbox",         desc: "Owns the transaction state machine (PENDING → COMPLETED/FAILED). Writes domain entity + outbox event atomically. Maintains MongoDB read models via CQRS.", color: "#059669", bg: "#ecfdf5" },
  ledger:    { id: "ledger",    label: "Ledger Service",       tier: "core",      port: 8083,  tech: "Spring Boot 3.2 / PostgreSQL (append-only)",    desc: "Append-only double-entry ledger. Balance reservation → commit → release. Optimistic locking on accounts. SUM(DEBIT) = SUM(CREDIT) invariant enforced always.", color: "#059669", bg: "#ecfdf5" },
  orch:      { id: "orch",      label: "Payment Orchestrator", tier: "core",      port: 8084,  tech: "Spring Boot 3.2 / Stripe SDK / Saga pattern",  desc: "Saga coordinator. Drives: Reserve → Risk → Stripe → Commit. Persists saga state to DB for crash recovery. Issues compensating transactions on failure.", color: "#059669", bg: "#ecfdf5" },
  risk:      { id: "risk",      label: "Risk Engine",          tier: "core",      port: 8085,  tech: "Spring Boot 3.2 / ML rules / RAG",              desc: "10 ML-style fraud rules (velocity, device, geography, amount). RAG query to AI Platform for similar fraud patterns. Issues ALLOW / REVIEW / BLOCK decisions.", color: "#059669", bg: "#ecfdf5" },
  notify:    { id: "notify",    label: "Notification Service", tier: "core",      port: 8086,  tech: "Spring Boot 3.2 / WebSocket / Gmail SMTP",      desc: "Resolves userId → contact from MongoDB. Dispatches all channels in parallel: WebSocket (STOMP), Email (Gmail SMTP), SMS, Slack. Never blocks the payment critical path.", color: "#059669", bg: "#ecfdf5" },
  ai:        { id: "ai",        label: "AI Platform",          tier: "core",      port: 8091,  tech: "Spring Boot 3.2 / Anthropic Claude / pgvector", desc: "RAG fraud copilot, error explanation, KYC OCR analysis. HNSW vector search over fraud patterns, error resolutions, KYC guidance. Claude (Haiku/Sonnet) for explanations.", color: "#059669", bg: "#ecfdf5" },
  pipeline:  { id: "pipeline",  label: "Data Pipeline",        tier: "analytics", port: 8089,  tech: "Kafka Streams / TopologyTestDriver",            desc: "TransactionMetricsStream + RiskAnalyticsStream. Batches to ClickHouse every 5s (or 500 records). At-least-once delivery — offsets committed after successful flush.", color: "#d97706", bg: "#fffbeb" },
  recon:     { id: "recon",     label: "Reconciliation Svc",   tier: "analytics", port: 8087,  tech: "Spring Batch / Stripe API",                    desc: "Nightly batch (02:00). Diffs PostgreSQL ledger_entries vs Stripe payment_intents. Writes AMOUNT_MISMATCH / MISSING_IN_LEDGER / MISSING_IN_STRIPE breaks to ClickHouse.", color: "#d97706", bg: "#fffbeb" },
  grafana:   { id: "grafana",   label: "Grafana",              tier: "analytics", port: 3100,  tech: "Grafana 10.4 / ClickHouse datasource",         desc: "3 ClickHouse-backed dashboards: Payment Operations, Fraud Intelligence, SLA & Latency. Auto-refresh 1–5 min. P50/P95/P99 saga latency panels.", color: "#d97706", bg: "#fffbeb" },
  postgres:  { id: "postgres",  label: "PostgreSQL 16",        tier: "data",      port: 5432,  tech: "PostgreSQL 16 / pgvector",                     desc: "8 databases: users, transactions (+ outbox), ledger, sagas, risk, AI vectors, keycloak, reconciliation. pgvector extension for 1536-dim embeddings.", color: "#64748b", bg: "#f8fafc" },
  redis:     { id: "redis",     label: "Redis 7",              tier: "data",      port: 6379,  tech: "Redis 7 / Lettuce",                            desc: "Sessions (TTL 30m), rate-limit counters, idempotency keys (24h TTL, SET NX). Key pattern: rate:limit:{userId}:{endpoint}. Cluster mode in prod.", color: "#64748b", bg: "#f8fafc" },
  mongo:     { id: "mongo",     label: "MongoDB 7",            tier: "data",      port: 27017, tech: "MongoDB 7 / Spring Data MongoDB",              desc: "CQRS read models: TransactionView documents (< 15ms reads). UserContactDocument for notification routing. No JOINs, no OLTP pressure.", color: "#64748b", bg: "#f8fafc" },
  clickhouse:{ id: "clickhouse",label: "ClickHouse 24",        tier: "data",      port: 8123,  tech: "ClickHouse 24.4 / MergeTree",                  desc: "Analytics store. 4 tables: transaction_facts, risk_assessments, saga_latencies, reconciliation_breaks. 3 materialized views for hourly aggregates. P99 latency < 100ms on 100M rows.", color: "#64748b", bg: "#f8fafc" },
  kafka:     { id: "kafka",     label: "Apache Kafka",         tier: "infra",     port: 9092,  tech: "Kafka 3.6 / Zookeeper / 10 topics",           desc: "Event backbone. Key topics: transaction.initiated, balance.reserved, risk.assessed, payment.completed, ledger.committed, transaction.failed. 3 partitions, RF=3 in prod.", color: "#dc2626", bg: "#fef2f2" },
};

const TIER_LABELS: Record<string, string> = { client: "Client Tier", gateway: "Auth & Gateway", core: "Core Services", infra: "Event Bus", data: "Data Layer", analytics: "Analytics" };
const TIER_ORDER  = ["client", "gateway", "infra", "core", "data", "analytics"];
const TIER_COLORS: Record<string, string> = { client: "#0ea5e9", gateway: "#8b5cf6", core: "#059669", infra: "#dc2626", data: "#64748b", analytics: "#d97706" };

type SvcId = keyof typeof SERVICES;
type Svc   = typeof SERVICES[SvcId];

export default function AegisPaySystemMap() {
  const [selected,  setSelected]  = useState<SvcId | null>(null);
  const [activeTab, setActiveTab] = useState<"services" | "events" | "dataflow">("services");
  const [highlight, setHighlight] = useState<SvcId | null>(null);

  const sel = selected ? SERVICES[selected] : null;

  const servicesByTier = (tier: string): Svc[] =>
    Object.values(SERVICES).filter(s => s.tier === tier);

  const TierRow = ({ tier }: { tier: string }) => {
    const svcs = servicesByTier(tier);
    if (!svcs.length) return null;
    const col = TIER_COLORS[tier];
    return (
      <div style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: "0.1em", textTransform: "uppercase", color: col, marginBottom: 8, paddingLeft: 4 }}>
          {TIER_LABELS[tier]}
        </div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
          {svcs.map(svc => {
            const id = svc.id as SvcId;
            const isSelected = selected === id;
            const isHighlit  = highlight === id;
            return (
              <button key={id} onClick={() => setSelected(isSelected ? null : id)} onMouseEnter={() => setHighlight(id)} onMouseLeave={() => setHighlight(null)}
                style={{ background: isSelected ? svc.color : (isHighlit ? svc.bg : "#fff"), border: `2px solid ${isSelected ? svc.color : (isHighlit ? svc.color : "#e2e8f0")}`, borderRadius: 8, padding: "8px 14px", cursor: "pointer", textAlign: "left", transition: "all 0.15s", minWidth: 120, flex: "0 0 auto" }}>
                <div style={{ fontSize: 12, fontWeight: 700, color: isSelected ? "#fff" : "#0f172a" }}>{svc.label}</div>
                {svc.port && <div style={{ fontSize: 10, color: isSelected ? "rgba(255,255,255,0.8)" : "#94a3b8", fontFamily: "monospace", marginTop: 2 }}>:{svc.port}</div>}
              </button>
            );
          })}
        </div>
      </div>
    );
  };

  const EventsPanel = () => (
    <div>
      <div style={{ fontSize: 12, fontWeight: 600, color: "#64748b", marginBottom: 12 }}>Key Kafka Topics & Event Flows</div>
      {[
        { topic: "transaction.initiated",     from: "Transaction Service",  to: ["Ledger Service", "Risk Engine", "Data Pipeline"],                                        color: "#059669" },
        { topic: "balance.reserved",          from: "Ledger Service",       to: ["Payment Orchestrator"],                                                                   color: "#8b5cf6" },
        { topic: "risk.assessed",             from: "Risk Engine",          to: ["Payment Orchestrator"],                                                                   color: "#dc2626" },
        { topic: "payment.completed",         from: "Payment Orchestrator", to: ["Ledger Service", "Notification Service", "Transaction Service", "Data Pipeline"],         color: "#059669" },
        { topic: "transaction.failed",        from: "Transaction Service",  to: ["Notification Service", "Data Pipeline"],                                                  color: "#dc2626" },
        { topic: "ledger.committed",          from: "Ledger Service",       to: ["Transaction Service"],                                                                    color: "#059669" },
        { topic: "transaction.completed",     from: "Transaction Service",  to: ["Notification Service", "Data Pipeline"],                                                  color: "#0ea5e9" },
        { topic: "risk.assessment.completed", from: "Risk Engine",          to: ["Data Pipeline"],                                                                          color: "#d97706" },
      ].map(ev => (
        <div key={ev.topic} style={{ marginBottom: 10, padding: "10px 12px", background: "#f8fafc", borderRadius: 8, borderLeft: `3px solid ${ev.color}` }}>
          <div style={{ fontSize: 11, fontFamily: "monospace", fontWeight: 700, color: ev.color }}>{ev.topic}</div>
          <div style={{ fontSize: 11, color: "#475569", marginTop: 4 }}>
            <span style={{ color: "#64748b" }}>from </span>{ev.from}
            <span style={{ color: "#64748b" }}> → </span>
            {ev.to.join(", ")}
          </div>
        </div>
      ))}
    </div>
  );

  const DataFlowPanel = () => (
    <div>
      <div style={{ fontSize: 12, fontWeight: 600, color: "#64748b", marginBottom: 12 }}>Data Store Ownership</div>
      {[
        { db: "PostgreSQL 16", dbs: ["aegispay_users", "aegispay_transactions + outbox", "aegispay_ledger", "aegispay_sagas", "aegispay_risk", "aegispay_ai (vectors)", "aegispay_keycloak", "aegispay_reconciliation"], owners: "User, Tx, Ledger, Orchestrator, Risk, AI, Keycloak, Recon Services", color: "#3b82f6", note: "Source of truth. pgvector for 1536-dim embeddings." },
        { db: "Redis 7",       dbs: ["sessions (TTL 30m)", "rate-limit counters", "idempotency keys (TTL 24h)"], owners: "API Gateway, User Service",   color: "#dc2626", note: "SET NX for idempotency. Sliding window rate limits." },
        { db: "MongoDB 7",     dbs: ["transaction-views (CQRS)", "user-contacts (notifications)"], owners: "Transaction Service, Notification Service", color: "#059669", note: "Read models only — never the write source of truth." },
        { db: "ClickHouse 24", dbs: ["transaction_facts", "risk_assessments", "saga_latencies", "reconciliation_breaks"], owners: "Data Pipeline, Reconciliation Service", color: "#d97706", note: "Analytics only. MergeTree + materialized views. 2yr TTL." },
        { db: "Kafka 3.6",     dbs: ["10 topics, RF=3 in prod", "3 partitions per topic", "at-least-once delivery"], owners: "All services (pub/sub)", color: "#8b5cf6", note: "Outbox relay ensures no event is lost even on crash." },
      ].map(row => (
        <div key={row.db} style={{ marginBottom: 12, padding: "10px 12px", background: "#f8fafc", borderRadius: 8, border: "1px solid #e2e8f0" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
            <span style={{ fontSize: 12, fontWeight: 700, color: row.color }}>{row.db}</span>
            <span style={{ fontSize: 10, color: "#94a3b8", fontFamily: "monospace" }}>{row.owners}</span>
          </div>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 4, marginBottom: 6 }}>
            {row.dbs.map(d => <span key={d} style={{ fontSize: 10, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 4, padding: "2px 6px", fontFamily: "monospace", color: "#475569" }}>{d}</span>)}
          </div>
          <div style={{ fontSize: 10, color: "#64748b", fontStyle: "italic" }}>{row.note}</div>
        </div>
      ))}
    </div>
  );

  return (
    <div style={{ fontFamily: "'IBM Plex Sans', system-ui, sans-serif", maxWidth: 960, margin: "0 auto", padding: 24, background: "#fff" }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap');`}</style>

      <div style={{ marginBottom: 24 }}>
        <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: "0.12em", color: "#94a3b8", textTransform: "uppercase", marginBottom: 4 }}>AegisPay · System Architecture</div>
        <div style={{ fontSize: 26, fontWeight: 700, color: "#0f172a", letterSpacing: "-0.02em" }}>Interactive System Map</div>
        <div style={{ fontSize: 13, color: "#64748b", marginTop: 4 }}>10 microservices · Event-driven · SAGA orchestration · Double-entry ledger · AI-powered fraud detection</div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 10, marginBottom: 24 }}>
        {[
          { label: "Microservices", value: "10",   color: "#059669" },
          { label: "Kafka Topics",  value: "10",   color: "#dc2626" },
          { label: "Data Stores",   value: "5",    color: "#3b82f6" },
          { label: "API Endpoints", value: "40+",  color: "#8b5cf6" },
          { label: "Test Coverage", value: "85%+", color: "#d97706" },
        ].map(k => (
          <div key={k.label} style={{ background: "#f8fafc", borderRadius: 8, padding: "12px 14px", border: "1px solid #e2e8f0", textAlign: "center" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: k.color, fontFamily: "monospace" }}>{k.value}</div>
            <div style={{ fontSize: 10, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.07em", marginTop: 2 }}>{k.label}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", gap: 4, marginBottom: 20 }}>
        {([{ id: "services", label: "🗂  Services" }, { id: "events", label: "⚡  Event Flows" }, { id: "dataflow", label: "🗄  Data Layer" }] as const).map(tab => (
          <button key={tab.id} onClick={() => { setActiveTab(tab.id); setSelected(null); }} style={{ padding: "7px 16px", borderRadius: 8, border: "1.5px solid", borderColor: activeTab === tab.id ? "#6366f1" : "#e2e8f0", background: activeTab === tab.id ? "#6366f1" : "#fff", color: activeTab === tab.id ? "#fff" : "#475569", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
            {tab.label}
          </button>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: sel ? "1fr 340px" : "1fr", gap: 20 }}>
        <div>
          {activeTab === "services" && (
            <div style={{ background: "#f8fafc", borderRadius: 12, padding: 20, border: "1px solid #e2e8f0" }}>
              <div style={{ fontSize: 11, color: "#94a3b8", marginBottom: 16 }}>Click any service to see details →</div>
              {TIER_ORDER.map(tier => <TierRow key={tier} tier={tier} />)}
            </div>
          )}
          {activeTab === "events"   && <div style={{ background: "#f8fafc", borderRadius: 12, padding: 20, border: "1px solid #e2e8f0" }}><EventsPanel /></div>}
          {activeTab === "dataflow" && <div style={{ background: "#f8fafc", borderRadius: 12, padding: 20, border: "1px solid #e2e8f0" }}><DataFlowPanel /></div>}
        </div>

        {sel && (
          <div style={{ background: "#fff", borderRadius: 12, border: `2px solid ${sel.color}`, padding: 20, alignSelf: "start" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 12 }}>
              <div>
                <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: "0.1em", textTransform: "uppercase", color: sel.color, marginBottom: 4 }}>{TIER_LABELS[sel.tier]}</div>
                <div style={{ fontSize: 18, fontWeight: 700, color: "#0f172a" }}>{sel.label}</div>
              </div>
              <button onClick={() => setSelected(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94a3b8", fontSize: 18, lineHeight: 1 }}>✕</button>
            </div>
            {sel.port && (
              <div style={{ marginBottom: 10 }}>
                <span style={{ fontSize: 10, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.05em" }}>Port</span>
                <div style={{ fontFamily: "monospace", fontSize: 14, fontWeight: 600, color: "#0f172a", marginTop: 2 }}>:{sel.port}</div>
              </div>
            )}
            <div style={{ marginBottom: 12 }}>
              <span style={{ fontSize: 10, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.05em" }}>Stack</span>
              <div style={{ fontSize: 12, color: "#475569", marginTop: 4, lineHeight: 1.6 }}>
                {sel.tech.split(" / ").map(t => (
                  <span key={t} style={{ display: "inline-block", background: sel.bg, border: `1px solid ${sel.color}22`, borderRadius: 4, padding: "2px 7px", fontSize: 10, marginRight: 4, marginBottom: 4, color: sel.color, fontWeight: 600 }}>{t}</span>
                ))}
              </div>
            </div>
            <div>
              <span style={{ fontSize: 10, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.05em" }}>Purpose</span>
              <div style={{ fontSize: 12, color: "#374151", marginTop: 6, lineHeight: 1.7 }}>{sel.desc}</div>
            </div>
            {(sel.id === "tx" || sel.id === "ledger" || sel.id === "orch") && (
              <div style={{ marginTop: 14, display: "flex", flexWrap: "wrap", gap: 6 }}>
                {sel.id === "tx"     && ["Outbox Pattern", "CQRS", "Idempotency"].map(p => <span key={p} style={{ fontSize: 9, background: "#f0f9ff", color: "#0369a1", border: "1px solid #bae6fd", borderRadius: 4, padding: "2px 7px", fontWeight: 600 }}>{p}</span>)}
                {sel.id === "ledger" && ["Double-entry", "Optimistic Locking", "Append-only"].map(p => <span key={p} style={{ fontSize: 9, background: "#f0fdf4", color: "#166534", border: "1px solid #bbf7d0", borderRadius: 4, padding: "2px 7px", fontWeight: 600 }}>{p}</span>)}
                {sel.id === "orch"   && ["Saga Orchestration", "Compensating Tx", "Crash Recovery"].map(p => <span key={p} style={{ fontSize: 9, background: "#faf5ff", color: "#7e22ce", border: "1px solid #e9d5ff", borderRadius: 4, padding: "2px 7px", fontWeight: 600 }}>{p}</span>)}
              </div>
            )}
          </div>
        )}
      </div>

      <div style={{ marginTop: 24, display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 12 }}>
        {[
          { title: "Event-Driven Core",  icon: "⚡", desc: "Every state change publishes a Kafka event. Services are decoupled — no synchronous chains that can cascade-fail.", color: "#dc2626" },
          { title: "Money Safety First", icon: "🔒", desc: "Outbox Pattern + idempotent consumers + saga compensation = no double-charges, no lost transactions, even under crashes.", color: "#059669" },
          { title: "AI-Augmented Fraud", icon: "🤖", desc: "Rule engine + RAG vector search over historical fraud patterns. Claude explains every decision to back-office agents.", color: "#8b5cf6" },
        ].map(p => (
          <div key={p.title} style={{ background: "#f8fafc", borderRadius: 10, padding: "14px 16px", border: "1px solid #e2e8f0" }}>
            <div style={{ fontSize: 20, marginBottom: 8 }}>{p.icon}</div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0f172a", marginBottom: 6 }}>{p.title}</div>
            <div style={{ fontSize: 11, color: "#64748b", lineHeight: 1.6 }}>{p.desc}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
