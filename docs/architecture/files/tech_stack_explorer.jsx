import { useState } from "react";

// ─── Data ─────────────────────────────────────────────────────────────────────

const LAYERS = [
  {
    id: "client", label: "Client Tier", icon: "📱",
    color: "#0ea5e9", bg: "#f0f9ff",
    desc: "Three native/web clients sharing the same backend API via JWT auth.",
    techs: [
      { name: "Next.js 14",       role: "Web app (dashboard + back-office)",  why: "App Router, Server Components, NextAuth.js for Keycloak OIDC. STOMP WebSocket for real-time status.", tag: "Web" },
      { name: "SwiftUI",          role: "iOS native app",                     why: "Combine for reactive state. FaceID/TouchID via LocalAuthentication. CoreData for offline queue. Stripe PaymentSheet.", tag: "iOS" },
      { name: "Jetpack Compose",  role: "Android native app",                 why: "Hilt DI, ViewModel, Room DB. WorkManager for offline payment sync. Biometric API. ProGuard/R8 hardened.", tag: "Android" },
      { name: "TypeScript",       role: "Type safety across web",             why: "Strict mode. Zod schema validation at API boundaries. No any-typed API responses.", tag: "Web" },
    ],
  },
  {
    id: "gateway", label: "Auth & Gateway", icon: "🔐",
    color: "#8b5cf6", bg: "#faf5ff",
    desc: "Every request passes through here — auth, rate limiting, idempotency, routing.",
    techs: [
      { name: "Spring Cloud Gateway", role: "API Gateway — single entry point", why: "JWT validation (Keycloak JWKS), rate limiting (Redis), idempotency-key enforcement, circuit breaker, retry.", tag: "Gateway" },
      { name: "Keycloak 24",          role: "Identity Provider (OIDC/OAuth2)", why: "Custom realm: aegispay. Protocol mappers add aegispay_user_id claim. Federates Google, Microsoft, GitHub, Apple.", tag: "Auth" },
      { name: "Resilience4j",         role: "Circuit breaker + retry",         why: "Protects gateway from downstream failures. Retry on 502/503 (GET + POST). Half-open state for gradual recovery.", tag: "Resilience" },
      { name: "Redis 7",              role: "Rate limiting + idempotency cache", why: "INCR sliding window counters. SET NX PX 86400000 for 24h idempotency keys. Cluster mode in prod.", tag: "Cache" },
    ],
  },
  {
    id: "backend", label: "Backend Services", icon: "⚙️",
    color: "#059669", bg: "#ecfdf5",
    desc: "8 Spring Boot microservices — each owns a bounded context and a dedicated PostgreSQL database.",
    techs: [
      { name: "Spring Boot 3.2",    role: "All 8 microservices",              why: "Spring Security Resource Server, Spring Data JPA (open-in-view=false), Spring Kafka, Actuator + Micrometer.", tag: "Framework" },
      { name: "PostgreSQL 16",      role: "Primary OLTP store (8 databases)", why: "One DB per service (bounded context isolation). pgvector extension for 1536-dim AI embeddings. pgcrypto for UUID gen.", tag: "Database" },
      { name: "Apache Kafka 3.6",   role: "Event backbone (10 topics)",       why: "Outbox relay → at-least-once delivery. 3 partitions + RF=3 in prod. Consumer groups per service. DLQ for unprocessable events.", tag: "Messaging" },
      { name: "MongoDB 7",          role: "CQRS read models",                 why: "TransactionView (< 15ms reads). UserContactDocument for notification routing. No migrations needed for read model schema changes.", tag: "Database" },
      { name: "Stripe SDK",         role: "Payment processing (Orchestrator)", why: "PaymentIntents API. Error mapping: Stripe error codes → internal failureCodes. Refund API for saga compensation.", tag: "Payment" },
      { name: "Anthropic Claude",   role: "AI explanations + RAG (AI Platform)", why: "Haiku for fast per-transaction RAG queries. Sonnet for deep fraud explanations. Tool use for structured JSON output.", tag: "AI" },
    ],
  },
  {
    id: "data", label: "Data & Analytics", icon: "📊",
    color: "#d97706", bg: "#fffbeb",
    desc: "Event-driven analytics pipeline. Never on the critical payment path.",
    techs: [
      { name: "Kafka Streams",    role: "Data Pipeline — event processing",  why: "TopologyTestDriver for unit tests. TransactionMetricsStream + RiskAnalyticsStream. 5-second batch flush to ClickHouse.", tag: "Streaming" },
      { name: "ClickHouse 24.4",  role: "OLAP analytics store",              why: "MergeTree engine. 4 tables + 3 materialized views. Array(String) for rule_flags. P99 < 100ms on 100M rows. 2yr TTL.", tag: "Database" },
      { name: "Spring Batch",     role: "Reconciliation (nightly at 02:00)", why: "Diffs PostgreSQL ledger_entries vs Stripe payment_intents. Writes AMOUNT_MISMATCH / MISSING_IN_LEDGER breaks to ClickHouse.", tag: "Batch" },
      { name: "Grafana 10.4",     role: "Business dashboards",               why: "3 ClickHouse-backed dashboards: Payment Ops, Fraud Intelligence, SLA & Latency. 1–5 min auto-refresh. Provisioned via ConfigMap.", tag: "Viz" },
    ],
  },
  {
    id: "infra", label: "Infrastructure", icon: "🏗️",
    color: "#64748b", bg: "#f8fafc",
    desc: "Kubernetes-first deployment. Local Docker Compose mirrors prod structure exactly.",
    techs: [
      { name: "Kubernetes (EKS)", role: "Container orchestration (all envs)", why: "HPA, PDB, init-containers for DB health checks. 4 namespaces: dev, staging, prod, on-prem (k3s). Helm chart for all services.", tag: "Orchestration" },
      { name: "Docker Compose",   role: "Local development stack",            why: "Full stack: all 8 services + all infra. Seed scripts inject test data. Port forwarding matches k8s services.", tag: "Local" },
      { name: "Prometheus",       role: "Metrics collection",                why: "Scrapes all Spring Boot /actuator/prometheus endpoints. PrometheusRules: SagaTimeout, BalanceNegative, DLQDepth, KafkaLag.", tag: "Monitoring" },
      { name: "Alertmanager",     role: "Alert routing",                     why: "Critical → Slack + Email. Warning → Slack only. Credentials mounted as secret files (not env vars).", tag: "Monitoring" },
      { name: "HashiCorp Vault",  role: "Secrets management (prod)",         why: "Prod environment only. Dev/staging use AWS Secrets Manager. Local uses plaintext .env.local.", tag: "Security" },
      { name: "AWS EKS",          role: "Managed Kubernetes (dev/staging/prod)", why: "3 clusters: aegispay-dev, aegispay-staging, aegispay-prod. Fargate for batch jobs. RDS Postgres in prod.", tag: "Cloud" },
    ],
  },
  {
    id: "testing", label: "Testing Stack", icon: "🧪",
    color: "#6366f1", bg: "#eef2ff",
    desc: "85%+ unit coverage. Integration and E2E tests per platform.",
    techs: [
      { name: "JUnit 5 + Mockito",       role: "Java service unit tests",         why: "All service layers. TopologyTestDriver for Kafka Streams. TestContainers for Postgres integration tests.", tag: "Backend" },
      { name: "TopologyTestDriver",       role: "Kafka Streams unit tests",        why: "TransactionMetricsStreamTest (7 tests), RiskAnalyticsStreamTest (5 tests). No broker required.", tag: "Backend" },
      { name: "XCTest",                  role: "iOS unit + integration tests",     why: "BiometricAuthManagerTests, CoreData persistence, Combine publisher tests.", tag: "iOS" },
      { name: "JUnit (Android)",         role: "Android unit tests",              why: "ViewModel tests, Room DAO tests, WorkManager tests.", tag: "Android" },
      { name: "Playwright",              role: "Web E2E tests",                   why: "Full transaction flow E2E. Back-office triage workflow. Cross-browser: Chrome, Firefox, Safari.", tag: "Web" },
      { name: "AssertJ",                 role: "Fluent assertions (Java)",         why: "Record field assertions for Kafka Streams test captors. Readable failure messages.", tag: "Backend" },
    ],
  },
];

