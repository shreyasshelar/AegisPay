// FILE: apps/web/app/docs/services/page.tsx

const SERVICES = [
  {
    name: 'API Gateway',
    port: '8080',
    tech: 'Spring Cloud Gateway',
    purpose: 'Edge routing, JWT validation, rate limiting, circuit breaking, idempotency',
    db: 'Redis',
    patterns: 'Circuit Breaker, Rate Limit, Idempotency',
    color: 'text-purple-600',
  },
  {
    name: 'User Service',
    port: '8081',
    tech: 'Spring Boot 3.2',
    purpose: 'User registration, profile management, Keycloak integration, KYC status',
    db: 'PostgreSQL',
    patterns: 'Outbox, Domain Events',
    color: 'text-blue-600',
  },
  {
    name: 'Transaction Service',
    port: '8082',
    tech: 'Spring Boot 3.2',
    purpose: 'Transaction lifecycle, state machine management, CQRS read model',
    db: 'PostgreSQL + MongoDB',
    patterns: 'Outbox, CQRS, Idempotency, State Machine',
    color: 'text-green-600',
  },
  {
    name: 'Ledger Service',
    port: '8083',
    tech: 'Spring Boot 3.2',
    purpose: 'Double-entry bookkeeping, balance reservation, fund commit/release',
    db: 'PostgreSQL',
    patterns: 'Double-entry Ledger, Optimistic Lock, FOR UPDATE',
    color: 'text-green-600',
  },
  {
    name: 'Payment Orchestrator',
    port: '8084',
    tech: 'Spring Boot 3.2',
    purpose: 'Saga coordination, Stripe integration, compensating transactions',
    db: 'PostgreSQL',
    patterns: 'Saga Orchestration, Compensation, Stripe Idempotency',
    color: 'text-green-600',
  },
  {
    name: 'Risk Engine',
    port: '8085',
    tech: 'Spring Boot 3.2',
    purpose: 'Rule evaluation (50+ rules), AI RAG fraud scoring, ALLOW/REVIEW/BLOCK decisions',
    db: 'PostgreSQL (pgvector)',
    patterns: 'RAG, Rule Engine, Vector Search',
    color: 'text-red-600',
  },
  {
    name: 'Notification Service',
    port: '8086',
    tech: 'Spring Boot 3.2',
    purpose: 'STOMP WebSocket, SendGrid email, Twilio SMS, Slack webhooks',
    db: 'MongoDB',
    patterns: 'Fan-out, Multi-channel, STOMP WebSocket',
    color: 'text-blue-600',
  },
  {
    name: 'Reconciliation Service',
    port: '8087',
    tech: 'Spring Boot 3.2 + Batch',
    purpose: 'Nightly Stripe reconciliation, break detection, ClickHouse reporting',
    db: 'PostgreSQL + ClickHouse',
    patterns: 'Spring Batch, Reconciliation, OLAP Sink',
    color: 'text-amber-600',
  },
  {
    name: 'Data Pipeline',
    port: '8089',
    tech: 'Spring Boot 3.2',
    purpose: 'Kafka → ClickHouse sink (5s batch), Kafka Streams aggregations',
    db: 'ClickHouse',
    patterns: 'Kafka Streams, Batch Sink, OLAP',
    color: 'text-amber-600',
  },
  {
    name: 'AI Platform',
    port: '8091',
    tech: 'Spring Boot 3.2',
    purpose: 'RAG pipeline, incident triage agent, KYC OCR, provider fallback chain',
    db: 'PostgreSQL (pgvector)',
    patterns: 'RAG, ReAct Agent, Tool Use, Provider Fallback',
    color: 'text-rose-600',
  },
]

const KAFKA_TOPICS = [
  {
    topic: 'transaction.initiated',
    producer: 'Outbox Relay (Tx Svc)',
    consumers: 'Ledger Svc, Risk Engine, DataPipeline',
    payload: 'transactionId, amount, payerId, payeeId, currency',
  },
  {
    topic: 'balance.reserved',
    producer: 'Ledger Service',
    consumers: 'Payment Orchestrator, DataPipeline',
    payload: 'transactionId, reservedAmount, payerBalance',
  },
  {
    topic: 'risk.assessed',
    producer: 'Risk Engine',
    consumers: 'Payment Orchestrator, DataPipeline',
    payload: 'transactionId, decision (ALLOW/REVIEW/BLOCK), riskScore, explanation',
  },
  {
    topic: 'payment.completed',
    producer: 'Payment Orchestrator',
    consumers: 'Ledger Service, DataPipeline',
    payload: 'transactionId, stripePaymentIntentId, amount',
  },
  {
    topic: 'ledger.committed',
    producer: 'Ledger Service',
    consumers: 'Transaction Svc, Notification Svc, DataPipeline',
    payload: 'transactionId, debitEntry, creditEntry',
  },
  {
    topic: 'transaction.completed',
    producer: 'Transaction Service',
    consumers: 'Notification Svc, DataPipeline',
    payload: 'transactionId, finalStatus, completedAt',
  },
  {
    topic: 'transaction.failed',
    producer: 'Transaction Service / Orchestrator',
    consumers: 'Notification Svc, DataPipeline',
    payload: 'transactionId, failureCode, aiExplanation',
  },
  {
    topic: 'user.registered',
    producer: 'User Service',
    consumers: 'Notification Svc',
    payload: 'userId, email, name, registeredAt',
  },
  {
    topic: 'risk.assessment.completed',
    producer: 'Risk Engine',
    consumers: 'DataPipeline',
    payload: 'Full risk assessment with rule results for analytics',
  },
]

