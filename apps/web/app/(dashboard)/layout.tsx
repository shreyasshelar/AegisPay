import { redirect } from 'next/navigation'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { Sidebar } from '@/components/sidebar'

/**
 * Dashboard route group layout.
 *
 * Server component — verifies session before rendering.
 * Unauthenticated users are redirected to /login.
 */
export default async function DashboardLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const session = await getServerSession(authOptions)

  // No session or session carries a broken refresh-token error.
  // Middleware handles RefreshAccessTokenError on server navigation, but this
  // catches any edge case where the layout renders before middleware can act.
  if (!session || session.error) {
    redirect('/login')
  }

  return (
    <div className="flex h-screen overflow-hidden bg-slate-50">
      {/* Persistent sidebar */}
      <Sidebar />

      {/* Main content area */}
      <div className="flex flex-1 flex-col overflow-hidden">
        <main className="flex-1 overflow-y-auto">
          {children}
        </main>
      </div>
    </div>
  )
}