const TAG_COLORS = {
  Web: "#0ea5e9", iOS: "#6366f1", Android: "#059669", Gateway: "#8b5cf6",
  Auth: "#8b5cf6", Resilience: "#d97706", Cache: "#dc2626", Framework: "#059669",
  Database: "#3b82f6", Messaging: "#dc2626", Payment: "#f59e0b", AI: "#8b5cf6",
  Streaming: "#d97706", Batch: "#64748b", Viz: "#0891b2", Orchestration: "#374151",
  Local: "#64748b", Monitoring: "#059669", Security: "#dc2626", Cloud: "#0ea5e9",
  Backend: "#059669", Frontend: "#0ea5e9", "": "#94a3b8",
};

export default function TechStackExplorer() {
  const [activeLayer, setActiveLayer] = useState(null);
  const [hoveredTech, setHoveredTech] = useState(null);

  const layer = activeLayer ? LAYERS.find(l => l.id === activeLayer) : null;

  return (
    <div style={{ fontFamily: "'IBM Plex Sans', system-ui, sans-serif", maxWidth: 900, margin: "0 auto", padding: 24 }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap');`}</style>

      {/* Header */}
      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 10, fontWeight: 700, color: "#94a3b8", letterSpacing: "0.12em", textTransform: "uppercase", marginBottom: 4 }}>AegisPay · Architecture</div>
        <div style={{ fontSize: 24, fontWeight: 700, color: "#0f172a", letterSpacing: "-0.02em" }}>Tech Stack Explorer</div>
        <div style={{ fontSize: 13, color: "#64748b", marginTop: 2 }}>Every technology in the stack — what it is, what it does, why it was chosen</div>
      </div>

      {/* Layer selector */}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 24 }}>
        {LAYERS.map(l => (
          <button
            key={l.id}
            onClick={() => setActiveLayer(activeLayer === l.id ? null : l.id)}
            style={{ padding: "8px 16px", borderRadius: 10, border: `2px solid ${activeLayer === l.id ? l.color : "#e2e8f0"}`, background: activeLayer === l.id ? l.color : "#fff", color: activeLayer === l.id ? "#fff" : "#374151", fontSize: 12, fontWeight: 600, cursor: "pointer", display: "flex", alignItems: "center", gap: 6, transition: "all 0.15s" }}
          >
            <span>{l.icon}</span> {l.label}
          </button>
        ))}
      </div>

      {/* All techs grid (when no layer selected) */}
      {!layer && (
        <>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16 }}>
            {LAYERS.map(l => (
              <div
                key={l.id}
                onClick={() => setActiveLayer(l.id)}
                style={{ background: "#fff", borderRadius: 12, border: `1.5px solid ${l.color}44`, padding: 16, cursor: "pointer", transition: "all 0.15s", boxShadow: "0 1px 3px rgba(0,0,0,0.06)" }}
                onMouseEnter={e => e.currentTarget.style.border = `1.5px solid ${l.color}`}
                onMouseLeave={e => e.currentTarget.style.border = `1.5px solid ${l.color}44`}
              >
                <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
                  <span style={{ fontSize: 18 }}>{l.icon}</span>
                  <span style={{ fontSize: 13, fontWeight: 700, color: l.color }}>{l.label}</span>
                </div>
                <div style={{ fontSize: 11, color: "#64748b", lineHeight: 1.5, marginBottom: 10 }}>{l.desc}</div>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
                  {l.techs.slice(0, 4).map(t => (
                    <span key={t.name} style={{ fontSize: 10, background: l.bg, color: l.color, border: `1px solid ${l.color}33`, borderRadius: 4, padding: "2px 7px", fontWeight: 600 }}>{t.name}</span>
                  ))}
                  {l.techs.length > 4 && <span style={{ fontSize: 10, color: "#94a3b8", padding: "2px 4px" }}>+{l.techs.length - 4}</span>}
                </div>
              </div>
            ))}
          </div>

          {/* Stats row */}
          <div style={{ marginTop: 20, display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12 }}>
            {[
              { label: "Total technologies",  value: LAYERS.reduce((s, l) => s + l.techs.length, 0), color: "#6366f1" },
              { label: "Backend services",    value: "8",     color: "#059669" },
              { label: "Languages",           value: "4",     color: "#d97706", note: "Java, TypeScript, Swift, Kotlin" },
              { label: "Databases",           value: "5",     color: "#3b82f6", note: "Postgres, Redis, Mongo, ClickHouse, Kafka" },
            ].map(s => (
              <div key={s.label} style={{ background: "#f8fafc", borderRadius: 10, padding: "12px 14px", border: "1px solid #e2e8f0", textAlign: "center" }}>
                <div style={{ fontSize: 28, fontWeight: 700, color: s.color, fontFamily: "monospace" }}>{s.value}</div>
                <div style={{ fontSize: 10, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.06em", marginTop: 2 }}>{s.label}</div>
                {s.note && <div style={{ fontSize: 9, color: "#b8c4ce", marginTop: 3 }}>{s.note}</div>}
              </div>
            ))}
          </div>
        </>
      )}

      {/* Layer detail */}
      {layer && (
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 16 }}>
            <span style={{ fontSize: 24 }}>{layer.icon}</span>
            <div>
              <div style={{ fontSize: 18, fontWeight: 700, color: layer.color }}>{layer.label}</div>
              <div style={{ fontSize: 12, color: "#64748b" }}>{layer.desc}</div>
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            {layer.techs.map(tech => {
              const tagColor = TAG_COLORS[tech.tag] || "#94a3b8";
              return (
                <div
                  key={tech.name}
                  onMouseEnter={() => setHoveredTech(tech.name)}
                  onMouseLeave={() => setHoveredTech(null)}
                  style={{ background: "#fff", borderRadius: 12, padding: 16, border: `1.5px solid ${hoveredTech === tech.name ? layer.color : "#e2e8f0"}`, transition: "all 0.15s" }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 8 }}>
                    <div style={{ fontSize: 14, fontWeight: 700, color: "#0f172a" }}>{tech.name}</div>
                    <span style={{ fontSize: 9, background: tagColor + "18", color: tagColor, border: `1px solid ${tagColor}33`, borderRadius: 4, padding: "2px 7px", fontWeight: 700, whiteSpace: "nowrap", marginLeft: 8 }}>{tech.tag}</span>
                  </div>
                  <div style={{ fontSize: 11, color: "#64748b", marginBottom: 8, fontStyle: "italic" }}>{tech.role}</div>
                  <div style={{ height: 1, background: "#f1f5f9", marginBottom: 8 }} />
                  <div style={{ fontSize: 11, color: "#374151", lineHeight: 1.65 }}>
                    <span style={{ fontSize: 9, fontWeight: 700, color: layer.color, textTransform: "uppercase", letterSpacing: "0.06em" }}>Why: </span>
                    {tech.why}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Design principles */}
      {!layer && (
        <div style={{ marginTop: 20 }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: "#0f172a", marginBottom: 10 }}>Design Principles Behind Stack Choices</div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            {[
              { icon: "🔒", title: "Bounded-context DB isolation", desc: "One PostgreSQL database per microservice. No cross-service JOINs. Services communicate via Kafka events, not shared tables.", color: "#059669" },
              { icon: "📝", title: "Eventual consistency by design", desc: "MongoDB read models are updated asynchronously via Kafka. A 200ms eventual consistency delay is acceptable — financial correctness lives in Postgres.", color: "#3b82f6" },
              { icon: "⚡", title: "Analytics never on critical path", desc: "ClickHouse, Grafana, Data Pipeline are downstream consumers. A ClickHouse outage has zero impact on payment processing.", color: "#d97706" },
              { icon: "🔁", title: "Idempotency everywhere",         desc: "Every Kafka consumer, every Outbox relay, every Saga step is idempotent. At-least-once delivery becomes effectively-once processing.", color: "#8b5cf6" },
            ].map(p => (
              <div key={p.title} style={{ background: "#f8fafc", borderRadius: 10, padding: "12px 14px", borderLeft: `3px solid ${p.color}` }}>
                <div style={{ display: "flex", gap: 6, alignItems: "center", marginBottom: 4 }}>
                  <span style={{ fontSize: 16 }}>{p.icon}</span>
                  <span style={{ fontSize: 12, fontWeight: 700, color: "#0f172a" }}>{p.title}</span>
                </div>
                <div style={{ fontSize: 11, color: "#64748b", lineHeight: 1.6 }}>{p.desc}</div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div style={{ marginTop: 16, fontSize: 10, color: "#cbd5e1", textAlign: "center" }}>AegisPay · docs/architecture/files/tech_stack_explorer.jsx</div>
    </div>
  );
}
