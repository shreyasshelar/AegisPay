'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useSession } from 'next-auth/react'
import { useQueryClient } from '@tanstack/react-query'
import { isStaffRole } from '@/lib/role-routing'
import {
  ShieldCheck,
  LayoutDashboard,
  ArrowUpRight,
  ScrollText,
  Bell,
  UserCircle,
  AlertTriangle,
  Database,
  LogOut,
  ChevronRight,
  Users2,
  Wallet,
  Stethoscope,
  X,
} from 'lucide-react'
import { useTransactionSocket, userKeys } from '@aegispay/api-client'
import { useNotificationStore } from '@/lib/useNotificationStore'
import { cn, resolveWsUrl } from '@/lib/utils'
import type { TransactionNotification } from '@aegispay/shared-types'
import { toast } from 'sonner'

interface NavItem {
  label:  string
  href:   string
  icon:   React.ElementType
  roles?: string[]
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard',     href: '/dashboard',              icon: LayoutDashboard },
  { label: 'Send Money',    href: '/send',         icon: ArrowUpRight    },
  { label: 'Wallet',        href: '/wallet',       icon: Wallet          },
  { label: 'Transactions',  href: '/transactions', icon: ScrollText      },
  { label: 'Notifications', href: '/notifications',icon: Bell            },
  { label: 'Profile / KYC', href: '/profile',     icon: UserCircle      },
]

const BACKOFFICE_ITEMS: NavItem[] = [
  { label: 'Users',      href: '/users',  icon: Users2,        roles: ['BACK_OFFICE', 'ADMIN'] },
  { label: 'Risk Cases', href: '/risk',   icon: AlertTriangle, roles: ['BACK_OFFICE', 'ADMIN'] },
  { label: 'Ledger',     href: '/ledger', icon: Database,      roles: ['BACK_OFFICE', 'ADMIN'] },
  // AI Triage is available to both BACK_OFFICE and ADMIN — Incidents was a
  // stripped-down duplicate of the same endpoint; merged here.
  { label: 'AI Triage',  href: '/triage', icon: Stethoscope,   roles: ['BACK_OFFICE', 'ADMIN'] },
]

interface SidebarProps {
  /** Controls visibility on mobile (< md). Ignored on md+ where sidebar is always shown. */
  open?: boolean
  /** Called when the user taps the backdrop or the close button on mobile. */
  onClose?: () => void
}

