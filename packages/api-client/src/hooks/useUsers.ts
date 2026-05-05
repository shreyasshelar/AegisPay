import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useApiClient } from './context.js'
import type { KycUploadRequest } from '@aegispay/shared-types'

export const userKeys = {
  me:     () => ['user', 'me'] as const,
  byId:   (id: string) => ['user', id] as const,
}

export function useMe() {
  const { users } = useApiClient()
  return useQuery({
    queryKey: userKeys.me(),
    queryFn:  () => users.getMe(),
  })
}

export function useUser(id: string) {
  const { users } = useApiClient()
  return useQuery({
    queryKey: userKeys.byId(id),
    queryFn:  () => users.getById(id),
    enabled:  !!id,
  })
}

export function useProcessKyc() {
  const { users } = useApiClient()
  return useMutation({
    mutationFn: (request: KycUploadRequest) => users.processKycDocument(request),
  })
}

export function useConfirmKyc() {
  const { users } = useApiClient()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, documentType }: { userId: string; documentType: string }) =>
      users.confirmKyc(userId, documentType),
    onSuccess: (_data, { userId }) => {
      queryClient.invalidateQueries({ queryKey: userKeys.byId(userId) })
      queryClient.invalidateQueries({ queryKey: userKeys.me() })
    },
  })
}
