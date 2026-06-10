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
  Filter,
  X,
  ChevronLeft,
  ChevronRight as ChevronRightIcon,
} from 'lucide-react'
import { useApiClient, useRiskCases } from '@aegispay/api-client'
import { Header } from '@/components/header'
import { formatDate, maskId, localDateToUtcStart, localDateToUtcEnd } from '@/lib/utils'
import type { RiskCase, FraudExplainResponse } from '@aegispay/shared-types'

// ── Constants ─────────────────────────────────────────────────────────────────

const PAGE_SIZE = 20

const DECISION_OPTIONS = [
  { label: 'All decisions', value: '' },
  { label: 'Approved',      value: 'APPROVED' },
  { label: 'Review',        value: 'REVIEW' },
  { label: 'Rejected',      value: 'REJECTED' },
]

// ── Sub-components ────────────────────────────────────────────────────────────

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

// ── Pagination control ────────────────────────────────────────────────────────

function Pagination({
  page,
  totalPages,
  totalElements,
  pageSize,
  onPrev,
  onNext,
  onPage,
  isFetching,
}: {
  page: number
  totalPages: number
  totalElements: number
  pageSize: number
  onPrev: () => void
  onNext: () => void
  onPage: (p: number) => void
  isFetching: boolean
}) {
  if (totalPages <= 1) return null

  // Show at most 5 page buttons centred around current page
  const windowSize = 5
  const half = Math.floor(windowSize / 2)
  const start = Math.max(0, Math.min(page - half, totalPages - windowSize))
  const end   = Math.min(totalPages - 1, start + windowSize - 1)
  const pages = Array.from({ length: end - start + 1 }, (_, i) => start + i)

  const from = page * pageSize + 1
  const to   = Math.min((page + 1) * pageSize, totalElements)

  return (
    <div className="flex items-center justify-between border-t border-slate-100 px-5 py-3">
      <p className="text-xs text-slate-400">
        {from}–{to} of {totalElements}
      </p>
      <div className="flex items-center gap-1">
        <button
          onClick={onPrev}
          disabled={page === 0 || isFetching}
          className="rounded p-1.5 text-slate-400 hover:bg-slate-50 disabled:opacity-30"
          aria-label="Previous page"
        >
          <ChevronLeft className="h-4 w-4" />
        </button>

        {start > 0 && (
          <>
            <button onClick={() => onPage(0)} className="rounded px-2.5 py-1 text-xs text-slate-500 hover:bg-slate-50">1</button>
            {start > 1 && <span className="px-1 text-xs text-slate-300">…</span>}
          </>
        )}

        {pages.map((p) => (
          <button
            key={p}
            onClick={() => onPage(p)}
            disabled={isFetching}
            className={`rounded px-2.5 py-1 text-xs font-medium transition ${
              p === page
                ? 'bg-primary-600 text-white'
                : 'text-slate-500 hover:bg-slate-50'
            }`}
          >
            {p + 1}
          </button>
        ))}

        {end < totalPages - 1 && (
          <>
            {end < totalPages - 2 && <span className="px-1 text-xs text-slate-300">…</span>}
            <button onClick={() => onPage(totalPages - 1)} className="rounded px-2.5 py-1 text-xs text-slate-500 hover:bg-slate-50">
              {totalPages}
            </button>
          </>
        )}

        <button
          onClick={onNext}
          disabled={page >= totalPages - 1 || isFetching}
          className="rounded p-1.5 text-slate-400 hover:bg-slate-50 disabled:opacity-30"
          aria-label="Next page"
        >
          <ChevronRightIcon className="h-4 w-4" />
        </button>
      </div>
    </div>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

export function RiskCasesClient() {
  const { ai } = useApiClient()

  // ── Filter state ────────────────────────────────────────────────────────────
  const [page,      setPage]      = useState(0)
  const [decision,  setDecision]  = useState('')
  const [minScore,  setMinScore]  = useState('')
  const [maxScore,  setMaxScore]  = useState('')
  const [fromDate,  setFromDate]  = useState('')
  const [toDate,    setToDate]    = useState('')

  const hasFilters =
    decision !== '' || minScore !== '' || maxScore !== '' ||
    fromDate !== '' || toDate !== ''

  const queryParams = {
    page,
    size: PAGE_SIZE,
    ...(decision               ? { decision }                              : {}),
    ...(minScore !== ''        ? { minScore: Number(minScore) }            : {}),
    ...(maxScore !== ''        ? { maxScore: Number(maxScore) }            : {}),
    // Local (IST) date → UTC conversion: see localDateToUtcStart/End in utils.ts
    ...(fromDate               ? { fromDate: localDateToUtcStart(fromDate) } : {}),
    ...(toDate                 ? { toDate:   localDateToUtcEnd(toDate) }     : {}),
  }

  const clearFilters = () => {
    setDecision(''); setMinScore(''); setMaxScore('')
    setFromDate('');  setToDate('');   setPage(0)
  }

  // When a filter changes, reset to page 0
  const applyFilter = (fn: () => void) => { fn(); setPage(0) }

  // ── Data ────────────────────────────────────────────────────────────────────
  const {
    data,
    isLoading,
    isError,
    refetch,
    isFetching,
  } = useRiskCases(queryParams)

  const cases         = data?.content ?? []
  const totalElements = data?.totalElements ?? 0
  const totalPages    = data?.totalPages    ?? 0

  // ── Case selection + AI explanation ─────────────────────────────────────────
  const [selectedCase, setSelectedCase] = useState<RiskCase | null>(null)
  const [explanation,  setExplanation]  = useState<FraudExplainResponse | null>(null)

  const flaggedRules = (rc: RiskCase) => rc.ruleFlags ?? []

  const { mutate: explain, isPending: explaining } = useMutation({
    mutationFn: (riskCase: RiskCase) =>
      ai.explainFraud({
        transactionId: riskCase.transactionId,
        riskScore:     riskCase.riskScore,
        flaggedRules:  flaggedRules(riskCase),
      }),
    onSuccess: (res) => setExplanation(res),
    onError:   (err) =>
      toast.error('AI explanation failed', {
        description: err instanceof Error ? err.message : 'Unknown error',
      }),
  })

  // ── Render ──────────────────────────────────────────────────────────────────
  return (
    <>
      <Header
        title="Risk Cases"
        subtitle={
          totalElements > 0
            ? `${totalElements} case${totalElements !== 1 ? 's' : ''}`
            : 'AI-augmented fraud review queue'
        }
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

      <div className="p-6 space-y-4 animate-fade-in">
        {/* ── Filter bar ───────────────────────────────────────────────────── */}
        <div className="flex flex-wrap items-end gap-3 rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <div className="flex items-center gap-2 text-sm font-medium text-slate-500">
            <Filter className="h-4 w-4" />
            Filters
          </div>

          {/* Decision */}
          <div className="flex flex-col gap-1">
            <label className="text-xs text-slate-400">Decision</label>
            <select
              value={decision}
              onChange={(e) => applyFilter(() => setDecision(e.target.value))}
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            >
              {DECISION_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </div>

          {/* Score range */}
          <div className="flex flex-col gap-1">
            <label className="text-xs text-slate-400">Min score</label>
            <input
              type="number"
              min={0}
              max={100}
              placeholder="0"
              value={minScore}
              onChange={(e) => applyFilter(() => setMinScore(e.target.value))}
              className="w-20 rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            />
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs text-slate-400">Max score</label>
            <input
              type="number"
              min={0}
              max={100}
              placeholder="100"
              value={maxScore}
              onChange={(e) => applyFilter(() => setMaxScore(e.target.value))}
              className="w-20 rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            />
          </div>

          {/* Date range */}
          <div className="flex flex-col gap-1">
            <label className="text-xs text-slate-400">From</label>
            <input
              type="date"
              value={fromDate}
              max={toDate || undefined}
              onChange={(e) => applyFilter(() => setFromDate(e.target.value))}
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            />
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs text-slate-400">To</label>
            <input
              type="date"
              value={toDate}
              min={fromDate || undefined}
              onChange={(e) => applyFilter(() => setToDate(e.target.value))}
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            />
          </div>

          {/* Clear */}
          {hasFilters && (
            <button
              onClick={clearFilters}
              className="flex items-center gap-1 rounded-lg px-3 py-1.5 text-sm text-slate-500 hover:bg-slate-50"
            >
              <X className="h-3.5 w-3.5" />
              Clear
            </button>
          )}

          {/* Fetching indicator */}
          {isFetching && !isLoading && (
            <Loader2 className="ml-auto h-4 w-4 animate-spin self-center text-slate-300" />
          )}
        </div>

        {/* ── Content area ─────────────────────────────────────────────────── */}
        {/* On mobile: single column stack. On lg+: side-by-side. */}
        <div className="flex flex-col gap-4 lg:flex-row lg:gap-6">
          {/* ── Cases list ───────────────────────────────────────────────────── */}
          <div className="flex-1 rounded-xl bg-white shadow-sm ring-1 ring-slate-200 overflow-hidden">
            {isLoading ? (
              <div className="flex h-48 items-center justify-center">
                <Loader2 className="h-7 w-7 animate-spin text-slate-300" />
              </div>
            ) : isError ? (
              <div className="flex flex-col items-center justify-center py-20 text-slate-400">
                <AlertTriangle className="mb-3 h-10 w-10 opacity-40 text-danger-400" />
                <p className="text-sm font-medium text-danger-500">Failed to load risk cases</p>
                <button onClick={() => refetch()} className="mt-2 text-xs text-primary-500 hover:underline">
                  Retry
                </button>
              </div>
            ) : cases.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-20 text-slate-400">
                <AlertTriangle className="mb-3 h-10 w-10 opacity-20" />
                <p className="text-sm font-medium">
                  {hasFilters ? 'No cases match your filters' : 'No risk cases'}
                </p>
                {hasFilters ? (
                  <button
                    onClick={clearFilters}
                    className="mt-2 text-xs text-primary-500 hover:underline"
                  >
                    Clear filters
                  </button>
                ) : (
                  <p className="mt-1 text-xs text-slate-300">All clear — no cases in the queue</p>
                )}
              </div>
            ) : (
              <>
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

                <Pagination
                  page={page}
                  totalPages={totalPages}
                  totalElements={totalElements}
                  pageSize={PAGE_SIZE}
                  isFetching={isFetching}
                  onPrev={() => setPage((p) => Math.max(0, p - 1))}
                  onNext={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  onPage={setPage}
                />
              </>
            )}
          </div>

          {/* ── Detail / AI panel ──────────────────────────────────────────────── */}
          {selectedCase && (
            <div className="w-full space-y-4 rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200 lg:w-96 lg:shrink-0">
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
                  <dt className="text-slate-400">User</dt>
                  <dd className="font-mono text-slate-700">{maskId(selectedCase.userId)}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-slate-400">Risk Score</dt>
                  <dd className="font-bold text-slate-900">{selectedCase.riskScore} / 100</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-slate-400">Created</dt>
                  <dd className="text-slate-700">{formatDate(selectedCase.createdAt)}</dd>
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
      </div>
    </>
  )
}
