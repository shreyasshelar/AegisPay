'use client'

import { useState } from 'react'

type AlertRule = {
  name: string
  expr: string
  severity: 'critical' | 'warning' | 'info'
  summary: string
  action: string
}

type Dashboard = {
  id: string
  title: string
  source: string
  panels: string[]
  color: string
}

const ALERT_RULES: AlertRule[] = [
  {
    name: 'SagaTimeoutRateHigh',
    severity: 'critical',
    expr: 'rate(saga_timeout_total[5m]) > 0.1',
    summary: 'More than 10% of orchestrated payments are timing out — Stripe or a downstream service is likely degraded.',
    action: 'Check Orchestrator logs → Payment Gateway circuit breaker state → run AI Triage on payment-orchestrator',
  },
  {
    name: 'DlqDepthNonZero',
    severity: 'critical',
    expr: 'kafka_consumer_group_lag{topic=~".*.dlq"} > 0',
    summary: 'A Dead Letter Queue has messages. An event failed processing and was moved to DLQ — data may be in an inconsistent state.',
    action: 'Read DLQ message headers for error context → fix root cause → replay with the dead-letter replay endpoint',
  },
  {
    name: 'BalanceNegative',
    severity: 'critical',
    expr: 'min(ledger_account_balance_cents) < 0',
    summary: 'A ledger balance has gone negative. This violates the double-entry invariant and indicates a concurrency or idempotency bug.',
    action: 'Immediately alert on-call → lock affected account → audit ledger_entries for duplicates → run reconciliation',
  },
  {
    name: 'NotificationDeliveryFailureHigh',
    severity: 'warning',
    expr: 'rate(notification_delivery_failures_total[10m]) / rate(notification_sent_total[10m]) > 0.05',
    summary: 'More than 5% of notifications (email/SMS/WebSocket) are failing. Users may not be receiving payment status updates.',
    action: 'Check Notification Service health → SMTP/Twilio quota → WebSocket session store (Redis)',
  },
  {
    name: 'DataPipelineSinkErrorHigh',
    severity: 'warning',
    expr: 'rate(clickhouse_sink_errors_total[5m]) > 0.02',
    summary: 'ClickHouse inserts are failing. Analytics data will lag and Grafana dashboards will show stale metrics.',
    action: 'Check ClickHouse connection pool → disk space → batch flush config in DataPipeline service',
  },
  {
    name: 'ReconciliationBreakCountHigh',
    severity: 'warning',
    expr: 'reconciliation_breaks_total > 0',
    summary: 'The reconciliation job found mismatches between Stripe settlement data and the internal ledger.',
    action: 'Review reconciliation_breaks table in ClickHouse → identify affected transactions → trigger manual reconciliation',
  },
]

const DASHBOARDS: Dashboard[] = [
  {
    id: 'payment-ops',
    title: 'Payment Operations',
    source: 'AegisPay Grafana (ClickHouse)',
    color: 'border-blue-200 bg-blue-50',
    panels: [
      'Transaction volume (per minute)',
      'Saga success / failure rate',
      'P50 / P95 / P99 end-to-end latency',
      'Stripe API response times',
      'Active PENDING transactions',
    ],
  },
  {
    id: 'fraud',
    title: 'Fraud Intelligence',
    source: 'AegisPay Grafana (ClickHouse)',
    color: 'border-red-200 bg-red-50',
    panels: [
      'ALLOW / REVIEW / BLOCK decision rates',
      'Risk score distribution histogram',
      'Top blocked entity types (IP, IBAN, userId)',
      'Fraud rule hit rates by category',
      'AI RAG query latency',
    ],
  },
  {
    id: 'sla',
    title: 'SLA & Latency',
    source: 'AegisPay Grafana (ClickHouse)',
    color: 'border-amber-200 bg-amber-50',
    panels: [
      'Saga step latencies (per step)',
      'Reconciliation break count',
      'DLQ depth trend',
      'Notification delivery SLA',
      'Data pipeline flush lag (seconds)',
    ],
  },
  {
    id: 'infra',
    title: 'JVM & Kafka',
    source: 'kube-prometheus-stack Grafana',
    color: 'border-purple-200 bg-purple-50',
    panels: [
      'JVM heap usage per service',
      'Kafka consumer group lag',
      'HTTP error rate (4xx/5xx)',
      'K8s pod restart count',
      'Circuit breaker state per service',
    ],
  },
]

const SEVERITY_COLORS = {
  critical: 'bg-red-100 text-red-700 border-red-200',
  warning:  'bg-amber-100 text-amber-700 border-amber-200',
  info:     'bg-blue-100 text-blue-700 border-blue-200',
}

