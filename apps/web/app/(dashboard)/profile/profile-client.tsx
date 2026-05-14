'use client'

import { useRef, useState, useCallback } from 'react'
import { useSession } from 'next-auth/react'
import { useAuthGuard } from '@/lib/useAuthGuard'
import { toast } from 'sonner'
import {
  UserCircle,
  Upload,
  CheckCircle2,
  Clock,
  XCircle,
  Loader2,
  ShieldCheck,
  Camera,
  Star,
  AlertTriangle,
  FileCheck,
  ChevronDown,
  Fingerprint,
} from 'lucide-react'
import { useUser, useProcessKyc, useConfirmKyc, useBiometric } from '@aegispay/api-client'
import { Header } from '@/components/header'
import { Button as AegisButton } from '@aegispay/design-system'
import { cn } from '@/lib/utils'
import type { KycStatus, KycProcessingResult } from '@aegispay/shared-types'

// ── KYC status config ─────────────────────────────────────────────────────────

const KYC_CONFIG: Record<KycStatus, { label: string; icon: React.ElementType; bg: string; text: string }> = {
  PENDING: {
    label: 'KYC Pending — upload a document to get started',
    icon:  Clock,
    bg:    'bg-warning-50 ring-warning-200',
    text:  'text-warning-700',
  },
  DOCUMENT_SUBMITTED: {
    label: 'Document received — awaiting AI processing',
    icon:  Loader2,
    bg:    'bg-primary-50 ring-primary-200',
    text:  'text-primary-700',
  },
  AI_PROCESSING: {
    label: 'AI processing your document…',
    icon:  Loader2,
    bg:    'bg-primary-50 ring-primary-200',
    text:  'text-primary-700',
  },
  APPROVED: {
    label: 'KYC Approved — your account is fully verified',
    icon:  CheckCircle2,
    bg:    'bg-success-50 ring-success-200',
    text:  'text-success-700',
  },
  REJECTED: {
    label: 'KYC Rejected — please re-upload a valid document',
    icon:  XCircle,
    bg:    'bg-danger-50 ring-danger-200',
    text:  'text-danger-700',
  },
  MANUAL_REVIEW: {
    label: 'Under manual review — our team will contact you',
    icon:  ShieldCheck,
    bg:    'bg-slate-50 ring-slate-200',
    text:  'text-slate-700',
  },
}

const DOCUMENT_TYPES = [
  { value: 'NATIONAL_ID',       label: 'National ID / Aadhaar' },
  { value: 'PASSPORT',          label: 'Passport' },
  { value: 'DRIVING_LICENSE',   label: "Driver's License" },
  { value: 'PAN_CARD',          label: 'PAN Card' },
] as const
type DocumentTypeValue = typeof DOCUMENT_TYPES[number]['value']

// ── Quality score badge ───────────────────────────────────────────────────────

function QualityBar({ score, label }: { score: number; label: string }) {
  const color = score >= 70 ? 'bg-success-500' : score >= 40 ? 'bg-warning-500' : 'bg-danger-500'
  return (
    <div className="space-y-1">
      <div className="flex justify-between text-xs text-slate-500">
        <span>{label}</span>
        <span className="font-medium">{Math.round(score)}%</span>
      </div>
      <div className="h-1.5 rounded-full bg-slate-100">
        <div className={cn('h-1.5 rounded-full transition-all', color)} style={{ width: `${score}%` }} />
      </div>
    </div>
  )
}

// ── Extracted data table ──────────────────────────────────────────────────────

function ExtractedDataCard({ data }: { data: NonNullable<KycProcessingResult['extractedData']> }) {
  const rows = [
    { label: 'Full Name',       value: data.fullName },
    { label: 'Date of Birth',   value: data.dateOfBirth },
    { label: 'Document Number', value: data.documentNumber },
    { label: 'Document Type',   value: data.documentType },
    { label: 'Expiry Date',     value: data.expiryDate },
    { label: 'Address',         value: data.address },
  ].filter(r => r.value)

  return (
    <dl className="divide-y divide-slate-100">
      {rows.map(r => (
        <div key={r.label} className="flex items-start justify-between gap-4 py-2.5">
          <dt className="text-xs text-slate-400 shrink-0">{r.label}</dt>
          <dd className="text-xs font-medium text-slate-800 text-right break-all">{r.value}</dd>
        </div>
      ))}
    </dl>
  )
}

// ── Main component ────────────────────────────────────────────────────────────

