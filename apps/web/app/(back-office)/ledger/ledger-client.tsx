'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useApiClient } from '@aegispay/api-client'
import { Database, Loader2, ArrowUpRight, ArrowDownLeft } from 'lucide-react'
import { Header } from '@/components/header'
import { formatAmount, formatDate } from '@/lib/utils'
import type { LedgerEntry } from '@aegispay/shared-types'

const ENTRY_TYPE_ICON: Record<string, React.ElementType> = {
  DEBIT:   ArrowUpRight,
  CREDIT:  ArrowDownLeft,
  RESERVE: Database,
  RELEASE: Database,
  COMMIT:  Database,
}

const ENTRY_TYPE_COLOR: Record<string, string> = {
  DEBIT:   'text-danger-600',
  CREDIT:  'text-success-600',
  RESERVE: 'text-warning-600',
  RELEASE: 'text-slate-500',
  COMMIT:  'text-primary-600',
}

export function LedgerClient() {
  const { ledger }    = useApiClient()
  const [userId,  setUserId]  = useState('')
  const [txId,    setTxId]    = useState('')
  const [searchUser, setSearchUser] = useState('')
  const [searchTx,   setSearchTx]   = useState('')

  const { data: account, isLoading: accountLoading } = useQuery({
    queryKey: ['ledger', 'account', searchUser],
    queryFn:  () => ledger.getAccount(searchUser),
    enabled:  searchUser.length > 10,
    staleTime: 30_000,
  })

  const { data: entries, isLoading: entriesLoading } = useQuery({
    queryKey: ['ledger', 'entries', searchTx],
    queryFn:  () => ledger.getEntries(searchTx),
    enabled:  searchTx.length > 10,
    staleTime: 30_000,
  })

  return (
    <>
      <Header title="Ledger" subtitle="Immutable financial record" />

      <div className="p-6 space-y-5 animate-fade-in">
        {/* Search — Account by User ID */}
        <div className="flex gap-3">
          <input
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            placeholder="User ID (UUID) — look up account balance…"
            className="flex-1 rounded-xl border border-slate-300 px-4 py-2.5 text-sm focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200"
          />
          <button
            onClick={() => setSearchUser(userId)}
            className="rounded-xl bg-primary-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 transition"
          >
            Look Up Account
          </button>
        </div>

        {/* Search — Entries by Transaction ID */}
        <div className="flex gap-3">
          <input
            value={txId}
            onChange={(e) => setTxId(e.target.value)}
            placeholder="Transaction ID (UUID) — look up ledger entries…"
            className="flex-1 rounded-xl border border-slate-300 px-4 py-2.5 text-sm focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200"
          />
          <button
            onClick={() => setSearchTx(txId)}
            className="rounded-xl bg-slate-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-slate-800 transition"
          >
            Look Up Entries
          </button>
        </div>

        {/* Account summary */}
        {accountLoading && (
          <div className="flex h-24 items-center justify-center">
            <Loader2 className="h-6 w-6 animate-spin text-slate-300" />
          </div>
        )}

        {account && (
          <div className="grid grid-cols-3 gap-4">
            {[
              { label: 'Available',  value: formatAmount(account.availableBalance, account.currency) },
              { label: 'Reserved',   value: formatAmount(account.reservedBalance,  account.currency) },
              { label: 'Currency',   value: account.currency },
            ].map(({ label, value }) => (
              <div key={label} className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
                <p className="text-xs text-slate-400">{label}</p>
                <p className="mt-1 text-xl font-bold text-slate-900">{value}</p>
              </div>
            ))}
          </div>
        )}

        {/* Ledger entries */}
        {entries && (
          <div className="rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
            <div className="border-b border-slate-100 px-5 py-4">
              <h3 className="text-sm font-semibold text-slate-900">
                Ledger Entries ({entries.length})
              </h3>
            </div>

            {entriesLoading ? (
              <div className="flex h-32 items-center justify-center">
                <Loader2 className="h-6 w-6 animate-spin text-slate-300" />
              </div>
            ) : (
              <div className="divide-y divide-slate-50 max-h-[60vh] overflow-y-auto">
                {entries.map((entry: LedgerEntry) => {
                  const Icon  = ENTRY_TYPE_ICON[entry.entryType] ?? Database
                  const color = ENTRY_TYPE_COLOR[entry.entryType] ?? 'text-slate-500'
                  return (
                    <div key={entry.id} className="flex items-center gap-4 px-5 py-3">
                      <div className={`${color}`}>
                        <Icon className="h-4 w-4" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-xs font-medium text-slate-700">
                          {entry.entryType}
                        </p>
                        <p className="text-xs text-slate-400 font-mono truncate">
                          Tx: {entry.transactionId.slice(0, 8)}…
                        </p>
                      </div>
                      <div className="text-right">
                        <p className={`text-sm font-semibold ${color}`}>
                          {['CREDIT', 'RELEASE'].includes(entry.entryType) ? '+' : '-'}
                          {formatAmount(entry.amount, account?.currency ?? 'INR')}
                        </p>
                        <p className="text-xs text-slate-400">
                          {formatDate(entry.createdAt)}
                        </p>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        )}
      </div>
    </>
  )
}
