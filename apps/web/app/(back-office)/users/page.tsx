import { getServerSession } from 'next-auth'
import { redirect }         from 'next/navigation'
import { authOptions }      from '@/lib/auth'
import { UsersClient }      from './users-client'

export const metadata = { title: 'Users — AegisPay Back Office' }

export default async function UsersPage() {
  const session = await getServerSession(authOptions)
  if (!session) redirect('/login')

  const role = (session.user as { role?: string }).role ?? ''
  if (!['BACK_OFFICE', 'ADMIN'].includes(role)) redirect('/dashboard')

  return <UsersClient />
}
