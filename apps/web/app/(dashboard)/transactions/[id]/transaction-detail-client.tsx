'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useSession } from 'next-auth/react'
import { useAuthGuard } from '@/lib/useAuthGuard'
import {
  ArrowLeft,
  Copy,
  Loader2,
  AlertCircle,
  Lightbulb,
  RefreshCw,
  Stethoscope,
} from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import { toast } from 'sonner'
import {
  useTransaction,
  useTransactionStatusSocket,
  useResolveError,
  transactionKeys,
} from '@aegispay/api-client'
import { useQueryClient } from '@tanstack/react-query'
import { AegisBadge, AegisStatusTimeline } from '@aegispay/design-system'
import { Header } from '@/components/header'
import { formatAmount, formatDate, copyToClipboard, resolveWsUrl } from '@/lib/utils'
import type { TransactionStatusUpdate } from '@aegispay/shared-types'

const TERMINAL = new Set(['COMPLETED', 'FAILED', 'ROLLED_BACK'])
const FAILED   = new Set(['FAILED', 'ROLLED_BACK'])

interface TransactionDetailClientProps {
  transactionId: string
}

export function TransactionDetailClient({
  transactionId,
}: TransactionDetailClientProps) {
  const blocking           = useAuthGuard()
  const router             = useRouter()
  const { data: session }  = useSession()
  const queryClient        = useQueryClient()

  const isAdmin = session?.user?.role === 'ADMIN'

  const { data: tx, isLoading, isError } = useTransaction(transactionId)
  const resolveError = useResolveError()

  // WebSocket: subscribe to transaction-service per-transaction status topic.
  // Skip the connection entirely when the transaction is already in a terminal
  // state (COMPLETED / FAILED / ROLLED_BACK) — nothing more will arrive on the
  // topic and keeping a dead socket alive wastes resources.
  const txWsBaseUrl = resolveWsUrl(process.env.NEXT_PUBLIC_TX_WS_BASE_URL ?? 'ws://localhost:8082')
  useTransactionStatusSocket({
    transactionId,
    accessToken: session?.accessToken ?? null,
    wsBaseUrl:   txWsBaseUrl,
    // tx is undefined while loading — default to enabled so we can catch the
    // status transition live. Once tx loads and is terminal, disconnect.
    enabled: !tx || !TERMINAL.has(tx.status),
    onStatusUpdate(update: TransactionStatusUpdate) {
      // Patch cache immediately — no extra fetch
      queryClient.setQueryData(
        transactionKeys.detail(transactionId),
        (prev: Record<string, unknown> | undefined) =>
          prev ? {
            ...prev,
            status: update.status,
            ...(update.failureReason != null && { failureReason: update.failureReason }),
            ...(update.failureCode   != null && { failureCode:   update.failureCode }),
          } : prev,
      )
      // Toast on terminal transitions
      if (update.status === 'COMPLETED') {
        toast.success('Payment completed!', { duration: 6_000 })
      } else if (update.status === 'FAILED' || update.status === 'ROLLED_BACK') {
        toast.error('Payment failed', { description: 'Check the details below.', duration: 6_000 })
      }
    },
  })

  // Auto-trigger AI resolution when a FAILED transaction loads for the first time
  useEffect(() => {
    if (
      tx &&
      FAILED.has(tx.status) &&
      tx.failureReason &&
      !resolveError.data &&
      !resolveError.isPending
    ) {
      const rawCode = tx.failureCode && tx.failureCode !== 'null' ? tx.failureCode : null
      const derivedCode = tx.failureReason
        ? tx.failureReason.split(':')[0].trim() || 'UNKNOWN_ERROR'
        : 'UNKNOWN_ERROR'
      resolveError.mutate({
        errorCode:    rawCode ?? derivedCode,
        errorMessage: tx.failureReason ?? undefined,
      })
    }
  // Only run when tx first loads (not on every render)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tx?.transactionId, tx?.status])

  async function handleCopy(value: string, label: string) {
    await copyToClipboard(value)
    toast.success(`${label} copied`)
  }

  if (blocking) return null

  // ── Loading ──────────────────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <>
        <Header title="Transaction" />
        <div className="flex h-64 items-center justify-center">
          <Loader2 className="h-8 w-8 animate-spin text-slate-300" />
        </div>
      </>
    )
  }

  if (isError || !tx) {
    return (
      <>
        <Header title="Transaction" />
        <div className="flex h-64 flex-col items-center justify-center gap-3 text-slate-400">
          <AlertCircle className="h-10 w-10 opacity-40" />
          <p className="text-sm">Transaction not found</p>
          <button onClick={() => router.back()} className="text-xs text-primary-600 hover:underline">
            Go back
          </button>
        </div>
      </>
    )
  }

  const isTerminal = TERMINAL.has(tx.status)
  const isFailed   = FAILED.has(tx.status)

  // ── Render ───────────────────────────────────────────────────────────────────
  return (
    <>
      <Header title="Transaction Detail" />

      <div className="p-6 max-w-2xl space-y-5 animate-fade-in">
        {/* Back */}
        <button
          onClick={() => router.back()}
          className="flex items-center gap-1.5 text-sm font-medium text-slate-500 hover:text-slate-700"
        >
          <ArrowLeft className="h-4 w-4" />
          Back
        </button>

        {/* Amount + timeline card */}
        <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
          <div className="mb-5 flex items-start justify-between">
            <div>
              <p className="text-xs font-medium uppercase tracking-widest text-slate-400">Amount</p>
              <p className="mt-1 text-3xl font-bold text-slate-900">
                {formatAmount(tx.amount, tx.currency)}
              </p>
            </div>
            <AegisBadge status={tx.status} />
          </div>

          <AegisStatusTimeline currentStatus={tx.status} failureReason={tx.failureReason} />

          {!isTerminal && (
            <div className="mt-4 flex items-center gap-2 rounded-lg bg-primary-50 px-3 py-2 text-xs text-primary-700">
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
              Live updates active — WebSocket connected
            </div>
          )}
        </div>

        {/* AI error resolution — shown only when transaction failed */}
        {isFailed && (
          <div className="rounded-xl bg-amber-50 p-5 shadow-sm ring-1 ring-amber-200">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Lightbulb className="h-4 w-4 text-amber-600" />
                <h3 className="text-sm font-semibold text-amber-800">
                  AI Error Explanation
                </h3>
              </div>
              {/* Re-trigger button */}
              {!resolveError.isPending && (
                <button
                  onClick={() => {
                    const rawCode = tx.failureCode && tx.failureCode !== 'null' ? tx.failureCode : null
                    const derivedCode = tx.failureReason
                      ? tx.failureReason.split(':')[0].trim() || 'UNKNOWN_ERROR'
                      : 'UNKNOWN_ERROR'
                    resolveError.mutate({
                      errorCode:    rawCode ?? derivedCode,
                      errorMessage: tx.failureReason ?? undefined,
                    })
                  }}
                  className="flex items-center gap-1 text-xs text-amber-600 hover:text-amber-800"
                >
                  <RefreshCw className="h-3 w-3" />
                  Retry
                </button>
              )}
            </div>

            {resolveError.isPending ? (
              <div className="flex items-center gap-2 text-sm text-amber-700">
                <Loader2 className="h-4 w-4 animate-spin" />
                Analysing failure reason…
              </div>
            ) : resolveError.isError ? (
              <p className="text-sm text-amber-700">
                Could not retrieve AI explanation. {tx.failureReason && (
                  <span className="font-mono text-xs">{tx.failureReason}</span>
                )}
              </p>
            ) : resolveError.data ? (
              <div className="space-y-2">
                <ReactMarkdown
                  components={{
                    p:      ({ children }) => <p className="text-sm text-amber-800 leading-relaxed">{children}</p>,
                    strong: ({ children }) => <strong className="font-semibold text-amber-900">{children}</strong>,
                    em:     ({ children }) => <em className="italic">{children}</em>,
                  }}
                >
                  {resolveError.data.resolution}
                </ReactMarkdown>
                {resolveError.data.errorCode && resolveError.data.errorCode !== 'null' && (
                  <p className="text-xs text-amber-600 font-mono">
                    Code: {resolveError.data.errorCode}
                  </p>
                )}
              </div>
            ) : tx.failureReason ? (
              <p className="text-sm text-amber-700 font-mono">{tx.failureReason}</p>
            ) : null}
          </div>
        )}

        {/* ADMIN — Triage incident shortcut (shown for failed transactions) */}
        {isAdmin && isFailed && (
          <div className="flex justify-end">
            <button
              onClick={() =>
                router.push(
                  `/triage?txId=${tx.transactionId}&service=payment-orchestrator`,
                )
              }
              className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-600 shadow-sm hover:border-indigo-300 hover:text-indigo-700 transition-colors"
            >
              <Stethoscope className="h-4 w-4" />
              Triage Incident
            </button>
          </div>
        )}

        {/* Details table */}
        <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
          <h3 className="mb-4 text-sm font-semibold text-slate-700">Transaction Details</h3>
          <dl className="space-y-3 text-sm">
            {[
              { label: 'Transaction ID', value: tx.transactionId, copy: true },
              { label: 'Payee ID',       value: tx.payeeId,       copy: true },
              { label: 'Initiated',      value: formatDate(tx.initiatedAt) },
              ...(tx.completedAt
                ? [{ label: 'Completed', value: formatDate(tx.completedAt) }]
                : []),
              ...(tx.failureReason
                ? [{ label: 'Failure reason', value: tx.failureReason }]
                : []),
              { label: 'Currency', value: tx.currency },
              ...(tx.note ? [{ label: 'Note', value: tx.note }] : []),
            ].map(({ label, value, copy }) => (
              <div key={label} className="flex items-start justify-between gap-4">
                <dt className="text-slate-400 shrink-0">{label}</dt>
                <dd className="flex items-center gap-1.5 text-right font-mono text-xs text-slate-700">
                  <span className="break-all">{value}</span>
                  {copy && (
                    <button
                      onClick={() => handleCopy(value!, label)}
                      className="shrink-0 text-slate-300 hover:text-slate-500"
                      title="Copy"
                    >
                      <Copy className="h-3.5 w-3.5" />
                    </button>
                  )}
                </dd>
              </div>
            ))}
          </dl>
        </div>
      </div>
    </>
  )
}
