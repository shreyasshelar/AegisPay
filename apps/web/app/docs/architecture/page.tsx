// FILE: apps/web/app/docs/architecture/page.tsx
import dynamic from 'next/dynamic'

const TechStackDemo = dynamic(
  () => import('../_components/TechStackDemo'),
  { ssr: false, loading: () => <div className="animate-pulse bg-gray-100 rounded-xl h-64" /> }
)

const ServiceTopologyDemo = dynamic(
  () => import('../_components/ServiceTopologyDemo'),
  { ssr: false, loading: () => <div className="animate-pulse bg-gray-100 rounded-xl h-48" /> }
)

const AegisPaySystemMap = dynamic(
  () => import('../_components/AegisPaySystemMap'),
  { ssr: false, loading: () => <div className="animate-pulse bg-gray-100 rounded-xl h-64" /> }
)

const KAFKA_TOPICS = `transaction.initiated
    ↓
balance.reserved
    ↓
risk.assessed
    ↓
payment.completed
    ↓
ledger.committed
    ↓
transaction.completed  ──  transaction.failed

Cross-cutting:
  user.registered              (User Svc → Notification Svc)
  risk.assessment.completed    (Risk Engine → DataPipeline)`

const SERVICE_PORTS = [
  { service: 'API Gateway', port: '8080', tech: 'Spring Cloud Gateway', db: 'Redis' },
  { service: 'User Service', port: '8081', tech: 'Spring Boot 3.2', db: 'PostgreSQL' },
  { service: 'Transaction Service', port: '8082', tech: 'Spring Boot 3.2', db: 'PostgreSQL + MongoDB' },
  { service: 'Ledger Service', port: '8083', tech: 'Spring Boot 3.2', db: 'PostgreSQL' },
  { service: 'Payment Orchestrator', port: '8084', tech: 'Spring Boot 3.2', db: 'PostgreSQL' },
  { service: 'Risk Engine', port: '8085', tech: 'Spring Boot 3.2', db: 'PostgreSQL (pgvector)' },
  { service: 'Notification Service', port: '8086', tech: 'Spring Boot 3.2', db: 'MongoDB' },
  { service: 'Reconciliation Service', port: '8087', tech: 'Spring Boot 3.2', db: 'PostgreSQL + ClickHouse' },
  { service: 'Data Pipeline', port: '8089', tech: 'Spring Boot 3.2 + Batch', db: 'ClickHouse' },
  { service: 'AI Platform', port: '8091', tech: 'Spring Boot 3.2', db: 'PostgreSQL (pgvector)' },
  { service: 'Web Frontend', port: '3000', tech: 'Next.js 14', db: '—' },
  { service: 'Keycloak', port: '8180', tech: 'Keycloak 24', db: 'PostgreSQL' },
  { service: 'Kafka', port: '9094', tech: 'Apache Kafka 3.6', db: '—' },
  { service: 'Kafka UI', port: '8090', tech: 'Kafka UI', db: '—' },
  { service: 'ClickHouse', port: '8123', tech: 'ClickHouse 24.4', db: '—' },
  { service: 'Grafana (Analytics)', port: '3100', tech: 'Grafana 10.4', db: 'ClickHouse' },
  { service: 'PostgreSQL', port: '5433', tech: 'PostgreSQL 16', db: '—' },
  { service: 'Redis', port: '6379', tech: 'Redis 7', db: '—' },
  { service: 'MongoDB', port: '27017', tech: 'MongoDB 7', db: '—' },
]

export default function ArchitecturePage() {
  return (
    <div className="max-w-4xl mx-auto space-y-12">
      <section>
        <h1 className="text-3xl font-bold text-gray-900 mb-4">System Architecture</h1>
        <p className="text-gray-600 leading-relaxed">
          AegisPay is built as 10 Spring Boot microservices connected by Apache Kafka, with a Next.js 14
          frontend. Every design decision is driven by one guarantee: money must never be lost or doubled,
          even when any single component fails.
        </p>
      </section>

      {/* Service Topology */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-2">Service Topology</h2>
        <p className="text-gray-600 mb-4">
          Click any service to explore its role, data store, and Kafka topic participation. All services
          communicate asynchronously through Kafka; synchronous HTTP is only used within the Gateway → Service hop.
        </p>
        <ServiceTopologyDemo />
      </section>

      {/* Tech Stack Demo */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Tech Stack</h2>
        <TechStackDemo />
      </section>

      {/* Interactive System Map */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Interactive System Map</h2>
        <p className="text-gray-600 mb-4">
          Explore all 20 components across four tiers: client, gateway, backend services, and data
          infrastructure. Switch between the services view, Kafka event flow, and data pipeline to
          understand how each component connects.
        </p>
        <AegisPaySystemMap />
      </section>

      {/* Architecture tiers */}
      <section className="space-y-6">
        <h2 className="text-2xl font-semibold text-gray-900">Architecture Tiers</h2>

        <div className="space-y-4">
          {[
            {
              tier: 'Client Tier',
              color: 'bg-blue-50 border-blue-200',
              desc: 'Next.js 14 App Router web app with React Server Components for fast initial loads. Mobile apps via SwiftUI (iOS) and Jetpack Compose (Android) communicate with the API Gateway over HTTPS.',
            },
            {
              tier: 'Auth & Gateway',
              color: 'bg-purple-50 border-purple-200',
              desc: 'Spring Cloud Gateway sits at the edge. Every request passes through JWT validation (Keycloak 24), Redis-backed sliding-window rate limiting (100 req/60s), idempotency key deduplication, and Resilience4j circuit breakers before being routed to backend services.',
            },
            {
              tier: 'Backend Services',
              color: 'bg-green-50 border-green-200',
              desc: 'Ten Spring Boot 3.2 microservices, each with its own PostgreSQL schema (database-per-service). Services communicate asynchronously via Kafka. Shared libraries (common-events, common-kafka, common-security, common-observability) enforce consistent patterns.',
            },
            {
              tier: 'Data & Analytics',
              color: 'bg-amber-50 border-amber-200',
              desc: 'Kafka events flow into ClickHouse via the Data Pipeline service (5-second batch flush). ClickHouse powers the AegisPay Grafana dashboards for payment ops, fraud intelligence, and SLA metrics. Spring Batch runs nightly reconciliation against Stripe.',
            },
          ].map(({ tier, color, desc }) => (
            <div key={tier} className={`rounded-xl border p-5 ${color}`}>
              <h3 className="font-semibold text-gray-900 mb-2">{tier}</h3>
              <p className="text-sm text-gray-600">{desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Kafka topics */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Kafka Topic Flow</h2>
        <pre className="bg-gray-900 text-gray-100 rounded-xl p-5 font-mono text-sm overflow-x-auto">
          {KAFKA_TOPICS}
        </pre>
      </section>

      {/* Port reference */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Port Reference</h2>
        <div className="rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                {['Service', 'Port', 'Technology', 'Data Store'].map((h) => (
                  <th key={h} className="text-left px-4 py-3 text-gray-500 font-medium whitespace-nowrap">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50 bg-white">
              {SERVICE_PORTS.map((row) => (
                <tr key={row.port} className="hover:bg-gray-50">
                  <td className="px-4 py-2.5 font-medium text-gray-800">{row.service}</td>
                  <td className="px-4 py-2.5 font-mono text-blue-600 font-bold">{row.port}</td>
                  <td className="px-4 py-2.5 text-gray-600">{row.tech}</td>
                  <td className="px-4 py-2.5 text-gray-500 text-xs">{row.db}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
