// FILE: apps/web/app/docs/_components/SagaStateMachineDemo.tsx
'use client'

import { useState } from 'react'

type SagaState = {
  id: string
  label: string
  description: string
  x: number
  y: number
  color: string
  textColor: string
}

type Transition = {
  from: string
  to: string
  label: string
  path?: string
}

const STATES: SagaState[] = [
  {
    id: 'STARTED',
    label: 'STARTED',
    description: 'Saga initiated. Funds reservation requested.',
    x: 200, y: 20,
    color: '#3b82f6',
    textColor: '#fff',
  },
  {
    id: 'FUNDS_RESERVED',
    label: 'FUNDS RESERVED',
    description: 'Ledger reserved payer balance. Payment can proceed.',
    x: 200, y: 110,
    color: '#8b5cf6',
    textColor: '#fff',
  },
  {
    id: 'RISK_ASSESSED',
    label: 'RISK ASSESSED',
    description: 'Risk Engine approved transaction (ALLOW decision).',
    x: 200, y: 200,
    color: '#10b981',
    textColor: '#fff',
  },
  {
    id: 'PAYMENT_PROCESSING',
    label: 'PAYMENT PROCESSING',
    description: 'Stripe API called. Waiting for payment confirmation.',
    x: 200, y: 290,
    color: '#f59e0b',
    textColor: '#fff',
  },
  {
    id: 'PAYMENT_COMPLETED',
    label: 'PAYMENT COMPLETED',
    description: 'Stripe confirmed payment success.',
    x: 200, y: 380,
    color: '#10b981',
    textColor: '#fff',
  },
  {
    id: 'LEDGER_COMMITTED',
    label: 'LEDGER COMMITTED',
    description: 'Double-entry committed: DEBIT payer, CREDIT payee.',
    x: 200, y: 470,
    color: '#10b981',
    textColor: '#fff',
  },
  {
    id: 'COMPLETED',
    label: 'COMPLETED',
    description: 'Saga complete. Notifications sent. Money transferred.',
    x: 200, y: 560,
    color: '#064e3b',
    textColor: '#fff',
  },
  {
    id: 'COMPENSATING',
    label: 'COMPENSATING',
    description: 'Saga failure detected. Running compensating transactions.',
    x: 430, y: 200,
    color: '#ef4444',
    textColor: '#fff',
  },
  {
    id: 'FUNDS_RELEASED',
    label: 'FUNDS RELEASED',
    description: 'Reserved balance returned to payer.',
    x: 430, y: 290,
    color: '#f97316',
    textColor: '#fff',
  },
  {
    id: 'FAILED',
    label: 'FAILED',
    description: 'Saga failed. Compensations applied. Payer notified.',
    x: 430, y: 380,
    color: '#7f1d1d',
    textColor: '#fff',
  },
]

const SCENARIOS = {
  happy: ['STARTED', 'FUNDS_RESERVED', 'RISK_ASSESSED', 'PAYMENT_PROCESSING', 'PAYMENT_COMPLETED', 'LEDGER_COMMITTED', 'COMPLETED'],
  risk: ['STARTED', 'FUNDS_RESERVED', 'COMPENSATING', 'FUNDS_RELEASED', 'FAILED'],
  stripe: ['STARTED', 'FUNDS_RESERVED', 'RISK_ASSESSED', 'PAYMENT_PROCESSING', 'COMPENSATING', 'FUNDS_RELEASED', 'FAILED'],
}

const SCENARIO_LABELS = [
  { key: 'happy' as const, label: 'Happy Path', color: 'bg-green-500' },
  { key: 'risk' as const, label: 'Risk Block', color: 'bg-red-500' },
  { key: 'stripe' as const, label: 'Stripe Failure', color: 'bg-amber-500' },
]

