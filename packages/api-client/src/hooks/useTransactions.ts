import {
  useQuery,
  useMutation,
  useInfiniteQuery,
  useQueryClient,
  type UseQueryOptions,
} from '@tanstack/react-query'
import { useApiClient } from './context.js'
import type { CreateTransactionRequest } from '@aegispay/shared-types'
import type { ListTransactionsParams } from '../client/transactions.js'

export const transactionKeys = {
  all: ['transactions'] as const,
  lists: () => [...transactionKeys.all, 'list'] as const,
  list: (params: ListTransactionsParams) => [...transactionKeys.lists(), params] as const,
  details: () => [...transactionKeys.all, 'detail'] as const,
  detail: (id: string) => [...transactionKeys.details(), id] as const,
}

export function useTransaction(id: string, options?: Partial<UseQueryOptions>) {
  const { transactions } = useApiClient()
  return useQuery({
    queryKey: transactionKeys.detail(id),
    queryFn: () => transactions.get(id),
    staleTime: 10_000,
    refetchInterval: (query) => {
      // Stop polling once terminal state reached
      const status = (query.state.data as { status?: string } | undefined)?.status
      const terminal = ['COMPLETED', 'FAILED', 'ROLLED_BACK']
      return terminal.includes(status ?? '') ? false : 3_000
    },
    ...options,
  })
}

export function useTransactionList(params: ListTransactionsParams = {}) {
  const { transactions } = useApiClient()
  return useQuery({
    queryKey: transactionKeys.list(params),
    queryFn: () => transactions.list(params),
    staleTime: 30_000,
  })
}

export function useInfiniteTransactions(params: Omit<ListTransactionsParams, 'page'> = {}) {
  const { transactions } = useApiClient()
  return useInfiniteQuery({
    queryKey: [...transactionKeys.lists(), 'infinite', params],
    queryFn: ({ pageParam = 0 }) => transactions.list({ ...params, page: pageParam as number, size: 20 }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.last ? undefined : lastPage.number + 1,
    staleTime: 30_000,
  })
}

export function useCreateTransaction() {
  const { transactions } = useApiClient()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({
      request,
      idempotencyKey,
    }: {
      request: CreateTransactionRequest
      idempotencyKey: string
    }) => transactions.create(request, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: transactionKeys.lists() })
    },
  })
}
