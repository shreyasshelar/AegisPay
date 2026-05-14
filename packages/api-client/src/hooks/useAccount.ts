import { useQuery } from '@tanstack/react-query'
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
