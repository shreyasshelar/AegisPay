import dynamic from 'next/dynamic'

const SecurityFlowDemo = dynamic(
  () => import('../_components/SecurityFlowDemo'),
  { ssr: false, loading: () => <div className="animate-pulse bg-gray-100 rounded-xl h-64" /> }
)

const ROLES = [
  {
    role: 'CUSTOMER',
    color: 'border-blue-200 bg-blue-50 text-blue-800',
    badge: 'bg-blue-100 text-blue-700',
    access: [
      'Own transactions (read + initiate)',
      'Own wallet & balance',
      'Own KYC profile',
      'Own notifications',
    ],
    denied: ['Other users\' data', 'Back-office views', 'Admin endpoints'],
  },
  {
    role: 'BACK_OFFICE',
    color: 'border-amber-200 bg-amber-50 text-amber-800',
    badge: 'bg-amber-100 text-amber-700',
    access: [
      'All user accounts (read)',
      'All transactions (read)',
      'Risk cases (read + decision)',
      'Ledger (read)',
      'AI Triage (run)',
    ],
    denied: ['Write to ledger', 'Admin config', 'Vault secrets'],
  },
  {
    role: 'ADMIN',
    color: 'border-red-200 bg-red-50 text-red-800',
    badge: 'bg-red-100 text-red-700',
    access: [
      'All BACK_OFFICE access',
      'User management (write)',
      'System configuration',
      'Audit log access',
      'Risk rule management',
    ],
    denied: ['Direct DB access (must go through service APIs)'],
  },
  {
    role: 'PARTNER',
    color: 'border-purple-200 bg-purple-50 text-purple-800',
    badge: 'bg-purple-100 text-purple-700',
    access: [
      'Initiate payments on behalf of customers',
      'Webhook event subscriptions',
      'Reconciliation reports',
    ],
    denied: ['User PII', 'Internal ledger entries', 'Risk scores'],
  },
]

const SECURITY_LAYERS = [
  {
    layer: 'Transport',
    icon: '🔒',
    controls: ['TLS 1.3 everywhere (in-cluster + external)', 'mTLS between microservices (Istio sidecar)', 'HSTS + certificate pinning on web client'],
  },
  {
    layer: 'Authentication',
    icon: '🪪',
    controls: ['OAuth2 + PKCE via Keycloak 24', 'Multi-IdP: Google, Microsoft, GitHub, Apple', 'RS256 JWT — validated by API Gateway on every request'],
  },
  {
    layer: 'Authorisation',
    icon: '🛡️',
    controls: ['RBAC via @PreAuthorize on every service method', 'Claims extracted from JWT — no DB roundtrip', 'Scoped tokens for Partner integrations'],
  },
  {
    layer: 'Rate Limiting',
    icon: '⏱️',
    controls: ['Redis sliding-window: 100 req / 60s per userId', 'Burst allowance: 10 req / 1s', 'Gateway returns 429 with Retry-After header'],
  },
  {
    layer: 'Secret Management',
    icon: '🔑',
    controls: ['HashiCorp Vault (dev: k3s in-cluster, prod: managed)', 'External Secrets Operator syncs Vault → K8s Secrets', 'Secrets never in env vars or config files'],
  },
  {
    layer: 'WebSocket Auth',
    icon: '🔌',
    controls: ['JWT sent in STOMP CONNECT frame header', 'StompAuthChannelInterceptor validates before subscribe', 'Per-user topic routing: /user/{userId}/queue'],
  },
]

