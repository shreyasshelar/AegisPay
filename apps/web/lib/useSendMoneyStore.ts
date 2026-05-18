'use client'

import { create } from 'zustand'

export type SendStep = 'payee' | 'amount' | 'review' | 'status'

interface SendMoneyState {
  // Navigation
  step: SendStep

  // Form values
  payeeId:    string
  amount:     string   // kept as string to match CreateTransactionRequest schema
  currency:   string
  note:       string

  // Idempotency — generated once, reused on retry
  idempotencyKey: string

  // Post-submission
  transactionId: string | null

  // Actions
  setPayeeId:  (v: string) => void
  setAmount:   (v: string) => void
  setCurrency: (v: string) => void
  setNote:     (v: string) => void
  goTo:        (step: SendStep) => void
  setTransactionId: (id: string) => void
  reset:       () => void
}

function newIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return Math.random().toString(36).slice(2) + Date.now().toString(36)
}

export const useSendMoneyStore = create<SendMoneyState>((set) => ({
  step:           'payee',
  payeeId:        '',
  amount:         '',
  currency:       'INR',
  note:           '',
  idempotencyKey: newIdempotencyKey(),
  transactionId:  null,

  setPayeeId:  (v) => set({ payeeId: v }),
  setAmount:   (v) => set({ amount: v }),
  setCurrency: (v) => set({ currency: v }),
  setNote:     (v) => set({ note: v }),
  goTo:        (step) => set({ step }),
  setTransactionId: (id) => set({ transactionId: id }),

  reset: () =>
    set({
      step:           'payee',
      payeeId:        '',
      amount:         '',
      currency:       'INR',
      note:           '',
      idempotencyKey: newIdempotencyKey(),
      transactionId:  null,
    }),
}))
