import { z } from 'zod'

export const AccountSchema = z.object({
  id: z.string().uuid(),
  userId: z.string().uuid(),
  currency: z.string().length(3),
  availableBalance: z.coerce.number(),
  reservedBalance: z.coerce.number(),
  totalBalance: z.coerce.number(),
})
export type Account = z.infer<typeof AccountSchema>

export const LedgerEntryTypeSchema = z.enum([
  'DEBIT',
  'CREDIT',
  'RESERVE',
  'RELEASE',
  'COMMIT',
])
export type LedgerEntryType = z.infer<typeof LedgerEntryTypeSchema>

export const LedgerEntrySchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  transactionId: z.string().uuid(),
  entryType: LedgerEntryTypeSchema,
  amount: z.string(),
  balanceBefore: z.string(),
  balanceAfter: z.string(),
  createdAt: z.string().datetime(),
})
export type LedgerEntry = z.infer<typeof LedgerEntrySchema>