export function Sidebar({ open = false, onClose }: SidebarProps) {
  const pathname          = usePathname()
  const { data: session, status: sessionStatus } = useSession()
  const queryClient        = useQueryClient()
  const role              = session?.user?.role ?? 'CUSTOMER'
  const wsBaseUrl         = resolveWsUrl(process.env.NEXT_PUBLIC_WS_BASE_URL ?? 'ws://localhost:8086')

  const { unreadCount, increment } = useNotificationStore()

  // Global WebSocket listener — increments badge when not already on /notifications
  useTransactionSocket({
    userId:      session?.user?.id ?? '',
    accessToken: session?.accessToken ?? null,
    wsBaseUrl,
    onNotification(notification: TransactionNotification) {
      queryClient.invalidateQueries({ queryKey: ['notifications', 'list'] })
      if (!pathname.startsWith('/notifications')) {
        increment()
      }
      // Suppress global toast on pages that already show their own status toast:
      //   /send        → StepStatus.tsx fires toast.success on COMPLETED
      //   /transactions/[id] → transaction-detail-client.tsx fires toast from WebSocket
      // Showing both would give the user two simultaneous toasts for the same event.
      const pageHandlesOwnToast =
        pathname.startsWith('/send') ||
        pathname.startsWith('/transactions/')
      if (!pathname.startsWith('/notifications') && !pageHandlesOwnToast) {
        // Use a success toast for KYC approval, warning for others, info for generic
        const notifType = (notification as { type?: string }).type ?? ''
        if (notifType === 'KYC_STATUS_CHANGED') {
          // Invalidate the user profile cache so the KYC status banner on /profile
          // updates instantly without a manual page refresh.
          queryClient.invalidateQueries({ queryKey: userKeys.me() })

          // Only toast for FINAL states — the /profile banner already shows
          // DOCUMENT_SUBMITTED / AI_PROCESSING with a spinner, so a toast for
          // those intermediate states is redundant noise.
          const body  = (notification.body  ?? '').toLowerCase()
          const title = (notification.title ?? '').toLowerCase()
          const isApproved     = body.includes('approved')  || title.includes('approved')
          const isRejected     = body.includes('rejected')  || title.includes('rejected')
          const isManualReview = body.includes('manual')    || title.includes('manual')

          if (isApproved) {
            toast.success(notification.title, { description: notification.body, duration: 12_000 })
          } else if (isRejected) {
            toast.error(notification.title, { description: notification.body, duration: 15_000 })
          } else if (isManualReview) {
            toast.info(notification.title, { description: notification.body, duration: 10_000 })
          }
          // DOCUMENT_SUBMITTED / AI_PROCESSING → no toast; the /profile banner covers it.
        } else {
          toast.info(notification.title, { description: notification.body })
        }
      }
    },
  })

  const isActive = (href: string) =>
    href === '/dashboard' ? pathname === '/dashboard' : pathname.startsWith(href)

  function NavLink({ item }: { item: NavItem }) {
    const active       = isActive(item.href)
    const showBadge    = item.href === '/notifications' && unreadCount > 0

    return (
      <Link
        href={item.href}
        className={cn(
          'group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all',
          active
            ? 'bg-primary-100 text-primary-700'
            : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
        )}
      >
        <item.icon className={cn(
          'h-4 w-4 shrink-0 transition-colors',
          active ? 'text-primary-600' : 'text-slate-400 group-hover:text-slate-600',
        )} />
        <span className="flex-1">{item.label}</span>
        {showBadge && (
          <span className="flex h-5 min-w-[20px] items-center justify-center rounded-full bg-danger-500 px-1.5 text-[10px] font-bold text-white">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
        {active && <ChevronRight className="h-3 w-3 text-primary-500" />}
      </Link>
    )
  }

  const visibleBackOffice = BACKOFFICE_ITEMS.filter(
    (item) => !item.roles || item.roles.includes(role),
  )

  return (
    <>
      {/* Mobile backdrop — tapping it closes the sidebar */}
      {open && (
        <div
          className="fixed inset-0 z-30 bg-black/40 md:hidden"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      <aside
        className={cn(
          // Base: full-height column with border
          'flex h-full w-64 shrink-0 flex-col border-r border-slate-200 bg-white',
          // Mobile: slide in from left as a fixed overlay above content
          'fixed inset-y-0 left-0 z-40 transition-transform duration-200 ease-in-out',
          open ? 'translate-x-0' : '-translate-x-full',
          // Desktop (md+): static, always visible, no transform needed
          'md:static md:z-auto md:translate-x-0',
        )}
      >
      {/* Logo + mobile close button */}
      <div className="flex h-16 items-center justify-between border-b border-slate-100 px-5">
        <div className="flex items-center gap-2.5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-600">
            <ShieldCheck className="h-5 w-5 text-white" />
          </div>
          <span className="text-lg font-bold tracking-tight text-slate-900">AegisPay</span>
        </div>
        {/* Close button — mobile only */}
        <button
          onClick={onClose}
          className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-600 md:hidden"
          aria-label="Close navigation"
        >
          <X className="h-5 w-5" />
        </button>
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto p-3 scrollbar-none">
        {/* Customer nav — hidden for staff roles (ADMIN, BACK_OFFICE, etc.) */}
        {!isStaffRole(role) && (
          <ul className="space-y-0.5">
            {NAV_ITEMS.map((item) => (
              <li key={item.href}>
                <NavLink item={item} />
              </li>
            ))}
          </ul>
        )}

        {visibleBackOffice.length > 0 && (
          <>
            <div className="my-3 border-t border-slate-100" />
            <p className="mb-1.5 px-3 text-[10px] font-semibold uppercase tracking-widest text-slate-400">
              Back Office
            </p>
            <ul className="space-y-0.5">
              {visibleBackOffice.map((item) => (
                <li key={item.href}>
                  <NavLink item={item} />
                </li>
              ))}
            </ul>
          </>
        )}
      </nav>

      {/* User + sign-out */}
      <div className="border-t border-slate-100 p-3">
        {sessionStatus === 'loading' ? (
          /* Skeleton while NextAuth re-hydrates on first load / SSO redirect */
          <div className="mb-2 flex items-center gap-3 rounded-lg px-3 py-2">
            <div className="skeleton h-8 w-8 shrink-0 rounded-full" />
            <div className="min-w-0 flex-1 space-y-1.5">
              <div className="skeleton h-3.5 w-24 rounded" />
              <div className="skeleton h-3 w-14 rounded" />
            </div>
          </div>
        ) : (
          <div className="mb-2 flex items-center gap-3 rounded-lg px-3 py-2">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-100 text-primary-700 text-xs font-bold uppercase">
              {session?.user?.name?.charAt(0) ?? '?'}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-slate-900">
                {session?.user?.name ?? 'Unknown'}
              </p>
              <p className="truncate text-xs text-slate-400">{role}</p>
            </div>
          </div>
        )}
        <button
          onClick={() => {
            queryClient.clear()
            // Navigate to the server-side signout route which ends the Keycloak
            // SSO session (via end_session endpoint) AND clears all NextAuth
            // session cookies (including chunks) in one redirect chain.
            window.location.href = '/api/auth/keycloak-signout'
          }}
          className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-slate-600 transition-all hover:bg-red-50 hover:text-red-600"
        >
          <LogOut className="h-4 w-4" />
          Sign out
        </button>
      </div>
    </aside>
    </>
  )
}
