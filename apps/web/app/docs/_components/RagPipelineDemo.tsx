// FILE: apps/web/app/docs/_components/RagPipelineDemo.tsx
'use client'

import { useState, useEffect, useRef } from 'react'
import { Play, Pause, RotateCcw } from 'lucide-react'

const SAMPLE_TRANSACTION = {
  id: 'txn_8f2a1c',
  amount: '₹15,000',
  device: 'new (first-seen)',
  velocity: '5 transactions in 2 minutes',
  ruleFlags: ['HIGH_VELOCITY', 'NEW_DEVICE', 'LARGE_AMOUNT'],
  merchant: 'International Merchant #4471',
}

const RETRIEVED_DOCS = [
  'HIGH_VELOCITY rule: >3 transactions in 5min from same account triggers review.',
  'NEW_DEVICE pattern: First-time device login with large transaction is high-risk.',
  'LARGE_AMOUNT: Transactions >₹10,000 require additional verification.',
  'Pattern: Velocity + new device combination = 87% fraud probability historically.',
  'Remediation: Block transaction, freeze device session, notify user via all channels.',
]

const AI_EXPLANATION =
  'This transaction exhibits three high-risk indicators simultaneously: HIGH_VELOCITY (5 transactions in 2 minutes far exceeds the 3-in-5min threshold), NEW_DEVICE (device has never been seen for this account), and LARGE_AMOUNT (₹15,000 exceeds the ₹10,000 review threshold). Historical data shows this combination yields an 87% fraud probability. Recommendation: BLOCK and notify user immediately for verification.'

const STEPS = [
  {
    id: 0,
    icon: '📥',
    label: 'Input',
    sublabel: 'Transaction context from Risk Engine',
    color: 'bg-blue-50 border-blue-200',
    headerColor: 'bg-blue-500',
  },
  {
    id: 1,
    icon: '🔢',
    label: 'Embed Query',
    sublabel: 'text → vector[1536] via text-embedding-3-small',
    color: 'bg-purple-50 border-purple-200',
    headerColor: 'bg-purple-500',
  },
  {
    id: 2,
    icon: '🔍',
    label: 'Vector Search',
    sublabel: 'pgvector HNSW cosine similarity, top-5 docs',
    color: 'bg-indigo-50 border-indigo-200',
    headerColor: 'bg-indigo-500',
  },
  {
    id: 3,
    icon: '📝',
    label: 'Assemble Prompt',
    sublabel: 'context + retrieved docs + question',
    color: 'bg-amber-50 border-amber-200',
    headerColor: 'bg-amber-500',
  },
  {
    id: 4,
    icon: '🤖',
    label: 'LLM Generates',
    sublabel: 'Claude Sonnet → streaming explanation',
    color: 'bg-rose-50 border-rose-200',
    headerColor: 'bg-rose-500',
  },
  {
    id: 5,
    icon: '📋',
    label: 'Audit + Respond',
    sublabel: 'ai_audit_log → caller',
    color: 'bg-green-50 border-green-200',
    headerColor: 'bg-green-500',
  },
]

