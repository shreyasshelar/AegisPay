'use client'

import { useRef, useState, useCallback, useEffect } from 'react'
import { useSession } from 'next-auth/react'
import { useAuthGuard } from '@/lib/useAuthGuard'
import { toast } from 'sonner'
import {
  Upload,
  CheckCircle2,
  Clock,
  XCircle,
  Loader2,
  ShieldCheck,
  Camera,
  AlertTriangle,
  ChevronDown,
  Fingerprint,
  Copy,
  Check,
} from 'lucide-react'
import { useMe, useProcessKyc, useBiometric, ApiError } from '@aegispay/api-client'
import { Header } from '@/components/header'
import { Button as AegisButton } from '@aegispay/design-system'
import { cn } from '@/lib/utils'
import type { KycStatus } from '@aegispay/shared-types'

// ── KYC status config ─────────────────────────────────────────────────────────

const KYC_CONFIG: Record<KycStatus, { label: string; icon: React.ElementType; bg: string; text: string }> = {
  PENDING: {
    label: 'KYC Pending — upload a document to get started',
    icon:  Clock,
    bg:    'bg-warning-50 ring-warning-200',
    text:  'text-warning-700',
  },
  DOCUMENT_SUBMITTED: {
    label: 'Document received — AI analysis running in the background (takes a few minutes)',
    icon:  Loader2,
    bg:    'bg-primary-50 ring-primary-200',
    text:  'text-primary-700',
  },
  AI_PROCESSING: {
    label: 'AI is reviewing your document — you\'ll be notified when done',
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

// ── Main component ────────────────────────────────────────────────────────────

export function ProfileClient({ userId }: { userId: string }) {
  const blocking          = useAuthGuard()
  const { data: session } = useSession()
  const fileInputRef      = useRef<HTMLInputElement>(null)
  const cameraInputRef    = useRef<HTMLInputElement>(null)

  const [documentType, setDocumentType] = useState<DocumentTypeValue>('NATIONAL_ID')
  const [isDragging,   setIsDragging]   = useState(false)
  const [idCopied,     setIdCopied]     = useState(false)

  // Use /me endpoint so it works for all users regardless of whether
  // aegispay_user_id has been written back to the Keycloak JWT yet.
  const { data: user, isLoading, isError, error, refetch } = useMe()
  const processKyc = useProcessKyc()

  const kycStatus: KycStatus = user?.kycStatus ?? 'PENDING'
  const cfg = KYC_CONFIG[kycStatus]
  const KycIcon = cfg.icon
  const canUpload = kycStatus === 'PENDING' || kycStatus === 'REJECTED'

  // ── Safety-net polling while AI pipeline is in flight ────────────────────────
  // The banner transitions DOCUMENT_SUBMITTED → APPROVED/REJECTED via a WebSocket
  // KYC_STATUS_CHANGED notification (sidebar handler invalidates userKeys.me()).
  // On the very first login the session access-token lacks the aegispay_user_id
  // claim (written to Keycloak asynchronously after registration), so the STOMP
  // principal falls back to the Keycloak sub and the WebSocket message is silently
  // dropped.  Poll every 10 s as a fallback so the banner still auto-updates
  // without requiring a manual page refresh.
  useEffect(() => {
    const isProcessing = kycStatus === 'DOCUMENT_SUBMITTED' || kycStatus === 'AI_PROCESSING'
    if (!isProcessing) return
    const id = setInterval(() => { void refetch() }, 10_000)
    return () => clearInterval(id)
  }, [kycStatus, refetch])

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

    // No base64 encoding needed — the raw File is sent as multipart/form-data.
    // The server encodes to base64 for the vision AI API.  This keeps the HTTP
    // payload at the raw file size (≤ 5 MB) instead of ~6.7 MB base64 JSON,
    // and allows the Next.js proxy to stream rather than buffer the request.
    try {
      await processKyc.mutateAsync({
        file,
        documentType,
        registeredName: session?.user?.name ?? undefined,
      })

      // 202 Accepted — the document is queued; the KYC status banner on this
      // page transitions to DOCUMENT_SUBMITTED automatically via refetch().
      // We do NOT show a separate "Document submitted" toast here because:
      //  1. The banner state change is immediate and visible on this page.
      //  2. A success toast followed seconds later by "KYC Rejected" is jarring
      //     and contradictory from the user's perspective.
      // The final result (APPROVED / REJECTED / MANUAL_REVIEW) is surfaced via
      // a WebSocket notification toast from the sidebar — that single toast is
      // the meaningful user-facing signal.
      refetch()
    } catch (err) {
      if (err instanceof ApiError && (err.status === 503 || err.errorCode === 'SERVICE_UNAVAILABLE')) {
        const retryIn = err.retryAfterSecs
          ? ` Try again in ${err.retryAfterSecs} seconds.`
          : ' Please try again in a moment.'
        toast.warning('AI service temporarily unavailable', {
          description: `Document verification is offline right now.${retryIn}`,
          duration: 8000,
        })
      } else if (
        err instanceof ApiError &&
        err.status === 0 &&
        err.message.toLowerCase().includes('timeout')
      ) {
        // Network timeout — the 202 didn't arrive in time but the document may
        // already be queued.  Refetch; the banner will show DOCUMENT_SUBMITTED
        // if the server received it, or remain PENDING/REJECTED if it didn't.
        refetch()
        toast.warning('Upload is taking longer than expected', {
          description:
            'Your document may already be queued for verification — ' +
            'check the status banner below. If it still shows Pending or Rejected, please try again.',
          duration: 12_000,
        })
      } else {
        toast.error('Upload failed', {
          description: err instanceof Error ? err.message : 'Please try again',
        })
      }
    }
  }, [processKyc, documentType, session?.user?.name, refetch])

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

  if (isError) {
    const is503          = error instanceof ApiError && (error.status === 503 || error.errorCode === 'SERVICE_UNAVAILABLE')
    const isNotFound     = error instanceof ApiError && error.errorCode === 'USER_NOT_FOUND'
    const retryAfterSecs = error instanceof ApiError ? error.retryAfterSecs : undefined

    // USER_NOT_FOUND: the onUserNotFound Axios interceptor already fired and called
    // triggerSignOut() → toast → signOut({ callbackUrl: '/login' }).  signOut() is
    // async (POST /api/auth/signout + redirect), so the component stays mounted for
    // ~1-2 seconds while the sign-out completes.  Show a neutral loading state rather
    // than a confusing error card — the user is about to be redirected regardless.
    if (isNotFound) {
      return (
        <>
          <Header title="Profile & KYC" />
          <div className="flex h-64 flex-col items-center justify-center gap-3">
            <Loader2 className="h-7 w-7 animate-spin text-slate-300" />
            <p className="text-sm text-slate-500">Refreshing your session…</p>
          </div>
        </>
      )
    }

    return (
      <>
        <Header title="Profile & KYC" />
        <div className="px-6 py-10 max-w-xl">
          <div className="flex flex-col items-center gap-4 rounded-xl bg-white p-8 shadow-sm ring-1 ring-slate-200 text-center">
            <AlertTriangle className={cn('h-10 w-10', is503 ? 'text-warning-400' : 'text-danger-400')} />
            <div>
              <p className="font-semibold text-slate-900">
                {is503 ? 'Service temporarily unavailable' : 'Could not load profile'}
              </p>
              <p className="mt-1 text-sm text-slate-500">
                {is503
                  ? `The profile service is momentarily offline.${retryAfterSecs ? ` Please wait ${retryAfterSecs} seconds and try again.` : ' Please wait a moment and try again.'}`
                  : (error instanceof Error ? error.message : 'An unexpected error occurred.')}
              </p>
            </div>
            <button
              onClick={() => refetch()}
              className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 transition-colors"
            >
              Retry
            </button>
          </div>
        </div>
      </>
    )
  }

  return (
    <>
      <Header title="Profile & KYC" subtitle="Identity verification status" />

      <div className="px-6 pb-10 max-w-xl space-y-5 animate-fade-in">

        {/* ── Profile card ─────────────────────────────────────────────────── */}
        <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200 space-y-4">
          <div className="flex items-center gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-primary-100 text-xl font-bold uppercase text-primary-700">
              {session?.user?.name?.charAt(0) ?? '?'}
            </div>
            <div className="min-w-0">
              <p className="text-base font-semibold text-slate-900 truncate">{session?.user?.name}</p>
              <p className="text-sm text-slate-400 truncate">{session?.user?.email}</p>
              <span className="mt-0.5 inline-block rounded-full bg-primary-50 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-primary-700">
                {user?.role ?? session?.user?.role ?? 'CUSTOMER'}
              </span>
            </div>
          </div>

          {/* AegisPay User ID — only revealed after KYC is approved */}
          {user?.id && (
            <div className="rounded-lg bg-slate-50 px-4 py-3 ring-1 ring-slate-100">
              <p className="text-xs text-slate-400 mb-1.5 font-medium">Your AegisPay ID</p>
              {kycStatus === 'APPROVED' ? (
                <>
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-mono text-xs text-slate-700 break-all select-all">
                      {user.id}
                    </span>
                    <button
                      type="button"
                      onClick={() => {
                        navigator.clipboard.writeText(user.id).then(() => {
                          setIdCopied(true)
                          setTimeout(() => setIdCopied(false), 2000)
                        })
                      }}
                      className="shrink-0 rounded-md p-1.5 text-slate-400 hover:bg-slate-200 hover:text-slate-700 transition-colors"
                      title="Copy ID"
                    >
                      {idCopied
                        ? <Check className="h-3.5 w-3.5 text-success-500" />
                        : <Copy className="h-3.5 w-3.5" />}
                    </button>
                  </div>
                  <p className="text-xs text-slate-400 mt-1.5">
                    Share this ID with others so they can send you money.
                  </p>
                </>
              ) : (
                <>
                  <span className="font-mono text-sm tracking-widest text-slate-300 select-none">
                    ••••••••-••••-••••-••••-••••••••••••
                  </span>
                  <p className="mt-1.5 flex items-center gap-1.5 text-xs text-warning-600">
                    <ShieldCheck className="h-3.5 w-3.5 shrink-0" />
                    Complete KYC verification to unlock your ID and receive payments.
                  </p>
                </>
              )}
            </div>
          )}
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

        {/* ── Processing hint (shown while AI is working) ───────────────────── */}
        {(kycStatus === 'DOCUMENT_SUBMITTED' || kycStatus === 'AI_PROCESSING') && (
          <div className="rounded-xl bg-primary-50 px-5 py-4 ring-1 ring-primary-200 flex items-start gap-3">
            <Loader2 className="h-5 w-5 text-primary-500 shrink-0 mt-0.5 animate-spin" />
            <div>
              <p className="text-sm font-semibold text-primary-700">Analysis in progress</p>
              <p className="text-xs text-primary-600 mt-0.5">
                Our AI is reviewing your document. This usually takes a few minutes.
                You&apos;ll get a notification as soon as it&apos;s done — no need to stay on this page.
              </p>
            </div>
          </div>
        )}

        {/* ── Upload panel ─────────────────────────────────────────────────── */}
        {canUpload && (
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
                  <p className="text-sm font-medium text-slate-600">Sending document…</p>
                  <p className="text-xs text-slate-400">AI analysis starts once received</p>
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
