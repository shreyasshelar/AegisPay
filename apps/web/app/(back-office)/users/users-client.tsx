'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  Users2,
  Search,
  CheckCircle2,
  XCircle,
  Clock,
  Loader2,
  ShieldCheck,
  ChevronLeft,
  ChevronRight,
  RefreshCw,
  Eye,
} from 'lucide-react'
import { useApiClient, useUserList, userKeys } from '@aegispay/api-client'
import { Header } from '@/components/header'
import { cn } from '@/lib/utils'
import type { KycStatus, User } from '@aegispay/shared-types'

// ── KYC status config ─────────────────────────────────────────────────────────

const KYC_META: Record<KycStatus, { label: string; icon: React.ElementType; chip: string }> = {
  PENDING:            { label: 'Pending',         icon: Clock,         chip: 'bg-warning-50 text-warning-700 ring-warning-200'   },
  DOCUMENT_SUBMITTED: { label: 'Doc Submitted',   icon: Loader2,       chip: 'bg-primary-50 text-primary-700 ring-primary-200'   },
  AI_PROCESSING:      { label: 'AI Processing',   icon: Loader2,       chip: 'bg-primary-50 text-primary-700 ring-primary-200'   },
  APPROVED:           { label: 'Approved',        icon: CheckCircle2,  chip: 'bg-success-50 text-success-700 ring-success-200'   },
  REJECTED:           { label: 'Rejected',        icon: XCircle,       chip: 'bg-danger-50 text-danger-700 ring-danger-200'      },
  MANUAL_REVIEW:      { label: 'Manual Review',   icon: ShieldCheck,   chip: 'bg-slate-50 text-slate-700 ring-slate-200'         },
}

const KYC_FILTERS: { value: string; label: string }[] = [
  { value: '',                  label: 'All'            },
  { value: 'PENDING',           label: 'Pending'        },
  { value: 'DOCUMENT_SUBMITTED',label: 'Doc Submitted'  },
  { value: 'AI_PROCESSING',     label: 'AI Processing'  },
  { value: 'APPROVED',          label: 'Approved'       },
  { value: 'REJECTED',          label: 'Rejected'       },
  { value: 'MANUAL_REVIEW',     label: 'Manual Review'  },
]

// Use the shared User type directly (UserSummary kept as alias for clarity)
type UserSummary = User

// ── KYC status chip ───────────────────────────────────────────────────────────

