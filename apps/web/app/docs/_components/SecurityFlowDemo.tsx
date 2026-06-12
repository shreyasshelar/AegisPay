'use client'

import { useState } from 'react'

type Step = {
  id: number
  actor: 'browser' | 'gateway' | 'keycloak' | 'service'
  label: string
  detail: string
  token?: string
}

const OAUTH_STEPS: Step[] = [
  {
    id: 1,
    actor: 'browser',
    label: 'User clicks "Sign in"',
    detail: 'Next.js redirects to /api/auth/signin/keycloak. NextAuth generates a PKCE code_verifier (random 43-128 char string) + code_challenge = BASE64URL(SHA256(verifier)).',
    token: 'code_challenge + code_challenge_method=S256',
  },
  {
    id: 2,
    actor: 'keycloak',
    label: 'Keycloak shows login form',
    detail: 'Keycloak stores the code_challenge and waits for credentials. The browser never sees the code_verifier yet — it stays only in the NextAuth server session.',
  },
  {
    id: 3,
    actor: 'browser',
    label: 'User submits credentials',
    detail: 'Keycloak validates username/password (or delegates to Google/GitHub SSO). Issues a short-lived authorization code (valid ~60s) and redirects to the NextAuth callback URL.',
    token: 'authorization_code (opaque, 60s TTL)',
  },
  {
    id: 4,
    actor: 'gateway',
    label: 'NextAuth exchanges code for tokens',
    detail: 'The NextAuth callback handler sends code + code_verifier to Keycloak\'s token endpoint. Keycloak verifies SHA256(verifier) === stored challenge — proving the requestor is the one who started the flow.',
    token: 'access_token (JWT, 5m) + refresh_token (24h)',
  },
  {
    id: 5,
    actor: 'gateway',
    label: 'API Gateway validates JWT',
    detail: 'Every subsequent API call passes the JWT in the Authorization header. The gateway verifies the RS256 signature against Keycloak\'s JWKS endpoint, checks exp, and extracts aegispay_user_id + realm_access.roles claims.',
    token: 'Bearer <JWT> → { sub, aegispay_user_id, roles }',
  },
  {
    id: 6,
    actor: 'service',
    label: 'Microservice enforces RBAC',
    detail: 'Each service method is guarded by @PreAuthorize("hasRole(\'CUSTOMER\')") or similar. Spring Security reads the roles from the validated JWT — no database lookup needed at runtime.',
    token: 'roles: [CUSTOMER | BACK_OFFICE | ADMIN | PARTNER]',
  },
]

const ACTOR_COLORS: Record<Step['actor'], string> = {
  browser:  'bg-blue-100 text-blue-700 border-blue-200',
  gateway:  'bg-purple-100 text-purple-700 border-purple-200',
  keycloak: 'bg-emerald-100 text-emerald-700 border-emerald-200',
  service:  'bg-amber-100 text-amber-700 border-amber-200',
}

const ACTOR_LABELS: Record<Step['actor'], string> = {
  browser:  'Browser / Next.js',
  gateway:  'API Gateway',
  keycloak: 'Keycloak (IdP)',
  service:  'Microservice',
}

export default function SecurityFlowDemo() {
  const [active, setActive] = useState<number>(1)

  const current = OAUTH_STEPS.find(s => s.id === active)!

  return (
    <div className="rounded-xl border border-gray-100 bg-white overflow-hidden">
      {/* Step pills */}
      <div className="flex gap-1.5 flex-wrap p-4 border-b border-gray-100 bg-gray-50">
        {OAUTH_STEPS.map(step => (
          <button
            key={step.id}
            onClick={() => setActive(step.id)}
            className={`flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-medium border transition-all ${
              active === step.id
                ? 'bg-blue-600 text-white border-blue-600'
                : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300 hover:text-blue-600'
            }`}
          >
            <span className={`w-4 h-4 rounded-full flex items-center justify-center text-[10px] font-bold ${
              active === step.id ? 'bg-white/20 text-white' : 'bg-gray-100 text-gray-500'
            }`}>{step.id}</span>
            {step.label.split(' ').slice(0, 3).join(' ')}
          </button>
        ))}
      </div>

      {/* Detail panel */}
      <div className="p-5 space-y-4">
        <div className="flex items-start gap-3">
          <span className={`shrink-0 rounded-full border px-2.5 py-0.5 text-xs font-semibold ${ACTOR_COLORS[current.actor]}`}>
            {ACTOR_LABELS[current.actor]}
          </span>
          <h3 className="font-semibold text-gray-900">{current.label}</h3>
        </div>

        <p className="text-sm text-gray-600 leading-relaxed">{current.detail}</p>

        {current.token && (
          <div className="rounded-lg bg-slate-900 px-4 py-3 font-mono text-xs text-green-300 overflow-x-auto">
            {current.token}
          </div>
        )}

        {/* Navigation */}
        <div className="flex gap-2 pt-1">
          <button
            disabled={active === 1}
            onClick={() => setActive(a => a - 1)}
            className="flex-1 rounded-lg border border-gray-200 py-2 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-40 transition"
          >
            ← Previous
          </button>
          <button
            disabled={active === OAUTH_STEPS.length}
            onClick={() => setActive(a => a + 1)}
            className="flex-1 rounded-lg bg-blue-600 py-2 text-xs font-medium text-white hover:bg-blue-700 disabled:opacity-40 transition"
          >
            Next →
          </button>
        </div>
      </div>
    </div>
  )
}
