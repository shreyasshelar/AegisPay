// FILE: apps/web/app/docs/_components/TransactionFlowDemo.tsx
'use client'

import { useState, useEffect, useRef } from 'react'
import { Play, Pause, RotateCcw, AlertTriangle } from 'lucide-react'

type Step = {
  id: number
  actor: string
  actorColor: string
  action: string
  detail: string
  state?: string
  kafkaEvent?: string
  code: string
  isFailure?: boolean
}

const HAPPY_STEPS: Step[] = [
  {
    id: 1,
    actor: 'Client',
    actorColor: 'bg-blue-500',
    action: 'User submits payment',
    detail: 'POST /api/v1/transactions with Idempotency-Key header',
    code: 'POST /api/v1/transactions\nAuthorization: Bearer <jwt>\nIdempotency-Key: uuid-v4',
  },
  {
    id: 2,
    actor: 'Gateway',
    actorColor: 'bg-purple-500',
    action: 'Validate JWT, rate-limit, idempotency check',
    detail: 'Redis sliding window: 100 req/60s per userId. Idempotency key stored 24h.',
    code: 'JwtFilter → RateLimitFilter → IdempotencyFilter\n→ RouteToTransactionService',
  },
  {
    id: 3,
    actor: 'Transaction Svc',
    actorColor: 'bg-green-500',
    action: 'Atomic write: INSERT transactions + INSERT outbox_events',
    detail: 'One DB transaction ensures event can never be lost, even on crash.',
    state: 'PENDING',
    code: 'BEGIN;\nINSERT INTO transactions (id, status, amount) VALUES (?, PENDING, ?);\nINSERT INTO outbox_events (aggregate_id, event_type) VALUES (?, \'transaction.initiated\');\nCOMMIT;',
  },
  {
    id: 4,
    actor: 'Outbox Relay',
    actorColor: 'bg-red-500',
    action: 'Publish transaction.initiated to Kafka',
    detail: 'Relay polls outbox every 100ms, publishes, marks published=true.',
    kafkaEvent: 'transaction.initiated',
    code: 'kafkaTemplate.send("transaction.initiated", event);\noutboxRepository.markPublished(event.id);',
  },
  {
    id: 5,
    actor: 'Ledger Svc',
    actorColor: 'bg-green-500',
    action: 'Reserve funds',
    detail: 'Optimistic lock + FOR UPDATE prevents concurrent deductions.',
    state: 'RESERVED',
    kafkaEvent: 'balance.reserved',
    code: 'UPDATE accounts\nSET reserved_balance = reserved_balance + :amount\nWHERE id = :payerId\nAND (available_balance - :amount) >= 0\nFOR UPDATE;',
  },
  {
    id: 6,
    actor: 'Risk Engine',
    actorColor: 'bg-green-500',
    action: 'Rule evaluation + RAG query → ALLOW',
    detail: '50+ rules evaluated in <20ms. AI RAG generates explainability report.',
    state: 'RISK_CLEARED',
    kafkaEvent: 'risk.assessed',
    code: 'RuleEngineResult result = ruleEngine.evaluate(transaction);\nRagExplanation explanation = ragService.explain(result);\nemit("risk.assessed", {decision: ALLOW, explanation});',
  },
  {
    id: 7,
    actor: 'Orchestrator',
    actorColor: 'bg-green-500',
    action: 'Call Stripe API',
    detail: 'Saga step with compensating transaction on failure. Stripe idempotency key = sagaId.',
    state: 'PROCESSING',
    code: 'stripe.paymentIntents.create({\n  amount: amountInCents,\n  currency: "inr",\n  idempotencyKey: sagaId\n});',
  },
  {
    id: 8,
    actor: 'Ledger Svc',
    actorColor: 'bg-green-500',
    action: 'Commit: DEBIT payer + CREDIT payee',
    detail: 'Double-entry: SUM(all entries) always = 0.',
    state: 'COMPLETED',
    kafkaEvent: 'ledger.committed',
    code: 'INSERT INTO ledger_entries VALUES\n  (payerId, DEBIT, amount),\n  (payeeId, CREDIT, amount);\nASSERT SUM(all_entries) = 0;',
  },
  {
    id: 9,
    actor: 'Notification Svc',
    actorColor: 'bg-green-500',
    action: 'Push WebSocket + Email',
    detail: 'STOMP WebSocket for real-time UI update. SendGrid email with receipt.',
    kafkaEvent: 'transaction.completed',
    code: 'messagingTemplate.convertAndSendToUser(\n  userId, "/queue/notifications", payload\n);\nsendGridClient.send(receipt);',
  },
]

