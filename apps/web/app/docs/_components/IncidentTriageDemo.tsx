// FILE: apps/web/app/docs/_components/IncidentTriageDemo.tsx
'use client'

import { useState, useEffect, useRef } from 'react'
import { Play, RotateCcw } from 'lucide-react'

type StepType = 'think' | 'act' | 'report'

type TriageStep = {
  type: StepType
  title: string
  content: string
  tool?: string
  toolResult?: string
}

const INCIDENT_STEPS: TriageStep[] = [
  {
    type: 'think',
    title: 'Reasoning',
    content:
      'I can see saga failures spiking to 47/min at 14:31 in payment-orchestrator. This is severe — let me start by reading recent logs to find the root error.',
  },
  {
    type: 'act',
    title: 'Tool Call',
    tool: 'readLogs("payment-orchestrator", "15m")',
    toolResult: `[14:31:02] ERROR PaymentSagaCoordinator.java:247 - NullPointerException
  at com.aegispay.orchestrator.PaymentSagaCoordinator.processRisk(PaymentSagaCoordinator.java:247)
  at com.aegispay.orchestrator.SagaWorker.run(SagaWorker.java:89)
Caused by: java.lang.NullPointerException: Cannot invoke getRiskScore() on null
[14:31:03] ERROR PaymentSagaCoordinator.java:247 - NullPointerException (repeated x 47)`,
    content: 'Logs retrieved — clear NullPointerException pattern.',
  },
  {
    type: 'think',
    title: 'Reasoning',
    content:
      'NullPointerException at PaymentSagaCoordinator.java:247 calling getRiskScore(). This started right at 14:31. Let me check metrics to see when this failure rate began and correlate with deployment events.',
  },
  {
    type: 'act',
    title: 'Tool Call',
    tool: 'queryMetrics("payment-orchestrator", "saga_failure_rate", "30m")',
    toolResult: `14:00 ████░░░░░░ 0.2/min (baseline)
14:10 ████░░░░░░ 0.3/min
14:20 ████░░░░░░ 0.1/min
14:28 ████░░░░░░ 0.0/min  ← DEPLOY
14:29 ████████░░ 12/min
14:30 ██████████ 38/min
14:31 ██████████ 47/min  ← ALERT TRIGGERED`,
    content: 'Metrics show spike began at exactly 14:29, one minute after a deployment at 14:28.',
  },
  {
    type: 'think',
    title: 'Reasoning',
    content:
      'Clear correlation: NPE started after deployment at 14:28. getRiskScore() returning null suggests an API contract change in a dependency. Let me check what was deployed at 14:28.',
  },
  {
    type: 'act',
    title: 'Tool Call',
    tool: 'getDeploymentHistory("payment-orchestrator", "1h")',
    toolResult: `14:28:04 DEPLOY payment-orchestrator:2.1.4
  Commit: a4f7c2e
  Author: Rohan Mehta
  Message: feat: upgrade stripe sdk 5.0 → 6.0
  Changed files:
    - pom.xml (stripe-java 5.0.0 → 6.0.0)
    - PaymentSagaCoordinator.java (getRiskAssessment() call)`,
    content: 'Found it: Stripe SDK upgraded from 5.0 to 6.0 in this deployment.',
  },
  {
    type: 'think',
    title: 'Reasoning',
    content:
      'Stripe SDK 6.0 breaking change: getRiskAssessment() return type changed from Optional<RiskAssessment> to nullable RiskAssessment. The code calls .getRiskScore() without null check — NPE on every transaction. Fix requires rollback or null guard. Fastest resolution: rollback the deployment immediately.',
  },
  {
    type: 'act',
    title: 'Tool Call',
    tool: 'restartDeployment("payment-orchestrator", "2.1.3", "NPE in Stripe 6.0 SDK getRiskAssessment() nullable — rolling back to 2.1.3")',
    toolResult: `✅ Rolling back payment-orchestrator: 2.1.4 → 2.1.3
✅ New pods healthy (3/3 ready)
✅ Saga failure rate: 47/min → 0.2/min (14:33)
✅ PagerDuty incident #4821 resolved`,
    content: 'Rollback successful. Service restored within 2 minutes of diagnosis.',
  },
  {
    type: 'report',
    title: 'Incident Report',
    content: `**Root Cause**: Stripe SDK upgrade 5.0→6.0 introduced breaking change: \`getRiskAssessment()\` return type changed from \`Optional<RiskAssessment>\` to nullable \`RiskAssessment\`. Code at \`PaymentSagaCoordinator.java:247\` called \`.getRiskScore()\` without null guard.

**Contributing Factors**: No integration test covering null \`RiskAssessment\` response. SDK upgrade PR lacked changelog review for breaking changes.

**Actions Taken**: Identified NPE within 90s via log analysis. Correlated with deployment via metrics. Rolled back to 2.1.3 at 14:33.

**Remediation**: (1) Add null-safe guard in 2.1.5. (2) Add integration test for null risk assessment. (3) Add SDK changelog review to PR checklist.`,
  },
]