export default function ServicesPage() {
  return (
    <div className="max-w-5xl mx-auto space-y-12">
      <section>
        <h1 className="text-3xl font-bold text-gray-900 mb-4">Service Reference</h1>
        <p className="text-gray-600 leading-relaxed">
          AegisPay comprises 10 Spring Boot microservices. Each service owns its data store
          (database-per-service pattern) and communicates asynchronously via Kafka.
        </p>
      </section>

      {/* Service table */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">All Services</h2>
        <div className="rounded-xl border border-gray-100 shadow-sm overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                {['Service', 'Port', 'Tech', 'Purpose', 'Data Store', 'Key Patterns'].map((h) => (
                  <th key={h} className="text-left px-4 py-3 text-gray-500 font-medium whitespace-nowrap">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50 bg-white">
              {SERVICES.map((svc) => (
                <tr key={svc.name} className="hover:bg-gray-50">
                  <td className={`px-4 py-3 font-bold whitespace-nowrap ${svc.color}`}>{svc.name}</td>
                  <td className="px-4 py-3 font-mono text-blue-600 font-bold whitespace-nowrap">{svc.port}</td>
                  <td className="px-4 py-3 text-gray-600 whitespace-nowrap text-xs">{svc.tech}</td>
                  <td className="px-4 py-3 text-gray-600 text-xs max-w-xs">{svc.purpose}</td>
                  <td className="px-4 py-3 text-gray-500 text-xs whitespace-nowrap">{svc.db}</td>
                  <td className="px-4 py-3 text-xs">
                    <div className="flex flex-wrap gap-1">
                      {svc.patterns.split(', ').map((p) => (
                        <span key={p} className="bg-gray-100 text-gray-600 px-1.5 py-0.5 rounded text-xs">
                          {p}
                        </span>
                      ))}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Kafka topic reference */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Kafka Topic Reference</h2>
        <div className="space-y-3">
          {KAFKA_TOPICS.map((topic) => (
            <div key={topic.topic} className="rounded-xl border border-gray-100 bg-white p-4">
              <div className="flex items-center gap-2 mb-2 flex-wrap">
                <code className="text-sm font-mono font-bold text-amber-700 bg-amber-50 px-2 py-0.5 rounded">
                  {topic.topic}
                </code>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
                <div>
                  <p className="text-gray-400 font-medium mb-0.5">Producer</p>
                  <p className="text-gray-700">{topic.producer}</p>
                </div>
                <div>
                  <p className="text-gray-400 font-medium mb-0.5">Consumers</p>
                  <p className="text-gray-700">{topic.consumers}</p>
                </div>
                <div>
                  <p className="text-gray-400 font-medium mb-0.5">Key Payload</p>
                  <p className="text-gray-500">{topic.payload}</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Shared libraries */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Shared Libraries</h2>
        <div className="rounded-xl border border-gray-100 bg-white divide-y divide-gray-50">
          {[
            { lib: 'common-domain', desc: 'Shared domain model classes, value objects, AggregateRoot base' },
            { lib: 'common-events', desc: 'Kafka event DTOs: TransactionInitiatedEvent, RiskAssessedEvent, etc.' },
            { lib: 'common-kafka', desc: 'KafkaTemplate wrappers, idempotent consumer base class, DLQ handler' },
            { lib: 'common-security', desc: 'JWT filter, StompAuthChannelInterceptor, role-based access control' },
            { lib: 'common-observability', desc: 'Micrometer custom meters, structured logging, trace propagation' },
          ].map(({ lib, desc }) => (
            <div key={lib} className="flex gap-4 px-5 py-3">
              <code className="text-blue-600 font-mono text-sm shrink-0 w-48">{lib}</code>
              <p className="text-sm text-gray-600">{desc}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
