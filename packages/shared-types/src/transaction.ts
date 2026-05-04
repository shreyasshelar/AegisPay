import { z } from 'zod'

export const TransactionStatusSchema = z.enum([
  'INITIATED',
  'RESERVED',
  'RISK_CLEARED',
  'PROCESSING',
  'COMPLETED',
  'FAILED',
  'ROLLED_BACK',
])
export type TransactionStatus = z.infer<typeof TransactionStatusSchema>

export const TransactionSchema = z.object({
  transactionId: z.string().uuid(),
  userId: z.string().uuid(),
  payeeId: z.string().uuid(),
  amount: z.string(), // BigDecimal comes as string from JSON
  currency: z.string().length(3),
  status: TransactionStatusSchema,
  idempotencyKey: z.string(),
  sagaId: z.string().uuid().nullable(),
  initiatedAt: z.string().datetime(),
  completedAt: z.string().datetime().nullable(),
  failureReason: z.string().nullable(),
  note: z.string().nullable(),
})
export type Transaction = z.infer<typeof TransactionSchema>

export const TransactionSummarySchema = TransactionSchema.pick({
  transactionId: true,
  amount: true,
  currency: true,
  status: true,
  initiatedAt: true,
  completedAt: true,
  payeeId: true,
})
export type TransactionSummary = z.infer<typeof TransactionSummarySchema>

export const CreateTransactionRequestSchema = z.object({
  payeeId: z.string().uuid(),
  amount: z.string().regex(/^\d+(\.\d{1,2})?$/, 'Invalid amount'),
  currency: z.string().length(3),
  note: z.string().max(200).optional(),
})
export type CreateTransactionRequest = z.infer<typeof CreateTransactionRequestSchema>

export const PagedTransactionsSchema = z.object({
  content: z.array(TransactionSummarySchema),
  totalElements: z.number(),
  totalPages: z.number(),
  number: z.number(),
  size: z.number(),
  last: z.boolean(),
})
export type PagedTransactions = z.infer<typeof PagedTransactionsSchema>

// WebSocket notification payload
export const TransactionNotificationSchema = z.object({
  type: z.enum(['TRANSACTION_COMPLETED', 'TRANSACTION_FAILED', 'TRANSACTION_ROLLED_BACK']),
  title: z.string(),
  body: z.string(),
  transactionId: z.string().uuid().optional(),
})
export type TransactionNotification = z.infer<typeof TransactionNotificationSchema>
