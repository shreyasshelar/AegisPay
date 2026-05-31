'use client'

import { ArrowLeft, Send, ShieldCheck } from 'lucide-react'
import { toast } from 'sonner'
import { Button as AegisButton } from '@aegispay/design-system'
import { useCreateTransaction } from '@aegispay/api-client'
import { useSendMoneyStore } from '@/lib/useSendMoneyStore'
import { formatAmount } from '@/lib/utils'

export function StepReview() {
  const {
    payeeId,
    amount,
    currency,
    note,
    idempotencyKey,
    goTo,
    setTransactionId,
  } = useSendMoneyStore()

  const { mutateAsync: createTx, isPending } = useCreateTransaction()

  async function handleConfirm() {
    try {
      const tx = await createTx({
        request: { payeeId, amount, currency, note: note || undefined },
        idempotencyKey,
      })
      setTransactionId(tx.transactionId)
      goTo('status')
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Payment failed. Please try again.'
      toast.error('Payment failed', { description: msg })
      // idempotencyKey stays the same — safe to retry
    }
  }

  const displayAmount = (() => {
    const n = parseFloat(amount)
    try { return isNaN(n) ? `${currency} ${amount}` : formatAmount(n, currency) }
    catch { return `${currency} ${amount}` }
  })()

  return (
    <div className="rounded-2xl bg-white p-8 shadow-sm ring-1 ring-slate-200 space-y-6">
      {/* Heading */}
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary-50">
          <ShieldCheck className="h-5 w-5 text-primary-600" />
        </div>
        <div>
          <h2 className="text-base font-semibold text-slate-900">Review your transfer</h2>
          <p className="text-xs text-slate-400">Double-check the details before sending</p>
        </div>
      </div>

      {/* Summary */}
      <div className="rounded-xl bg-slate-50 p-5 ring-1 ring-slate-100 space-y-4">
        {/* Amount — prominent */}
        <div className="text-center">
          <p className="text-3xl font-bold text-slate-900">{displayAmount}</p>
          <p className="mt-1 text-xs font-medium uppercase tracking-widest text-slate-400">
            Total Amount
          </p>
        </div>

        <hr className="border-slate-200" />

        <dl className="space-y-2.5 text-sm">
          <ReviewRow label="To" value={payeeId} mono />
          <ReviewRow label="Currency" value={currency} />
          {note && <ReviewRow label="Note" value={note} />}
        </dl>
      </div>

      {/* Risk notice */}
      <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700 ring-1 ring-amber-200">
        This transfer will undergo real-time risk assessment. Funds are reserved
        immediately and released only after all checks pass.
      </p>

      {/* Actions */}
      <div className="flex gap-3">
        <AegisButton
          type="button"
          variant="secondary"
          onClick={() => goTo('amount')}
          disabled={isPending}
          className="flex-1"
        >
          <ArrowLeft className="h-4 w-4" />
          Edit
        </AegisButton>
        <AegisButton
          type="button"
          loading={isPending}
          onClick={handleConfirm}
          className="flex-1"
        >
          <Send className="h-4 w-4" />
          {isPending ? 'Sending…' : 'Confirm & Send'}
        </AegisButton>
      </div>
    </div>
  )
}

function ReviewRow({
  label,
  value,
  mono = false,
}: {
  label: string
  value: string
  mono?: boolean
}) {
  return (
    <div className="flex items-start justify-between gap-4">
      <dt className="shrink-0 text-slate-400">{label}</dt>
      <dd
        className={`break-all text-right text-slate-700 ${
          mono ? 'font-mono text-xs' : 'font-medium'
        }`}
      >
        {value}
      </dd>
    </div>
  )
}
