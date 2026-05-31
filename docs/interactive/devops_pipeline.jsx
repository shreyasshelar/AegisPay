import { useState } from "react";

// ─── Data ─────────────────────────────────────────────────────────────────────

const TABS = [
  { id: "cicd",    label: "⚙️  CI/CD Pipeline" },
  { id: "k8s",     label: "☸️  Kubernetes" },
  { id: "security",label: "🛡️  Security" },
  { id: "observ",  label: "📊  Observability" },
  { id: "secrets", label: "🔐  Secrets" },
  { id: "envs",    label: "🌍  Environments" },
];

// CI/CD Steps
const CI_STEPS = [
  {
    id: 1, phase: "Trigger",   actor: "GitHub Actions",     color: "#6366f1",
    title: "Push to main / dev",
    detail: "Push triggers ci-java.yml. dorny/paths-filter detects which services changed. Only changed services (+ anything that imports a changed lib) run their build job — saves 60-80% CI minutes.",
    code: `on:\n  push:\n    branches: [main, dev]\n    paths: ['libs/**', 'services/**']\n\n# detect-changes job outputs:\n#   transaction-service: true\n#   ledger-service: false  ← not rebuilt`,
  },
  {
    id: 2, phase: "CI",        actor: "Maven + JUnit 5",    color: "#059669",
    title: "Build, test, verify",
    detail: "Each changed service: mvn verify (compile + unit tests + Checkstyle). Skips Docker build on dev branch — only main gets pushed to GHCR to save registry costs.",
    code: `mvn --batch-mode -pl services/transaction-service\n  -am verify\n  -Dcheckstyle.failOnViolation=true\n  -Dsurefire.failIfNoSpecifiedTests=false\n\n# Layer-cached Docker build (only on main):\ndocker buildx build --cache-from type=gha\n  --tag ghcr.io/.../transaction-service:$SHA`,
  },
  {
    id: 3, phase: "CI",        actor: "GHCR",               color: "#0891b2",
    title: "Push layered image to GHCR",
    detail: "Spring Boot layered JAR: dependencies / spring-boot-loader / snapshot-dependencies / application. Only the 'application' layer changes per build — pulls from cache. Non-root user 1001 (aegispay) baked in.",
    code: `# Dockerfile: layered extraction\nFROM eclipse-temurin:21-jre-jammy AS builder\nRUN java -Djarmode=layertools -jar app.jar extract\n\nFROM eclipse-temurin:21-jre-jammy\nUSER aegispay  # non-root UID 1001\nCOPY --from=builder /app/dependencies/ ./\nCOPY --from=builder /app/application/ ./`,
  },
  {
    id: 4, phase: "Security",  actor: "Trivy + CodeQL",     color: "#dc2626",
    title: "Security scans (parallel)",
    detail: "Trivy: scans all 10 service images for CRITICAL+HIGH CVEs, uploads SARIF to GitHub Security tab. CodeQL: Java static analysis. OWASP dep-check: fails on CVSS ≥ 7. Runs weekly + on every main push.",
    code: `trivy-action:\n  image-ref: ghcr.io/.../transaction-service:latest\n  severity: CRITICAL,HIGH\n  format: sarif\n  exit-code: "0"  # advisory, not blocking\n\ncodeql:\n  languages: java\n  # Runs full compilation to analyze\n\nowasp: -DfailBuildOnCVSS=7`,
  },
  {
    id: 5, phase: "CD",        actor: "cd-prod.yml",        color: "#8b5cf6",
    title: "Update values-prod.yaml",
    detail: "cd-prod.yml triggers after Java CI succeeds on main. Validates all 10 images exist in GHCR (docker manifest inspect). Updates values-prod.yaml image tags using yq. Commits [skip ci] back to main.",
    code: `for svc in $JAVA_SERVICES; do\n  yq e ".services.${svc}.image.tag = \\"${SHA}\\""\n    -i values-prod.yaml\ndone\n\ngit commit -m "chore(cd/prod): deploy ${SHA:0:12} [skip ci]"\ngit push origin main`,
  },
  {
    id: 6, phase: "CD",        actor: "ArgoCD",             color: "#d97706",
    title: "ArgoCD detects OutOfSync → syncs",
    detail: "ArgoCD watches the git repo. When values-prod.yaml changes, app becomes OutOfSync. ArgoCD runs 'helm template' against the new values and applies the diff to k8s. Waits for health=Healthy.",
    code: `argocd app sync aegispay-prod \\\n  --auth-token $ARGOCD_TOKEN \\\n  --server $ARGOCD_SERVER \\\n  --grpc-web --timeout 300\n\nargocd app wait aegispay-prod \\\n  --health --timeout 300\n\n# Post-deploy smoke:\ncurl --fail ${GATEWAY_URL}/actuator/health`,
  },
  {
    id: 7, phase: "Deploy",    actor: "Kubernetes",         color: "#059669",
    title: "Rolling update — zero downtime",
    detail: "k8s rolling update replaces pods one at a time. topologySpreadConstraints ensure pods spread across nodes. terminationGracePeriodSeconds=30 lets Spring drain in-flight requests gracefully before kill.",
    code: `# All deployments: runAsNonRoot, readOnlyRootFilesystem\n# seccompProfile: RuntimeDefault\n# capabilities: drop: [ALL]\n\n# Liveness: /actuator/health/liveness\n# Readiness: /actuator/health/readiness\n# initialDelaySeconds: 20-30 (JVM warmup)`,
  },
];

// K8s resources per service
const K8S_RESOURCES = [
  { name: "api-gateway",          replicas: "2-10", cpu: "250m-1000m", mem: "512Mi-1Gi",   hpa: true, pdb: true, np: true, ingress: true  },
  { name: "transaction-service",  replicas: "3-12", cpu: "300m-1500m", mem: "768Mi-2Gi",  hpa: true, pdb: true, np: true, ingress: false },
  { name: "ledger-service",       replicas: "2-8",  cpu: "300m-1500m", mem: "512Mi-1.5Gi",hpa: true, pdb: true, np: true, ingress: false },
  { name: "payment-orchestrator", replicas: "2-8",  cpu: "300m-1500m", mem: "768Mi-2Gi",  hpa: true, pdb: true, np: true, ingress: false },
  { name: "risk-engine",          replicas: "2-8",  cpu: "300m-1500m", mem: "768Mi-2Gi",  hpa: true, pdb: true, np: true, ingress: false },
  { name: "user-service",         replicas: "2-6",  cpu: "200m-1000m", mem: "512Mi-1Gi",   hpa: true, pdb: true, np: true, ingress: false },
  { name: "notification-service", replicas: "2-6",  cpu: "200m-1000m", mem: "512Mi-1Gi",   hpa: true, pdb: true, np: true, ingress: false },
  { name: "ai-platform",          replicas: "2-6",  cpu: "500m-2000m", mem: "1Gi-4Gi",     hpa: true, pdb: true, np: true, ingress: false },
  { name: "data-pipeline",        replicas: "1-4",  cpu: "200m-1000m", mem: "512Mi-1Gi",   hpa: true, pdb: true, np: true, ingress: false },
  { name: "reconciliation-svc",   replicas: "1-1",  cpu: "200m-500m",  mem: "512Mi-1Gi",   hpa: false,pdb: false,np: true, ingress: false },
];