export function ProfileClient({ userId }: { userId: string }) {
  const blocking          = useAuthGuard()
  const { data: session } = useSession()
  const fileInputRef      = useRef<HTMLInputElement>(null)
  const cameraInputRef    = useRef<HTMLInputElement>(null)

  const [documentType, setDocumentType] = useState<DocumentTypeValue>('NATIONAL_ID')
  const [kycResult,    setKycResult]    = useState<KycProcessingResult | null>(null)
  const [isDragging,   setIsDragging]   = useState(false)

  const { data: user, isLoading, refetch } = useUser(userId)
  const processKyc  = useProcessKyc()
  const confirmKyc  = useConfirmKyc()

  const kycStatus: KycStatus = user?.kycStatus ?? 'PENDING'
  const cfg = KYC_CONFIG[kycStatus]
  const KycIcon = cfg.icon
  const canUpload = kycStatus === 'PENDING' || kycStatus === 'REJECTED'

  // ── File handling ──────────────────────────────────────────────────────────

  const handleFile = useCallback(async (file: File) => {
    if (file.size > 5 * 1024 * 1024) {
      toast.error('File too large', { description: 'Maximum 5 MB allowed' })
      return
    }
    if (!file.type.match(/^image\/(jpeg|png|webp)$/)) {
      toast.error('Invalid file type', { description: 'JPG, PNG or WebP only' })
      return
    }

    const buffer = await file.arrayBuffer()
    const bytes = new Uint8Array(buffer)
    let binary = ''
    for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i])
    const base64 = btoa(binary)

    try {
      const result = await processKyc.mutateAsync({
        base64ImageData: base64,
        mimeType: file.type as 'image/jpeg' | 'image/png' | 'image/webp',
      })
      setKycResult(result)

      if (!result.quality.acceptable) {
        toast.warning('Low image quality', {
          description: result.quality.rejectionReason ?? 'Please retake or use a higher-quality image',
        })
      } else if (result.tampering?.tampered) {
        toast.error('Document tampering detected', {
          description: 'Please upload an unmodified original document',
        })
      } else {
        toast.success('Document analysed', { description: 'Review the extracted data below and confirm' })
      }
    } catch (err) {
      toast.error('Upload failed', { description: err instanceof Error ? err.message : 'Please try again' })
    }
  }, [processKyc])

  function onFileInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (file) handleFile(file)
    e.target.value = ''
  }

  function onDrop(e: React.DragEvent) {
    e.preventDefault()
    setIsDragging(false)
    const file = e.dataTransfer.files?.[0]
    if (file) handleFile(file)
  }

  // ── Confirm extracted data ─────────────────────────────────────────────────

  async function handleConfirm() {
    try {
      await confirmKyc.mutateAsync({ userId, documentType })
      toast.success('KYC submitted', { description: 'Your identity verification is under review' })
      setKycResult(null)
      refetch()
    } catch (err) {
      toast.error('Confirmation failed', { description: err instanceof Error ? err.message : 'Please try again' })
    }
  }

  // ── Auth guard (all hooks above; conditional renders below) ──────────────

  if (blocking) return null

  // ── Loading skeleton ───────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <>
        <Header title="Profile & KYC" />
        <div className="flex h-64 items-center justify-center">
          <Loader2 className="h-7 w-7 animate-spin text-slate-300" />
        </div>
      </>
    )
  }

  return (
    <>
      <Header title="Profile & KYC" subtitle="Identity verification status" />

      <div className="px-6 pb-10 max-w-xl space-y-5 animate-fade-in">

        {/* ── Profile card ─────────────────────────────────────────────────── */}
        <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
          <div className="flex items-center gap-4">
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-primary-100 text-xl font-bold uppercase text-primary-700">
              {session?.user?.name?.charAt(0) ?? '?'}
            </div>
            <div>
              <p className="text-base font-semibold text-slate-900">{session?.user?.name}</p>
              <p className="text-sm text-slate-400">{session?.user?.email}</p>
              <span className="mt-0.5 inline-block rounded-full bg-primary-50 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-primary-700">
                {user?.role ?? session?.user?.role ?? 'CUSTOMER'}
              </span>
            </div>
          </div>
        </div>

        {/* ── KYC status banner ────────────────────────────────────────────── */}
        <div className={cn('flex items-center gap-3 rounded-xl px-4 py-3 ring-1', cfg.bg, cfg.text)}>
          <KycIcon className={cn('h-5 w-5 shrink-0', (kycStatus === 'AI_PROCESSING' || kycStatus === 'DOCUMENT_SUBMITTED') && 'animate-spin')} />
          <p className="text-sm font-medium">{cfg.label}</p>
        </div>

        {/* ── Verified badge ───────────────────────────────────────────────── */}
        {kycStatus === 'APPROVED' && (
          <div className="flex items-center gap-3 rounded-xl bg-success-50 px-5 py-4 ring-1 ring-success-200">
            <CheckCircle2 className="h-8 w-8 text-success-500" />
            <div>
              <p className="font-semibold text-success-700">Identity Verified</p>
              <p className="text-sm text-success-600">Your account has full transaction limits</p>
            </div>
          </div>
        )}

        {/* ── Upload panel ─────────────────────────────────────────────────── */}
        {canUpload && !kycResult && (
          <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200 space-y-4">
            <div>
              <h3 className="text-sm font-semibold text-slate-900">Upload Identity Document</h3>
              <p className="mt-0.5 text-xs text-slate-400">JPG · PNG · WebP · Max 5 MB</p>
            </div>

            {/* Document type selector */}
            <div className="space-y-1.5">
              <label className="block text-xs font-medium text-slate-700">Document Type</label>
              <div className="relative">
                <select
                  value={documentType}
                  onChange={e => setDocumentType(e.target.value as DocumentTypeValue)}
                  className="w-full appearance-none rounded-lg border border-slate-300 bg-white px-3 py-2.5 pr-8 text-sm text-slate-900 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-200"
                >
                  {DOCUMENT_TYPES.map(dt => (
                    <option key={dt.value} value={dt.value}>{dt.label}</option>
                  ))}
                </select>
                <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              </div>
            </div>

            {/* Drag-and-drop zone */}
            <div
              onDragOver={e => { e.preventDefault(); setIsDragging(true) }}
              onDragLeave={() => setIsDragging(false)}
              onDrop={onDrop}
              className={cn(
                'flex flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed py-10 transition-colors',
                isDragging
                  ? 'border-primary-400 bg-primary-50'
                  : 'border-slate-200 bg-slate-50 hover:border-primary-300',
                processKyc.isPending && 'opacity-60 pointer-events-none',
              )}
            >
              {processKyc.isPending ? (
                <>
                  <Loader2 className="h-8 w-8 animate-spin text-primary-400" />
                  <p className="text-sm text-slate-500">Analysing document…</p>
                </>
              ) : (
                <>
                  <Upload className="h-8 w-8 text-slate-300" />
                  <p className="text-sm text-slate-500">Drag & drop here, or choose an option below</p>
                </>
              )}
            </div>

            {/* Gallery + Camera buttons */}
            <div className="grid grid-cols-2 gap-3">
              <input ref={fileInputRef} type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={onFileInputChange} />
              <input ref={cameraInputRef} type="file" accept="image/*" capture="environment" className="hidden" onChange={onFileInputChange} />

              <AegisButton
                type="button"
                variant="secondary"
                onClick={() => fileInputRef.current?.click()}
                disabled={processKyc.isPending}
                className="w-full"
              >
                <Upload className="h-4 w-4" />
                Gallery
              </AegisButton>

              <AegisButton
                type="button"
                variant="secondary"
                onClick={() => cameraInputRef.current?.click()}
                disabled={processKyc.isPending}
                className="w-full"
              >
                <Camera className="h-4 w-4" />
                Camera
              </AegisButton>
            </div>
          </div>
        )}

        {/* ── Security (WebAuthn biometric) ────────────────────────────────── */}
        <BiometricSetupCard />

        {/* ── KYC result panel ─────────────────────────────────────────────── */}
        {kycResult && (
          <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200 space-y-5">

            {/* Quality scores */}
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Star className="h-4 w-4 text-warning-500" />
                <h3 className="text-sm font-semibold text-slate-900">Image Quality</h3>
                <span className={cn(
                  'ml-auto rounded-full px-2 py-0.5 text-xs font-semibold',
                  kycResult.quality.acceptable
                    ? 'bg-success-50 text-success-700'
                    : 'bg-danger-50 text-danger-700',
                )}>
                  {kycResult.quality.acceptable ? 'Acceptable' : 'Low quality'}
                </span>
              </div>
              <div className="space-y-2">
                <QualityBar score={kycResult.quality.sharpness}   label="Sharpness" />
                <QualityBar score={kycResult.quality.brightness}  label="Brightness" />
                <QualityBar score={kycResult.quality.overallScore} label="Overall" />
              </div>
            </div>

            {/* Tampering alert */}
            {kycResult.tampering?.tampered && (
              <div className="flex items-start gap-2 rounded-lg bg-danger-50 p-3 ring-1 ring-danger-200">
                <AlertTriangle className="h-4 w-4 text-danger-600 mt-0.5 shrink-0" />
                <div>
                  <p className="text-xs font-semibold text-danger-700">Tampering detected</p>
                  {kycResult.tampering.indicators.length > 0 && (
                    <p className="text-xs text-danger-600 mt-0.5">{kycResult.tampering.indicators.join(', ')}</p>
                  )}
                </div>
              </div>
            )}

            {/* Extracted data */}
            {kycResult.extractedData && (
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <FileCheck className="h-4 w-4 text-primary-500" />
                  <h3 className="text-sm font-semibold text-slate-900">Extracted Information</h3>
                </div>
                <div className="rounded-lg bg-slate-50 px-4 ring-1 ring-slate-100">
                  <ExtractedDataCard data={kycResult.extractedData} />
                </div>
                <p className="text-xs text-slate-400">
                  Please verify this information is correct before confirming.
                </p>
              </div>
            )}

            {/* Actions */}
            <div className="flex gap-3">
              <AegisButton
                type="button"
                variant="secondary"
                onClick={() => setKycResult(null)}
                disabled={confirmKyc.isPending}
                className="flex-1"
              >
                Retake
              </AegisButton>
              <AegisButton
                type="button"
                loading={confirmKyc.isPending}
                onClick={handleConfirm}
                disabled={!kycResult.quality.acceptable || kycResult.tampering?.tampered === true}
                className="flex-1"
              >
                <CheckCircle2 className="h-4 w-4" />
                {confirmKyc.isPending ? 'Submitting…' : 'Confirm & Submit'}
              </AegisButton>
            </div>
          </div>
        )}
      </div>
    </>
  )
}

