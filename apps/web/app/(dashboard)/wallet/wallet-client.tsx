'use client'

import { useState } from 'react'
import Link         from 'next/link'
import { useRouter } from 'next/navigation'
import { toast }    from 'sonner'
import {
  Wallet,
  ArrowDownToLine,
  CheckCircle2,
  XCircle,
  Loader2,
  RefreshCw,
  ChevronRight,
  CreditCard,
  Info,
} from 'lucide-react'
import { useAccount, useTopUp } from '@aegispay/api-client'
import { Header }               from '@/components/header'
import { useAuthGuard }         from '@/lib/useAuthGuard'
import { formatAmount, cn }     from '@/lib/utils'
import { inrToCurrency, currencyToInr } from '@/lib/currency'

// ── Constants ─────────────────────────────────────────────────────────────────

const CURRENCIES    = ['INR', 'USD', 'EUR', 'GBP'] as const
type Currency = typeof CURRENCIES[number]

const PRESET_AMOUNTS: Record<Currency, number[]> = {
  INR: [500, 1000, 2000, 5000],
  USD: [10,  25,   50,   100],
  EUR: [10,  25,   50,   100],
  GBP: [10,  20,   50,   100],
}

/**
 * Maximum wallet balance defined in INR — matches the backend
 * aegispay.ledger.topup.max-balance (default ₹1,00,000).
 * For non-INR top-ups the backend converts the amount to INR before checking;
 * the frontend converts here so users see limits in their selected currency.
 */
const BALANCE_LIMIT_INR = 100_000

type Step = 'form' | 'processing' | 'success' | 'failed'

// ── Helpers ───────────────────────────────────────────────────────────────────

function parseAmount(raw: string): number | null {
  const n = parseFloat(raw)
  return isNaN(n) || n <= 0 ? null : n
}

// ── Sub-components ────────────────────────────────────────────────────────────

function BalanceCard({ balance, currency }: { balance: number; currency: string }) {
  return (
    <div className="rounded-2xl bg-gradient-to-br from-primary-600 to-primary-700 p-6 text-white shadow-lg">
      <div className="flex items-center gap-2 mb-4 opacity-80">
        <Wallet className="h-4 w-4" />
        <span className="text-sm font-medium">Available Balance</span>
      </div>
      <p className="text-3xl font-bold tracking-tight">
        {formatAmount(balance, currency)}
      </p>
      <p className="mt-1 text-xs opacity-60">Updated in real-time</p>
    </div>
  )
}

// ── Main component ─────────────────────────────────────────────────────────────

interface WalletClientProps {
  userId: string
}

