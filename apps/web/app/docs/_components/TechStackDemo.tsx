// FILE: apps/web/app/docs/_components/TechStackDemo.tsx
'use client'

import { useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'

type TechItem = {
  name: string
  role: string
  why: string
  tag: string
  tagColor: string
}

type Layer = {
  id: string
  label: string
  color: string
  bg: string
  border: string
  items: TechItem[]
}

const LAYERS: Layer[] = [
  {
    id: 'client',
    label: 'Client Tier',
    color: 'text-blue-700',
    bg: 'bg-blue-50',
    border: 'border-blue-200',
    items: [
      {
        name: 'Next.js 14',
        role: 'Web frontend',
        why: 'App Router SSR + streaming for fast dashboards',
        tag: 'Web',
        tagColor: 'bg-blue-100 text-blue-700',
      },
      {
        name: 'SwiftUI',
        role: 'iOS app',
        why: 'Native performance, Face ID biometric auth',
        tag: 'iOS',
        tagColor: 'bg-gray-100 text-gray-700',
      },
      {
        name: 'Jetpack Compose',
        role: 'Android app',
        why: 'Declarative UI, Kotlin coroutines for async ops',
        tag: 'Android',
        tagColor: 'bg-green-100 text-green-700',
      },
      {
        name: 'TypeScript',
        role: 'Type safety',
        why: 'End-to-end type safety with shared-types package',
        tag: 'Language',
        tagColor: 'bg-blue-100 text-blue-700',
      },
    ],
  },
  {
    id: 'gateway',
    label: 'Auth & Gateway',
    color: 'text-purple-700',
    bg: 'bg-purple-50',
    border: 'border-purple-200',
    items: [
      {
        name: 'Spring Cloud Gateway',
        role: 'API gateway',
        why: 'JWT validation, rate limiting, circuit breaker, routing',
        tag: 'Gateway',
        tagColor: 'bg-purple-100 text-purple-700',
      },
      {
        name: 'Keycloak 24',
        role: 'Identity provider',
        why: 'OAuth2/OIDC, multi-IdP (Google, GitHub, Apple, Microsoft)',
        tag: 'Auth',
        tagColor: 'bg-purple-100 text-purple-700',
      },
      {
        name: 'Resilience4j',
        role: 'Resilience patterns',
        why: 'Circuit breaker, retry, bulkhead — prevents cascade failures',
        tag: 'Resilience',
        tagColor: 'bg-orange-100 text-orange-700',
      },
      {
        name: 'Redis 7',
        role: 'Cache / rate limit',
        why: 'Sliding window rate limits (100 req/60s), idempotency keys, sessions',
        tag: 'Cache',
        tagColor: 'bg-red-100 text-red-700',
      },
    ],
  },
  {
    id: 'backend',
    label: 'Backend Services',
    color: 'text-green-700',
    bg: 'bg-green-50',
    border: 'border-green-200',
    items: [
      {
        name: 'Spring Boot 3.2',
        role: '10 microservices',
        why: 'Virtual threads (Loom), actuator, Micrometer metrics, Flyway migrations',
        tag: 'Core',
        tagColor: 'bg-green-100 text-green-700',
      },
      {
        name: 'PostgreSQL 16',
        role: 'Transactional store',
        why: 'ACID guarantees, optimistic locking, FOR UPDATE, pgvector for RAG',
        tag: 'DB',
        tagColor: 'bg-sky-100 text-sky-700',
      },
      {
        name: 'Apache Kafka 3.6',
        role: 'Event backbone',
        why: 'At-least-once delivery with consumer idempotency = exactly-once semantics',
        tag: 'Events',
        tagColor: 'bg-amber-100 text-amber-700',
      },
      {
        name: 'MongoDB 7',
        role: 'CQRS read models',
        why: 'Denormalised read projections for tx history and notification contacts',
        tag: 'DB',
        tagColor: 'bg-sky-100 text-sky-700',
      },
      {
        name: 'Stripe SDK',
        role: 'Payment processing',
        why: 'PCI-DSS compliant card processing with idempotency key per saga step',
        tag: 'Payments',
        tagColor: 'bg-indigo-100 text-indigo-700',
      },
      {
        name: 'Anthropic Claude',
        role: 'AI inference',
        why: 'RAG explanations, fraud copilot, incident triage — with Groq/Gemini fallback',
        tag: 'AI',
        tagColor: 'bg-rose-100 text-rose-700',
      },
    ],
  },
  {
    id: 'data',
    label: 'Data & Analytics',
    color: 'text-amber-700',
    bg: 'bg-amber-50',
    border: 'border-amber-200',
    items: [
      {
        name: 'Kafka Streams',
        role: 'Stream processing',
        why: 'Real-time aggregations before sink to ClickHouse (5s batch flush)',
        tag: 'Streaming',
        tagColor: 'bg-amber-100 text-amber-700',
      },
      {
        name: 'ClickHouse 24.4',
        role: 'Analytics OLAP',
        why: 'Columnar storage for transaction_facts, risk_assessments, saga_latencies',
        tag: 'Analytics',
        tagColor: 'bg-amber-100 text-amber-700',
      },
      {
        name: 'Spring Batch',
        role: 'Reconciliation',
        why: 'Nightly batch reconciliation — flags any breaks between ledger and Stripe',
        tag: 'Batch',
        tagColor: 'bg-gray-100 text-gray-700',
      },
      {
        name: 'Grafana 10.4',
        role: 'Dashboards',
        why: 'Two instances: kube-prometheus (JVM/K8s) + AegisPay (ClickHouse analytics)',
        tag: 'Observability',
        tagColor: 'bg-orange-100 text-orange-700',
      },
    ],
  },
]

export default function TechStackDemo() {
  const [openLayers, setOpenLayers] = useState<Set<string>>(new Set(['client']))

  const toggle = (id: string) => {
    setOpenLayers((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  return (
    <div className="rounded-xl border border-gray-100 shadow-sm bg-white overflow-hidden">
      <div className="px-5 py-4 border-b border-gray-100">
        <h3 className="font-semibold text-gray-900">Tech Stack Explorer</h3>
        <p className="text-sm text-gray-500 mt-0.5">Click a layer to expand its components</p>
      </div>
      <div className="divide-y divide-gray-100">
        {LAYERS.map((layer) => {
          const isOpen = openLayers.has(layer.id)
          return (
            <div key={layer.id}>
              <button
                onClick={() => toggle(layer.id)}
                className={`w-full flex items-center justify-between px-5 py-4 hover:bg-gray-50 transition-colors ${
                  isOpen ? layer.bg : ''
                }`}
              >
                <div className="flex items-center gap-3">
                  <div className={`w-2.5 h-2.5 rounded-full ${layer.bg} border-2 ${layer.border}`} />
                  <span className={`font-medium ${isOpen ? layer.color : 'text-gray-700'}`}>
                    {layer.label}
                  </span>
                  <span className="text-xs text-gray-400">{layer.items.length} technologies</span>
                </div>
                {isOpen ? (
                  <ChevronDown size={16} className="text-gray-400" />
                ) : (
                  <ChevronRight size={16} className="text-gray-400" />
                )}
              </button>

              {isOpen && (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 px-5 pb-4">
                  {layer.items.map((item) => (
                    <div
                      key={item.name}
                      className="rounded-lg border border-gray-100 bg-white p-4 hover:shadow-sm transition-shadow"
                    >
                      <div className="flex items-start justify-between gap-2 mb-1.5">
                        <p className="font-semibold text-gray-900 text-sm">{item.name}</p>
                        <span className={`text-xs px-2 py-0.5 rounded-full font-medium shrink-0 ${item.tagColor}`}>
                          {item.tag}
                        </span>
                      </div>
                      <p className="text-xs text-gray-500 font-medium mb-1">{item.role}</p>
                      <p className="text-xs text-gray-400">{item.why}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
