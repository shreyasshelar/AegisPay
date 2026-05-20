import { redirect } from 'next/navigation'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import type { Metadata } from 'next'
import { TriageClient } from './triage-client'

export const metadata: Metadata = { title: 'AI Triage Agent' }

/** ADMIN-only page — BACK_OFFICE cannot access triage. */
export default async function TriagePage({
  searchParams,
}: {
  searchParams: { txId?: string; service?: string }
}) {
  const session = await getServerSession(authOptions)

  if (!session) redirect('/login')
  if (session.user.role !== 'ADMIN') redirect('/back-office/incidents')

  return (
    <TriageClient
      prefillTxId={searchParams.txId}
      prefillService={searchParams.service}
    />
  )
}
