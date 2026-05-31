# AegisPay — Architecture Diagrams

All diagrams live in a single FigJam board:
**https://www.figma.com/board/Z0MumgtSHuAhDW4syBTO82**

---

## Diagrams in this board

| Diagram | Description |
|---|---|
| System Architecture | 8 microservices, API Gateway, Kafka, Postgres, MongoDB, Redis, Keycloak, 3 client platforms |
| iOS App Architecture | SwiftUI MVVM — Views → ViewModels → Services → ApiClient, AppAuth PKCE, Keychain, SSL pinning |
| Android App Architecture | Jetpack Compose — Screens → ViewModels → UseCases → Repositories → Retrofit / AppAuth |
| Web App Architecture | Next.js App Router — Pages → Zustand + React Query → Axios (JWT interceptor) → Keycloak.js |

---

## System Architecture

Key flows:
- All 3 clients (Web, iOS, Android) call the **API Gateway (:8080)** over HTTPS with a JWT Bearer token
- Gateway validates tokens against **Keycloak (:8180)** and routes to the appropriate microservice
- **Transaction Service** publishes `TransactionInitiatedEvent` to Kafka; **Payment Orchestrator** consumes it and drives the saga
- **Risk Service** and **AI Service** consume Kafka events for async fraud scoring and error resolution
- All services expose Prometheus metrics scraped by **Grafana**

## iOS App Architecture

Stack: Swift 6, SwiftUI, SPM, AppAuth-iOS, KeychainAccess, Stripe iOS SDK

- `AuthStore` (`@MainActor ObservableObject`) owns the PKCE session; `refreshTokens()` preserves existing `aegispay_user_id` when Keycloak omits ID token on refresh
- `TokenStore` wraps Keychain (`whenUnlockedThisDeviceOnly`) — access token, refresh token, userId, userRole
- All network service classes delegate to a shared `ApiClient` (URLSession + JWT auto-injection)
- `CertificatePinningDelegate` enforces SSL pinning in non-dev environments
- `AegisMarkdownView` renders AI triage reports and fraud explanations using `AttributedString(markdown:)` (iOS 15+)
- `MainTabView`: customer tabs hidden for staff roles; ADMIN lands on Triage tab (7) via one-shot `.task`; sessions survive tab switching via `@StateObject` lifetime
- All config values (API URL, Keycloak issuer, Stripe key) injected via `AppConfig.swift` — never hardcoded

## Android App Architecture

Stack: Kotlin, Jetpack Compose, Hilt DI, Retrofit + Moshi, AppAuth-Android, EncryptedSharedPreferences, FCM, WorkManager

- `AegisNavHost`: role-based landing (ADMIN → Triage, BACK_OFFICE → BackOffice, CUSTOMER → Dashboard)
- `AuthRepository` (@Singleton): PKCE via AppAuth Chrome Custom Tab; `persistTokens()` decodes both ID token and access token for `aegispay_user_id`; fallback to stored value prevents claim deletion on refresh
- `TokenStore` wraps `EncryptedSharedPreferences` (AES256-GCM) — no plaintext tokens on device
- `@Singleton TriageSessionStore`: triage history survives `NavBackStackEntry` pops; `TriageViewModel` (`@HiltViewModel`) merges form state + session store via `combine()`
- `MarkdownText` composable: renders headings, bullets, bold, inline code from AI responses
- `StompWebSocketClient` (OkHttp): live transaction status; `AegisFcmService` (FCM): push notifications
- `PaymentSyncWorker` (WorkManager): offline payment queue retry on reconnect
- `BigDecimalAdapter` (Moshi): financial amounts deserialized as `BigDecimal`, never `Double`

## Web App Architecture

Stack: Next.js 14 (App Router), TypeScript, NextAuth.js, Zustand, native fetch (`base.ts`), Stomp.js, ReactMarkdown

- NextAuth.js (`lib/auth.ts`) with Keycloak PKCE provider; `session.updateAge: 0` prevents double-refresh race with single-use refresh tokens; custom `GET /api/auth/keycloak-signout` clears all chunked session cookies and ends the Keycloak session
- `middleware.ts` enforces role-based routing on every server-side render; clears chunked cookie variants (`.session-token.0`–`.4`) on `RefreshAccessTokenError`
- `useTriageStore` (Zustand): triage session history persists across sidebar navigation
- Role-based sidebar: customer nav hidden for `BACK_OFFICE` / `ADMIN` roles via `isStaffRole()`
- `ReactMarkdown` renders AI triage reports, fraud explanations, and incident reports with Tailwind prose styling
- Back-office pages: Users (`PagedResponse` with `page` field), Risk Cases (`GET /api/v1/risk/cases`), Ledger lookup (account by userId OR entries by transactionId — mutually exclusive inputs)