export default function ObservabilityDemo() {
  const [tab, setTab] = useState<'alerts' | 'dashboards'>('alerts')
  const [selectedAlert, setSelectedAlert] = useState<string>(ALERT_RULES[0].name)
  const [selectedDash, setSelectedDash] = useState<string>(DASHBOARDS[0].id)

  const alert = ALERT_RULES.find(r => r.name === selectedAlert)!
  const dash  = DASHBOARDS.find(d => d.id === selectedDash)!

  return (
    <div className="rounded-xl border border-gray-100 bg-white overflow-hidden">
      {/* Tab bar */}
      <div className="flex border-b border-gray-100 bg-gray-50">
        {(['alerts', 'dashboards'] as const).map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`flex-1 py-3 text-sm font-medium transition-colors ${
              tab === t
                ? 'border-b-2 border-blue-600 text-blue-600 bg-white'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {t === 'alerts' ? '🔔 Alert Rules' : '📊 Grafana Dashboards'}
          </button>
        ))}
      </div>

      {tab === 'alerts' && (
        <div className="flex divide-x divide-gray-100">
          {/* Rule list */}
          <div className="w-48 shrink-0 divide-y divide-gray-50">
            {ALERT_RULES.map(rule => (
              <button
                key={rule.name}
                onClick={() => setSelectedAlert(rule.name)}
                className={`w-full px-3 py-2.5 text-left transition-colors ${
                  selectedAlert === rule.name ? 'bg-blue-50' : 'hover:bg-gray-50'
                }`}
              >
                <span className={`inline-block rounded-full border px-1.5 py-0.5 text-[9px] font-semibold mb-1 ${SEVERITY_COLORS[rule.severity]}`}>
                  {rule.severity.toUpperCase()}
                </span>
                <p className="text-xs font-mono font-medium text-gray-800 truncate">{rule.name}</p>
              </button>
            ))}
          </div>

          {/* Rule detail */}
          <div className="flex-1 p-5 space-y-4">
            <div className="flex items-center gap-2">
              <span className={`rounded-full border px-2 py-0.5 text-xs font-semibold ${SEVERITY_COLORS[alert.severity]}`}>
                {alert.severity.toUpperCase()}
              </span>
              <h3 className="font-mono font-semibold text-gray-900">{alert.name}</h3>
            </div>

            <div>
              <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide mb-1">Expression</p>
              <code className="block rounded-lg bg-slate-900 text-green-300 text-xs px-4 py-3 font-mono overflow-x-auto">
                {alert.expr}
              </code>
            </div>

            <div>
              <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide mb-1">What it means</p>
              <p className="text-sm text-gray-600 leading-relaxed">{alert.summary}</p>
            </div>

            <div>
              <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide mb-1">Runbook</p>
              <p className="text-sm text-gray-600 leading-relaxed">{alert.action}</p>
            </div>
          </div>
        </div>
      )}

      {tab === 'dashboards' && (
        <div className="p-5 space-y-4">
          <div className="flex flex-wrap gap-2">
            {DASHBOARDS.map(d => (
              <button
                key={d.id}
                onClick={() => setSelectedDash(d.id)}
                className={`rounded-full border px-3 py-1.5 text-xs font-medium transition-all ${
                  selectedDash === d.id
                    ? 'bg-blue-600 text-white border-blue-600'
                    : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
                }`}
              >
                {d.title}
              </button>
            ))}
          </div>

          <div className={`rounded-xl border p-5 ${dash.color}`}>
            <div className="flex items-start justify-between mb-3">
              <h3 className="font-semibold text-gray-900">{dash.title}</h3>
              <span className="text-xs text-gray-500 bg-white/70 border border-gray-200 rounded-full px-2 py-0.5">
                {dash.source}
              </span>
            </div>
            <ul className="space-y-2">
              {dash.panels.map(panel => (
                <li key={panel} className="flex items-center gap-2 text-sm text-gray-700">
                  <span className="w-1.5 h-1.5 rounded-full bg-current shrink-0 opacity-60" />
                  {panel}
                </li>
              ))}
            </ul>
          </div>

          <p className="text-xs text-gray-400">
            Two separate Grafana instances are intentional: the kube-prometheus-stack instance is operator-managed and queries Prometheus for infra metrics; the AegisPay instance is application-managed and queries ClickHouse for business/payment metrics.
          </p>
        </div>
      )}
    </div>
  )
}
