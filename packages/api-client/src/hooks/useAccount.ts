import { useQuery } from '@tanstack/react-query'
import { useApiClient } from './context.js'

export const accountKeys = {
  all: ['account'] as const,
  byUser: (userId: string) => [...accountKeys.all, userId] as const,
}

export function useAccount(userId: string) {
  const { ledger } = useApiClient()
  return useQuery({
    queryKey: accountKeys.byUser(userId),
    queryFn: () => ledger.getAccount(userId),
    staleTime: 15_000,
    enabled: !!userId,
  })
}
