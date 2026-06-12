'use client'

import { useState } from 'react'

type ServiceNode = {
  id: string
  label: string
  port: string
  tier: 'client' | 'gateway' | 'core' | 'data'
  db: string
  desc: string
  kafka: string[]
}

const SERVICES: ServiceNode[] = [
  {
    id: 'web',
    label: 'Next.js Web',
    port: '3000',
    tier: 'client',
    db: 'None',
    desc: 'React Server Components + App Router. Communicates with API Gateway over HTTPS. WebSocket for real-time notifications.',
    kafka: [],
  },
  {
    id: 'gateway',
    label: 'API Gateway',
    port: '8080',
    tier: 'gateway',
    db: 'Redis',
    desc: 'JWT validation, rate limiting (Redis), idempotency deduplication, circuit breaker, and request routing. The only entry point from outside the cluster.',
    kafka: [],
  },
  {
    id: 'user',
    label: 'User Service',
    port: '8081',
    tier: 'core',
    db: 'PostgreSQL',
    desc: 'User registration, KYC document upload + AI OCR, profile management. Publishes user.registered on Keycloak SSO.',
    kafka: ['user.registered (publish)'],
  },
  {
    id: 'transaction',
    label: 'Transaction',
    port: '8082',
    tier: 'core',
    db: 'PostgreSQL + MongoDB',
    desc: 'Creates transactions with Outbox pattern. CQRS: writes to Postgres, maintains denormalized read model in MongoDB. Publishes transaction.initiated.',
    kafka: ['transaction.initiated (publish)', 'transaction.completed (consume)', 'transaction.failed (consume)'],
  },
  {
    id: 'ledger',
    label: 'Ledger Service',
    port: '8083',
    tier: 'core',
    db: 'PostgreSQL',
    desc: 'Double-entry bookkeeping. Reserves funds on balance.reserved, commits entries on payment.completed. Invariant: SUM(all entries) = 0.',
    kafka: ['balance.reserved (publish)', 'ledger.committed (publish)', 'payment.completed (consume)'],
  },
  {
    id: 'orchestrator',
    label: 'Orchestrator',
    port: '8084',
    tier: 'core',
    db: 'PostgreSQL',
    desc: 'Saga orchestrator. Drives the payment through Stripe API. Triggers compensating transactions on failure (credit reversal). Manages saga state machine.',
    kafka: ['payment.completed (publish)', 'risk.assessed (consume)'],
  },
  {
    id: 'risk',
    label: 'Risk Engine',
    port: '8085',
    tier: 'core',
    db: 'PostgreSQL (pgvector)',
    desc: 'Rule evaluation + AI RAG fraud analysis. Returns ALLOW / REVIEW / BLOCK decision. Calls AI Platform for explanation generation.',
    kafka: ['risk.assessed (publish)', 'transaction.initiated (consume)'],
  },
  {
    id: 'notification',
    label: 'Notifications',
    port: '8086',
    tier: 'core',
    db: 'MongoDB',
    desc: 'Sends WebSocket push (STOMP), email, SMS, and Slack notifications. Subscribes to transaction.completed + transaction.failed.',
    kafka: ['transaction.completed (consume)', 'transaction.failed (consume)'],
  },
  {
    id: 'ai',
    label: 'AI Platform',
    port: '8091',
    tier: 'core',
    db: 'PostgreSQL (pgvector)',
    desc: 'RAG pipeline, fraud copilot, error resolution, KYC OCR, and incident triage. Multi-provider fallback (OpenRouter → Groq → Gemini).',
    kafka: [],
  },
  {
    id: 'data',
    label: 'Data Pipeline',
    port: '8089',
    tier: 'data',
    db: 'ClickHouse',
    desc: 'Consumes all Kafka events and batch-flushes to ClickHouse every 5 seconds. Powers all Grafana analytics dashboards.',
    kafka: ['All topics (consume)'],
  },
  {
    id: 'recon',
    label: 'Reconciliation',
    port: '8087',
    tier: 'data',
    db: 'PostgreSQL + ClickHouse',
    desc: 'Nightly Spring Batch job comparing Stripe settlement data against the internal ledger. Writes reconciliation_breaks to ClickHouse.',
    kafka: [],
  },
]

