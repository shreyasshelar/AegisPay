// FILE: apps/web/app/docs/_components/OutboxPatternDemo.tsx
'use client'

import { useState, useEffect, useRef } from 'react'
import { Play, Pause, RotateCcw, AlertTriangle, CheckCircle2, XCircle } from 'lucide-react'

type FlowStep = {
  actor: string
  action: string
  result: 'ok' | 'fail' | 'warn' | 'info'
  note?: string
}

const SCENARIOS: Record<string, { label: string; steps: FlowStep[] }> = {
  without: {
    label: '❌ Without Outbox',
    steps: [
      { actor: 'Transaction Svc', action: 'INSERT transactions (id, status=PENDING)', result: 'ok' },
      { actor: 'Transaction Svc', action: 'SERVICE CRASHES before kafka.send()', result: 'fail', note: 'Process killed — no event ever published' },
      { actor: 'Kafka', action: 'transaction.initiated never published', result: 'fail' },
      { actor: 'Ledger Svc', action: 'Never receives event — funds never reserved', result: 'fail' },
      { actor: 'DB', action: 'Transaction stuck in PENDING state forever — ghost record', result: 'fail', note: 'Manual intervention required' },
    ],
  },
  with: {
    label: '✅ With Outbox',
    steps: [
      { actor: 'Transaction Svc', action: 'BEGIN TRANSACTION', result: 'info' },
      { actor: 'Transaction Svc', action: 'INSERT transactions (id, status=PENDING)', result: 'ok' },
      { actor: 'Transaction Svc', action: 'INSERT outbox_events (aggregate_id, event_type=\'transaction.initiated\', published=false)', result: 'ok', note: 'Same DB transaction — atomic!' },
      { actor: 'Transaction Svc', action: 'COMMIT', result: 'ok' },
      { actor: 'Outbox Relay', action: 'SELECT * FROM outbox_events WHERE published=false (poll every 100ms)', result: 'info' },
      { actor: 'Outbox Relay', action: 'kafkaTemplate.send("transaction.initiated", event)', result: 'ok' },
      { actor: 'Outbox Relay', action: 'UPDATE outbox_events SET published=true WHERE id=?', result: 'ok' },
      { actor: 'Kafka Consumers', action: 'Ledger + Risk Engine consume transaction.initiated', result: 'ok' },
    ],
  },
  crash: {
    label: '⚠️ Relay Crash Recovery',
    steps: [
      { actor: 'Transaction Svc', action: 'BEGIN → INSERT transactions + INSERT outbox_events → COMMIT', result: 'ok' },
      { actor: 'Outbox Relay', action: 'kafkaTemplate.send("transaction.initiated") ✅', result: 'ok' },
      { actor: 'Outbox Relay', action: 'RELAY CRASHES before UPDATE published=true', result: 'warn', note: 'Did not mark as published — will re-publish on restart' },
      { actor: 'Outbox Relay', action: 'Relay restarts — finds outbox_event still published=false — re-publishes (duplicate)', result: 'warn' },
      { actor: 'Kafka Consumer', action: 'Idempotency check: SELECT FROM processed_events WHERE event_id=?', result: 'ok', note: 'Already processed — duplicate detected' },
      { actor: 'Kafka Consumer', action: 'Duplicate safely ignored — no double processing', result: 'ok' },
    ],
  },
}

const RESULT_STYLES: Record<string, string> = {
  ok: 'bg-green-50 border-green-200',
  fail: 'bg-red-50 border-red-200',
  warn: 'bg-amber-50 border-amber-200',
  info: 'bg-blue-50 border-blue-200',
}

const RESULT_ICONS: Record<string, React.ReactNode> = {
  ok: <CheckCircle2 size={14} className="text-green-500 shrink-0" />,
  fail: <XCircle size={14} className="text-red-500 shrink-0" />,
  warn: <AlertTriangle size={14} className="text-amber-500 shrink-0" />,
  info: <div className="w-3.5 h-3.5 rounded-full bg-blue-500 shrink-0" />,
}

export default function OutboxPatternDemo() {
  const [tab, setTab] = useState<keyof typeof SCENARIOS>('without')
  const [currentStep, setCurrentStep] = useState(0)
  const [playing, setPlaying] = useState(false)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const scenario = SCENARIOS[tab]
  const steps = scenario.steps

  useEffect(() => {
    setCurrentStep(0)
    setPlaying(false)
  }, [tab])

  useEffect(() => {
    if (playing) {
      intervalRef.current = setInterval(() => {
        setCurrentStep((prev) => {
          if (prev >= steps.length - 1) {
            setPlaying(false)
            return prev
          }
          return prev + 1
        })
      }, 1500)
    }
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [playing, steps.length])

  const reset = () => {
    setPlaying(false)
    setCurrentStep(0)
  }

  return (
    <div className="rounded-xl border border-gray-100 shadow-sm bg-white overflow-hidden">
      <div className="border-b border-gray-100">
        <div className="flex overflow-x-auto">
          {Object.entries(SCENARIOS).map(([key, { label }]) => (
            <button
              key={key}
              onClick={() => setTab(key as keyof typeof SCENARIOS)}
              className={`shrink-0 px-5 py-3 text-sm font-medium border-b-2 transition-colors ${
                tab === key
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="px-5 py-3 border-b border-gray-100 flex items-center justify-between">
        <p className="text-sm text-gray-500">
          Step {Math.min(currentStep + 1, steps.length)} of {steps.length}
        </p>
        <div className="flex items-center gap-2">
          <button onClick={reset} className="p-1.5 rounded-lg text-gray-500 hover:bg-gray-100">
            <RotateCcw size={14} />
          </button>
          <button
            onClick={() => setPlaying((p) => !p)}
            className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-blue-500 text-white font-medium hover:bg-blue-600"
          >
            {playing ? <Pause size={12} /> : <Play size={12} />}
            {playing ? 'Pause' : 'Play'}
          </button>
        </div>
      </div>

      <div className="p-5 space-y-2">
        {steps.map((step, idx) => (
          <div
            key={idx}
            className={`rounded-lg border p-3 transition-all duration-300 ${
              idx <= currentStep
                ? RESULT_STYLES[step.result]
                : 'border-gray-100 bg-white opacity-30'
            }`}
          >
            <div className="flex items-start gap-2.5">
              {RESULT_ICONS[step.result]}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
                    {step.actor}
                  </span>
                  <span className="text-xs bg-gray-100 text-gray-600 px-1.5 py-0.5 rounded">
                    Step {idx + 1}
                  </span>
                </div>
                <p className="text-sm font-mono text-gray-800 mt-0.5">{step.action}</p>
                {step.note && idx <= currentStep && (
                  <p className="text-xs text-gray-500 mt-1 italic">{step.note}</p>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="px-5 pb-4">
        <div className="flex gap-1">
          {steps.map((_, idx) => (
            <button
              key={idx}
              onClick={() => { setPlaying(false); setCurrentStep(idx) }}
              className={`h-1.5 rounded-full flex-1 transition-all ${
                idx <= currentStep ? 'bg-blue-500' : 'bg-gray-200'
              }`}
            />
          ))}
        </div>
      </div>
    </div>
  )
}
