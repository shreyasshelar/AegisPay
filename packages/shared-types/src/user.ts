import { z } from 'zod'

export const KycStatusSchema = z.enum([
  'PENDING',
  'DOCUMENT_SUBMITTED',
  'AI_PROCESSING',
  'APPROVED',
  'REJECTED',
  'MANUAL_REVIEW',
])
export type KycStatus = z.infer<typeof KycStatusSchema>

export const UserRoleSchema = z.enum([
  'CUSTOMER',
  'MERCHANT_OPS',
  'BACK_OFFICE',
  'ADMIN',
  'PARTNER',
])
export type UserRole = z.infer<typeof UserRoleSchema>

export const UserSchema = z.object({
  id: z.string().uuid(),
  externalId: z.string().optional(),
  /** Full display name: "{firstName} {lastName}" */
  name: z.string().nullable().optional(),
  email: z.string(),          // masked server-side: j***@example.com
  phone: z.string().nullable(), // masked server-side
  firstName: z.string().nullable().optional(),
  lastName: z.string().nullable().optional(),
  kycStatus: KycStatusSchema,
  role: UserRoleSchema,
  tenantId: z.string().nullable(),
  active: z.boolean().optional(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime().optional(),
})
export type User = z.infer<typeof UserSchema>

export const KycUploadRequestSchema = z.object({
  base64ImageData: z.string().min(1),
  mimeType: z.enum(['image/jpeg', 'image/png', 'image/webp']),
  registeredName: z.string().optional(),
})
export type KycUploadRequest = z.infer<typeof KycUploadRequestSchema>

export const KycProcessingResultSchema = z.object({
  status: z.enum(['APPROVED', 'MANUAL_REVIEW', 'REJECTED']),
  rejectionCode: z.string().nullable(),
  rejectionReason: z.string().nullable(),
  quality: z.object({
    overallScore: z.number(),
    sharpness: z.number(),
    brightness: z.number(),
    crop: z.number(),
    glare: z.number(),
    acceptable: z.boolean(),
    rejectionReason: z.string().nullable(),
  }),
  tampering: z
    .object({
      tampered: z.boolean(),
      confidence: z.number(),
      indicators: z.array(z.string()),
    })
    .nullable(),
  extractedData: z
    .object({
      fullName: z.string().nullable(),
      dateOfBirth: z.string().nullable(),
      documentNumber: z.string().nullable(),
      documentType: z.string().nullable(),
      expiryDate: z.string().nullable(),
      address: z.string().nullable(),
    })
    .nullable(),
  validation: z
    .object({
      documentTypeDetected: z.string().nullable(),
      formatValid: z.boolean(),
      formatDetails: z.string().nullable(),
      notExpired: z.boolean().nullable(),
      ageVerified: z.boolean().nullable(),
      securityFeaturesPresent: z.boolean(),
      missingSecurityFeatures: z.array(z.string()),
      nameMatch: z.boolean().nullable(),
      nameMatchDetails: z.string().nullable(),
      issuingAuthorityVisible: z.boolean(),
      photoPresent: z.boolean(),
      extractedDocumentNumber: z.string().nullable(),
      extractedExpiry: z.string().nullable(),
      extractedDob: z.string().nullable(),
      extractedName: z.string().nullable(),
      overallValid: z.boolean(),
      failureReasons: z.array(z.string()),
    })
    .nullable(),
})
export type KycProcessingResult = z.infer<typeof KycProcessingResultSchema>
