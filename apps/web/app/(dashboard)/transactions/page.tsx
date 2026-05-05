import type { Metadata } from 'next'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { TransactionsClient } from './transactions-client'

export const metadata: Metadata = { title: 'Transactions' }

export default async function TransactionsPage() {
  const session = await getServerSession(authOptions)
  return <TransactionsClient userId={session?.user?.id ?? ''} />
}