export default function RagPipelineDemo() {
  const [currentStep, setCurrentStep] = useState(-1)
  const [playing, setPlaying] = useState(false)
  const [streamedText, setStreamedText] = useState('')
  const [streamDone, setStreamDone] = useState(false)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const streamRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (playing) {
      intervalRef.current = setInterval(() => {
        setCurrentStep((prev) => {
          if (prev >= STEPS.length - 1) {
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
  }, [playing])

  // Stream text on step 4
  useEffect(() => {
    if (currentStep === 4 && !streamDone) {
      let i = 0
      setStreamedText('')
      streamRef.current = setInterval(() => {
        i += 3
        setStreamedText(AI_EXPLANATION.slice(0, i))
        if (i >= AI_EXPLANATION.length) {
          setStreamDone(true)
          if (streamRef.current) clearInterval(streamRef.current)
        }
      }, 30)
    }
    return () => {
      if (streamRef.current) clearInterval(streamRef.current)
    }
  }, [currentStep, streamDone])

  const reset = () => {
    setPlaying(false)
    setCurrentStep(-1)
    setStreamedText('')
    setStreamDone(false)
    if (intervalRef.current) clearInterval(intervalRef.current)
    if (streamRef.current) clearInterval(streamRef.current)
  }

  const start = () => {
    if (currentStep === -1) {
      setCurrentStep(0)
      setPlaying(true)
    } else {
      setPlaying((p) => !p)
    }
  }

  return (
    <div className="rounded-xl border border-gray-100 shadow-sm bg-white overflow-hidden">
      <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between flex-wrap gap-3">
        <div>
          <h3 className="font-semibold text-gray-900">RAG Pipeline — Live Demo</h3>
          <p className="text-sm text-gray-500 mt-0.5">Fraud transaction through the AI pipeline</p>
        </div>
        <div className="flex gap-2">
          <button onClick={reset} className="p-1.5 rounded-lg text-gray-500 hover:bg-gray-100">
            <RotateCcw size={14} />
          </button>
          <button
            onClick={start}
            className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-blue-500 text-white font-medium hover:bg-blue-600"
          >
            {playing ? <Pause size={12} /> : <Play size={12} />}
            {playing ? 'Pause' : currentStep === -1 ? 'Start' : 'Resume'}
          </button>
        </div>
      </div>

      {/* Sample input */}
      <div className="px-5 py-4 border-b border-gray-50 bg-gray-50">
        <p className="text-xs font-medium text-gray-500 uppercase tracking-wide mb-2">Sample Fraud Transaction</p>
        <div className="flex gap-4 flex-wrap text-xs">
          <span><span className="text-gray-400">ID:</span> <span className="font-mono text-gray-700">{SAMPLE_TRANSACTION.id}</span></span>
          <span><span className="text-gray-400">Amount:</span> <span className="font-bold text-red-600">{SAMPLE_TRANSACTION.amount}</span></span>
          <span><span className="text-gray-400">Device:</span> <span className="text-amber-600">{SAMPLE_TRANSACTION.device}</span></span>
          <span><span className="text-gray-400">Velocity:</span> <span className="text-red-600">{SAMPLE_TRANSACTION.velocity}</span></span>
        </div>
        <div className="flex gap-1.5 mt-2 flex-wrap">
          {SAMPLE_TRANSACTION.ruleFlags.map((flag) => (
            <span key={flag} className="text-xs bg-red-100 text-red-700 px-2 py-0.5 rounded-full font-medium">
              {flag}
            </span>
          ))}
        </div>
      </div>

      {/* Pipeline steps */}
      <div className="p-5">
        {/* Progress bar */}
        <div className="flex gap-1 mb-5">
          {STEPS.map((s) => (
            <div
              key={s.id}
              className={`h-1.5 flex-1 rounded-full transition-all duration-500 ${
                s.id <= currentStep ? 'bg-blue-500' : 'bg-gray-200'
              }`}
            />
          ))}
        </div>

        <div className="space-y-3">
          {STEPS.map((step) => {
            const isActive = step.id === currentStep
            const isDone = step.id < currentStep

            return (
              <div
                key={step.id}
                className={`rounded-xl border transition-all duration-300 overflow-hidden ${
                  isActive || isDone ? step.color : 'border-gray-100 bg-gray-50 opacity-40'
                }`}
              >
                <div className="flex items-center gap-3 px-4 py-3">
                  <span className="text-lg">{step.icon}</span>
                  <div className="flex-1">
                    <p className="text-sm font-semibold text-gray-900">{step.label}</p>
                    <p className="text-xs text-gray-500">{step.sublabel}</p>
                  </div>
                  {isDone && <span className="text-green-500 text-xs font-medium">✓ Done</span>}
                  {isActive && <span className="text-blue-500 text-xs font-medium animate-pulse">● Processing</span>}
                </div>

                {/* Step-specific content */}
                {(isActive || isDone) && step.id === 2 && (
                  <div className="px-4 pb-3">
                    <p className="text-xs text-gray-500 mb-2">Retrieved documents:</p>
                    <div className="space-y-1">
                      {RETRIEVED_DOCS.map((doc, i) => (
                        <div key={i} className="flex gap-2 text-xs">
                          <span className="text-indigo-400 font-bold shrink-0">#{i + 1}</span>
                          <span className="text-gray-600">{doc}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {(isActive || isDone) && step.id === 4 && (
                  <div className="px-4 pb-3">
                    <p className="text-xs text-gray-500 mb-2">Claude Sonnet response:</p>
                    <div className="bg-gray-900 text-gray-100 rounded-lg p-3 text-xs font-mono leading-relaxed min-h-[60px]">
                      {streamedText}
                      {isActive && !streamDone && (
                        <span className="inline-block w-1.5 h-3 bg-gray-100 animate-pulse ml-0.5" />
                      )}
                    </div>
                  </div>
                )}

                {(isActive || isDone) && step.id === 5 && (
                  <div className="px-4 pb-3">
                    <div className="bg-green-900 text-green-100 rounded-lg p-3 text-xs font-mono">
                      {`INSERT INTO ai_audit_log (txn_id, model, decision, explanation)\nVALUES ('${SAMPLE_TRANSACTION.id}', 'claude-sonnet-4-6', 'BLOCK', ...);\n→ 200 OK returned to Risk Engine`}
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
