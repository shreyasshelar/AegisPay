import dynamic from 'next/dynamic'

const ObservabilityDemo = dynamic(
  () => import('../_components/ObservabilityDemo'),
  { ssr: false, loading: () => <div className="animate-pulse bg-gray-100 rounded-xl h-64" /> }
)

const TRACE_FLOW = [
  { step: 'Request', actor: 'API Gateway', detail: 'Micrometer traces every inbound request. Span ID + Trace ID injected into MDC for log correlation.' },
  { step: 'Kafka Event', actor: 'Transaction Service', detail: 'Outbox relay adds traceId to every Kafka message header. Consumers extract it and continue the trace.' },
  { step: 'DB Query', actor: 'Ledger Service', detail: 'p6spy logs SQL queries with durations. Slow queries (>100ms) emit a warning metric.' },
  { step: 'AI Call', actor: 'AI Platform', detail: 'Each RAG + LLM call logs retrieved doc IDs, prompt token count, latency, and provider used to ai_audit_log.' },
  { step: 'Saga End', actor: 'Orchestrator', detail: 'saga_latencies fact emitted to ClickHouse with all step durations for SLA reporting.' },
]

const CLICKHOUSE_TABLES = [
  {
    table: 'transaction_facts',
    ttl: '90 days',
    color: 'border-blue-200',
    columns: ['transaction_id', 'amount_cents', 'currency', 'status', 'payer_id', 'payee_id', 'created_at', 'completed_at'],
    use: 'Payment volume, revenue, P95 latency dashboards',
  },
  {
    table: 'risk_assessments',
    ttl: '180 days',
    color: 'border-red-200',
    columns: ['transaction_id', 'risk_score', 'decision', 'rule_hits', 'ai_explanation', 'assessed_at'],
    use: 'Fraud intelligence dashboard, rule efficacy analysis',
  },
  {
    table: 'saga_latencies',
    ttl: '30 days',
    color: 'border-amber-200',
    columns: ['saga_id', 'step_name', 'duration_ms', 'success', 'failure_reason', 'recorded_at'],
    use: 'SLA dashboard, bottleneck identification',
  },
  {
    table: 'reconciliation_breaks',
    ttl: '365 days',
    color: 'border-purple-200',
    columns: ['break_id', 'transaction_id', 'internal_amount', 'external_amount', 'delta_cents', 'detected_at'],
    use: 'Finance team reconciliation, regulatory audit trail',
  },
]

export default function ObservabilityPage() {
  return (
    <div className="max-w-4xl mx-auto space-y-12">
      <section>
        <h1 className="text-3xl font-bold text-gray-900 mb-4">Observability</h1>
        <p className="text-gray-600 leading-relaxed">
          AegisPay has two intentionally separate Grafana instances: one for infrastructure metrics
          (Prometheus) and one for business/payment metrics (ClickHouse). This separation means a
          database spike never drowns out a fraud alert, and business dashboards don&apos;t require
          Prometheus expertise to query.
        </p>
      </section>

      {/* Two-Grafana architecture */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Two-Grafana Architecture</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {[
            {
              title: 'kube-prometheus-stack',
              port: ':3000 (internal)',
              source: 'Prometheus',
              color: 'border-orange-200 bg-orange-50',
              badge: 'bg-orange-100 text-orange-700',
              items: ['JVM heap, GC, threads per service', 'Kafka consumer group lag', 'K8s pod restarts, OOM kills', 'HTTP error rates & latencies', 'Circuit breaker open/closed state'],
            },
            {
              title: 'AegisPay Grafana',
              port: ':3100 (external)',
              source: 'ClickHouse',
              color: 'border-blue-200 bg-blue-50',
              badge: 'bg-blue-100 text-blue-700',
              items: ['Payment volume & revenue trends', 'Fraud decision rates', 'Saga SLA & latency percentiles', 'Reconciliation break count', 'Data pipeline flush lag'],
            },
          ].map(({ title, port, source, color, badge, items }) => (
            <div key={title} className={`rounded-xl border p-5 ${color}`}>
              <div className="flex items-start justify-between mb-3">
                <div>
                  <p className="font-semibold text-gray-900">{title}</p>
                  <p className="text-xs font-mono text-gray-500">{port}</p>
                </div>
                <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${badge}`}>
                  {source}
                </span>
              </div>
              <ul className="space-y-1.5">
                {items.map(i => (
                  <li key={i} className="flex items-start gap-2 text-sm text-gray-700">
                    <span className="mt-1.5 w-1 h-1 rounded-full bg-current shrink-0 opacity-50" />
                    {i}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </section>

      {/* Alert rules + dashboards interactive */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-2">Alert Rules & Dashboards</h2>
        <p className="text-gray-600 mb-4">
          Six PrometheusRules fire pages when critical thresholds are breached. The Dashboards tab
          shows what each Grafana board covers and which data source powers it.
        </p>
        <ObservabilityDemo />
      </section>

      {/* ClickHouse schema */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">ClickHouse Fact Tables</h2>
        <p className="text-gray-600 mb-4">
          The Data Pipeline service consumes Kafka events and writes to four ClickHouse tables using a
          5-second batch flush (configurable). All tables use{' '}
          <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded font-mono">MergeTree</code> engine
          with TTL-based data retention.
        </p>
        <div className="space-y-4">
          {CLICKHOUSE_TABLES.map(({ table, ttl, color, columns, use }) => (
            <div key={table} className={`rounded-xl border ${color} bg-white overflow-hidden`}>
              <div className="flex items-center justify-between px-5 py-3 border-b border-inherit">
                <code className="text-sm font-mono font-semibold text-gray-900">{table}</code>
                <span className="text-xs text-gray-400">TTL: {ttl}</span>
              </div>
              <div className="px-5 py-4 space-y-3">
                <div className="flex flex-wrap gap-1.5">
                  {columns.map(col => (
                    <span key={col} className="rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-mono text-gray-600">
                      {col}
                    </span>
                  ))}
                </div>
                <p className="text-sm text-gray-500">{use}</p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Distributed tracing */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Distributed Tracing & Log Correlation</h2>
        <p className="text-gray-600 mb-4">
          Every request generates a traceId that propagates through Kafka headers, HTTP headers, and MDC
          log context — so a single grep on the traceId returns logs from every service that touched the request.
        </p>
        <div className="rounded-xl border border-gray-100 bg-white divide-y divide-gray-50">
          {TRACE_FLOW.map(({ step, actor, detail }, i) => (
            <div key={step} className="flex gap-4 px-5 py-4">
              <div className="flex flex-col items-center gap-1 shrink-0">
                <div className="w-7 h-7 rounded-full bg-blue-100 text-blue-700 text-xs font-bold flex items-center justify-center">
                  {i + 1}
                </div>
                {i < TRACE_FLOW.length - 1 && (
                  <div className="w-px flex-1 bg-blue-100 min-h-[16px]" />
                )}
              </div>
              <div className="min-w-0 pb-2">
                <div className="flex items-center gap-2 mb-1">
                  <p className="font-semibold text-gray-900 text-sm">{step}</p>
                  <span className="text-xs text-gray-400 bg-gray-100 rounded-full px-2 py-0.5">{actor}</span>
                </div>
                <p className="text-sm text-gray-600">{detail}</p>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
