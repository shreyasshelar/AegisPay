// FILE: apps/web/app/docs/patterns/page.tsx
import dynamic from 'next/dynamic'

const OutboxPatternDemo = dynamic(
  () => import('../_components/OutboxPatternDemo'),
  { ssr: false, loading: () => <div className="animate-pulse bg-gray-100 rounded-xl h-64" /> }
)

const LedgerExplorerDemo = dynamic(
  () => import('../_components/LedgerExplorerDemo'),
  { ssr: false, loading: () => <div className="animate-pulse bg-gray-100 rounded-xl h-64" /> }
)

const CQRS_TABLE = [
  { concern: 'Write path', store: 'PostgreSQL', pattern: 'INSERT/UPDATE via Transaction Service', reason: 'ACID guarantees, Flyway migrations' },
  { concern: 'Read path', store: 'MongoDB', pattern: 'Projection updated by Kafka event consumer', reason: 'Denormalised, fast pagination, no JOINs' },
  { concern: 'Sync mechanism', store: '—', pattern: 'Kafka consumer (transaction.completed / .failed)', reason: 'Same events that drive state machine update read model' },
  { concern: 'Consistency', store: '—', pattern: 'Eventually consistent (typically <100ms lag)', reason: 'Acceptable for read dashboards — write truth is Postgres' },
]

export default function PatternsPage() {
  return (
    <div className="max-w-4xl mx-auto space-y-16">
      <section>
        <h1 className="text-3xl font-bold text-gray-900 mb-4">Core Patterns</h1>
        <p className="text-gray-600 leading-relaxed">
          Six patterns work together to ensure correctness, durability, and idempotency across all
          payment flows.
        </p>
      </section>

      {/* Outbox */}
      <section className="space-y-4">
        <div>
          <h2 className="text-2xl font-semibold text-gray-900">Outbox Pattern</h2>
          <p className="text-gray-600 mt-2">
            The Outbox Pattern is the foundation of event durability. Instead of writing to the
            database and then publishing to Kafka (two separate operations that can partially fail),
            the Transaction Service writes <em>both</em> the domain record and the outbox event in a
            single database transaction. A separate Outbox Relay process polls the outbox table and
            publishes pending events to Kafka.
          </p>
        </div>
        <OutboxPatternDemo />
      </section>

      {/* Ledger */}
      <section className="space-y-4">
        <div>
          <h2 className="text-2xl font-semibold text-gray-900">Double-Entry Ledger</h2>
          <p className="text-gray-600 mt-2">
            Every money movement creates two ledger entries: a DEBIT (money leaves) and a CREDIT
            (money arrives). The mathematical invariant <code className="bg-gray-100 px-1 rounded">SUM(all_entries) = 0</code>{' '}
            is enforced at the database level. This makes it impossible to create or destroy money
            — any discrepancy is immediately detectable.
          </p>
        </div>
        <LedgerExplorerDemo />
      </section>

      {/* CQRS */}
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-gray-900">CQRS</h2>
        <p className="text-gray-600">
          Command Query Responsibility Segregation separates write operations (commands) from read
          operations (queries). In AegisPay, the Transaction Service writes to PostgreSQL for ACID
          guarantees but serves read queries from MongoDB, which holds denormalised projections updated
          by Kafka events.
        </p>
        <div className="rounded-xl border border-gray-100 shadow-sm overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                {['Concern', 'Data Store', 'Mechanism', 'Reason'].map((h) => (
                  <th key={h} className="text-left px-4 py-3 text-gray-500 font-medium whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50 bg-white">
              {CQRS_TABLE.map((row, i) => (
                <tr key={i} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-800">{row.concern}</td>
                  <td className="px-4 py-3 font-mono text-blue-600 text-xs">{row.store}</td>
                  <td className="px-4 py-3 text-gray-600">{row.pattern}</td>
                  <td className="px-4 py-3 text-gray-500 text-xs">{row.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Idempotency */}
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-gray-900">Idempotency</h2>
        <p className="text-gray-600">
          Kafka delivers messages at-least-once. Every consumer applies an idempotency check before
          processing to achieve exactly-once semantics. Three layers work together:
        </p>
        <div className="rounded-xl border border-gray-100 bg-white divide-y divide-gray-50">
          {[
            {
              layer: 'API Gateway',
              mechanism: 'Idempotency-Key header → 24h Redis key → return cached response for duplicates',
              color: 'text-purple-600',
            },
            {
              layer: 'Database',
              mechanism: 'UNIQUE constraints on transaction IDs and outbox event IDs prevent duplicate inserts',
              color: 'text-blue-600',
            },
            {
              layer: 'Kafka Consumers',
              mechanism: 'processed_events table: SELECT before process → skip if already handled',
              color: 'text-green-600',
            },
            {
              layer: 'Stripe SDK',
              mechanism: 'idempotencyKey = sagaId ensures duplicate Stripe calls never charge twice',
              color: 'text-amber-600',
            },
          ].map(({ layer, mechanism, color }) => (
            <div key={layer} className="flex gap-4 px-5 py-4">
              <span className={`shrink-0 font-semibold text-sm ${color} w-36`}>{layer}</span>
              <p className="text-sm text-gray-600">{mechanism}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Circuit Breaker */}
      <section className="space-y-4">
        <h2 className="text-2xl font-semibold text-gray-900">Circuit Breaker</h2>
        <p className="text-gray-600">
          Resilience4j circuit breakers at the API Gateway prevent cascade failures. When a downstream
          service is unhealthy, the circuit opens and returns a structured error immediately rather than
          accumulating timeouts. Three states: CLOSED (normal), OPEN (failing fast), HALF_OPEN (probing
          recovery).
        </p>
        <pre className="bg-gray-900 text-gray-100 rounded-xl p-5 font-mono text-sm overflow-x-auto">
{`@CircuitBreaker(name = "transaction-service", fallbackMethod = "fallback")
public ResponseEntity<?> routeToTransactionService(ServerWebExchange exchange) {
  // normal routing
}

public ResponseEntity<?> fallback(Throwable t) {
  return ResponseEntity.status(503).body(new ErrorResponse(
    "SERVICE_UNAVAILABLE",
    "Transaction service is temporarily unavailable. Try again shortly."
  ));
}`}
        </pre>
      </section>
    </div>
  )
}
