// FILE: apps/web/app/docs/infrastructure/page.tsx
import dynamic from 'next/dynamic'

const DevOpsPipelineDemo = dynamic(
  () => import('../_components/DevOpsPipelineDemo'),
  { ssr: false, loading: () => <div className="animate-pulse bg-gray-100 rounded-xl h-64" /> }
)

const K8S_RESOURCES = [
  { resource: 'HorizontalPodAutoscaler', description: 'Scale services 2→10 replicas based on CPU (70%) and custom Kafka lag metrics' },
  { resource: 'PodDisruptionBudget', description: 'Minimum 1 replica always available during rolling updates' },
  { resource: 'NetworkPolicy', description: 'Deny-all default; explicit allow rules per service-pair' },
  { resource: 'ResourceQuotas', description: 'Per-namespace limits: CPU 4 cores, Memory 8Gi per service' },
  { resource: 'Liveness / Readiness Probes', description: '/actuator/health endpoints — unhealthy pods auto-evicted' },
  { resource: 'ConfigMaps', description: 'Non-secret config: Kafka brokers, service URLs, feature flags' },
  { resource: 'ExternalSecrets', description: 'Synced from HashiCorp Vault via External Secrets Operator' },
]

const GRAFANA_DASHBOARDS = [
  { name: 'Payment Operations', source: 'ClickHouse', panels: 'Transaction volume, success rate, p50/p95/p99 latency' },
  { name: 'Fraud Intelligence', source: 'ClickHouse', panels: 'Risk score distribution, block rate, top flagged rules' },
  { name: 'SLA & Latency', source: 'ClickHouse', panels: 'Saga step latencies, timeout rate, DLQ depth' },
  { name: 'Reconciliation', source: 'ClickHouse', panels: 'Break count, matched/unmatched, Stripe delta' },
  { name: 'JVM & K8s', source: 'Prometheus', panels: 'Heap, GC, thread count, pod CPU/mem, HPA events' },
  { name: 'Kafka Lag', source: 'Prometheus', panels: 'Consumer group lag per topic, partition offset drift' },
]

export default function InfrastructurePage() {
  return (
    <div className="max-w-4xl mx-auto space-y-12">
      <section>
        <h1 className="text-3xl font-bold text-gray-900 mb-4">Infrastructure &amp; DevOps</h1>
        <p className="text-gray-600 leading-relaxed">
          AegisPay runs on Kubernetes (k3s for dev, production cluster for prod) with full GitOps via
          Argo CD. The CI/CD pipeline enforces security scanning on every build. Secrets never touch
          plaintext — all secrets flow through HashiCorp Vault via External Secrets Operator.
        </p>
      </section>

      {/* DevOps demo */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">CI/CD &amp; Operations</h2>
        <DevOpsPipelineDemo />
      </section>

      {/* Kubernetes */}
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-gray-900">Kubernetes Resources</h2>
        <p className="text-gray-600">
          All services run in the <code className="bg-gray-100 px-1 rounded">aegispay</code> namespace
          with Helm-managed deployments. The Helm chart lives at{' '}
          <code className="bg-gray-100 px-1 rounded">infra/helm/aegispay/</code>.
        </p>
        <div className="rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                {['Resource', 'Purpose'].map((h) => (
                  <th key={h} className="text-left px-4 py-3 text-gray-500 font-medium">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50 bg-white">
              {K8S_RESOURCES.map((row) => (
                <tr key={row.resource} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-sm text-blue-600 font-medium whitespace-nowrap">{row.resource}</td>
                  <td className="px-4 py-3 text-gray-600">{row.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Observability */}
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-gray-900">Observability</h2>
        <p className="text-gray-600">
          Two intentionally separate Grafana instances provide different observability lenses:
          kube-prometheus-stack for infrastructure metrics, and AegisPay Grafana (port 3100) for
          business/payment metrics sourced from ClickHouse.
        </p>
        <div className="rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                {['Dashboard', 'Source', 'Key Panels'].map((h) => (
                  <th key={h} className="text-left px-4 py-3 text-gray-500 font-medium">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50 bg-white">
              {GRAFANA_DASHBOARDS.map((row) => (
                <tr key={row.name} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-800">{row.name}</td>
                  <td className="px-4 py-3">
                    <span className={`text-xs px-2 py-0.5 rounded font-medium ${
                      row.source === 'ClickHouse'
                        ? 'bg-amber-100 text-amber-700'
                        : 'bg-orange-100 text-orange-700'
                    }`}>
                      {row.source}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-500 text-xs">{row.panels}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Secrets */}
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-gray-900">Secrets Management</h2>
        <p className="text-gray-600">
          Zero plaintext secrets in code or ConfigMaps. All secrets flow through a two-layer system:
        </p>
        <div className="rounded-xl border border-gray-100 bg-white divide-y divide-gray-50">
          {[
            {
              step: '1. HashiCorp Vault',
              desc: 'Central secret store. Dev uses in-cluster Vault (k3s). Prod uses dedicated Vault cluster with audit logging.',
              badge: 'Vault',
              badgeColor: 'bg-yellow-100 text-yellow-800',
            },
            {
              step: '2. External Secrets Operator',
              desc: 'ESO syncs Vault secrets into Kubernetes Secrets on a configurable schedule (30s refresh). Pods consume K8s Secrets as env vars.',
              badge: 'ESO',
              badgeColor: 'bg-blue-100 text-blue-700',
            },
            {
              step: '3. Zero in Git',
              desc: 'GitHub scanning + Trivy secret detection blocks any commit containing credentials. .gitignore enforces .env exclusion.',
              badge: 'GitGuard',
              badgeColor: 'bg-red-100 text-red-700',
            },
          ].map(({ step, desc, badge, badgeColor }) => (
            <div key={step} className="flex gap-4 px-5 py-4">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <p className="font-semibold text-gray-900 text-sm">{step}</p>
                  <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${badgeColor}`}>{badge}</span>
                </div>
                <p className="text-sm text-gray-600">{desc}</p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Local dev */}
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-gray-900">Local Development</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="rounded-xl border border-gray-100 p-5">
            <p className="font-semibold text-gray-900 mb-2">macOS / Linux</p>
            <pre className="bg-gray-900 text-gray-100 rounded-lg p-3 font-mono text-xs">
              {`docker compose up -d\n./start-local.sh`}
            </pre>
            <p className="text-xs text-gray-400 mt-2">Auto-detects Maven, waits for all services</p>
          </div>
          <div className="rounded-xl border border-gray-100 p-5">
            <p className="font-semibold text-gray-900 mb-2">Windows</p>
            <pre className="bg-gray-900 text-gray-100 rounded-lg p-3 font-mono text-xs">
              {`docker compose up -d\nstart-aegispay.bat`}
            </pre>
            <p className="text-xs text-gray-400 mt-2">Bootstrap script for Windows environments</p>
          </div>
        </div>
      </section>
    </div>
  )
}
