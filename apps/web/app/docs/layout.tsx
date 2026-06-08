// FILE: apps/web/app/docs/layout.tsx
import Link from 'next/link'
import { ArrowRight, Shield } from 'lucide-react'
import { DesktopSidebar, MobileSidebar } from './_components/SidebarNav'

export default function DocsLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      {/* Top header */}
      <header className="sticky top-0 z-40 bg-white border-b border-gray-100 shadow-sm">
        <div className="flex items-center justify-between h-14 px-4 max-w-screen-2xl mx-auto">
          <div className="flex items-center gap-3">
            <MobileSidebar />
            <Link href="/docs" className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-lg bg-blue-500 flex items-center justify-center">
                <Shield size={14} className="text-white" />
              </div>
              <span className="font-bold text-gray-900 text-sm">AegisPay Docs</span>
            </Link>
          </div>
          <Link
            href="/dashboard"
            className="flex items-center gap-1.5 text-sm text-blue-600 font-medium hover:text-blue-700 transition-colors"
          >
            Open App
            <ArrowRight size={14} />
          </Link>
        </div>
      </header>

      {/* Body */}
      <div className="flex flex-1 max-w-screen-2xl mx-auto w-full">
        <DesktopSidebar />
        <main className="flex-1 min-w-0 px-6 py-8 lg:px-10 lg:py-10">
          {children}
        </main>
      </div>

      {/* Footer */}
      <footer className="border-t border-gray-100 bg-white py-4 text-center text-xs text-gray-400">
        AegisPay &copy; 2026 &middot; Production-grade fintech platform
      </footer>
    </div>
  )
}
