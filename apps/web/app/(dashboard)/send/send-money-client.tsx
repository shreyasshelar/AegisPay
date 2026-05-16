'use client'

import { useEffect } from 'react'
import Link          from 'next/link'
import { Plus, ShieldAlert, ArrowRight, Loader2 } from 'lucide-react'
import { useSession } from 'next-auth/react'
import { useSendMoneyStore } from '@/lib/useSendMoneyStore'
import { useAuthGuard } from '@/lib/useAuthGuard'
import { useUser } from '@aegispay/api-client'
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

// ── KYC guard banner ───────────────────────────────────────────────────────────

function KycGuardBanner() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] px-6">
      <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-sm ring-1 ring-warning-200 space-y-5 text-center">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-warning-50">
          <ShieldAlert className="h-7 w-7 text-warning-500" />
        </div>
        <div>
          <h2 className="text-lg font-semibold text-slate-900">Identity verification required</h2>
          <p className="mt-2 text-sm text-slate-500">
            You need to complete KYC verification before sending money. This protects you and
            your recipients from fraud.
          </p>
        </div>
        <Link
          href="/profile"
          className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-primary-600 px-5 py-3 text-sm font-semibold text-white hover:bg-primary-700 transition-colors"
        >
          Complete KYC now
          <ArrowRight className="h-4 w-4" />
        </Link>
      </div>
    </div>
  )
}

// ── Root component ─────────────────────────────────────────────────────────────

export function SendMoneyClient() {
  const blocking           = useAuthGuard()
  const { data: session }  = useSession()
  const { step, reset }    = useSendMoneyStore()

  // Check if the user's KYC is approved before allowing any send action
  const { data: user, isLoading: userLoading } = useUser(session?.user?.id ?? '', {
    enabled: !!session?.user?.id,
  })

  // Reset wizard state every time this page mounts so previous transaction doesn't leak
  useEffect(() => {
    reset()
  }, [reset])

  if (blocking) return null

  // Wait for KYC status to load — show spinner so layout doesn't flash
  if (userLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Loader2 className="h-7 w-7 animate-spin text-slate-300" />
      </div>
    )
  }

  // Block send flow if KYC not approved (or if user fetch failed — safe default)
  if (!user || user.kycStatus !== 'APPROVED') return <KycGuardBanner />

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
