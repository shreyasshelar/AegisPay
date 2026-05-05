import type { Metadata } from 'next'
import { TransactionDetailClient } from './transaction-detail-client'

export const metadata: Metadata = { title: 'Transaction Detail' }

export default function TransactionDetailPage({
  params,
}: {
  params: { id: string }
}) {
  return <TransactionDetailClient transactionId={params.id} />
}
