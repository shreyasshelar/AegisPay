# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.x (current) | ✅ Yes |
| < 1.0 (pre-release) | ❌ No |

## Reporting a Vulnerability

We take security vulnerabilities in AegisPay seriously. **Please do not open a public GitHub issue for security reports.**

### How to Report

Send an email to **security@aegispay.shreyasshelar.uk** with the following details:

1. **Description** — A clear description of the vulnerability and its potential impact.
2. **Reproduction steps** — Step-by-step instructions to reproduce the issue.
3. **Affected component(s)** — Which service, endpoint, or client (iOS/Android/Web) is affected.
4. **Proof of concept** — If possible, include a minimal PoC (no active exploitation of production systems).
5. **Your contact info** — So we can follow up and credit you appropriately.

### What to Expect

| Timeline | Action |
|----------|--------|
| **24 hours** | Acknowledgement of your report |
| **72 hours** | Initial severity assessment and triage |
| **7 days** | Preliminary fix or workaround communicated to reporter |
| **30 days** | Patch released (critical/high severity) |
| **90 days** | Public disclosure (coordinated with reporter) |

For critical vulnerabilities affecting financial data or authentication, we target a 7-day patch cycle.

### Scope

The following are **in scope**:

- All microservices under `/services/` (payment-orchestrator, transaction-service, user-service, risk-engine, ledger-service, notification-service, reconciliation-service, ai-platform, data-pipeline, api-gateway)
- iOS app (`apps/ios`)
- Android app (`apps/android`)
- Web app (`apps/web`)
- Authentication flows (Keycloak / OAuth 2.0 / PKCE)
- API endpoints and JWT validation
- Stripe webhook signature verification

The following are **out of scope**:

- Denial-of-service attacks requiring significant bandwidth (infrastructure-level)
- Social engineering or phishing of AegisPay staff
- Physical access attacks
- Third-party services (Stripe, Keycloak, Kafka hosted externally)
- Issues in dependencies — please report those directly to the dependency maintainer; we will apply patches promptly via Dependabot

### Safe Harbour

We support responsible disclosure and will not pursue legal action against researchers who:

- Report findings privately to our security team before public disclosure
- Do not access, modify, or exfiltrate real user data
- Do not disrupt production services
- Act in good faith to avoid privacy violations

### Recognition

Security researchers who responsibly disclose valid vulnerabilities will be acknowledged in our release notes and, for critical findings, in a public Hall of Fame.

---

## Security Architecture Overview

For reviewers and auditors, key security controls include:

- **Authentication**: OAuth 2.0 + PKCE via Keycloak; all API requests validated with RS256 JWTs
- **Transport**: TLS 1.2+ enforced on all services; HSTS on web frontend; `cleartextTrafficPermitted=false` on Android
- **Input validation**: `@Valid` + `@Validated` Bean Validation on all REST controllers
- **Circuit breakers**: Resilience4j on all AI/external service calls
- **Secrets**: No secrets in source; all injected via environment variables / Kubernetes Secrets
- **SAST/SCA**: CodeQL + OWASP Dependency-Check + Trivy on every CI run (see `.github/workflows/security-scan.yml`)
- **Dependency updates**: Automated via Dependabot (see `.github/dependabot.yml`)
