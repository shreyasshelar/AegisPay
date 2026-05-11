'use client'

import { useRef, useCallback, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Loader2, TrendingUp, Filter, X } from 'lucide-react'
import { useInfiniteTransactions } from '@aegispay/api-client'
import { AegisTransactionRow } from '@aegispay/design-system'
import { Header } from '@/components/header'
import type { TransactionStatus } from '@aegispay/shared-types'

const STATUS_OPTIONS: { label: string; value: TransactionStatus | '' }[] = [
  { label: 'All',          value: '' },
  { label: 'Initiated',    value: 'INITIATED' },
  { label: 'Reserved',     value: 'RESERVED' },
  { label: 'Risk Cleared', value: 'RISK_CLEARED' },
  { label: 'Processing',   value: 'PROCESSING' },
  { label: 'Completed',    value: 'COMPLETED' },
  { label: 'Failed',       value: 'FAILED' },
  { label: 'Rolled Back',  value: 'ROLLED_BACK' },
]

interface TransactionsClientProps {
  userId: string
}

export function TransactionsClient({ userId }: TransactionsClientProps) {
  const router = useRouter()

  // ── Filter state ────────────────────────────────────────────────────────────
  const [status,   setStatus]   = useState<TransactionStatus | ''>('')
  const [fromDate, setFromDate] = useState('')
  const [toDate,   setToDate]   = useState('')

  const activeFilters = {
    ...(status   ? { status }                     : {}),
    ...(fromDate ? { fromDate: `${fromDate}T00:00:00Z` } : {}),
    ...(toDate   ? { toDate:   `${toDate}T23:59:59Z` }   : {}),
  }

  const hasFilters = status !== '' || fromDate !== '' || toDate !== ''

  // ── Data ────────────────────────────────────────────────────────────────────
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading,
  } = useInfiniteTransactions({ size: 20, ...activeFilters })

  // ── Infinite scroll sentinel ────────────────────────────────────────────────
  const observerRef = useRef<IntersectionObserver | null>(null)
  const sentinelRef = useCallback(
    (node: HTMLDivElement | null) => {
      if (isLoading) return
      observerRef.current?.disconnect()
      observerRef.current = new IntersectionObserver((entries) => {
        if (entries[0].isIntersecting && hasNextPage && !isFetchingNextPage) {
          fetchNextPage()
        }
      })
      if (node) observerRef.current.observe(node)
    },
    [isLoading, hasNextPage, isFetchingNextPage, fetchNextPage],
  )

  const allTransactions = data?.pages.flatMap((p) => p.content) ?? []
  const totalElements   = data?.pages[0]?.totalElements

  // ── Render ──────────────────────────────────────────────────────────────────
  return (
    <>
      <Header
        title="Transactions"
        subtitle={totalElements != null ? `${totalElements} total` : undefined}
      />

      <div className="p-6 space-y-4 animate-fade-in">
        {/* Filter bar */}
        <div className="flex flex-wrap items-end gap-3 rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <div className="flex items-center gap-2 text-sm font-medium text-slate-500">
            <Filter className="h-4 w-4" />
            Filters
          </div>

          {/* Status */}
          <div className="flex flex-col gap-1">
            <label className="text-xs text-slate-400">Status</label>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as TransactionStatus | '')}
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            >
              {STATUS_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>

          {/* From date */}
          <div className="flex flex-col gap-1">
            <label className="text-xs text-slate-400">From</label>
            <input
              type="date"
              value={fromDate}
              max={toDate || undefined}
              onChange={(e) => setFromDate(e.target.value)}
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            />
          </div>

          {/* To date */}
          <div className="flex flex-col gap-1">
            <label className="text-xs text-slate-400">To</label>
            <input
              type="date"
              value={toDate}
              min={fromDate || undefined}
              onChange={(e) => setToDate(e.target.value)}
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            />
          </div>

          {/* Clear */}
          {hasFilters && (
            <button
              onClick={() => { setStatus(''); setFromDate(''); setToDate('') }}
              className="flex items-center gap-1 rounded-lg px-3 py-1.5 text-sm text-slate-500 hover:bg-slate-50"
            >
              <X className="h-3.5 w-3.5" />
              Clear
            </button>
          )}
        </div>

        {/* Transaction list */}
        <div className="rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
          {isLoading ? (
            <div className="flex items-center justify-center py-24">
              <Loader2 className="h-8 w-8 animate-spin text-slate-300" />
            </div>
          ) : allTransactions.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-24 text-slate-400">
              <TrendingUp className="mb-3 h-12 w-12 opacity-20" />
              <p className="text-sm font-medium">
                {hasFilters ? 'No transactions match your filters' : 'No transactions yet'}
              </p>
              {hasFilters && (
                <button
                  onClick={() => { setStatus(''); setFromDate(''); setToDate('') }}
                  className="mt-2 text-xs text-primary-600 hover:underline"
                >
                  Clear filters
                </button>
              )}
            </div>
          ) : (
            <>
              <div className="divide-y divide-slate-50">
                {allTransactions.map((tx) => (
                  <AegisTransactionRow
                    key={tx.transactionId}
                    transaction={tx}
                    currentUserId={userId}
                    onClick={() =>
                      router.push(`/transactions/${tx.transactionId}`)
                    }
                  />
                ))}
              </div>

              {/* Infinite scroll sentinel */}
              <div ref={sentinelRef} className="py-4 text-center">
                {isFetchingNextPage && (
                  <Loader2 className="mx-auto h-5 w-5 animate-spin text-slate-300" />
                )}
                {!hasNextPage && allTransactions.length > 0 && (
                  <p className="text-xs text-slate-300">All transactions loaded</p>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </>
  )
}
