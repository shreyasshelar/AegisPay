// FILE: apps/web/app/docs/_components/SidebarNav.tsx
'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useState } from 'react'
import {
  BookOpen,
  Layers,
  GitBranch,
  Puzzle,
  Brain,
  Server,
  Database,
  Menu,
  X,
} from 'lucide-react'

const NAV_ITEMS = [
  { href: '/docs', label: 'Getting Started', icon: BookOpen, exact: true },
  { href: '/docs/architecture', label: 'Architecture', icon: Layers },
  { href: '/docs/flows', label: 'Transaction Flow', icon: GitBranch },
  { href: '/docs/patterns', label: 'Patterns', icon: Puzzle },
  { href: '/docs/ai', label: 'AI Platform', icon: Brain },
  { href: '/docs/infrastructure', label: 'Infrastructure', icon: Server },
  { href: '/docs/services', label: 'Services', icon: Database },
]

function NavLinks({ onClose }: { onClose?: () => void }) {
  const pathname = usePathname()

  return (
    <nav className="flex flex-col gap-1 px-3 py-4">
      {NAV_ITEMS.map(({ href, label, icon: Icon, exact }) => {
        const isActive = exact ? pathname === href : pathname.startsWith(href)
        return (
          <Link
            key={href}
            href={href}
            onClick={onClose}
            className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors ${
              isActive
                ? 'bg-blue-50 text-blue-600 font-medium'
                : 'text-gray-600 hover:text-gray-900 hover:bg-gray-50'
            }`}
          >
            <Icon size={16} className={isActive ? 'text-blue-500' : 'text-gray-400'} />
            {label}
          </Link>
        )
      })}
    </nav>
  )
}

export function MobileSidebar() {
  const [open, setOpen] = useState(false)

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="lg:hidden p-2 rounded-lg text-gray-600 hover:bg-gray-100"
        aria-label="Open navigation"
      >
        <Menu size={20} />
      </button>

      {open && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="fixed inset-0 bg-black/30" onClick={() => setOpen(false)} />
          <div className="fixed inset-y-0 left-0 w-72 bg-white shadow-xl flex flex-col">
            <div className="flex items-center justify-between px-4 py-4 border-b border-gray-100">
              <span className="font-semibold text-gray-900">AegisPay Docs</span>
              <button
                onClick={() => setOpen(false)}
                className="p-1 rounded-lg text-gray-500 hover:bg-gray-100"
              >
                <X size={18} />
              </button>
            </div>
            <NavLinks onClose={() => setOpen(false)} />
          </div>
        </div>
      )}
    </>
  )
}

export function DesktopSidebar() {
  return (
    <aside className="hidden lg:flex flex-col w-60 shrink-0 border-r border-gray-100 bg-white min-h-screen sticky top-0">
      <NavLinks />
    </aside>
  )
}
