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
  /**
   * Non-null only when kycStatus === 'REJECTED'.
   * Human-readable AI explanation of why the document was rejected,
   * shown directly in the KYC status banner on the profile page.
   */
  rejectionReason: z.string().nullable().optional(),
  /**
   * Whether the user has opted in to SMS notifications.
   * true  → phone on file AND user opted in  → SMS will be sent
   * false → no phone OR user explicitly disabled → SMS skipped
   * Automatically enabled when a verified phone number is first saved.
   */
  smsNotificationsEnabled: z.boolean().optional(),
})
export type User = z.infer<typeof UserSchema>

/**
 * Payload for KYC document upload.
 *
 * The file is sent as raw binary via multipart/form-data — no base64 encoding in
 * the browser.  The AI Platform server reads the bytes and encodes them once for the
 * downstream vision AI API.  This keeps the HTTP payload 33 % smaller than the old
 * base64-JSON approach and allows the Next.js proxy to stream rather than buffer.
 */
export interface KycUploadRequest {
  /** Raw image file selected by the user (JPEG / PNG / WebP, max 5 MB). */
  file: File
  /** Document type declared by the user (NATIONAL_ID, PASSPORT, DRIVING_LICENSE, PAN_CARD). */
  documentType?: string
  /** Optional registered account name for AI cross-validation against the document. */
  registeredName?: string
}

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
