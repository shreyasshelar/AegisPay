import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useApiClient } from './context'
import type { KycUploadRequest } from '@aegispay/shared-types'
import type { UserListParams } from '../client/users'

export const userKeys = {
  me:     ()                     => ['user', 'me']         as const,
  byId:   (id: string)           => ['user', id]           as const,
  list:   (p: UserListParams)    => ['users', 'list', p]   as const,
}

export function useMe() {
  const { users } = useApiClient()
  return useQuery({
    queryKey: userKeys.me(),
    queryFn:  () => users.getMe(),
  })
}

export function useUser(id: string, options?: { enabled?: boolean }) {
  const { users } = useApiClient()
  return useQuery({
    queryKey: userKeys.byId(id),
    queryFn:  () => users.getById(id),
    enabled:  options?.enabled !== undefined ? options.enabled : !!id,
  })
}

/** Back-office: paginated user list with optional KYC status filter. */
export function useUserList(params: UserListParams = {}) {
  const { users } = useApiClient()
  return useQuery({
    queryKey:        userKeys.list(params),
    queryFn:         () => users.list(params),
    placeholderData: (prev) => prev,
    staleTime:       30_000,
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

/** Send a Fast2SMS OTP to the user's phone number. */
export function useSendPhoneOtp() {
  const { users } = useApiClient()
  return useMutation({
    mutationFn: ({ userId, phone }: { userId: string; phone: string }) =>
      users.sendPhoneOtp(userId, phone),
  })
}

/**
 * Verify OTP and save the phone number.
 * Invalidates the /me query so the profile card updates immediately.
 */
export function useVerifyPhoneOtp() {
  const { users } = useApiClient()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, phone, otp }: { userId: string; phone: string; otp: string }) =>
      users.verifyPhoneOtp(userId, phone, otp),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.me() })
    },
  })
}

/** Directly update phone without OTP (back-office use). */
export function useUpdatePhone() {
  const { users } = useApiClient()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, phone }: { userId: string; phone: string | null }) =>
      users.updatePhone(userId, phone),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.me() })
    },
  })
}
