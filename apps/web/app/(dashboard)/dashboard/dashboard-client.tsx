'use client'

import Link from 'next/link'
import { useAuthGuard } from '@/lib/useAuthGuard'
import {
  ArrowUpRight,
  Wallet,
  TrendingUp,
  Clock,
  CheckCircle2,
  XCircle,
  Send,
} from 'lucide-react'
import { useAccount, useTransactionList } from '@aegispay/api-client'
import { Header } from '@/components/header'
import { AegisBadge } from '@aegispay/design-system'
import { AegisTransactionRow } from '@aegispay/design-system'
import { formatAmount, formatDate } from '@/lib/utils'
import type { Transaction } from '@aegispay/shared-types'

// ── Skeleton loaders ─────────────────────────────────────────────────────────

function StatSkeleton() {
  return (
    <div className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
      <div className="skeleton mb-3 h-4 w-24 rounded" />
      <div className="skeleton h-8 w-36 rounded" />
    </div>
  )
}

function RowSkeleton() {
  return (
    <div className="flex items-center gap-4 px-5 py-4">
      <div className="skeleton h-8 w-8 rounded-full" />
      <div className="flex-1 space-y-2">
        <div className="skeleton h-3.5 w-32 rounded" />
        <div className="skeleton h-3 w-20 rounded" />
      </div>
      <div className="skeleton h-4 w-20 rounded" />
    </div>
  )
}

// ── Stat card ────────────────────────────────────────────────────────────────

interface StatCardProps {
  label:    string
  value:    string
  icon:     React.ElementType
  iconBg:   string
  iconColor: string
}

function StatCard({ label, value, icon: Icon, iconBg, iconColor }: StatCardProps) {
  return (
    <div className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
      <div className="mb-4 flex items-center justify-between">
        <span className="text-sm font-medium text-slate-500">{label}</span>
        <div className={`flex h-9 w-9 items-center justify-center rounded-lg ${iconBg}`}>
          <Icon className={`h-5 w-5 ${iconColor}`} />
        </div>
      </div>
      <p className="text-2xl font-bold tracking-tight text-slate-900">{value}</p>
    </div>
  )
}

// ── Main dashboard component ─────────────────────────────────────────────────

interface DashboardClientProps {
  userId: string
}

export function DashboardClient({ userId }: DashboardClientProps) {
  const blocking = useAuthGuard()
  const { data: account, isLoading: accountLoading } = useAccount(userId)
  const { data: txPage, isLoading: txLoading } = useTransactionList(
    { page: 0, size: 10 },
  )

  const recentTxs: Transaction[] = txPage?.content ?? []

  if (blocking) return null

  const completedCount = recentTxs.filter((t) => t.status === 'COMPLETED').length
  const failedCount    = recentTxs.filter((t) => t.status === 'FAILED').length
  const pendingCount   = recentTxs.filter(
    (t) => !['COMPLETED', 'FAILED', 'ROLLED_BACK'].includes(t.status),
  ).length

  return (
    <>
      <Header
        title="Dashboard"
        subtitle={`Welcome back${account ? ' — your balance is live' : ''}`}
      />

      <div className="p-6 space-y-6 animate-fade-in">
        {/* ── Stat cards ── */}
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {accountLoading ? (
            <>
              <StatSkeleton />
              <StatSkeleton />
              <StatSkeleton />
              <StatSkeleton />
            </>
          ) : (
            <>
              <StatCard
                label="Available Balance"
                value={formatAmount(
                  account?.availableBalance ?? 0,
                  account?.currency ?? 'INR',
                )}
                icon={Wallet}
                iconBg="bg-primary-100"
                iconColor="text-primary-600"
              />
              <StatCard
                label="Reserved"
                value={formatAmount(
                  account?.reservedBalance ?? 0,
                  account?.currency ?? 'INR',
                )}
                icon={Clock}
                iconBg="bg-warning-50"
                iconColor="text-warning-600"
              />
              <StatCard
                label="Completed (recent)"
                value={String(completedCount)}
                icon={CheckCircle2}
                iconBg="bg-success-50"
                iconColor="text-success-600"
              />
              <StatCard
                label="Failed (recent)"
                value={String(failedCount)}
                icon={XCircle}
                iconBg="bg-danger-50"
                iconColor="text-danger-600"
              />
            </>
          )}
        </div>

        {/* ── Quick actions ── */}
        <div className="flex flex-wrap gap-3">
          <Link
            href="/send"
            className="flex items-center gap-2 rounded-xl bg-primary-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-all hover:bg-primary-700 active:scale-95"
          >
            <Send className="h-4 w-4" />
            Send Money
          </Link>
          <Link
            href="/transactions"
            className="flex items-center gap-2 rounded-xl bg-white px-5 py-2.5 text-sm font-semibold text-slate-700 shadow-sm ring-1 ring-slate-200 transition-all hover:bg-slate-50"
          >
            <TrendingUp className="h-4 w-4" />
            All Transactions
          </Link>
        </div>

        {/* ── Recent transactions ── */}
        <div className="rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
          <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
            <h2 className="text-sm font-semibold text-slate-900">
              Recent Transactions
            </h2>
            <Link
              href="/transactions"
              className="flex items-center gap-1 text-xs font-medium text-primary-600 hover:text-primary-700"
            >
              View all
              <ArrowUpRight className="h-3 w-3" />
            </Link>
          </div>

          {txLoading ? (
            <div className="divide-y divide-slate-50">
              {Array.from({ length: 5 }).map((_, i) => (
                <RowSkeleton key={i} />
              ))}
            </div>
          ) : recentTxs.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-slate-400">
              <TrendingUp className="mb-3 h-10 w-10 opacity-30" />
              <p className="text-sm font-medium">No transactions yet</p>
              <p className="mt-1 text-xs">Send money to get started</p>
            </div>
          ) : (
            <div className="divide-y divide-slate-50">
              {recentTxs.slice(0, 8).map((tx) => (
                <AegisTransactionRow
                  key={tx.transactionId}
                  transaction={tx}
                  currentUserId={userId}
                  onClick={() => {
                    window.location.href = `/transactions/${tx.transactionId}`
                  }}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  )
}
