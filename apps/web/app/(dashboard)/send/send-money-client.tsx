'use client'

import { Plus } from 'lucide-react'
import { useSendMoneyStore } from '@/lib/useSendMoneyStore'
import { useAuthGuard } from '@/lib/useAuthGuard'
import { StepPayee }  from './steps/StepPayee'
import { StepAmount } from './steps/StepAmount'
import { StepReview } from './steps/StepReview'
import { StepStatus } from './steps/StepStatus'
import { Header }     from '@/components/header'

// ── Step indicator ─────────────────────────────────────────────────────────────

const STEPS = ['Payee', 'Amount', 'Review', 'Status'] as const
const STEP_INDEX: Record<string, number> = {
  payee: 0, amount: 1, review: 2, status: 3,
}

function StepIndicator({ current }: { current: string }) {
  const idx = STEP_INDEX[current] ?? 0
  return (
    <div className="flex items-center gap-0">
      {STEPS.map((label, i) => (
        <div key={label} className="flex items-center">
          {/* Circle */}
          <div
            className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-semibold transition-colors ${
              i < idx
                ? 'bg-primary-600 text-white'
                : i === idx
                  ? 'bg-primary-600 text-white ring-4 ring-primary-100'
                  : 'bg-slate-100 text-slate-400'
            }`}
          >
            {i < idx ? '✓' : i + 1}
          </div>
          {/* Label (hidden on mobile) */}
          <span
            className={`ml-1.5 hidden text-xs font-medium sm:block ${
              i === idx ? 'text-primary-700' : 'text-slate-400'
            }`}
          >
            {label}
          </span>
          {/* Connector */}
          {i < STEPS.length - 1 && (
            <div
              className={`mx-2 h-0.5 w-8 rounded-full transition-colors ${
                i < idx ? 'bg-primary-600' : 'bg-slate-200'
              }`}
            />
          )}
        </div>
      ))}
    </div>
  )
}

// ── Root component ─────────────────────────────────────────────────────────────

export function SendMoneyClient() {
  const blocking = useAuthGuard()
  const { step, reset } = useSendMoneyStore()

  if (blocking) return null

  return (
    <>
      <Header
        title="Send Money"
        subtitle="Secure peer-to-peer transfer"
        action={
          step === 'status'
            ? (
              <button
                onClick={reset}
                className="flex items-center gap-1.5 rounded-lg bg-primary-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-primary-700"
              >
                <Plus className="h-4 w-4" />
                New Transfer
              </button>
            )
            : undefined
        }
      />

      <div className="flex justify-center px-6 pb-10 animate-fade-in">
        <div className="w-full max-w-md space-y-6">
          {/* Step indicator — hidden on status screen */}
          {step !== 'status' && (
            <div className="flex justify-center pt-2">
              <StepIndicator current={step} />
            </div>
          )}

          {/* Active step */}
          {step === 'payee'  && <StepPayee />}
          {step === 'amount' && <StepAmount />}
          {step === 'review' && <StepReview />}
          {step === 'status' && <StepStatus />}
        </div>
      </div>
    </>
  )
}
