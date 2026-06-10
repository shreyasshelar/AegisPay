import { redirect } from 'next/navigation'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { ShellClient } from '@/components/shell-client'

/**
 * Dashboard route group layout.
 *
 * Server component — verifies session before rendering.
 * Unauthenticated users are redirected to /login.
 * The ShellClient child owns the mobile sidebar open/close state so this
 * layout can remain a Server Component.
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

  return <ShellClient>{children}</ShellClient>
}
