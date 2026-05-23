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

- `AuthStore` (@MainActor ObservableObject) owns the OAuth session and token refresh lifecycle
- All network service classes (`UserService`, `TransactionService`, etc.) are `@MainActor` and delegate to a shared `ApiClient` (URLSession + JWT injection)
- `StompWebSocket` provides live transaction status updates
- `CertificatePinningDelegate` enforces SSL pinning in non-dev environments
- All secrets (API URL, Keycloak issuer, Stripe key) injected via `Info.plist` — never hardcoded

## Android App Architecture

Stack: Kotlin, Jetpack Compose, Hilt DI, Retrofit, AppAuth, EncryptedDataStore, FCM

- Clean architecture: UI → ViewModel → UseCase → Repository → Data sources
- AppAuth handles OAuth 2.0 PKCE flow; tokens stored in EncryptedDataStore
- OkHttp WebSocket client for live transaction updates (mirrors iOS StompWebSocket)
- FCM for push notifications on transaction state changes

## Web App Architecture

Stack: Next.js 14 (App Router), React, TypeScript, Zustand, React Query, Axios, Keycloak.js, Stomp.js

- `AuthContext` wraps Keycloak.js; Axios interceptor injects `Authorization: Bearer <token>` on every request
- Zustand for client-side state; React Query for server state with automatic cache invalidation
- Stomp.js WebSocket mirrors the mobile live-update flow
- Admin Panel (risk cases, KYC review) gated by `ROLE_ADMIN` claim in the JWT
