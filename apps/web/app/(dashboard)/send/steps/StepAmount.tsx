'use client'

import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { ArrowLeft, ArrowRight, Banknote } from 'lucide-react'
import {
  AmountInput as AegisAmountInput,
  Button as AegisButton,
} from '@aegispay/design-system'
import { useSendMoneyStore } from '@/lib/useSendMoneyStore'

const CURRENCIES = ['INR', 'USD', 'EUR', 'GBP'] as const

const schema = z.object({
  amount:   z.string().regex(/^\d+(\.\d{1,2})?$/, 'Enter a valid amount (e.g. 500 or 1234.50)'),
  currency: z.enum(CURRENCIES),
  note:     z.string().max(140, 'Note max 140 characters').optional(),
})
type FormValues = z.infer<typeof schema>

export function StepAmount() {
  const { amount, currency, note, setAmount, setCurrency, setNote, goTo } =
    useSendMoneyStore()

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<FormValues>({
    resolver:      zodResolver(schema),
    defaultValues: {
      amount,
      currency: (currency as typeof CURRENCIES[number]) ?? 'INR',
      note,
    },
  })

  // Watch currency so symbol stays in sync with selection
  const selectedCurrency = watch('currency')

  function onSubmit(values: FormValues) {
    setAmount(values.amount)
    setCurrency(values.currency)
    setNote(values.note ?? '')
    goTo('review')
  }

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      className="rounded-2xl bg-white p-8 shadow-sm ring-1 ring-slate-200 space-y-6"
    >
      {/* Heading */}
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary-50">
          <Banknote className="h-5 w-5 text-primary-600" />
        </div>
        <div>
          <h2 className="text-base font-semibold text-slate-900">How much to send?</h2>
          <p className="text-xs text-slate-400">Enter the amount and an optional note</p>
        </div>
      </div>

      {/* ── Unified amount + currency field ── */}
      <div>
        <label className="mb-1.5 block text-sm font-medium text-slate-700">Amount</label>
        <AegisAmountInput
          currency={selectedCurrency}
          currencies={CURRENCIES}
          onCurrencyChange={(c) =>
            setValue('currency', c as typeof CURRENCIES[number], { shouldValidate: true })
          }
          defaultValue={amount}
          error={errors.amount?.message}
          onChange={(v) => setValue('amount', v, { shouldValidate: true })}
        />
      </div>

      {/* Note */}
      <div>
        <label className="mb-1.5 block text-sm font-medium text-slate-700">
          Note <span className="font-normal text-slate-400">(optional)</span>
        </label>
        <textarea
          rows={2}
          maxLength={140}
          placeholder="What's this for?"
          className="block w-full resize-none rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200"
          {...register('note')}
        />
        {errors.note && (
          <p className="mt-1 text-xs text-danger-600">{errors.note.message}</p>
        )}
      </div>

      {/* Actions */}
      <div className="flex gap-3">
        <AegisButton
          type="button"
          variant="secondary"
          onClick={() => goTo('payee')}
          className="flex-1"
        >
          <ArrowLeft className="h-4 w-4" />
          Back
        </AegisButton>
        <AegisButton type="submit" className="flex-1">
          Review
          <ArrowRight className="h-4 w-4" />
        </AegisButton>
      </div>
    </form>
  )
}