// Security checklist
const SECURITY_ITEMS = [
  { cat: "Container",   item: "Non-root user (UID 1001 aegispay)",              done: true  },
  { cat: "Container",   item: "readOnlyRootFilesystem: true",                   done: true  },
  { cat: "Container",   item: "capabilities: drop: [ALL]",                      done: true  },
  { cat: "Container",   item: "seccompProfile: RuntimeDefault",                 done: true  },
  { cat: "Container",   item: "automountServiceAccountToken: false",            done: true  },
  { cat: "Network",     item: "NetworkPolicy per service (ingress + egress)",   done: true  },
  { cat: "Network",     item: "HSTS header (max-age=31536000)",                 done: true  },
  { cat: "Network",     item: "CSP default-src 'self'",                         done: true  },
  { cat: "Network",     item: "X-Frame-Options: DENY",                          done: true  },
  { cat: "Scanning",    item: "Trivy image scan (CRITICAL+HIGH, SARIF upload)", done: true  },
  { cat: "Scanning",    item: "CodeQL static analysis (Java)",                  done: true  },
  { cat: "Scanning",    item: "OWASP Dependency Check (fail CVSS ≥ 7)",        done: true  },
  { cat: "Scanning",    item: "Dependabot (Maven + npm + Docker + Actions)",    done: true  },
  { cat: "Secrets",     item: "ExternalSecrets (Vault/AWS SM — no plaintext in git)", done: true },
  { cat: "Secrets",     item: "Alertmanager creds in mounted secret file",      done: true  },
  { cat: "Auth",        item: "Stripe webhook Webhook.constructEvent() verify", done: true  },
  { cat: "Auth",        item: "JWT JWKS validation at gateway",                 done: true  },
  { cat: "Auth",        item: "Android cleartext traffic disabled",             done: true  },
  { cat: "Auth",        item: "iOS ITSAppUsesNonExemptEncryption: false",       done: true  },
  { cat: "Deployment",  item: "Image signing with cosign",                      done: false },
  { cat: "Deployment",  item: "GitHub branch protection + required CI checks",  done: false },
  { cat: "Deployment",  item: "CODEOWNERS file",                                done: false },
];

// Observability
const ALERTS = [
  { name: "SagaTimeoutRateHigh",          expr: "rate(saga_timeout_total[5m]) > 0.1",           sev: "warning",  team: "Platform" },
  { name: "SagaCompensatingRateHigh",     expr: "rate(saga_compensating_total[5m]) > 0.5",      sev: "critical", team: "Platform" },
  { name: "DlqDepthNonZero",             expr: "kafka_consumer_group_lag{topic=~'.*DLQ'} > 0", sev: "critical", team: "Platform" },
  { name: "KafkaConsumerLagHigh",         expr: "kafka_consumer_group_lag > 5000",              sev: "warning",  team: "Platform" },
  { name: "BalanceNegative",             expr: "min(ledger_account_available_balance) < 0",    sev: "critical", team: "Finance"  },
  { name: "LedgerReservationFailureHigh", expr: "rate(ledger_reservation_failures[5m]) > 0.1", sev: "warning",  team: "Finance"  },
  { name: "ReconciliationBreakCountHigh", expr: "recon_breaks_unresolved > 5",                 sev: "critical", team: "Finance"  },
  { name: "ReconciliationJobFailed",      expr: "kube_job_failed{job='reconciliation'} > 0",   sev: "critical", team: "Finance"  },
  { name: "NotificationDeliveryFail",    expr: "rate(notification_delivery_failures[1m]) > 0.5", sev: "warning", team: "Ops"    },
  { name: "DataPipelineSinkErrorHigh",    expr: "rate(pipeline_sink_errors[1m]) > 0.2",         sev: "warning",  team: "Ops"     },
];

