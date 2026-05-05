import type { Metadata } from 'next'
import { SendMoneyClient } from './send-money-client'

export const metadata: Metadata = { title: 'Send Money' }

export default function SendMoneyPage() {
  return <SendMoneyClient />
}
