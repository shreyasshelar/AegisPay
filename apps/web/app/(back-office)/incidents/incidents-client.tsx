'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ShieldCheck, Loader2, Sparkles, Terminal } from 'lucide-react'
import { useApiClient } from '@aegispay/api-client'
import { Header } from '@/components/header'

export function IncidentsClient() {
  const { ai } = useApiClient()
  const [serviceName,  setServiceName]  = useState('')
  const [description,  setDescription]  = useState('')
  const [report, setReport] = useState<string | null>(null)

  const { mutate: triage, isPending } = useMutation({
    mutationFn: () =>
      ai.triageIncident(serviceName, description),
    onSuccess: (data) => {
      setReport(data)
      toast.success('Triage complete')
    },
    onError: (err) => {
      toast.error('Triage failed', {
        description: err instanceof Error ? err.message : 'Unknown error',
      })
    },
  })

  return (
    <>
      <Header
        title="Incident Triage"
        subtitle="Agentic AI reads logs, metrics & deployments to identify root cause"
      />

      <div className="p-6 max-w-3xl space-y-5 animate-fade-in">
        {/* Input form */}
        <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200 space-y-4">
          <h3 className="text-sm font-semibold text-slate-900 flex items-center gap-2">
            <Terminal className="h-4 w-4 text-slate-500" />
            New Incident
          </h3>

          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700">
              Affected Service
            </label>
            <input
              value={serviceName}
              onChange={(e) => setServiceName(e.target.value)}
              placeholder="e.g. transaction-service"
              className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700">
              Incident Description
            </label>
            <textarea
              rows={4}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Describe the symptoms, error messages, or timeline…"
              className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200 resize-none"
            />
          </div>

          <button
            onClick={() => triage()}
            disabled={isPending || !serviceName.trim() || !description.trim()}
            className="flex items-center gap-2 rounded-xl bg-primary-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-primary-700 disabled:opacity-50"
          >
            {isPending ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Sparkles className="h-4 w-4" />
            )}
            {isPending ? 'Triaging…' : 'Run AI Triage'}
          </button>
        </div>

        {/* Report */}
        {report && (
          <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200 space-y-4 animate-fade-in">
            <div className="flex items-center gap-2">
              <ShieldCheck className="h-5 w-5 text-primary-500" />
              <h3 className="text-sm font-semibold text-slate-900">
                Triage Report
              </h3>
            </div>

            <pre className="overflow-x-auto rounded-lg bg-slate-900 p-5 text-sm text-green-300 whitespace-pre-wrap leading-relaxed">
              {report}
            </pre>
          </div>
        )}
      </div>
    </>
  )
}
