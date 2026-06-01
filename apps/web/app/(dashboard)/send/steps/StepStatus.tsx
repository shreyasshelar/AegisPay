'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useSession } from 'next-auth/react'
import { useQueryClient } from '@tanstack/react-query'
import {
  CheckCircle2,
  XCircle,
  Loader2,
  RotateCcw,
  ExternalLink,
  Lightbulb,
} from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import { toast } from 'sonner'
import {
  useTransaction,
  useTransactionStatusSocket,
  useResolveError,
  transactionKeys,
} from '@aegispay/api-client'
import { AegisStatusTimeline } from '@aegispay/design-system'
import { Button as AegisButton } from '@aegispay/design-system'
import { useSendMoneyStore } from '@/lib/useSendMoneyStore'
import { formatAmount, resolveWsUrl } from '@/lib/utils'
import type { TransactionStatusUpdate, Transaction } from '@aegispay/shared-types'

const TERMINAL = new Set(['COMPLETED', 'FAILED', 'ROLLED_BACK'])
const FAILED   = new Set(['FAILED', 'ROLLED_BACK'])

export function StepStatus() {
  const router       = useRouter()
  const { data: session } = useSession()
  const queryClient  = useQueryClient()
  const { transactionId, amount, currency, reset } = useSendMoneyStore()

  const {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    data: txRaw,
    isLoading,
  } = useTransaction(transactionId ?? '', {
    enabled: !!transactionId,
  })
  const tx = txRaw as Transaction | undefined

  const resolveError = useResolveError()

  // WebSocket: subscribe to /topic/transactions/{id}/status on transaction-service
  const txWsBaseUrl = resolveWsUrl(process.env.NEXT_PUBLIC_TX_WS_BASE_URL ?? 'ws://localhost:8082')
  useTransactionStatusSocket({
    transactionId: transactionId ?? null,
    accessToken:   session?.accessToken ?? null,
    wsBaseUrl:     txWsBaseUrl,
    enabled:       !!transactionId,
    onStatusUpdate(update: TransactionStatusUpdate) {
      // Patch cache directly — no round-trip needed
      queryClient.setQueryData(
        transactionKeys.detail(update.transactionId),
        (prev: Record<string, unknown> | undefined) =>
          prev ? { ...prev, status: update.status, ...(update.failureReason != null && { failureReason: update.failureReason }) } : prev,
      )
    },
  })

  // Haptic-equivalent: browser notification on completion
  useEffect(() => {
    if (!tx) return
    if (tx.status === 'COMPLETED') {
      const n = parseFloat(amount)
      const displayAmt = isNaN(n) ? `${currency} ${amount}` : formatAmount(n, currency)
      toast.success('Payment completed!', {
        description: `${displayAmt} sent successfully.`,
        duration: 8_000,
      })
    }
  }, [tx?.status])

  // Auto-trigger AI error resolution on failure
  useEffect(() => {
    if (
      tx &&
      FAILED.has(tx.status) &&
      tx.failureReason &&
      !resolveError.data &&
      !resolveError.isPending
    ) {
      // Derive a short machine-readable code for the AI:
      // 1. Use failureCode if present and not the literal string "null"
      // 2. Otherwise take the first colon-delimited token of failureReason (e.g. "INSUFFICIENT_FUNDS: ..." → "INSUFFICIENT_FUNDS")
      // 3. Fall back to "UNKNOWN_ERROR"
      const rawCode = tx.failureCode && tx.failureCode !== 'null' ? tx.failureCode : null
      const derivedCode = tx.failureReason
        ? tx.failureReason.split(':')[0].trim() || 'UNKNOWN_ERROR'
        : 'UNKNOWN_ERROR'
      resolveError.mutate({
        errorCode:    rawCode ?? derivedCode,
        errorMessage: tx.failureReason,
      })
    }
  // Only re-run when the transaction first loads or status changes
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tx?.transactionId, tx?.status])

  if (!transactionId || isLoading) {
    return (
      <div className="flex h-64 flex-col items-center justify-center gap-3">
        <Loader2 className="h-8 w-8 animate-spin text-slate-300" />
        <p className="text-sm text-slate-400">Starting transfer…</p>
      </div>
    )
  }

  if (!tx) return null

  const isTerminal = TERMINAL.has(tx.status)
  const isFailed   = FAILED.has(tx.status)
  const isComplete = tx.status === 'COMPLETED'

  return (
    <div className="space-y-5 animate-fade-in">
      {/* Main status card */}
      <div className="rounded-2xl bg-white p-8 shadow-sm ring-1 ring-slate-200 space-y-6">
        {/* Amount + final state icon */}
        <div className="text-center space-y-2">
          {isComplete && (
            <CheckCircle2 className="mx-auto h-14 w-14 text-success-500" />
          )}
          {isFailed && (
            <XCircle className="mx-auto h-14 w-14 text-danger-500" />
          )}
          {!isTerminal && (
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-primary-50">
              <Loader2 className="h-7 w-7 animate-spin text-primary-600" />
            </div>
          )}

          <p className="text-2xl font-bold text-slate-900">
            {(() => { const n = parseFloat(amount); return isNaN(n) ? `${currency} ${amount}` : formatAmount(n, currency) })()}
          </p>
          <p
            className={`text-sm font-medium ${
              isComplete
                ? 'text-success-600'
                : isFailed
                  ? 'text-danger-600'
                  : 'text-primary-600'
            }`}
          >
            {isComplete
              ? 'Transfer Complete'
              : isFailed
                ? 'Transfer Failed'
                : 'Transfer in Progress…'}
          </p>
        </div>

        {/* Live indicator */}
        {!isTerminal && (
          <div className="flex items-center justify-center gap-2 rounded-lg bg-primary-50 px-3 py-2 text-xs text-primary-700">
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            Live updates active
          </div>
        )}

        {/* Timeline */}
        <AegisStatusTimeline currentStatus={tx.status} failureReason={tx.failureReason} />
      </div>

      {/* AI error explanation — shown only on failure */}
      {isFailed && (
        <div className="rounded-xl bg-amber-50 p-5 shadow-sm ring-1 ring-amber-200 space-y-3">
          <div className="flex items-center gap-2">
            <Lightbulb className="h-4 w-4 text-amber-600" />
            <h3 className="text-sm font-semibold text-amber-800">
              What went wrong?
            </h3>
          </div>

          {resolveError.isPending ? (
            <div className="flex items-center gap-2 text-sm text-amber-700">
              <Loader2 className="h-4 w-4 animate-spin" />
              Analysing failure…
            </div>
          ) : resolveError.data ? (
            <div className="space-y-1">
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
                <p className="font-mono text-xs text-amber-600">
                  Code: {resolveError.data.errorCode}
                </p>
              )}
            </div>
          ) : tx.failureReason ? (
            <p className="font-mono text-xs text-amber-700">{tx.failureReason}</p>
          ) : null}
        </div>
      )}

      {/* CTA buttons */}
      <div className="space-y-3">
        <AegisButton
          onClick={() =>
            router.push(`/transactions/${tx.transactionId}`)
          }
          className="w-full"
        >
          <ExternalLink className="h-4 w-4" />
          View Full Details
        </AegisButton>

        <AegisButton
          variant="secondary"
          onClick={reset}
          className="w-full"
        >
          <RotateCcw className="h-4 w-4" />
          {isFailed ? 'Try Again' : 'Send Another'}
        </AegisButton>
      </div>
    </div>
  )
}
