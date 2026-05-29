import { z } from 'zod'

export const NotificationTypeSchema = z.enum([
  'TRANSACTION_COMPLETED',
  'TRANSACTION_FAILED',
  'TRANSACTION_ROLLED_BACK',
  'MONEY_RECEIVED',
  'KYC_STATUS_CHANGED',
  'USER_REGISTERED',
  'GENERIC',
])
export type NotificationType = z.infer<typeof NotificationTypeSchema>

export const NotificationSchema = z.object({
  id: z.string(),
  userId: z.string(),
  type: NotificationTypeSchema,
  channel: z.string(),
  status: z.enum(['PENDING', 'SENT', 'FAILED']),
  title: z.string(),
  body: z.string(),
  createdAt: z.string().datetime(),
  sentAt: z.string().datetime().nullable(),
})
export type Notification = z.infer<typeof NotificationSchema>

export const PagedNotificationsSchema = z.object({
  content: z.array(NotificationSchema),
  totalElements: z.number(),
  totalPages: z.number(),
  page: z.number(),
  size: z.number(),
  last: z.boolean(),
})
export type PagedNotifications = z.infer<typeof PagedNotificationsSchema>