function KycChip({ status }: { status: KycStatus }) {
  const meta  = KYC_META[status]
  const Icon  = meta.icon
  const spin  = status === 'AI_PROCESSING' || status === 'DOCUMENT_SUBMITTED'
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ring-1', meta.chip)}>
      <Icon className={cn('h-3 w-3', spin && 'animate-spin')} />
      {meta.label}
    </span>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

export function UsersClient() {
  const qc                 = useQueryClient()
  const { users: usersApi } = useApiClient()

  const [page,       setPage]       = useState(0)
  const [kycFilter,  setKycFilter]  = useState('')
  const [search,     setSearch]     = useState('')
  const [actionUser, setActionUser] = useState<UserSummary | null>(null)

  const PAGE_SIZE = 50

  // ── Fetch via shared hook ────────────────────────────────────────────────────

  const { data, isLoading, isError, isFetching } = useUserList({
    page,
    size:      PAGE_SIZE,
    kycStatus: kycFilter || undefined,
  })

  // ── Admin KYC approve / reject / manual-review ───────────────────────────────

  const updateKyc = useMutation({
    mutationFn: ({ userId, status }: { userId: string; status: string }) =>
      usersApi.updateKycStatus(userId, status),
    onSuccess: (_data, { status }) => {
      toast.success(`KYC status updated to ${status}`)
      qc.invalidateQueries({ queryKey: userKeys.list({}) })
      setActionUser(null)
    },
    onError: (err) =>
      toast.error('Update failed', { description: err instanceof Error ? err.message : 'Unknown error' }),
  })

  // ── Local search filter (client-side on current page) ────────────────────────

  const filtered = (data?.content ?? []).filter(u =>
    search.trim() === '' ||
    (u.name ?? '').toLowerCase().includes(search.toLowerCase()) ||
    u.email.toLowerCase().includes(search.toLowerCase()),
  )

  const totalEl    = data?.totalElements ?? 0
  const totalPages = data?.totalPages    ?? 1

  return (
    <>
      <Header
        title="Users"
        subtitle={`${totalEl.toLocaleString()} total users`}
      />

      <div className="px-6 pb-10 space-y-4 animate-fade-in">

        {/* ── Toolbar ───────────────────────────────────────────────────────── */}
        <div className="flex flex-wrap items-center gap-3">
          {/* Search */}
          <div className="relative flex-1 min-w-48 max-w-72">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search name or email…"
              className="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200"
            />
          </div>

          {/* KYC status filter */}
          <div className="flex flex-wrap gap-1.5">
            {KYC_FILTERS.map(f => (
              <button
                key={f.value}
                onClick={() => { setKycFilter(f.value); setPage(0) }}
                className={cn(
                  'rounded-full px-3 py-1 text-xs font-medium ring-1 transition-colors',
                  kycFilter === f.value
                    ? 'bg-primary-600 text-white ring-primary-600'
                    : 'bg-white text-slate-600 ring-slate-200 hover:ring-slate-300',
                )}
              >
                {f.label}
              </button>
            ))}
          </div>

          {/* Refresh */}
          <button
            onClick={() => qc.invalidateQueries({ queryKey: ['users'] })}
            className="ml-auto flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-medium text-slate-600 hover:border-slate-300 hover:text-slate-800"
          >
            <RefreshCw className={cn('h-3.5 w-3.5', isFetching && 'animate-spin')} />
            Refresh
          </button>
        </div>

        {/* ── Table ─────────────────────────────────────────────────────────── */}
        <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
          {isLoading ? (
            <div className="flex h-48 items-center justify-center">
              <Loader2 className="h-7 w-7 animate-spin text-slate-300" />
            </div>
          ) : isError ? (
            <div className="flex h-48 flex-col items-center justify-center gap-2 text-slate-400">
              <Users2 className="h-8 w-8 text-danger-400 opacity-60" />
              <p className="text-sm text-danger-500">Failed to load users</p>
              <p className="text-xs text-slate-400">Check that the user service is running</p>
            </div>
          ) : filtered.length === 0 ? (
            <div className="flex h-48 flex-col items-center justify-center gap-2 text-slate-400">
              <Users2 className="h-8 w-8" />
              <p className="text-sm">No users found</p>
            </div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50">
                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">User</th>
                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Role</th>
                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">KYC</th>
                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Joined</th>
                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {filtered.map(user => (
                  <tr key={user.id} className="hover:bg-slate-50 transition-colors">
                    {/* User */}
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-3">
                        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-100 text-xs font-bold uppercase text-primary-700">
                          {(user.name ?? user.email).charAt(0)}
                        </div>
                        <div className="min-w-0">
                          <p className="truncate font-medium text-slate-900">{user.name ?? user.email}</p>
                          <p className="truncate text-xs text-slate-400">{user.email}</p>
                        </div>
                      </div>
                    </td>

                    {/* Role */}
                    <td className="px-5 py-3.5">
                      <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                        {user.role}
                      </span>
                    </td>

                    {/* KYC */}
                    <td className="px-5 py-3.5">
                      <KycChip status={user.kycStatus} />
                    </td>

                    {/* Joined */}
                    <td className="px-5 py-3.5 text-xs text-slate-500">
                      {new Date(user.createdAt).toLocaleDateString('en-IN', {
                        day: '2-digit', month: 'short', year: 'numeric',
                      })}
                    </td>

                    {/* Actions */}
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-2">
                        {/* View — opens detail in a future modal/drawer; placeholder for now */}
                        <button
                          onClick={() => setActionUser(user)}
                          className="flex items-center gap-1 rounded-md border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-600 hover:border-slate-300 hover:text-slate-800"
                          title="Manage KYC"
                        >
                          <Eye className="h-3.5 w-3.5" />
                          Manage
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* ── Pagination ─────────────────────────────────────────────────────── */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between">
            <p className="text-xs text-slate-500">
              Page {page + 1} of {totalPages} · {totalEl.toLocaleString()} users
            </p>
            <div className="flex gap-1.5">
              <button
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
                className="flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 hover:border-slate-300 disabled:opacity-40"
              >
                <ChevronLeft className="h-3.5 w-3.5" />
                Prev
              </button>
              <button
                disabled={!!data?.last}
                onClick={() => setPage(p => p + 1)}
                className="flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 hover:border-slate-300 disabled:opacity-40"
              >
                Next
                <ChevronRight className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* ── KYC Action modal ─────────────────────────────────────────────────── */}
      {actionUser && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm animate-fade-in">
          <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl ring-1 ring-slate-200 space-y-4">
            <div className="flex items-start justify-between">
              <div>
                <h2 className="text-base font-semibold text-slate-900">{actionUser.name}</h2>
                <p className="text-xs text-slate-400">{actionUser.email}</p>
              </div>
              <KycChip status={actionUser.kycStatus} />
            </div>

            <p className="text-sm text-slate-600">
              Update KYC verification status for this user. This action is logged to the audit trail.
            </p>

            <div className="grid grid-cols-3 gap-2">
              <button
                disabled={updateKyc.isPending || actionUser.kycStatus === 'APPROVED'}
                onClick={() => updateKyc.mutate({ userId: actionUser.id, status: 'APPROVED' })}
                className="flex flex-col items-center gap-1.5 rounded-xl border border-success-200 bg-success-50 px-3 py-3 text-xs font-semibold text-success-700 hover:bg-success-100 disabled:opacity-40 transition-colors"
              >
                <CheckCircle2 className="h-5 w-5" />
                Approve
              </button>
              <button
                disabled={updateKyc.isPending || actionUser.kycStatus === 'REJECTED'}
                onClick={() => updateKyc.mutate({ userId: actionUser.id, status: 'REJECTED' })}
                className="flex flex-col items-center gap-1.5 rounded-xl border border-danger-200 bg-danger-50 px-3 py-3 text-xs font-semibold text-danger-700 hover:bg-danger-100 disabled:opacity-40 transition-colors"
              >
                <XCircle className="h-5 w-5" />
                Reject
              </button>
              <button
                disabled={updateKyc.isPending || actionUser.kycStatus === 'MANUAL_REVIEW'}
                onClick={() => updateKyc.mutate({ userId: actionUser.id, status: 'MANUAL_REVIEW' })}
                className="flex flex-col items-center gap-1.5 rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 text-xs font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-40 transition-colors"
              >
                <ShieldCheck className="h-5 w-5" />
                Review
              </button>
            </div>

            {updateKyc.isPending && (
              <div className="flex items-center justify-center gap-2 text-xs text-slate-400">
                <Loader2 className="h-4 w-4 animate-spin" />
                Updating…
              </div>
            )}

            <button
              onClick={() => setActionUser(null)}
              className="w-full rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </>
  )
}
