import type { Metadata } from 'next'
import { TransactionsClient } from './transactions-client'

export const metadata: Metadata = { title: 'Transactions' }

export default function TransactionsPage() {
  return <TransactionsClient />
}
