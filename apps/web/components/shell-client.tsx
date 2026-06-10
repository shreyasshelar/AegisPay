'use client'

import { useState } from 'react'
import { Sidebar } from './sidebar'
import { SidebarContext } from '@/lib/sidebar-context'

/**
 * Client shell wrapper that owns the mobile-sidebar open/close state.
 * Server layouts (dashboard & back-office) render this as their top-level
 * shell so they remain Server Components while the toggle lives client-side.
 */
export function ShellClient({ children }: { children: React.ReactNode }) {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <SidebarContext.Provider value={{ openSidebar: () => setSidebarOpen(true) }}>
      <div className="flex h-screen overflow-hidden bg-slate-50">
        <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
        {/* min-w-0 prevents the flex child from overflowing its container */}
        <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
          <main className="flex-1 overflow-y-auto">{children}</main>
        </div>
      </div>
    </SidebarContext.Provider>
  )
}