// ── Biometric setup card ──────────────────────────────────────────────────────

function BiometricSetupCard() {
  const {
    isSupported,
    isRegistered,
    isEnabled,
    isAuthenticating,
    error,
    register,
    setEnabled,
    reset,
  } = useBiometric()

  if (!isSupported) return null

  async function handleToggle(checked: boolean) {
    if (checked && !isRegistered) {
      const ok = await register()
      if (!ok) return
      toast.success('Biometric unlock enabled')
    } else {
      setEnabled(checked)
      if (!checked) toast.success('Biometric unlock disabled')
    }
  }

  return (
    <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200 space-y-4">
      {/* Header */}
      <div className="flex items-center gap-2.5">
        <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary-50">
          <Fingerprint className="h-5 w-5 text-primary-600" />
        </div>
        <h3 className="text-sm font-semibold text-slate-900">Security</h3>
      </div>

      <div className="border-t border-slate-100" />

      {/* Toggle row */}
      <div className="flex items-center justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-slate-800">Biometric Unlock</p>
          <p className="text-xs text-slate-400 mt-0.5">
            {isRegistered
              ? 'Uses your device passkey (Face ID, Touch ID, Windows Hello) to re-authenticate.'
              : 'Register a passkey to unlock AegisPay with your device biometric.'}
          </p>
        </div>

        {/* Accessible toggle */}
        <button
          role="switch"
          aria-checked={isEnabled}
          aria-label="Biometric unlock"
          disabled={isAuthenticating}
          onClick={() => handleToggle(!isEnabled)}
          className={cn(
            'relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
            isEnabled ? 'bg-primary-600' : 'bg-slate-200',
            isAuthenticating && 'opacity-50 cursor-not-allowed',
          )}
        >
          <span
            className={cn(
              'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out',
              isEnabled ? 'translate-x-5' : 'translate-x-0',
            )}
          />
        </button>
      </div>

      {/* Loading state */}
      {isAuthenticating && (
        <div className="flex items-center gap-2 text-xs text-slate-500">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Waiting for biometric prompt…
        </div>
      )}

      {/* Error */}
      {error && (
        <p className="text-xs text-danger-600">{error}</p>
      )}

      {/* Reset link */}
      {isRegistered && (
        <button
          type="button"
          onClick={() => { reset(); toast.success('Passkey removed') }}
          className="text-xs text-slate-400 underline hover:text-slate-600"
        >
          Remove passkey
        </button>
      )}
    </div>
  )
}
