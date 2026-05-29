import { redirect } from 'next/navigation'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { ROLE_LANDING } from '@/lib/role-routing'

/**
 * Root page — redirect based on auth state and role.
 * Mapping is the single source of truth in lib/role-routing.ts.
 */
export default async function RootPage() {
  const session = await getServerSession(authOptions)
  if (!session) redirect('/login')

  const dest = ROLE_LANDING[session.user.role] ?? '/dashboard'
  redirect(dest)
}
