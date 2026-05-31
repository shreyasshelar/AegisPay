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
  payerId: z.string().uuid().nullable(),
  payeeId: z.string().uuid(),
  amount: z.string(), // BigDecimal comes as string from JSON
  currency: z.string().length(3),
  status: TransactionStatusSchema,
  idempotencyKey: z.string(),
  sagaId: z.string().uuid().nullable(),
  initiatedAt: z.string().datetime(),
  completedAt: z.string().datetime().nullable(),
  failureReason: z.string().nullable(),
  failureCode: z.string().nullable().optional(),
  note: z.string().nullable(),
  externalReference: z.string().nullable().optional(),
  /**
   * Direction of the transaction relative to the requesting user.
   * "SENT" = caller is payer; "RECEIVED" = caller is payee.
   * Null on write-side responses (create / getById).
   */
  direction: z.enum(['SENT', 'RECEIVED']).nullable().optional(),
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
  direction: true,
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
  page: z.number(),    // matches PagedResponse.page on the Java side
  size: z.number(),
  last: z.boolean(),
})
export type PagedTransactions = z.infer<typeof PagedTransactionsSchema>

// WebSocket notification payload — from notification-service (/user queue)
export const TransactionNotificationSchema = z.object({
  type: z.enum(['TRANSACTION_COMPLETED', 'TRANSACTION_FAILED', 'TRANSACTION_ROLLED_BACK']),
  title: z.string(),
  body: z.string(),
  transactionId: z.string().uuid().optional(),
})
export type TransactionNotification = z.infer<typeof TransactionNotificationSchema>

// Real-time status update — from transaction-service (/topic/transactions/{id}/status)
// Matches TransactionStatusResponse.java
export const TransactionStatusUpdateSchema = z.object({
  transactionId: z.string().uuid(),
  status:        TransactionStatusSchema,
  lastEvent:     z.string(),
  updatedAt:     z.string().datetime({ offset: true }),
  failureReason: z.string().nullable().optional(),
  failureCode:   z.string().nullable().optional(),
  aiExplanation: z.string().nullable().optional(),
})
export type TransactionStatusUpdate = z.infer<typeof TransactionStatusUpdateSchema>
