'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  AlertTriangle,
  Loader2,
  ChevronRight,
  Sparkles,
  RefreshCw,
} from 'lucide-react'
import { useApiClient, useRiskCases } from '@aegispay/api-client'
import { Header } from '@/components/header'
import { formatDate, maskId } from '@/lib/utils'
import type { RiskCase, FraudExplainResponse } from '@aegispay/shared-types'

// ── Decision pill ─────────────────────────────────────────────────────────────

function RiskDecisionPill({ decision }: { decision: string }) {
  const colorMap: Record<string, string> = {
    APPROVED: 'bg-success-50 text-success-700 ring-success-200',
    REVIEW:   'bg-warning-50 text-warning-700 ring-warning-200',
    REJECTED: 'bg-danger-50 text-danger-700 ring-danger-200',
  }
  return (
    <span
      className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ${
        colorMap[decision] ?? 'bg-slate-50 text-slate-600 ring-slate-200'
      }`}
    >
      {decision}
    </span>
  )
}

// ── Risk score gauge ──────────────────────────────────────────────────────────

function RiskScoreBar({ score }: { score: number }) {
  const color =
    score >= 70 ? 'bg-danger-500'
    : score >= 30 ? 'bg-warning-400'
    : 'bg-success-500'
  return (
    <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
      <div
        className={`h-full rounded-full transition-all ${color}`}
        style={{ width: `${score}%` }}
      />
    </div>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

export function RiskCasesClient() {
  const { ai } = useApiClient()
  const [selectedCase, setSelectedCase] = useState<RiskCase | null>(null)
  const [explanation, setExplanation]   = useState<FraudExplainResponse | null>(null)

  const { data: cases, isLoading, refetch, isFetching } = useRiskCases()

  const flaggedRules = (rc: RiskCase) => Object.keys(rc.ruleFlags ?? {})

  const { mutate: explain, isPending: explaining } = useMutation({
    mutationFn: (riskCase: RiskCase) =>
      ai.explainFraud({
        transactionId: riskCase.transactionId,
        riskScore:     riskCase.riskScore,
        flaggedRules:  flaggedRules(riskCase),
      }),
    onSuccess: (data) => setExplanation(data),
    onError: (err) =>
      toast.error('AI explanation failed', {
        description: err instanceof Error ? err.message : 'Unknown error',
      }),
  })

  return (
    <>
      <Header
        title="Risk Cases"
        subtitle="AI-augmented fraud review queue"
        action={
          <button
            onClick={() => refetch()}
            disabled={isFetching}
            className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium text-slate-500 hover:bg-slate-100 disabled:opacity-40"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        }
      />

      <div className="flex gap-6 p-6 animate-fade-in">
        {/* ── Cases list ─────────────────────────────────────────────────────── */}
        <div className="flex-1 rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
          {isLoading ? (
            <div className="flex h-48 items-center justify-center">
              <Loader2 className="h-7 w-7 animate-spin text-slate-300" />
            </div>
          ) : !cases?.length ? (
            <div className="flex flex-col items-center justify-center py-20 text-slate-400">
              <AlertTriangle className="mb-3 h-10 w-10 opacity-20" />
              <p className="text-sm font-medium">No risk cases</p>
              <p className="mt-1 text-xs text-slate-300">All clear — no cases in the queue</p>
            </div>
          ) : (
            <ul className="divide-y divide-slate-50">
              {cases.map((rc) => (
                <li
                  key={rc.id}
                  onClick={() => {
                    setSelectedCase(rc)
                    setExplanation(null)
                  }}
                  className={`flex cursor-pointer items-center gap-4 px-5 py-4 transition hover:bg-slate-50 ${
                    selectedCase?.id === rc.id ? 'bg-primary-50' : ''
                  }`}
                >
                  <div className="flex-1 min-w-0">
                    <p className="truncate text-sm font-medium text-slate-800">
                      Tx {maskId(rc.transactionId)}
                    </p>
                    <p className="text-xs text-slate-400">{formatDate(rc.createdAt)}</p>
                  </div>
                  <div className="w-28 text-right">
                    <p className="text-sm font-bold text-slate-900">Score: {rc.riskScore}</p>
                    <RiskScoreBar score={rc.riskScore} />
                  </div>
                  <RiskDecisionPill decision={rc.decision} />
                  <ChevronRight className="h-4 w-4 shrink-0 text-slate-300" />
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* ── Detail / AI panel ──────────────────────────────────────────────── */}
        {selectedCase && (
          <div className="w-96 shrink-0 space-y-4 rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-slate-900">Case Detail</h3>
              <RiskDecisionPill decision={selectedCase.decision} />
            </div>

            <dl className="space-y-2.5 text-xs">
              <div className="flex justify-between">
                <dt className="text-slate-400">Transaction</dt>
                <dd className="font-mono text-slate-700">{maskId(selectedCase.transactionId)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-400">Risk Score</dt>
                <dd className="font-bold text-slate-900">{selectedCase.riskScore} / 100</dd>
              </div>
              <div>
                <dt className="mb-1 text-slate-400">Triggered Rules</dt>
                <dd className="flex flex-wrap gap-1">
                  {flaggedRules(selectedCase).length > 0
                    ? flaggedRules(selectedCase).map((rule) => (
                        <span
                          key={rule}
                          className="rounded bg-danger-50 px-1.5 py-0.5 font-mono text-[10px] text-danger-700 ring-1 ring-danger-100"
                        >
                          {rule}
                        </span>
                      ))
                    : <span className="text-slate-400">None</span>}
                </dd>
              </div>
              {selectedCase.ragExplanation && (
                <div>
                  <dt className="mb-1 text-slate-400">Stored RAG Note</dt>
                  <dd className="text-slate-600 leading-relaxed">{selectedCase.ragExplanation}</dd>
                </div>
              )}
            </dl>

            <button
              onClick={() => explain(selectedCase)}
              disabled={explaining || flaggedRules(selectedCase).length === 0}
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-primary-600 py-2 text-sm font-semibold text-white transition hover:bg-primary-700 disabled:opacity-60"
            >
              {explaining ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Sparkles className="h-4 w-4" />
              )}
              {explaining ? 'Asking AI…' : 'AI Fraud Explanation'}
            </button>

            {explanation && (
              <div className="rounded-lg bg-slate-50 p-4 ring-1 ring-slate-200">
                <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-slate-400">
                  AI Explanation
                </p>
                <p className="text-sm leading-relaxed text-slate-700">{explanation.explanation}</p>
              </div>
            )}
          </div>
        )}
      </div>
    </>
  )
}