// Environments — 3-tier: Local → Dev (cost-optimised) → Prod
const ENVS = [
  {
    id: "local",
    name: "Local",
    subtitle: "Full stack, zero cost",
    icon: "💻",
    color: "#64748b",
    trigger: "Manual — docker compose up",
    branch: null,
    argocd: false, hpa: false, tls: false, pdb: false,
    replicas: "1 each",
    infra: "Docker Compose (no k8s)",
    secrets: ".env.local — plaintext, never committed",
    integrations: [
      { name: "AI / LLM",    value: "Anthropic direct (dev key, low RPM limit)", cost: "free tier" },
      { name: "Payments",    value: "Stripe test mode (pk_test_...)",             cost: "free" },
      { name: "Auth",        value: "Keycloak local container :8180",             cost: "free" },
      { name: "SMS",         value: "Fast2SMS sandbox — no real SMS sent",        cost: "free" },
      { name: "Email",       value: "Gmail SMTP dev account",                     cost: "free" },
    ],
    cost: "~₹0 / month",
    costNote: "Everything runs on your laptop. No cloud spend.",
    gates: [],
    desc: "All 10 services + all infra via Docker Compose. Seed scripts inject test users and transactions. Port names match k8s service names so config is shared.",
    values: "docker-compose.yml + .env.local",
  },
  {
    id: "dev",
    name: "Dev",
    subtitle: "Cost-optimised cloud — real infra, sandbox integrations",
    icon: "🔧",
    color: "#0891b2",
    trigger: "Auto — push to dev branch → CI green",
    branch: "dev",
    argocd: true, hpa: false, tls: true, pdb: false,
    replicas: "1 each (no HA)",
    infra: "EKS t3.medium × 2 nodes  (or k3s single-node)",
    secrets: "AWS Secrets Manager — free tier (< 100 secrets, 10k API calls/mo)",
    integrations: [
      { name: "AI / LLM",    value: "OpenRouter (claude-haiku-3 + fallbacks)",    cost: "~$0.25/1M tokens  ≈ 10× cheaper than direct Anthropic" },
      { name: "Payments",    value: "Stripe test mode — no real money moves",      cost: "free" },
      { name: "Auth",        value: "Keycloak pod in-cluster",                     cost: "included in node cost" },
      { name: "SMS",         value: "Fast2SMS sandbox — OTP logged, not sent",     cost: "free" },
      { name: "Email",       value: "Gmail SMTP dev account",                      cost: "free" },
      { name: "Kafka",       value: "Single-broker in-cluster (1 partition)",      cost: "included" },
      { name: "Postgres",    value: "Single RDS t3.micro (Multi-AZ off)",          cost: "~$15/mo" },
      { name: "Redis",       value: "ElastiCache t3.micro (no cluster mode)",      cost: "~$12/mo" },
      { name: "ClickHouse",  value: "Single-node in-cluster pod",                  cost: "included" },
    ],
    cost: "~₹2,500–3,500 / month",
    costNote: "2 × t3.medium + 1 × RDS t3.micro + ElastiCache. OpenRouter keeps AI cost near zero during dev iteration.",
    gates: ["CI green (ci-java.yml + ci-web.yml)", "Auto-sync ArgoCD (no approval)"],
    desc: "Mirrors prod topology exactly — same Helm chart, same ArgoCD ApplicationSet, same NetworkPolicies. Only the integration endpoints and replica counts differ. Auto-deploys on every green push to dev. No PDB since single replica.",
    values: "values-dev.yaml (committed)",
  },
  {
    id: "prod",
    name: "Prod",
    subtitle: "Full HA — real integrations, approval gate",
    icon: "🚀",
    color: "#059669",
    trigger: "Manual gate — PR to main → CI green → GitHub Env approval → deploy",
    branch: "main",
    argocd: true, hpa: true, tls: true, pdb: true,
    replicas: "2–10 (HPA)",
    infra: "EKS m5.large × 3+ nodes (Multi-AZ)",
    secrets: "HashiCorp Vault — k8s service account auth (no static tokens)",
    integrations: [
      { name: "AI / LLM",    value: "Anthropic direct (claude-haiku + sonnet)",   cost: "pay-per-token, full rate limits" },
      { name: "Payments",    value: "Stripe live mode (pk_live_...)",              cost: "Stripe fees apply" },
      { name: "Auth",        value: "Keycloak HA (2 replicas + RDS backend)",     cost: "included in node cost" },
      { name: "SMS",         value: "Fast2SMS live — real OTPs delivered",        cost: "per-SMS billing" },
      { name: "Email",       value: "Gmail SMTP (prod account)",                  cost: "Google Workspace" },
      { name: "Kafka",       value: "3-broker cluster, RF=3, 3 partitions each",  cost: "included" },
      { name: "Postgres",    value: "RDS r6g.large Multi-AZ per service DB",      cost: "~$200/mo (8 DBs)" },
      { name: "Redis",       value: "ElastiCache r6g.large cluster mode",         cost: "~$80/mo" },
      { name: "ClickHouse",  value: "3-shard cluster — MergeTree, TTL enforced",  cost: "~$60/mo" },
    ],
    cost: "~₹35,000–50,000 / month",
    costNote: "EKS node group + RDS Multi-AZ + ElastiCache + ClickHouse. HPA keeps costs proportional to load.",
    gates: ["CI green (all workflows)", "GitHub Environment approval (manual)", "CD smoke test /actuator/health"],
    desc: "PDB ensures min 1 pod always available during rolling deploys. topologySpreadConstraints spread pods across AZs. CD smoke test fails fast if the deploy is broken. ArgoCD manual sync (not auto) for safety.",
    values: "values-prod.yaml (image tags updated by ci-cd bot, committed with [skip ci])",
  },
];

// What changes between Dev and Prod
const DEV_VS_PROD = [
  { aspect: "AI provider",       dev: "OpenRouter (haiku-3 via proxy)",           prod: "Anthropic direct (haiku + sonnet)",         why: "10× cheaper per token during iteration" },
  { aspect: "Stripe mode",       dev: "Test mode (pk_test_ / sk_test_)",          prod: "Live mode (pk_live_ / sk_live_)",            why: "No real money in dev — sandbox charges only" },
  { aspect: "Replicas",          dev: "1 per service",                            prod: "2–10 via HPA",                              why: "No HA needed in dev, saves ~60% node cost" },
  { aspect: "Secrets backend",   dev: "AWS Secrets Manager (free tier)",          prod: "HashiCorp Vault (k8s auth, no static tokens)", why: "Full Vault adds ops burden not needed in dev" },
  { aspect: "Postgres",          dev: "RDS t3.micro single-AZ",                  prod: "RDS r6g.large Multi-AZ per service DB",      why: "Single-AZ is fine for dev; Multi-AZ avoids prod data loss" },
  { aspect: "Redis",             dev: "ElastiCache t3.micro, no cluster",         prod: "ElastiCache r6g.large cluster mode",         why: "Cluster mode not needed for 1-replica services" },
  { aspect: "Kafka",             dev: "1 broker, 1 partition",                   prod: "3 brokers, RF=3, 3 partitions",              why: "RF=1 is cheap; prod needs durability" },
  { aspect: "ArgoCD sync",       dev: "Auto-sync (no gate)",                     prod: "Manual sync + GitHub Env approval",           why: "Auto-sync in dev for fast iteration; prod needs human gate" },
  { aspect: "PDB",               dev: "None (1 replica = PDB pointless)",         prod: "minAvailable: 1 (blocks eviction)",          why: "PDB only matters when replicas > 1" },
  { aspect: "SMS delivery",      dev: "Sandbox — OTP logged not sent",            prod: "Fast2SMS live — real OTPs delivered",         why: "Real SMS would cost money during dev testing" },
];

// ─── Component ───────────────────────────────────────────────────────────────

