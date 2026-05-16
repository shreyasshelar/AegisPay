import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useApiClient } from './context'

export const accountKeys = {
  all:    ['account'] as const,
  me:     () => [...accountKeys.all, 'me'] as const,
  byUser: (userId: string) => [...accountKeys.all, userId] as const,
}

/** Customer hook — fetches all own accounts (one per currency). */
export function useAccounts(_userId?: string) {
  const { ledger } = useApiClient()
  return useQuery({
    queryKey: accountKeys.me(),
    queryFn:  () => ledger.getMyAccounts(),
    staleTime: 15_000,
  })
}

/** @deprecated Use useAccounts() for multi-currency support. */
export function useAccount(_userId?: string) {
  const { ledger } = useApiClient()
  return useQuery({
    queryKey: accountKeys.me(),
    queryFn:  () => ledger.getMyAccount(),
    staleTime: 15_000,
  })
}

/** Back-office hook — fetches any user's accounts by userId. */
export function useAccountsByUser(userId: string) {
  const { ledger } = useApiClient()
  return useQuery({
    queryKey: accountKeys.byUser(userId),
    queryFn:  () => ledger.getAccountsForUser(userId),
    staleTime: 15_000,
    enabled:  !!userId,
  })
}

/**
 * Wallet top-up mutation.
 *
 * Step 1 — `createIntent(amount, currency)`: creates a Stripe PaymentIntent server-side
 *   and returns `{ paymentIntentId, clientSecret }`.
 * Step 2 — `confirm(paymentIntentId)`: backend verifies + credits the account.
 *
 * Usage:
 * ```tsx
 * const topUp = useTopUp()
 * const intent = await topUp.createIntent.mutateAsync({ amount: 1000, currency: 'INR' })
 * // ... stripe.confirmCardPayment(intent.clientSecret) ...
 * await topUp.confirm.mutateAsync(intent.paymentIntentId)
 * ```
 */
export function useTopUp() {
  const { ledger } = useApiClient()
  const qc = useQueryClient()

  const createIntent = useMutation({
    mutationFn: ({ amount, currency }: { amount: number; currency: string }) =>
      ledger.createTopUpIntent(amount, currency),
  })

  const confirm = useMutation({
    mutationFn: (paymentIntentId: string) => ledger.confirmTopUp(paymentIntentId),
    onSuccess: () => {
      // Invalidate account balance so the dashboard reflects the new funds
      qc.invalidateQueries({ queryKey: accountKeys.me() })
    },
  })

  return { createIntent, confirm }
}
