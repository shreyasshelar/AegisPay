'use client'

import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { ArrowRight, User } from 'lucide-react'
import { Input as AegisInput, Button as AegisButton } from '@aegispay/design-system'
import { useSendMoneyStore } from '@/lib/useSendMoneyStore'

const schema = z.object({
  payeeId: z
    .string()
    .min(1, 'Payee ID is required')
    .uuid('Must be a valid UUID (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)'),
})
type FormValues = z.infer<typeof schema>

export function StepPayee() {
  const { payeeId, setPayeeId, goTo } = useSendMoneyStore()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver:      zodResolver(schema),
    defaultValues: { payeeId },
  })

  function onSubmit({ payeeId }: FormValues) {
    setPayeeId(payeeId)
    goTo('amount')
  }

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      className="rounded-2xl bg-white p-8 shadow-sm ring-1 ring-slate-200 space-y-6"
    >
      {/* Heading */}
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary-50">
          <User className="h-5 w-5 text-primary-600" />
        </div>
        <div>
          <h2 className="text-base font-semibold text-slate-900">Who are you sending to?</h2>
          <p className="text-xs text-slate-400">Enter the recipient's AegisPay user ID</p>
        </div>
      </div>

      <AegisInput
        label="Payee ID (UUID)"
        placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
        autoFocus
        autoComplete="off"
        spellCheck={false}
        error={errors.payeeId?.message}
        {...register('payeeId')}
      />

      <p className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500 ring-1 ring-slate-100">
        You can find a recipient's user ID on their AegisPay profile page.
      </p>

      <AegisButton type="submit" className="w-full">
        Continue
        <ArrowRight className="h-4 w-4" />
      </AegisButton>
    </form>
  )
}
