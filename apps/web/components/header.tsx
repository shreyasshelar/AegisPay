'use client'

import { useSession } from 'next-auth/react'
import { Bell, Search, RefreshCw } from 'lucide-react'
import Link from 'next/link'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { cn } from '@/lib/utils'

interface HeaderProps {
  title?:    string
  subtitle?: string
}

export function Header({ title, subtitle }: HeaderProps) {
  const { data: session }    = useSession()
  const queryClient          = useQueryClient()
  const [spinning, setSpinning] = useState(false)

  async function handleRefresh() {
    setSpinning(true)
    await queryClient.invalidateQueries()
    setTimeout(() => setSpinning(false), 800)
  }

  return (
    <header className="flex h-16 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-6">
      {/* Left — page title */}
      <div>
        {title && (
          <h1 className="text-lg font-semibold text-slate-900">{title}</h1>
        )}
        {subtitle && (
          <p className="text-xs text-slate-400">{subtitle}</p>
        )}
      </div>

      {/* Right — actions */}
      <div className="flex items-center gap-2">
        {/* Global refresh */}
        <button
          onClick={handleRefresh}
          title="Refresh data"
          className="rounded-lg p-2 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700"
        >
          <RefreshCw className={cn('h-4 w-4', spinning && 'animate-spin')} />
        </button>

        {/* Notifications bell */}
        <Link
          href="/notifications"
          className="relative rounded-lg p-2 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700"
          title="Notifications"
        >
          <Bell className="h-4 w-4" />
          {/* Unread dot — wired in Phase F4 */}
          <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-danger-500" />
        </Link>

        {/* Avatar */}
        <div
          className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-100 text-xs font-bold uppercase text-primary-700"
          title={session?.user?.email ?? ''}
        >
          {session?.user?.name?.charAt(0) ?? '?'}
        </div>
      </div>
    </header>
  )
}
