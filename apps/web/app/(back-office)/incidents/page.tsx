import type { Metadata } from 'next'
import { IncidentsClient } from './incidents-client'

export const metadata: Metadata = { title: 'Incident Triage' }

export default function IncidentsPage() {
  return <IncidentsClient />
}
