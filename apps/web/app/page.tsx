import { redirect } from 'next/navigation'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'

/**
 * Root page — immediately redirect based on auth state.
 */
export default async function RootPage() {
  const session = await getServerSession(authOptions)
  redirect(session ? '/dashboard' : '/login')
}