export default function DevOpsPipeline() {
  const [tab, setTab]           = useState("cicd");
  const [ciStep, setCiStep]     = useState(0);
  const [selectedSvc, setSelectedSvc] = useState(null);
  const [secCat, setSecCat]     = useState("All");
  const [selectedEnv, setSelectedEnv] = useState("dev");
  const [activeEnvTab, setActiveEnvTab] = useState("integrations"); // integrations | infra | gates

  const step = CI_STEPS[ciStep];

  // Security filter
  // (selectedEnv is a string id; env is resolved inline in the envs tab via ENVS.find)
  const secCats = ["All", ...Array.from(new Set(SECURITY_ITEMS.map(i => i.cat)))];
  const filteredSec = secCat === "All" ? SECURITY_ITEMS : SECURITY_ITEMS.filter(i => i.cat === secCat);
  const doneCount = SECURITY_ITEMS.filter(i => i.done).length;

  return (
    <div style={{ fontFamily: "'IBM Plex Sans', system-ui, sans-serif", maxWidth: 960, margin: "0 auto", padding: 24 }}>
      <style>{`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap');`}</style>

      {/* Header */}
      <div style={{ marginBottom: 22 }}>
        <div style={{ fontSize: 10, fontWeight: 700, color: "#94a3b8", letterSpacing: "0.12em", textTransform: "uppercase", marginBottom: 4 }}>AegisPay · DevOps</div>
        <div style={{ fontSize: 26, fontWeight: 700, color: "#0f172a", letterSpacing: "-0.02em" }}>Infrastructure & DevOps Pipeline</div>
        <div style={{ fontSize: 13, color: "#64748b", marginTop: 3 }}>
          GitHub Actions → GHCR → ArgoCD → EKS/k3s · 5 environments · 10 services · Security-hardened
        </div>
      </div>

      {/* Summary KPIs */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 10, marginBottom: 22 }}>
        {[
          { label: "CI Workflows",    value: "6",  color: "#6366f1" },
          { label: "Environments",    value: "3",  color: "#0891b2" },
          { label: "Security checks", value: `${doneCount}/${SECURITY_ITEMS.length}`, color: "#059669" },
          { label: "Prometheus alerts", value: "12", color: "#d97706" },
          { label: "Kafka topics",    value: "10", color: "#dc2626" },
        ].map(k => (
          <div key={k.label} style={{ background: "#f8fafc", borderRadius: 8, padding: "10px 14px", textAlign: "center", border: "1px solid #e2e8f0" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: k.color, fontFamily: "monospace" }}>{k.value}</div>
            <div style={{ fontSize: 9, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.07em", marginTop: 2 }}>{k.label}</div>
          </div>
        ))}
      </div>

      {/* Tabs */}
      <div style={{ display: "flex", gap: 4, marginBottom: 20, flexWrap: "wrap" }}>
        {TABS.map(t => (
          <button key={t.id} onClick={() => setTab(t.id)} style={{ padding: "7px 14px", borderRadius: 8, border: `1.5px solid ${tab === t.id ? "#6366f1" : "#e2e8f0"}`, background: tab === t.id ? "#6366f1" : "#fff", color: tab === t.id ? "#fff" : "#475569", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
            {t.label}
          </button>
        ))}
      </div>

      {/* ── CI/CD ── */}
      {tab === "cicd" && (
        <div style={{ display: "grid", gridTemplateColumns: "220px 1fr", gap: 16 }}>
          {/* Step list */}
          <div style={{ background: "#f8fafc", borderRadius: 12, padding: 12, border: "1px solid #e2e8f0" }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: 10 }}>Pipeline Steps</div>
            {CI_STEPS.map((s, i) => {
              const isActive = i === ciStep;
              const isDone   = i < ciStep;
              return (
                <div key={s.id} onClick={() => setCiStep(i)} style={{ display: "flex", gap: 8, padding: "8px 8px", borderRadius: 7, marginBottom: 4, cursor: "pointer", background: isActive ? s.color + "15" : "transparent", border: `1.5px solid ${isActive ? s.color : "transparent"}`, transition: "all 0.12s" }}>
                  <div style={{ width: 22, height: 22, borderRadius: "50%", background: isDone ? "#059669" : (isActive ? s.color : "#e2e8f0"), color: isDone || isActive ? "#fff" : "#94a3b8", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 9, fontWeight: 700, flexShrink: 0 }}>
                    {isDone ? "✓" : s.id}
                  </div>
                  <div>
                    <div style={{ fontSize: 9, fontWeight: 700, color: s.color, textTransform: "uppercase", letterSpacing: "0.06em" }}>{s.phase}</div>
                    <div style={{ fontSize: 10, color: isActive ? "#0f172a" : "#64748b", lineHeight: 1.3, fontWeight: isActive ? 600 : 400 }}>{s.title}</div>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Step detail */}
          <div>
            <div style={{ background: "#fff", borderRadius: 12, border: `2px solid ${step.color}`, padding: 20, marginBottom: 14 }}>
              <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", marginBottom: 10 }}>
                <div>
                  <div style={{ fontSize: 10, fontWeight: 700, color: step.color, textTransform: "uppercase", letterSpacing: "0.1em", marginBottom: 4 }}>Step {step.id} · {step.phase} · {step.actor}</div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: "#0f172a" }}>{step.title}</div>
                </div>
                <span style={{ background: step.color + "18", color: step.color, border: `1px solid ${step.color}33`, borderRadius: 6, padding: "4px 10px", fontSize: 10, fontWeight: 700, whiteSpace: "nowrap" }}>{step.actor}</span>
              </div>
              <div style={{ fontSize: 13, color: "#374151", lineHeight: 1.7, marginBottom: 14 }}>{step.detail}</div>
              <div style={{ background: "#0f172a", borderRadius: 8, padding: "12px 14px" }}>
                <pre style={{ margin: 0, fontSize: 11, color: "#e2e8f0", fontFamily: "'JetBrains Mono', monospace", lineHeight: 1.7, whiteSpace: "pre-wrap" }}>{step.code}</pre>
              </div>
            </div>
            <div style={{ display: "flex", gap: 8 }}>
              <button onClick={() => setCiStep(s => Math.max(0, s - 1))} disabled={ciStep === 0} style={{ padding: "7px 16px", borderRadius: 8, border: "1.5px solid #e2e8f0", background: "#fff", color: ciStep === 0 ? "#cbd5e1" : "#374151", fontSize: 12, cursor: ciStep === 0 ? "not-allowed" : "pointer" }}>← Prev</button>
              <button onClick={() => setCiStep(s => Math.min(CI_STEPS.length - 1, s + 1))} disabled={ciStep === CI_STEPS.length - 1} style={{ padding: "7px 16px", borderRadius: 8, border: `1.5px solid ${ciStep === CI_STEPS.length - 1 ? "#e2e8f0" : "#6366f1"}`, background: ciStep === CI_STEPS.length - 1 ? "#fff" : "#6366f1", color: ciStep === CI_STEPS.length - 1 ? "#cbd5e1" : "#fff", fontSize: 12, fontWeight: 600, cursor: ciStep === CI_STEPS.length - 1 ? "not-allowed" : "pointer" }}>Next →</button>
              <div style={{ marginLeft: "auto", fontSize: 11, color: "#94a3b8", fontFamily: "monospace", alignSelf: "center" }}>{ciStep + 1} / {CI_STEPS.length}</div>
            </div>
          </div>
        </div>
      )}

      {/* ── Kubernetes ── */}
      {tab === "k8s" && (
        <div>
          <div style={{ marginBottom: 14 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: "#64748b", marginBottom: 10 }}>Per-service Kubernetes resources — click a service for details</div>
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 11 }}>
                <thead>
                  <tr style={{ borderBottom: "2px solid #e2e8f0" }}>
                    {["Service", "Replicas (min-max)", "CPU (req-limit)", "Memory (req-limit)", "HPA", "PDB", "NetworkPolicy", "Ingress"].map(h => (
                      <th key={h} style={{ textAlign: "left", padding: "8px 10px", color: "#94a3b8", fontWeight: 700, fontSize: 10, textTransform: "uppercase", letterSpacing: "0.05em", whiteSpace: "nowrap" }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {K8S_RESOURCES.map((svc, i) => (
                    <tr key={svc.name} onClick={() => setSelectedSvc(selectedSvc === i ? null : i)} style={{ borderBottom: "1px solid #f1f5f9", cursor: "pointer", background: selectedSvc === i ? "#f0f9ff" : (i % 2 === 0 ? "#fff" : "#fafafa"), transition: "background 0.1s" }}>
                      <td style={{ padding: "9px 10px", fontWeight: 600, color: "#0f172a", fontFamily: "monospace", fontSize: 11 }}>{svc.name}</td>
                      <td style={{ padding: "9px 10px", fontFamily: "monospace", color: "#374151" }}>{svc.replicas}</td>
                      <td style={{ padding: "9px 10px", fontFamily: "monospace", color: "#374151" }}>{svc.cpu}</td>
                      <td style={{ padding: "9px 10px", fontFamily: "monospace", color: "#374151" }}>{svc.mem}</td>
                      {[svc.hpa, svc.pdb, svc.np, svc.ingress].map((v, j) => (
                        <td key={j} style={{ padding: "9px 10px", textAlign: "center" }}>
                          <span style={{ fontSize: 14 }}>{v ? "✅" : "—"}</span>
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* K8s hardening callout */}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginTop: 16 }}>
            {[
              { title: "Pod Security", color: "#059669", items: ["runAsNonRoot: true (UID 1001)", "readOnlyRootFilesystem: true", "allowPrivilegeEscalation: false", "capabilities: drop: [ALL]", "seccompProfile: RuntimeDefault", "automountServiceAccountToken: false"] },
              { title: "Traffic Control", color: "#0891b2", items: ["NetworkPolicy: ingress from ingress-nginx only", "NetworkPolicy: egress to same namespace only", "DNS egress on port 53 (UDP+TCP)", "HTTPS egress on port 443 (external APIs)", "Prometheus scrape from monitoring namespace", "Inter-service ports only (8081-8088)"] },
              { title: "Availability", color: "#8b5cf6", items: ["HPA: CPU target 70% utilization", "PDB: minAvailable=1 (prevents k8s eviction taking all pods)", "topologySpreadConstraints: maxSkew=1 across nodes", "terminationGracePeriodSeconds: 30", "Spring shutdown: graceful", "Rolling update (no Recreate strategy)"] },
              { title: "Health Probes", color: "#d97706", items: ["Liveness: /actuator/health/liveness", "Readiness: /actuator/health/readiness", "initialDelaySeconds: 20-30 (JVM warmup)", "periodSeconds: 10-15", "failureThreshold: 3", "ConfigMap change triggers rolling restart (checksum annotation)"] },
            ].map(box => (
              <div key={box.title} style={{ background: "#f8fafc", borderRadius: 10, padding: "12px 14px", border: "1px solid #e2e8f0" }}>
                <div style={{ fontSize: 12, fontWeight: 700, color: box.color, marginBottom: 8 }}>{box.title}</div>
                {box.items.map(item => (
                  <div key={item} style={{ display: "flex", gap: 6, alignItems: "flex-start", marginBottom: 4 }}>
                    <span style={{ color: box.color, flexShrink: 0, marginTop: 1 }}>·</span>
                    <span style={{ fontSize: 11, color: "#374151" }}>{item}</span>
                  </div>
                ))}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Security ── */}
      {tab === "security" && (
        <div>
          {/* Category filter */}
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 14 }}>
            {secCats.map(c => (
              <button key={c} onClick={() => setSecCat(c)} style={{ padding: "5px 12px", borderRadius: 20, border: `1.5px solid ${secCat === c ? "#059669" : "#e2e8f0"}`, background: secCat === c ? "#059669" : "#fff", color: secCat === c ? "#fff" : "#475569", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                {c}
              </button>
            ))}
            <div style={{ marginLeft: "auto", fontSize: 12, color: "#64748b", alignSelf: "center" }}>
              <span style={{ color: "#059669", fontWeight: 700 }}>{doneCount}</span> / {SECURITY_ITEMS.length} implemented
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 16 }}>
            {filteredSec.map((item, i) => (
              <div key={i} style={{ display: "flex", gap: 10, padding: "9px 12px", borderRadius: 8, background: item.done ? "#f0fdf4" : "#fef2f2", border: `1px solid ${item.done ? "#bbf7d0" : "#fecaca"}` }}>
                <span style={{ fontSize: 14, flexShrink: 0 }}>{item.done ? "✅" : "❌"}</span>
                <div>
                  <div style={{ fontSize: 9, fontWeight: 700, color: item.done ? "#166534" : "#dc2626", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 2 }}>{item.cat}</div>
                  <div style={{ fontSize: 11, color: "#374151" }}>{item.item}</div>
                </div>
              </div>
            ))}
          </div>

          {/* Scanning pipeline */}
          <div style={{ background: "#f8fafc", borderRadius: 10, padding: 14, border: "1px solid #e2e8f0" }}>
            <div style={{ fontSize: 12, fontWeight: 700, color: "#0f172a", marginBottom: 10 }}>Security Scan Pipeline (security-scan.yml)</div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 10 }}>
              {[
                { name: "Trivy", trigger: "push main + weekly Mon 02:00", scope: "All 10 Docker images", action: "SARIF → GitHub Security tab", color: "#dc2626" },
                { name: "CodeQL", trigger: "push main + weekly",         scope: "Java source code",      action: "SARIF → GitHub Code Scanning", color: "#6366f1" },
                { name: "OWASP DC", trigger: "push main + weekly",       scope: "Maven dependencies",   action: "Fail on CVSS ≥ 7",            color: "#d97706" },
              ].map(s => (
                <div key={s.name} style={{ background: "#fff", borderRadius: 8, padding: "10px 12px", border: `1.5px solid ${s.color}33` }}>
                  <div style={{ fontSize: 13, fontWeight: 700, color: s.color, marginBottom: 6 }}>{s.name}</div>
                  <div style={{ fontSize: 10, color: "#64748b", marginBottom: 4 }}>⏱ {s.trigger}</div>
                  <div style={{ fontSize: 10, color: "#374151", marginBottom: 4 }}>🎯 {s.scope}</div>
                  <div style={{ fontSize: 10, color: "#374151" }}>📤 {s.action}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* ── Observability ── */}
      {tab === "observ" && (
        <div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12, marginBottom: 16 }}>
            {[
              { title: "Metrics", icon: "📈", color: "#059669", items: ["Micrometer → /actuator/prometheus", "Prometheus scrapes every 15s", "kube-prometheus-stack dashboards", "Spring Boot Observability (gnetId 17175)", "JVM Micrometer (gnetId 4701)", "Kafka Overview (gnetId 7589)"] },
              { title: "Tracing", icon: "🔍", color: "#0891b2", items: ["W3C traceparent header propagation", "X-Correlation-Id injected at Gateway", "MDC: traceId + correlationId in all logs", "All 10 services emit structured JSON logs", "Logback masks PII (emails, phones)", "Distributed request journey trackable"] },
              { title: "Analytics Dashboards", icon: "📊", color: "#d97706", items: ["Payment Operations (ClickHouse)", "Fraud Intelligence (ClickHouse)", "SLA & Latency P50/P95/P99 (ClickHouse)", "Auto-refresh 1–5 min", "3 materialized views for fast queries", "Grafana on :3100, provisioned via ConfigMap"] },
            ].map(box => (
              <div key={box.title} style={{ background: "#f8fafc", borderRadius: 10, padding: "12px 14px", border: "1px solid #e2e8f0" }}>
                <div style={{ display: "flex", gap: 6, alignItems: "center", marginBottom: 8 }}>
                  <span style={{ fontSize: 16 }}>{box.icon}</span>
                  <span style={{ fontSize: 13, fontWeight: 700, color: box.color }}>{box.title}</span>
                </div>
                {box.items.map(item => (
                  <div key={item} style={{ display: "flex", gap: 6, marginBottom: 4 }}>
                    <span style={{ color: box.color, flexShrink: 0 }}>·</span>
                    <span style={{ fontSize: 11, color: "#374151" }}>{item}</span>
                  </div>
                ))}
              </div>
            ))}
          </div>

          {/* Alerts table */}
          <div style={{ background: "#f8fafc", borderRadius: 12, padding: 14, border: "1px solid #e2e8f0" }}>
            <div style={{ fontSize: 12, fontWeight: 700, color: "#0f172a", marginBottom: 10 }}>PrometheusRules — Alerts ({ALERTS.length} rules)</div>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 11 }}>
              <thead>
                <tr style={{ borderBottom: "1px solid #e2e8f0" }}>
                  {["Alert name", "Expression", "Severity", "Team"].map(h => (
                    <th key={h} style={{ textAlign: "left", padding: "6px 8px", color: "#94a3b8", fontWeight: 700, fontSize: 10, textTransform: "uppercase" }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {ALERTS.map((a, i) => (
                  <tr key={i} style={{ borderBottom: "1px solid #f1f5f9" }}>
                    <td style={{ padding: "7px 8px", fontFamily: "monospace", fontWeight: 600, color: "#0f172a", fontSize: 10 }}>{a.name}</td>
                    <td style={{ padding: "7px 8px", fontFamily: "monospace", color: "#475569", fontSize: 10 }}>{a.expr}</td>
                    <td style={{ padding: "7px 8px" }}>
                      <span style={{ fontSize: 9, fontWeight: 700, padding: "2px 7px", borderRadius: 10, background: a.sev === "critical" ? "#fef2f2" : "#fffbeb", color: a.sev === "critical" ? "#dc2626" : "#d97706" }}>{a.sev}</span>
                    </td>
                    <td style={{ padding: "7px 8px", fontSize: 11, color: "#64748b" }}>{a.team}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ── Secrets ── */}
      {tab === "secrets" && (
        <div>
          <div style={{ background: "#f8fafc", borderRadius: 12, padding: 16, border: "1px solid #e2e8f0", marginBottom: 16 }}>
            <div style={{ fontSize: 12, fontWeight: 700, color: "#0f172a", marginBottom: 12 }}>External Secrets Operator flow</div>
            <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
              {["HashiCorp Vault / AWS SM", "→", "SecretStore (k8s CRD)", "→", "ExternalSecret (CRD)", "→", "Kubernetes Secret", "→", "Pod env var / mounted file"].map((item, i) => (
                item === "→"
                  ? <span key={i} style={{ color: "#94a3b8", fontSize: 18 }}>→</span>
                  : <span key={i} style={{ background: "#fff", border: "1.5px solid #e2e8f0", borderRadius: 8, padding: "6px 12px", fontSize: 11, fontWeight: 600, color: "#374151" }}>{item}</span>
              ))}
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            {[
              { name: "aegispay-db-secret",           keys: ["password"], refresh: "1h",  env: "Postgres password per service" },
              { name: "aegispay-redis-secret",        keys: ["password"], refresh: "1h",  env: "Redis auth password" },
              { name: "aegispay-kafka-secret",        keys: ["sasl-username", "sasl-password"], refresh: "1h", env: "Kafka SASL/PLAIN credentials" },
              { name: "aegispay-stripe-secret",       keys: ["secret-key", "webhook-secret"], refresh: "24h", env: "Stripe API key + webhook signing secret" },
              { name: "aegispay-ai-secret",           keys: ["anthropic-api-key", "openai-api-key"], refresh: "12h", env: "Claude + OpenAI API keys" },
              { name: "aegispay-smtp-secret",         keys: ["password"], refresh: "24h", env: "Gmail SMTP app password" },
              { name: "aegispay-slack-secret",        keys: ["webhook-url"], refresh: "24h", env: "Alertmanager → Slack webhook" },
              { name: "aegispay-clickhouse-secret",   keys: ["password"], refresh: "24h", env: "ClickHouse analytics DB password" },
              { name: "aegispay-fast2sms-secret",     keys: ["api-key"], refresh: "24h", env: "SMS gateway API key" },
            ].map(s => (
              <div key={s.name} style={{ background: "#fff", borderRadius: 10, padding: "12px 14px", border: "1px solid #e2e8f0" }}>
                <div style={{ fontFamily: "monospace", fontSize: 11, fontWeight: 700, color: "#0f172a", marginBottom: 6 }}>{s.name}</div>
                <div style={{ display: "flex", gap: 4, flexWrap: "wrap", marginBottom: 6 }}>
                  {s.keys.map(k => <span key={k} style={{ background: "#f1f5f9", borderRadius: 4, padding: "2px 6px", fontSize: 10, fontFamily: "monospace", color: "#475569" }}>{k}</span>)}
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <span style={{ fontSize: 11, color: "#64748b" }}>{s.env}</span>
                  <span style={{ fontSize: 10, background: "#f0f9ff", color: "#0369a1", borderRadius: 4, padding: "2px 6px", fontFamily: "monospace" }}>refresh: {s.refresh}</span>
                </div>
              </div>
            ))}
          </div>

          <div style={{ marginTop: 14, background: "#f0fdf4", borderRadius: 10, padding: "12px 14px", border: "1px solid #bbf7d0" }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "#166534", marginBottom: 6 }}>🔒 Security notes</div>
            <div style={{ fontSize: 11, color: "#374151", lineHeight: 1.7 }}>
              • No plaintext secrets in Git — all managed via External Secrets Operator<br/>
              • Alertmanager SMTP + Slack credentials are <strong>mounted as files</strong> (not env vars) — won't appear in <code>kubectl describe pod</code><br/>
              • Prod: HashiCorp Vault with k8s service account auth (no static tokens)<br/>
              • Dev/Staging: AWS Secrets Manager with IRSA (IAM Roles for Service Accounts)
            </div>
          </div>
        </div>
      )}

      {/* ── Environments ── */}
      {tab === "envs" && (() => {
        const env = ENVS.find(e => e.id === selectedEnv);
        return (
          <div>
            {/* ── Promotion flow diagram ─────────────────────────────────── */}
            <div style={{ background: "#f8fafc", borderRadius: 14, padding: "20px 20px 16px", border: "1px solid #e2e8f0", marginBottom: 20 }}>
              <div style={{ fontSize: 10, fontWeight: 700, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.1em", marginBottom: 16 }}>Promotion Flow</div>

              {/* Three environment nodes + connector arrows */}
              <div style={{ display: "grid", gridTemplateColumns: "1fr 80px 1fr 80px 1fr", alignItems: "start", gap: 0 }}>

                {/* ── Local ── */}
                {ENVS.map((e, idx) => {
                  const isSelected = selectedEnv === e.id;
                  const isLast = idx === ENVS.length - 1;
                  return [
                    // Environment card
                    <div
                      key={e.id}
                      onClick={() => { setSelectedEnv(e.id); setActiveEnvTab("integrations"); }}
                      style={{ background: "#fff", borderRadius: 12, border: `2px solid ${isSelected ? e.color : "#e2e8f0"}`, padding: "14px 16px", cursor: "pointer", transition: "all 0.18s", boxShadow: isSelected ? `0 2px 16px ${e.color}25` : "none" }}
                    >
                      {/* Header */}
                      <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", marginBottom: 10 }}>
                        <div>
                          <div style={{ fontSize: 22 }}>{e.icon}</div>
                          <div style={{ fontSize: 15, fontWeight: 700, color: e.color, marginTop: 4 }}>{e.name}</div>
                          <div style={{ fontSize: 10, color: "#94a3b8", marginTop: 1 }}>{e.subtitle}</div>
                        </div>
                        <div style={{ textAlign: "right" }}>
                          <div style={{ fontSize: 13, fontWeight: 700, color: "#0f172a", fontFamily: "monospace" }}>{e.cost}</div>
                          <div style={{ fontSize: 9, color: "#94a3b8", marginTop: 2 }}>est. / month</div>
                        </div>
                      </div>

                      {/* Feature badges */}
                      <div style={{ display: "flex", gap: 4, flexWrap: "wrap", marginBottom: 10 }}>
                        {[
                          { label: "ArgoCD",  on: e.argocd, bg: "#faf5ff", color: "#7e22ce", border: "#e9d5ff" },
                          { label: "HPA",     on: e.hpa,    bg: "#f0fdf4", color: "#166534", border: "#bbf7d0" },
                          { label: "TLS",     on: e.tls,    bg: "#f0f9ff", color: "#0369a1", border: "#bae6fd" },
                          { label: "PDB",     on: e.pdb,    bg: "#fff7ed", color: "#c2410c", border: "#fed7aa" },
                        ].map(b => (
                          <span key={b.label} style={{ fontSize: 9, fontWeight: 700, padding: "2px 7px", borderRadius: 10, background: b.on ? b.bg : "#f8fafc", color: b.on ? b.color : "#cbd5e1", border: `1px solid ${b.on ? b.border : "#e2e8f0"}` }}>
                            {b.label}
                          </span>
                        ))}
                      </div>

                      {/* Trigger pill */}
                      <div style={{ background: e.color + "10", border: `1px solid ${e.color}30`, borderRadius: 6, padding: "5px 8px" }}>
                        <div style={{ fontSize: 9, fontWeight: 700, color: e.color, textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 2 }}>Trigger</div>
                        <div style={{ fontSize: 10, color: "#374151", lineHeight: 1.4 }}>{e.trigger}</div>
                      </div>

                      {/* Replicas + infra line */}
                      <div style={{ marginTop: 8, display: "flex", gap: 8 }}>
                        <div style={{ fontSize: 10, color: "#64748b" }}>
                          <span style={{ fontWeight: 600 }}>{e.replicas}</span>
                        </div>
                        <span style={{ color: "#e2e8f0" }}>·</span>
                        <div style={{ fontSize: 10, color: "#64748b", flex: 1 }}>{e.infra}</div>
                      </div>
                    </div>,

                    // Arrow between environments
                    !isLast && (
                      <div key={`arrow-${idx}`} style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "flex-start", paddingTop: 36, gap: 4 }}>
                        {/* Gate badges stacked above the arrow */}
                        {idx === 0 && (
                          <div style={{ display: "flex", flexDirection: "column", gap: 3, marginBottom: 6, alignItems: "center" }}>
                            <span style={{ fontSize: 8, fontWeight: 700, background: "#ecfdf5", color: "#059669", border: "1px solid #bbf7d0", borderRadius: 4, padding: "2px 5px", whiteSpace: "nowrap" }}>push dev</span>
                            <span style={{ fontSize: 8, fontWeight: 700, background: "#eff6ff", color: "#1d4ed8", border: "1px solid #bfdbfe", borderRadius: 4, padding: "2px 5px", whiteSpace: "nowrap" }}>CI green</span>
                          </div>
                        )}
                        {idx === 1 && (
                          <div style={{ display: "flex", flexDirection: "column", gap: 3, marginBottom: 6, alignItems: "center" }}>
                            <span style={{ fontSize: 8, fontWeight: 700, background: "#eff6ff", color: "#1d4ed8", border: "1px solid #bfdbfe", borderRadius: 4, padding: "2px 5px", whiteSpace: "nowrap" }}>PR → main</span>
                            <span style={{ fontSize: 8, fontWeight: 700, background: "#eff6ff", color: "#1d4ed8", border: "1px solid #bfdbfe", borderRadius: 4, padding: "2px 5px", whiteSpace: "nowrap" }}>CI green</span>
                            <span style={{ fontSize: 8, fontWeight: 700, background: "#fefce8", color: "#a16207", border: "1px solid #fde68a", borderRadius: 4, padding: "2px 5px", whiteSpace: "nowrap" }}>⚑ approval</span>
                          </div>
                        )}
                        <svg width="40" height="20" viewBox="0 0 40 20">
                          <line x1="0" y1="10" x2="32" y2="10" stroke="#cbd5e1" strokeWidth="1.5" />
                          <polygon points="28,6 40,10 28,14" fill="#cbd5e1" />
                        </svg>
                      </div>
                    ),
                  ];
                })}
              </div>

              {/* Cost delta annotation */}
              <div style={{ marginTop: 14, display: "flex", gap: 10 }}>
                <div style={{ flex: 1, background: "#f0fdf4", borderRadius: 8, padding: "8px 12px", border: "1px solid #bbf7d0", textAlign: "center" }}>
                  <div style={{ fontSize: 10, fontWeight: 700, color: "#166534" }}>Dev saves ~90% vs Prod cost</div>
                  <div style={{ fontSize: 10, color: "#374151", marginTop: 2 }}>OpenRouter · Stripe sandbox · 1 replica · single-AZ DB</div>
                </div>
                <div style={{ flex: 1, background: "#fffbeb", borderRadius: 8, padding: "8px 12px", border: "1px solid #fde68a", textAlign: "center" }}>
                  <div style={{ fontSize: 10, fontWeight: 700, color: "#92400e" }}>Same Helm chart across all envs</div>
                  <div style={{ fontSize: 10, color: "#374151", marginTop: 2 }}>values-dev.yaml vs values-prod.yaml — only endpoints + replicas differ</div>
                </div>
              </div>
            </div>

            {/* ── Selected env detail ─────────────────────────────────────── */}
            {env && (
              <div style={{ background: "#fff", borderRadius: 14, border: `2px solid ${env.color}`, padding: 20, marginBottom: 20 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 16 }}>
                  <span style={{ fontSize: 28 }}>{env.icon}</span>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 18, fontWeight: 700, color: env.color }}>{env.name}</div>
                    <div style={{ fontSize: 12, color: "#64748b" }}>{env.desc}</div>
                  </div>
                  <div style={{ textAlign: "right" }}>
                    <div style={{ fontSize: 20, fontWeight: 700, color: "#0f172a", fontFamily: "monospace" }}>{env.cost}</div>
                    <div style={{ fontSize: 10, color: "#94a3b8" }}>{env.costNote}</div>
                  </div>
                </div>

                {/* Sub-tabs */}
                <div style={{ display: "flex", gap: 6, marginBottom: 16 }}>
                  {[
                    { id: "integrations", label: "🔌 Integrations" },
                    { id: "infra",        label: "☸️ Infra" },
                    { id: "gates",        label: "⚑ Promotion gates" },
                  ].map(t => (
                    <button key={t.id} onClick={() => setActiveEnvTab(t.id)} style={{ padding: "5px 12px", borderRadius: 7, border: `1.5px solid ${activeEnvTab === t.id ? env.color : "#e2e8f0"}`, background: activeEnvTab === t.id ? env.color + "15" : "#fff", color: activeEnvTab === t.id ? env.color : "#64748b", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                      {t.label}
                    </button>
                  ))}
                </div>

                {/* Integrations tab */}
                {activeEnvTab === "integrations" && (
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                    {env.integrations.map(intg => (
                      <div key={intg.name} style={{ background: "#f8fafc", borderRadius: 8, padding: "10px 12px", border: "1px solid #e2e8f0" }}>
                        <div style={{ fontSize: 10, fontWeight: 700, color: env.color, textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 4 }}>{intg.name}</div>
                        <div style={{ fontSize: 11, color: "#0f172a", fontWeight: 600, marginBottom: 3 }}>{intg.value}</div>
                        <div style={{ fontSize: 10, color: "#64748b", fontStyle: "italic" }}>{intg.cost}</div>
                      </div>
                    ))}
                  </div>
                )}

                {/* Infra tab */}
                {activeEnvTab === "infra" && (
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                    {[
                      { label: "Kubernetes",  value: env.infra },
                      { label: "Replicas",    value: env.replicas },
                      { label: "Secrets",     value: env.secrets },
                      { label: "Helm values", value: env.values },
                      { label: "ArgoCD sync", value: env.argocd ? (env.id === "prod" ? "Manual (approval gate)" : "Auto-sync on git change") : "Not applicable — Docker Compose" },
                      { label: "HPA",         value: env.hpa ? "CPU target 70%, min→max replicas per service" : "Disabled — fixed 1 replica" },
                      { label: "TLS",         value: env.tls ? "cert-manager + Let's Encrypt (auto-renewed)" : "None — localhost only" },
                      { label: "PDB",         value: env.pdb ? "minAvailable: 1 (prevents full eviction)" : "Disabled (1 replica = PDB meaningless)" },
                    ].map(row => (
                      <div key={row.label} style={{ background: "#f8fafc", borderRadius: 8, padding: "10px 12px", border: "1px solid #e2e8f0" }}>
                        <div style={{ fontSize: 10, fontWeight: 700, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 3 }}>{row.label}</div>
                        <div style={{ fontSize: 11, color: "#374151" }}>{row.value}</div>
                      </div>
                    ))}
                  </div>
                )}

                {/* Gates tab */}
                {activeEnvTab === "gates" && (
                  <div>
                    {env.gates.length === 0 ? (
                      <div style={{ padding: "16px", background: "#f8fafc", borderRadius: 8, fontSize: 12, color: "#94a3b8", textAlign: "center" }}>
                        No gates — local environment runs directly via <code>docker compose up</code>
                      </div>
                    ) : (
                      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                        {env.gates.map((gate, i) => (
                          <div key={i} style={{ display: "flex", gap: 10, padding: "10px 14px", background: "#f8fafc", borderRadius: 8, alignItems: "flex-start" }}>
                            <span style={{ width: 22, height: 22, borderRadius: "50%", background: env.color, color: "#fff", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 10, fontWeight: 700, flexShrink: 0 }}>{i + 1}</span>
                            <span style={{ fontSize: 12, color: "#374151", lineHeight: 1.5 }}>{gate}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}

            {/* ── Dev vs Prod comparison table ──────────────────────────── */}
            <div style={{ background: "#f8fafc", borderRadius: 14, padding: 16, border: "1px solid #e2e8f0" }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: "#0f172a", marginBottom: 12 }}>
                Dev vs Prod — what actually differs (and why)
              </div>
              <div style={{ overflowX: "auto" }}>
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 11 }}>
                  <thead>
                    <tr style={{ borderBottom: "2px solid #e2e8f0" }}>
                      {["Aspect", "Dev", "Prod", "Why it differs"].map(h => (
                        <th key={h} style={{ textAlign: "left", padding: "7px 10px", color: "#94a3b8", fontWeight: 700, fontSize: 10, textTransform: "uppercase", letterSpacing: "0.05em" }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {DEV_VS_PROD.map((row, i) => (
                      <tr key={i} style={{ borderBottom: "1px solid #f1f5f9" }}>
                        <td style={{ padding: "8px 10px", fontWeight: 700, color: "#374151", whiteSpace: "nowrap" }}>{row.aspect}</td>
                        <td style={{ padding: "8px 10px", color: "#0891b2", fontFamily: "monospace", fontSize: 10 }}>{row.dev}</td>
                        <td style={{ padding: "8px 10px", color: "#059669", fontFamily: "monospace", fontSize: 10 }}>{row.prod}</td>
                        <td style={{ padding: "8px 10px", color: "#64748b", fontSize: 10, fontStyle: "italic" }}>{row.why}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        );
      })()}

      <div style={{ marginTop: 16, fontSize: 10, color: "#cbd5e1", textAlign: "center" }}>AegisPay · docs/interactive/devops_pipeline.jsx</div>
    </div>
  );
}
