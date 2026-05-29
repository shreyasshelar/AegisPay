'use client'

import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useAuthGuard } from '@/lib/useAuthGuard'
import { Bell, Loader2, CheckCircle2, XCircle, Info, ArrowDownLeft, AlertCircle } from 'lucide-react'
import { useApiClient } from '@aegispay/api-client'
import { Header } from '@/components/header'
import { timeAgo } from '@/lib/utils'
import { useNotificationStore } from '@/lib/useNotificationStore'
import type { Notification } from '@aegispay/shared-types'

// ── Notification icon helper ─────────────────────────────────────────────────

function NotifIcon({ type }: { type: string }) {
  if (type === 'TRANSACTION_COMPLETED')
    return <CheckCircle2 className="h-5 w-5 text-success-500" />
  if (type === 'MONEY_RECEIVED')
    return <ArrowDownLeft className="h-5 w-5 text-[#0E9F6E]" />
  if (type === 'TRANSACTION_FAILED' || type === 'TRANSACTION_ROLLED_BACK')
    return <XCircle className="h-5 w-5 text-danger-500" />
  if (type === 'KYC_STATUS_CHANGED')
    return <Bell className="h-5 w-5 text-primary-500" />
  return <Info className="h-5 w-5 text-primary-500" />
}

// ── Component ─────────────────────────────────────────────────────────────────

export function NotificationsClient() {
  const blocking               = useAuthGuard()
  const { notifications: nc } = useApiClient()
  const { reset: resetUnread } = useNotificationStore()

  // Clear badge when this page is mounted (sidebar's WS handler skips increment here)
  useEffect(() => { resetUnread() }, [resetUnread])

  const { data, isLoading, isError } = useQuery({
    queryKey: ['notifications', 'list'],
    queryFn:  () => nc.list(0, 50),
    staleTime: 30_000,
  })

  const items: Notification[] = data?.content ?? []

  if (blocking) return null

  return (
    <>
      <Header title="Notifications" subtitle="Real-time activity feed" />

      <div className="p-6 animate-fade-in">
        <div className="rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
          {isLoading ? (
            <div className="flex items-center justify-center py-20">
              <Loader2 className="h-7 w-7 animate-spin text-slate-300" />
            </div>
          ) : isError ? (
            <div className="flex flex-col items-center justify-center py-20 text-slate-400">
              <AlertCircle className="mb-3 h-10 w-10 opacity-40 text-danger-400" />
              <p className="text-sm font-medium text-danger-500">Could not load notifications</p>
              <p className="mt-1 text-xs">Try refreshing the page</p>
            </div>
          ) : items.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 text-slate-400">
              <Bell className="mb-3 h-10 w-10 opacity-20" />
              <p className="text-sm font-medium">No notifications yet</p>
            </div>
          ) : (
            <ul className="divide-y divide-slate-50">
              {items.map((n) => (
                <li key={n.id} className="flex items-start gap-4 px-5 py-4">
                  <div className="mt-0.5 shrink-0">
                    <NotifIcon type={n.type} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-slate-900">
                      {n.title ?? n.type}
                    </p>
                    {n.body && (
                      <p className="mt-0.5 text-sm text-slate-500 line-clamp-2">
                        {n.body}
                      </p>
                    )}
                  </div>
                  <span className="shrink-0 text-xs text-slate-400">
                    {timeAgo(n.createdAt)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </>
  )
}
