import { redirect } from 'next/navigation'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { Sidebar } from '@/components/sidebar'

// BACK_OFFICE can access all back-office pages including AI Triage
// (previously Incidents was a stripped-down duplicate; merged into Triage).
const ALLOWED_ROLES = ['BACK_OFFICE', 'ADMIN']

export default async function BackOfficeLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const session = await getServerSession(authOptions)

  if (!session) redirect('/login')

  if (!ALLOWED_ROLES.includes(session.user.role)) {
    // Customer tried to access back-office → redirect home
    redirect('/dashboard')
  }

  return (
    <div className="flex h-screen overflow-hidden bg-slate-50">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <main className="flex-1 overflow-y-auto">{children}</main>
      </div>
    </div>
  )
}
