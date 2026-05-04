import type { Metadata } from 'next'
import { RiskCasesClient } from './risk-cases-client'

export const metadata: Metadata = { title: 'Risk Cases' }

export default function RiskCasesPage() {
  return <RiskCasesClient />
}