export function WalletClient({ userId }: WalletClientProps) {
  const blocking = useAuthGuard()
  const router   = useRouter()

  const { data: account, isLoading: accountLoading } = useAccount(userId)
  const topUp = useTopUp()

  const [step,     setStep]     = useState<Step>('form')
  const [amount,   setAmount]   = useState('')
  const [currency, setCurrency] = useState<Currency>('INR')
  const [resultId, setResultId] = useState<string | null>(null)
  const [error,    setError]    = useState<string | null>(null)

  if (blocking) return null

  // ── Balance cap check (client-side early validation) ────────────────────────
  // The backend limit is ₹1,00,000 INR. For non-INR top-ups the backend converts
  // the entered amount to INR before checking, so we do the same here using
  // approximate FX rates to give early UX feedback.
  const accountCurrency      = account?.currency ?? 'INR'
  const currentBalance       = account?.availableBalance ?? 0
  const parsedAmt            = parseAmount(amount) ?? 0

  // All comparisons in INR
  const currentBalanceInInr  = currencyToInr(currentBalance, accountCurrency)
  const parsedAmtInInr       = currencyToInr(parsedAmt, currency)

  // Display values in selected currency
  const maxAllowed           = inrToCurrency(BALANCE_LIMIT_INR, currency)
  const remainingRoomInInr   = Math.max(0, BALANCE_LIMIT_INR - currentBalanceInInr)
  const remainingRoom        = inrToCurrency(remainingRoomInInr, currency)

  const wouldExceedLimit     = parsedAmt > 0 && (currentBalanceInInr + parsedAmtInInr) > BALANCE_LIMIT_INR

  // ── Handlers ────────────────────────────────────────────────────────────────

  const handleTopUp = async () => {
    const parsedAmount = parseAmount(amount)
    if (!parsedAmount) {
      toast.error('Please enter a valid amount')
      return
    }
    if (wouldExceedLimit) {
      toast.error('Balance limit exceeded', {
        description: `Maximum wallet balance is ${formatAmount(maxAllowed, currency)}. You can add up to ${formatAmount(remainingRoom, currency)} more.`,
      })
      return
    }

    setStep('processing')
    setError(null)

    try {
      // Step 1: create payment intent
      const intent = await topUp.createIntent.mutateAsync({
        amount:   parsedAmount,
        currency: currency,
      })

      // Step 2: in a real integration, you'd use Stripe.js here to collect and
      // confirm card details with `intent.clientSecret`. For on-prem / test mode,
      // the backend uses a stored test payment method (pm_card_visa), so we
      // proceed straight to confirmation.
      //
      // Production integration example:
      //   const stripe = await loadStripe(process.env.NEXT_PUBLIC_STRIPE_PK!)
      //   const { error } = await stripe!.confirmCardPayment(intent.clientSecret, {
      //     payment_method: { card: cardElement },
      //   })
      //   if (error) throw new Error(error.message)

      // Step 3: confirm and credit the account
      const result = await topUp.confirm.mutateAsync(intent.paymentIntentId)

      if (result.status === 'SUCCEEDED') {
        setResultId(result.referenceId)
        setStep('success')
        toast.success('Wallet topped up!', {
          description: `${formatAmount(result.amount, result.currency)} added to your balance.`,
        })
      } else {
        setError('Payment is pending. Your balance will update shortly.')
        setStep('success')
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Top-up failed'
      setError(msg)
      setStep('failed')
      toast.error('Top-up failed', { description: msg })
    }
  }

  const reset = () => {
    setStep('form')
    setAmount('')
    setError(null)
    setResultId(null)
    topUp.createIntent.reset()
    topUp.confirm.reset()
  }

  // ── Render ───────────────────────────────────────────────────────────────────

  return (
    <>
      <Header
        title="Wallet"
        subtitle="Add funds to your AegisPay balance"
      />

      <div className="px-6 pb-10 space-y-6 animate-fade-in">

        {/* Balance card */}
        {accountLoading ? (
          <div className="rounded-2xl bg-slate-100 h-32 animate-pulse" />
        ) : account ? (
          <BalanceCard
            balance={account.availableBalance}
            currency={account.currency}
          />
        ) : null}

        {/* ── Form step ── */}
        {step === 'form' && (
          <div className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200 space-y-5">
            <div className="flex items-center gap-2">
              <ArrowDownToLine className="h-5 w-5 text-primary-600" />
              <h2 className="text-base font-semibold text-slate-900">Top Up Wallet</h2>
            </div>

            {/* Currency selector */}
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1.5">Currency</label>
              <div className="flex gap-2 flex-wrap">
                {CURRENCIES.map(cur => (
                  <button
                    key={cur}
                    onClick={() => { setCurrency(cur); setAmount('') }}
                    className={cn(
                      'rounded-full px-3 py-1 text-xs font-medium ring-1 transition-colors',
                      currency === cur
                        ? 'bg-primary-600 text-white ring-primary-600'
                        : 'bg-white text-slate-600 ring-slate-200 hover:ring-slate-300',
                    )}
                  >
                    {cur}
                  </button>
                ))}
              </div>
            </div>

            {/* Amount input */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="text-xs font-medium text-slate-500">Amount</label>
                <span className="text-xs text-slate-400">
                  Max balance: {formatAmount(maxAllowed, currency)}
                  {remainingRoom < maxAllowed && (
                    <span className="ml-1 text-slate-400">
                      · room left: {formatAmount(remainingRoom, currency)}
                    </span>
                  )}
                </span>
              </div>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm font-medium text-slate-400">
                  {currency}
                </span>
                <input
                  type="number"
                  min="1"
                  step="any"
                  value={amount}
                  onChange={e => setAmount(e.target.value)}
                  placeholder="0.00"
                  className={cn(
                    'w-full rounded-xl border bg-white py-3 pl-14 pr-4 text-lg font-bold text-slate-900 focus:outline-none focus:ring-2',
                    wouldExceedLimit
                      ? 'border-danger-400 focus:border-danger-500 focus:ring-danger-200'
                      : 'border-slate-300 focus:border-primary-500 focus:ring-primary-200',
                  )}
                />
              </div>
              {wouldExceedLimit && (
                <p className="mt-1.5 text-xs text-danger-600">
                  This would exceed your {formatAmount(maxAllowed, currency)} balance limit.
                  {remainingRoom > 0
                    ? ` You can add up to ${formatAmount(remainingRoom, currency)}.`
                    : ' Your wallet is full.'}
                </p>
              )}
            </div>

            {/* Quick-amount presets */}
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1.5">Quick amounts</label>
              <div className="flex gap-2 flex-wrap">
                {PRESET_AMOUNTS[currency].map(preset => (
                  <button
                    key={preset}
                    onClick={() => setAmount(String(preset))}
                    className={cn(
                      'rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors',
                      amount === String(preset)
                        ? 'border-primary-500 bg-primary-50 text-primary-700'
                        : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300',
                    )}
                  >
                    {formatAmount(preset, currency)}
                  </button>
                ))}
              </div>
            </div>

            {/* Payment method info */}
            <div className="flex items-start gap-2 rounded-xl bg-slate-50 p-3">
              <CreditCard className="h-4 w-4 text-slate-400 mt-0.5 shrink-0" />
              <p className="text-xs text-slate-500 leading-relaxed">
                Payments are processed securely via <strong>Stripe</strong>. Your saved payment
                method on file will be charged. To update your payment method, visit{' '}
                <Link href="/profile" className="text-primary-600 underline underline-offset-2">
                  Profile settings
                </Link>.
              </p>
            </div>

            {/* CTA */}
            <button
              onClick={handleTopUp}
              disabled={!parseAmount(amount) || wouldExceedLimit}
              className="w-full flex items-center justify-center gap-2 rounded-xl bg-primary-600 px-5 py-3.5 text-sm font-semibold text-white hover:bg-primary-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              <ArrowDownToLine className="h-4 w-4" />
              Add {amount && parseAmount(amount) ? formatAmount(parseFloat(amount), currency) : 'Funds'}
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        )}

        {/* ── Processing step ── */}
        {step === 'processing' && (
          <div className="rounded-2xl bg-white p-10 shadow-sm ring-1 ring-slate-200 flex flex-col items-center gap-4 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary-50">
              <Loader2 className="h-8 w-8 animate-spin text-primary-600" />
            </div>
            <div>
              <p className="text-base font-semibold text-slate-900">Processing payment…</p>
              <p className="text-sm text-slate-500 mt-1">
                Please wait while we charge your payment method and credit your wallet.
              </p>
            </div>
          </div>
        )}

        {/* ── Success step ── */}
        {step === 'success' && (
          <div className="rounded-2xl bg-white p-8 shadow-sm ring-1 ring-slate-200 space-y-5">
            <div className="flex flex-col items-center gap-3 text-center">
              <CheckCircle2 className="h-16 w-16 text-success-500" />
              <div>
                <p className="text-lg font-bold text-slate-900">Top-up Successful!</p>
                <p className="text-sm text-slate-500 mt-1">
                  {amount && currency
                    ? `${formatAmount(parseFloat(amount), currency)} has been added to your wallet.`
                    : 'Funds added to your wallet.'}
                </p>
              </div>
              {error && (
                <div className="flex items-start gap-2 rounded-xl bg-warning-50 p-3 text-left w-full">
                  <Info className="h-4 w-4 text-warning-600 mt-0.5 shrink-0" />
                  <p className="text-xs text-warning-700">{error}</p>
                </div>
              )}
              {resultId && (
                <p className="font-mono text-xs text-slate-400">Ref: {resultId}</p>
              )}
            </div>

            <div className="space-y-2">
              <button
                onClick={() => router.push('/dashboard')}
                className="w-full flex items-center justify-center gap-2 rounded-xl bg-primary-600 px-5 py-3 text-sm font-semibold text-white hover:bg-primary-700 transition-colors"
              >
                Go to Dashboard
              </button>
              <button
                onClick={reset}
                className="w-full flex items-center justify-center gap-2 rounded-xl border border-slate-200 px-5 py-3 text-sm font-medium text-slate-600 hover:bg-slate-50 transition-colors"
              >
                <RefreshCw className="h-4 w-4" />
                Top Up Again
              </button>
            </div>
          </div>
        )}

        {/* ── Failed step ── */}
        {step === 'failed' && (
          <div className="rounded-2xl bg-white p-8 shadow-sm ring-1 ring-slate-200 space-y-5">
            <div className="flex flex-col items-center gap-3 text-center">
              <XCircle className="h-16 w-16 text-danger-500" />
              <div>
                <p className="text-lg font-bold text-slate-900">Top-up Failed</p>
                {error && (
                  <p className="text-sm text-slate-500 mt-1">{error}</p>
                )}
              </div>
            </div>

            <div className="space-y-2">
              <button
                onClick={reset}
                className="w-full flex items-center justify-center gap-2 rounded-xl bg-primary-600 px-5 py-3 text-sm font-semibold text-white hover:bg-primary-700 transition-colors"
              >
                <RefreshCw className="h-4 w-4" />
                Try Again
              </button>
              <Link
                href="/dashboard"
                className="w-full flex items-center justify-center gap-2 rounded-xl border border-slate-200 px-5 py-3 text-sm font-medium text-slate-600 hover:bg-slate-50 transition-colors"
              >
                Back to Dashboard
              </Link>
            </div>
          </div>
        )}
      </div>
    </>
  )
}