const TIER_CONFIG = {
  client:  { label: 'Client', color: 'bg-blue-600', ring: 'ring-blue-300', badge: 'bg-blue-100 text-blue-700' },
  gateway: { label: 'Gateway & Auth', color: 'bg-purple-600', ring: 'ring-purple-300', badge: 'bg-purple-100 text-purple-700' },
  core:    { label: 'Core Services', color: 'bg-emerald-600', ring: 'ring-emerald-300', badge: 'bg-emerald-100 text-emerald-700' },
  data:    { label: 'Data & Analytics', color: 'bg-amber-600', ring: 'ring-amber-300', badge: 'bg-amber-100 text-amber-700' },
}

export default function ServiceTopologyDemo() {
  const [selected, setSelected] = useState<string | null>(null)

  const tiers = ['client', 'gateway', 'core', 'data'] as const
  const service = SERVICES.find(s => s.id === selected)

  return (
    <div className="rounded-xl border border-gray-100 bg-white overflow-hidden">
      <div className="p-4 bg-gray-50 border-b border-gray-100">
        <p className="text-xs text-gray-500">Click any service node to see details. Services in the same tier share a deployment namespace.</p>
      </div>

      <div className="p-5 space-y-3">
        {tiers.map(tier => {
          const nodes = SERVICES.filter(s => s.tier === tier)
          const cfg = TIER_CONFIG[tier]
          return (
            <div key={tier}>
              <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-400 mb-2">
                {cfg.label}
              </p>
              <div className="flex flex-wrap gap-2">
                {nodes.map(node => (
                  <button
                    key={node.id}
                    onClick={() => setSelected(prev => prev === node.id ? null : node.id)}
                    className={`flex items-center gap-2 rounded-xl border px-3 py-2.5 text-left transition-all ${
                      selected === node.id
                        ? `${cfg.color} text-white border-transparent ring-2 ${cfg.ring}`
                        : 'bg-white border-gray-200 text-gray-700 hover:border-gray-300 hover:shadow-sm'
                    }`}
                  >
                    <div className={`w-2 h-2 rounded-full shrink-0 ${selected === node.id ? 'bg-white/50' : cfg.color}`} />
                    <span className="text-xs font-semibold whitespace-nowrap">{node.label}</span>
                    <span className={`text-[10px] font-mono rounded px-1 ${
                      selected === node.id ? 'bg-white/20 text-white' : 'bg-gray-100 text-gray-500'
                    }`}>:{node.port}</span>
                  </button>
                ))}
              </div>
            </div>
          )
        })}

        {/* Kafka bus */}
        <div className="rounded-xl bg-slate-900 px-4 py-3 flex items-center gap-3">
          <div className="w-2 h-2 rounded-full bg-orange-400 shrink-0 animate-pulse" />
          <p className="text-xs font-mono text-orange-300 font-semibold">Apache Kafka</p>
          <p className="text-xs text-slate-400">Async event bus connecting all core services</p>
        </div>
      </div>

      {/* Detail panel */}
      {service && (
        <div className="border-t border-gray-100 p-5 space-y-3 bg-gray-50 animate-fade-in">
          <div className="flex items-center gap-2">
            <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${TIER_CONFIG[service.tier].badge}`}>
              {TIER_CONFIG[service.tier].label}
            </span>
            <h3 className="font-semibold text-gray-900">{service.label}</h3>
            <span className="font-mono text-xs text-gray-400 bg-gray-200 rounded px-1.5 py-0.5">:{service.port}</span>
          </div>

          <p className="text-sm text-gray-600 leading-relaxed">{service.desc}</p>

          <div className="flex flex-wrap gap-4 text-xs">
            <div>
              <span className="font-semibold text-gray-500 uppercase tracking-wide text-[10px]">Data store</span>
              <p className="font-mono text-gray-800 mt-0.5">{service.db}</p>
            </div>
            {service.kafka.length > 0 && (
              <div>
                <span className="font-semibold text-gray-500 uppercase tracking-wide text-[10px]">Kafka topics</span>
                <ul className="mt-0.5 space-y-0.5">
                  {service.kafka.map(k => (
                    <li key={k} className="font-mono text-orange-700 text-[11px]">{k}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