const FAILURE_STEPS: Step[] = [
  {
    id: 1,
    actor: 'Client',
    actorColor: 'bg-blue-500',
    action: 'User submits payment',
    detail: 'Same entry point — failure happens later in the saga.',
    code: 'POST /api/v1/transactions',
    isFailure: false,
  },
  {
    id: 2,
    actor: 'Orchestrator',
    actorColor: 'bg-red-500',
    action: 'Stripe call fails — card declined',
    detail: 'Saga detects failure, triggers compensation steps.',
    state: 'FAILED',
    code: 'StripeException: card_declined\n→ sagaCoordinator.compensate(sagaId)',
    isFailure: true,
  },
  {
    id: 3,
    actor: 'Ledger Svc',
    actorColor: 'bg-amber-500',
    action: 'Release reserved funds',
    detail: 'Compensating transaction restores available_balance.',
    code: 'UPDATE accounts\nSET reserved_balance = reserved_balance - :amount,\n    available_balance = available_balance + :amount\nWHERE id = :payerId;',
    isFailure: false,
  },
  {
    id: 4,
    actor: 'Notification Svc',
    actorColor: 'bg-green-500',
    action: 'Push failure notification',
    detail: 'WebSocket + Email + SMS with AI-generated explanation.',
    kafkaEvent: 'transaction.failed',
    code: 'emit("transaction.failed", {\n  failureCode: "CARD_DECLINED",\n  aiExplanation: ragService.explain(error)\n});',
    isFailure: false,
  },
]

function StepCard({ step, isActive, isDone }: { step: Step; isActive: boolean; isDone: boolean }) {
  return (
    <div
      className={`rounded-xl border p-4 transition-all duration-300 ${
        isActive
          ? step.isFailure
            ? 'border-red-300 bg-red-50 shadow-md'
            : 'border-blue-300 bg-blue-50 shadow-md'
          : isDone
          ? 'border-gray-200 bg-white opacity-75'
          : 'border-gray-100 bg-white opacity-40'
      }`}
    >
      <div className="flex items-start gap-3">
        <div className={`shrink-0 w-6 h-6 rounded-full ${step.actorColor} flex items-center justify-center text-white text-xs font-bold`}>
          {step.id}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap mb-1">
            <span className={`text-xs px-2 py-0.5 rounded-full font-medium text-white ${step.actorColor}`}>
              {step.actor}
            </span>
            {step.state && (
              <span className="text-xs px-2 py-0.5 rounded-full bg-gray-800 text-gray-100 font-mono">
                → {step.state}
              </span>
            )}
            {step.kafkaEvent && (
              <span className="text-xs px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 font-mono">
                ⚡ {step.kafkaEvent}
              </span>
            )}
            {step.isFailure && (
              <span className="text-xs px-2 py-0.5 rounded-full bg-red-100 text-red-700 font-medium flex items-center gap-1">
                <AlertTriangle size={10} /> FAILURE
              </span>
            )}
          </div>
          <p className="font-medium text-gray-900 text-sm">{step.action}</p>
          <p className="text-xs text-gray-500 mt-0.5">{step.detail}</p>
          {isActive && (
            <pre className="mt-3 bg-gray-900 text-gray-100 rounded-lg p-3 text-xs font-mono overflow-x-auto whitespace-pre-wrap">
              {step.code}
            </pre>
          )}
        </div>
      </div>
    </div>
  )
}

export default function TransactionFlowDemo() {
  const [currentStep, setCurrentStep] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [showFailure, setShowFailure] = useState(false)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const steps = showFailure ? FAILURE_STEPS : HAPPY_STEPS

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
      }, 2000)
    }
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [playing, steps.length])

  const reset = () => {
    setPlaying(false)
    setCurrentStep(0)
  }

  const toggleScenario = (failure: boolean) => {
    reset()
    setShowFailure(failure)
  }

  return (
    <div className="rounded-xl border border-gray-100 shadow-sm bg-white overflow-hidden">
      <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between flex-wrap gap-3">
        <div>
          <h3 className="font-semibold text-gray-900">Transaction Flow — Step by Step</h3>
          <p className="text-sm text-gray-500 mt-0.5">
            Step {Math.min(currentStep + 1, steps.length)} of {steps.length}
          </p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <button
            onClick={() => toggleScenario(false)}
            className={`text-xs px-3 py-1.5 rounded-lg font-medium transition-colors ${
              !showFailure
                ? 'bg-green-500 text-white'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            Happy Path
          </button>
          <button
            onClick={() => toggleScenario(true)}
            className={`text-xs px-3 py-1.5 rounded-lg font-medium transition-colors ${
              showFailure
                ? 'bg-red-500 text-white'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            Failure Scenario
          </button>
          <button
            onClick={reset}
            className="p-1.5 rounded-lg text-gray-500 hover:bg-gray-100 transition-colors"
          >
            <RotateCcw size={15} />
          </button>
          <button
            onClick={() => setPlaying((p) => !p)}
            className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-blue-500 text-white font-medium hover:bg-blue-600 transition-colors"
          >
            {playing ? <Pause size={13} /> : <Play size={13} />}
            {playing ? 'Pause' : 'Play'}
          </button>
        </div>
      </div>

      <div className="p-5 space-y-3 max-h-[500px] overflow-y-auto">
        {steps.map((step, idx) => (
          <StepCard
            key={`${showFailure}-${step.id}`}
            step={step}
            isActive={idx === currentStep}
            isDone={idx < currentStep}
          />
        ))}
      </div>

      <div className="px-5 pb-4">
        <div className="flex gap-1">
          {steps.map((_, idx) => (
            <button
              key={idx}
              onClick={() => { setPlaying(false); setCurrentStep(idx) }}
              className={`h-1.5 rounded-full transition-all ${
                idx === currentStep
                  ? 'bg-blue-500 flex-1'
                  : idx < currentStep
                  ? 'bg-blue-200 flex-1'
                  : 'bg-gray-200 flex-1'
              }`}
            />
          ))}
        </div>
      </div>
    </div>
  )
}