export default function SecurityPage() {
  return (
    <div className="max-w-4xl mx-auto space-y-12">
      <section>
        <h1 className="text-3xl font-bold text-gray-900 mb-4">Security Architecture</h1>
        <p className="text-gray-600 leading-relaxed">
          AegisPay uses a defence-in-depth model: every request is authenticated at the edge, authorised
          at the method level, rate-limited by identity, and all secrets are managed through HashiCorp
          Vault — never stored in environment variables or config files.
        </p>
      </section>

      {/* OAuth2 / PKCE flow */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-2">OAuth2 + PKCE Login Flow</h2>
        <p className="text-gray-600 mb-4">
          Step through the authentication flow from the user clicking "Sign in" to a validated JWT
          reaching a microservice. PKCE (Proof Key for Code Exchange) prevents authorization code
          interception attacks, which is critical in a browser-based SPA.
        </p>
        <SecurityFlowDemo />
      </section>

      {/* JWT claims */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">JWT Payload</h2>
        <p className="text-gray-600 mb-4">
          Every microservice reads identity from the JWT, not from a database. The gateway validates the
          signature and passes the claims downstream via request headers.
        </p>
        <div className="rounded-xl border border-gray-100 bg-white overflow-hidden">
          <div className="bg-slate-900 px-4 py-2 flex items-center gap-2">
            <div className="flex gap-1.5">
              <div className="w-3 h-3 rounded-full bg-red-400" />
              <div className="w-3 h-3 rounded-full bg-yellow-400" />
              <div className="w-3 h-3 rounded-full bg-green-400" />
            </div>
            <span className="text-xs text-slate-400 font-mono">jwt-payload.json</span>
          </div>
          <pre className="bg-slate-900 px-5 py-4 overflow-x-auto text-xs font-mono text-green-300">
{`{
  "sub": "keycloak-internal-uuid",
  "aegispay_user_id": "550e8400-e29b-41d4-a716-446655440000",
  "preferred_username": "alice@example.com",
  "realm_access": {
    "roles": ["CUSTOMER"]
  },
  "iss": "http://keycloak:8180/realms/aegispay",
  "aud": "aegispay-gateway",
  "exp": 1718000000,
  "iat": 1717999700
}`}
          </pre>
        </div>
        <p className="mt-3 text-sm text-gray-500">
          <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded font-mono">aegispay_user_id</code> is
          the stable domain UUID stored in every service&apos;s database.{' '}
          <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded font-mono">sub</code> is Keycloak&apos;s
          internal identifier and is never used as a foreign key.
        </p>
      </section>

      {/* RBAC */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Role-Based Access Control</h2>
        <p className="text-gray-600 mb-4">
          Four roles define the permission surface. Every Spring Boot method that handles a request
          is annotated with <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded font-mono">@PreAuthorize</code>.
        </p>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {ROLES.map(({ role, color, badge, access, denied }) => (
            <div key={role} className={`rounded-xl border p-5 ${color}`}>
              <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-bold font-mono mb-3 ${badge}`}>
                {role}
              </span>
              <div className="mb-3">
                <p className="text-xs font-semibold mb-1.5 opacity-70 uppercase tracking-wide">Can access</p>
                <ul className="space-y-1">
                  {access.map(a => (
                    <li key={a} className="flex items-start gap-1.5 text-xs">
                      <span className="mt-0.5 text-green-600 shrink-0">✓</span>
                      {a}
                    </li>
                  ))}
                </ul>
              </div>
              <div>
                <p className="text-xs font-semibold mb-1.5 opacity-70 uppercase tracking-wide">Denied</p>
                <ul className="space-y-1">
                  {denied.map(d => (
                    <li key={d} className="flex items-start gap-1.5 text-xs opacity-70">
                      <span className="mt-0.5 text-red-500 shrink-0">✗</span>
                      {d}
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Security layers */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Defence in Depth</h2>
        <p className="text-gray-600 mb-4">
          Security controls are stacked across every layer. Compromising any single layer still leaves
          subsequent layers to limit the blast radius.
        </p>
        <div className="rounded-xl border border-gray-100 bg-white divide-y divide-gray-50">
          {SECURITY_LAYERS.map(({ layer, icon, controls }) => (
            <div key={layer} className="flex gap-4 px-5 py-4">
              <div className="text-2xl shrink-0 w-8 text-center">{icon}</div>
              <div className="min-w-0">
                <p className="font-semibold text-gray-900 mb-1.5">{layer}</p>
                <ul className="space-y-1">
                  {controls.map(c => (
                    <li key={c} className="flex items-start gap-2 text-sm text-gray-600">
                      <span className="mt-1 w-1 h-1 rounded-full bg-gray-300 shrink-0" />
                      {c}
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Rate limiting */}
      <section>
        <h2 className="text-2xl font-semibold text-gray-900 mb-4">Rate Limiting</h2>
        <div className="rounded-xl border border-gray-100 bg-white p-5 space-y-4">
          <div className="flex flex-col sm:flex-row gap-4">
            {[
              { label: 'Default limit', value: '100 req / 60s', sub: 'per userId', color: 'bg-blue-50 border-blue-200' },
              { label: 'Burst allowance', value: '10 req / 1s', sub: 'sliding window', color: 'bg-purple-50 border-purple-200' },
              { label: 'On breach', value: 'HTTP 429', sub: 'with Retry-After', color: 'bg-red-50 border-red-200' },
            ].map(({ label, value, sub, color }) => (
              <div key={label} className={`flex-1 rounded-xl border p-4 text-center ${color}`}>
                <p className="text-2xl font-bold text-gray-900">{value}</p>
                <p className="text-xs text-gray-500 mt-0.5">{sub}</p>
                <p className="text-xs font-semibold text-gray-600 mt-1">{label}</p>
              </div>
            ))}
          </div>
          <p className="text-sm text-gray-500">
            Rate limiting runs at the API Gateway using Redis sliding-window counters keyed by{' '}
            <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded font-mono">ratelimit:{'{'}userId{'}'}</code>.
            Unauthenticated requests are rate-limited by IP address.
          </p>
        </div>
      </section>
    </div>
  )
}