export default function SagaStateMachineDemo() {
  const [selectedState, setSelectedState] = useState<string | null>('STARTED')
  const [scenario, setScenario] = useState<keyof typeof SCENARIOS>('happy')

  const activeStates = new Set(SCENARIOS[scenario])
  const selectedStateData = STATES.find((s) => s.id === selectedState)

  const RECT_W = 150
  const RECT_H = 36

  // Build transitions for current scenario
  const scenarioPath = SCENARIOS[scenario]
  const transitions: Transition[] = []
  for (let i = 0; i < scenarioPath.length - 1; i++) {
    transitions.push({ from: scenarioPath[i], to: scenarioPath[i + 1], label: '' })
  }

  const getCenter = (id: string) => {
    const s = STATES.find((st) => st.id === id)
    if (!s) return { x: 0, y: 0 }
    return { x: s.x + RECT_W / 2, y: s.y + RECT_H / 2 }
  }

  return (
    <div className="rounded-xl border border-gray-100 shadow-sm bg-white overflow-hidden">
      <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between flex-wrap gap-3">
        <div>
          <h3 className="font-semibold text-gray-900">Saga State Machine</h3>
          <p className="text-sm text-gray-500 mt-0.5">Click a state to see details</p>
        </div>
        <div className="flex gap-2 flex-wrap">
          {SCENARIO_LABELS.map(({ key, label, color }) => (
            <button
              key={key}
              onClick={() => { setScenario(key); setSelectedState(SCENARIOS[key][0]) }}
              className={`text-xs px-3 py-1.5 rounded-lg font-medium transition-colors ${
                scenario === key
                  ? `${color} text-white`
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {key === 'happy' ? '✅' : key === 'risk' ? '🛡️' : '💳'} {label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex flex-col lg:flex-row gap-0">
        {/* SVG diagram */}
        <div className="flex-1 overflow-x-auto p-4">
          <svg width="620" height="640" className="min-w-[400px]">
            <defs>
              <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
                <path d="M0,0 L0,6 L8,3 z" fill="#94a3b8" />
              </marker>
              <marker id="arrow-active" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
                <path d="M0,0 L0,6 L8,3 z" fill="#3b82f6" />
              </marker>
            </defs>

            {/* Draw transitions */}
            {transitions.map((t, i) => {
              const from = getCenter(t.from)
              const to = getCenter(t.to)
              const isHoriz = Math.abs(from.y - to.y) < 10
              const d = isHoriz
                ? `M${from.x},${from.y} L${to.x},${to.y}`
                : `M${from.x},${from.y + RECT_H / 2} L${to.x},${to.y - RECT_H / 2 - 2}`
              return (
                <path
                  key={i}
                  d={`M${from.x},${from.y + RECT_H / 2 - 2} L${to.x},${to.y - RECT_H / 2 - 2}`}
                  stroke="#3b82f6"
                  strokeWidth="2"
                  fill="none"
                  markerEnd="url(#arrow-active)"
                />
              )
            })}

            {/* Draw states */}
            {STATES.map((state) => {
              const isActive = activeStates.has(state.id)
              const isSelected = selectedState === state.id
              return (
                <g
                  key={state.id}
                  onClick={() => setSelectedState(state.id)}
                  style={{ cursor: 'pointer' }}
                >
                  <rect
                    x={state.x}
                    y={state.y}
                    width={RECT_W}
                    height={RECT_H}
                    rx={8}
                    fill={isActive ? state.color : '#e5e7eb'}
                    stroke={isSelected ? '#1d4ed8' : 'transparent'}
                    strokeWidth={3}
                    opacity={isActive ? 1 : 0.4}
                  />
                  <text
                    x={state.x + RECT_W / 2}
                    y={state.y + RECT_H / 2 + 1}
                    textAnchor="middle"
                    dominantBaseline="middle"
                    fill={isActive ? state.textColor : '#6b7280'}
                    fontSize="10"
                    fontWeight="600"
                    fontFamily="ui-monospace, monospace"
                  >
                    {state.label}
                  </text>
                </g>
              )
            })}
          </svg>
        </div>

        {/* Detail panel */}
        <div className="lg:w-64 border-t lg:border-t-0 lg:border-l border-gray-100 p-5">
          {selectedStateData ? (
            <div>
              <div
                className="inline-block px-3 py-1 rounded-full text-white text-xs font-bold mb-3"
                style={{ backgroundColor: selectedStateData.color }}
              >
                {selectedStateData.label}
              </div>
              <p className="text-sm text-gray-700">{selectedStateData.description}</p>
              <div className="mt-4 text-xs text-gray-400">
                {activeStates.has(selectedStateData.id) ? (
                  <span className="text-green-600 font-medium">Active in this scenario</span>
                ) : (
                  <span className="text-gray-400">Not in this scenario path</span>
                )}
              </div>
            </div>
          ) : (
            <p className="text-sm text-gray-400">Select a state to see details</p>
          )}

          <div className="mt-6">
            <p className="text-xs font-medium text-gray-500 mb-2 uppercase tracking-wide">
              Scenario path
            </p>
            <div className="space-y-1">
              {SCENARIOS[scenario].map((id, i) => {
                const s = STATES.find((st) => st.id === id)
                return s ? (
                  <div key={id} className="flex items-center gap-2 text-xs">
                    <span className="text-gray-400">{i + 1}.</span>
                    <button
                      onClick={() => setSelectedState(id)}
                      className="font-mono text-left hover:underline"
                      style={{ color: s.color === '#e5e7eb' ? '#6b7280' : s.color }}
                    >
                      {id}
                    </button>
                  </div>
                ) : null
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