const STEP_STYLES: Record<StepType, { bg: string; border: string; badge: string; badgeBg: string }> = {
  think: {
    bg: 'bg-blue-50',
    border: 'border-blue-200',
    badge: '🧠 Reasoning',
    badgeBg: 'bg-blue-500 text-white',
  },
  act: {
    bg: 'bg-amber-50',
    border: 'border-amber-200',
    badge: '⚡ Tool',
    badgeBg: 'bg-amber-500 text-white',
  },
  report: {
    bg: 'bg-green-50',
    border: 'border-green-200',
    badge: '📋 Report',
    badgeBg: 'bg-green-600 text-white',
  },
}

export default function IncidentTriageDemo() {
  const [visibleSteps, setVisibleSteps] = useState(0)
  const [playing, setPlaying] = useState(false)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (playing) {
      intervalRef.current = setInterval(() => {
        setVisibleSteps((prev) => {
          if (prev >= INCIDENT_STEPS.length) {
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
  }, [playing])

  const reset = () => {
    setPlaying(false)
    setVisibleSteps(0)
  }

  const replay = () => {
    reset()
    setTimeout(() => setPlaying(true), 100)
  }

  return (
    <div className="rounded-xl border border-gray-100 shadow-sm bg-white overflow-hidden">
      <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between flex-wrap gap-3">
        <div>
          <h3 className="font-semibold text-gray-900">AI Incident Triage</h3>
          <p className="text-sm text-gray-500 mt-0.5">
            🚨 Incident: payment-orchestrator saga failures — 47/min at 14:31
          </p>
        </div>
        <div className="flex gap-2">
          <button onClick={reset} className="p-1.5 rounded-lg text-gray-500 hover:bg-gray-100">
            <RotateCcw size={14} />
          </button>
          {visibleSteps === 0 ? (
            <button
              onClick={() => setPlaying(true)}
              className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-red-500 text-white font-medium hover:bg-red-600"
            >
              <Play size={12} /> Triage Incident
            </button>
          ) : visibleSteps >= INCIDENT_STEPS.length ? (
            <button
              onClick={replay}
              className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-blue-500 text-white font-medium hover:bg-blue-600"
            >
              <RotateCcw size={12} /> Replay
            </button>
          ) : (
            <button
              onClick={() => setPlaying((p) => !p)}
              className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-blue-500 text-white font-medium hover:bg-blue-600"
            >
              {playing ? '⏸ Pause' : '▶ Resume'}
            </button>
          )}
        </div>
      </div>

      <div className="p-5 space-y-3 max-h-[600px] overflow-y-auto">
        {visibleSteps === 0 && (
          <div className="text-center py-12 text-sm text-gray-400">
            Press "Triage Incident" to watch the AI agent reason through the incident
          </div>
        )}
        {INCIDENT_STEPS.slice(0, visibleSteps).map((step, idx) => {
          const style = STEP_STYLES[step.type]
          return (
            <div
              key={idx}
              className={`rounded-xl border ${style.bg} ${style.border} overflow-hidden`}
            >
              <div className="flex items-center gap-2 px-4 py-2.5 border-b border-white/50">
                <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${style.badgeBg}`}>
                  {style.badge}
                </span>
                <span className="text-xs text-gray-400">Step {idx + 1}</span>
              </div>
              <div className="px-4 py-3 space-y-2">
                <p className="text-sm text-gray-700">{step.content}</p>
                {step.tool && (
                  <div className="mt-2">
                    <pre className="bg-gray-900 text-amber-300 rounded-lg p-3 text-xs font-mono overflow-x-auto">
                      {`→ ${step.tool}`}
                    </pre>
                    {step.toolResult && (
                      <pre className="bg-gray-800 text-gray-200 rounded-b-lg px-3 pb-3 text-xs font-mono overflow-x-auto -mt-2 pt-2 whitespace-pre-wrap">
                        {step.toolResult}
                      </pre>
                    )}
                  </div>
                )}
                {step.type === 'report' && (
                  <div className="mt-2 space-y-1.5 text-sm text-gray-700">
                    {step.content.split('\n\n').map((para, i) => (
                      <p key={i} className={para.startsWith('**') ? 'font-medium' : ''}>
                        {para.replace(/\*\*/g, '')}
                      </p>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
