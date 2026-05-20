# AegisPay — Comprehensive Prod-Grade Testing Guide

Complete guide for testing every feature of AegisPay across **Web**, **Android**, and **iOS** —
including environment setup on your specific hardware (Mac 8 GB for iOS, Windows 32 GB for Android).

---

## Table of Contents

1. [System Setup — Mac 8 GB (iOS)](#1-system-setup--mac-8-gb-ios)
2. [System Setup — Windows 32 GB (Android)](#2-system-setup--windows-32-gb-android)
3. [Backend Stack — Start Everything](#3-backend-stack--start-everything)
4. [Web Testing](#4-web-testing)
5. [Android Testing](#5-android-testing)
6. [iOS Testing](#6-ios-testing)
7. [Feature Test Matrix](#7-feature-test-matrix)
   - [Auth & Onboarding](#71-auth--onboarding)
   - [KYC / Profile](#72-kyc--profile)
   - [Dashboard](#73-dashboard)
   - [Send Money](#74-send-money)
   - [Wallet & Top-Up](#75-wallet--top-up)
   - [Transactions History](#76-transactions-history)
   - [Notifications](#77-notifications)
   - [Biometric Lock](#78-biometric-lock)
   - [Offline Queue](#79-offline-queue)
   - [AI Triage Agent (ADMIN only)](#710-ai-triage-agent-admin-only)
   - [Risk Engine Rules](#711-risk-engine-rules)
   - [KYC Rate Limiter](#712-kyc-rate-limiter)
   - [Back-Office (Web only)](#713-back-office-web-only)
8. [Observability — Verify in Grafana & Logs](#8-observability--verify-in-grafana--logs)
9. [Test Accounts & Seed Data](#9-test-accounts--seed-data)
10. [Known Prod-Blocking Gaps (Do Not Test as Working)](#10-known-prod-blocking-gaps-do-not-test-as-working)

---

## 1. System Setup — Mac 8 GB (iOS)

Your Mac has 8 GB RAM. Xcode + one simulator + backend = tight but doable with the right choices.

### 1.1 Install Xcode

1. Open **App Store** → search **Xcode** → Install (≈ 14 GB, takes 30–60 min).
2. After install, open Xcode once to accept the license and let it install additional components.
3. Install command-line tools:
   ```bash
   xcode-select --install
   ```
4. Verify:
   ```bash
   xcodebuild -version   # Xcode 15.x or 16.x
   swift --version       # Swift 5.9+
   ```

### 1.2 Download Only the Simulator You Need

Full simulator libraries for all iOS versions eat ~15 GB. Download only what you need:

1. Open **Xcode → Settings → Platforms**.
2. Click `+` → **iOS** → download only the **latest iOS release** (e.g. iOS 17.5).
3. Do **not** download watchOS/tvOS — saves 4–5 GB.

### 1.3 Choose the Right Simulator (critical for 8 GB)

Use a **non-Pro, non-Max** iPhone model — they use a smaller resolution and less GPU memory:

| Simulator | RAM use | Recommendation |
|-----------|---------|----------------|
| iPhone 15 Pro Max | ~2.4 GB | ❌ Too heavy |
| iPhone 15 Pro | ~2.1 GB | ❌ Marginal |
| **iPhone 15** | **~1.6 GB** | ✅ Use this |
| iPhone SE (3rd gen) | ~1.3 GB | ✅ Lightest option |

To add it: **Xcode → Window → Devices and Simulators → Simulators tab → `+`** → choose iPhone 15, iOS 17.x.

### 1.4 Memory Optimisation Before Running

Do this each time before launching the simulator:

```bash
# Free cached/inactive RAM before launch
sudo purge

# Quit memory-heavy background apps
osascript -e 'quit app "Google Chrome"'
osascript -e 'quit app "Slack"'
osascript -e 'quit app "Spotify"'
```

In **Activity Monitor** (CPU tab): confirm free memory is ≥ 3.5 GB before starting the simulator.

### 1.5 Build & Run the iOS App

```bash
cd /path/to/AegisPay/apps/ios

# Open in Xcode
open AegisPay.xcworkspace    # or .xcodeproj if no workspace

# --- OR build from CLI ---
xcodebuild \
  -workspace AegisPay.xcworkspace \
  -scheme AegisPay \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -configuration Debug \
  build
```

From Xcode: select **iPhone 15** in the device picker → press **▶ Run** (⌘R).

### 1.6 Point iOS App at Local Backend

The simulator and your Mac share the same network — use `localhost` directly.

In `apps/ios/AegisPay/Network/ApiClient.swift` (or your config file), ensure:
```swift
// For simulator — host machine is reachable via localhost
let baseURL = URL(string: "http://localhost:8080")!
```

> For a **physical iPhone** on the same Wi-Fi: replace `localhost` with your Mac's LAN IP
> (`ifconfig en0 | grep inet | awk '{print $2}'`).

### 1.7 Xcode Simulator Tips

| Task | How |
|------|-----|
| Reset all app data | Simulator → **Device → Erase All Content and Settings** |
| Simulate biometric | **Features → Face ID → Enrolled** then **Features → Face ID → Matching Face** |
| Simulate no network | **Network Link Conditioner** (developer.apple.com/download/more → download "Additional Tools for Xcode") → 100% loss profile |
| View console logs | **Xcode → Window → Devices and Simulators** → select device → open console |
| Take screenshot | ⌘S inside simulator window |

---

## 2. System Setup — Windows 32 GB (Android)

Windows 32 GB is comfortable for Android development. You'll run Android Studio with an x86_64 emulator.

### 2.1 Install Android Studio

1. Download from https://developer.android.com/studio — choose the Windows installer (.exe).
2. Run the installer with default options (includes SDK, AVD Manager, Build Tools).
3. On first launch: **Standard** setup → accept all licenses.
4. SDK components to install (SDK Manager → SDK Tools tab):
   - Android SDK Build-Tools 34
   - Android Emulator
   - Android SDK Platform-Tools
   - Intel x86 Emulator Accelerator (HAXM) — auto-installed on Windows
5. Verify HAXM is running (required for fast emulation):
   ```powershell
   sc query intelhaxm
   # Should show: STATE: 4 RUNNING
   ```
   If not running: Android Studio → SDK Manager → SDK Tools → Intel x86 Emulator Accelerator → Install.

### 2.2 Enable Hyper-V or HAXM

Android Emulator needs hardware acceleration. Choose one:

**Option A — HAXM (Intel CPUs, no Hyper-V):**
- Ensure Hyper-V is **disabled** in Windows Features
- HAXM installs as part of Android Studio setup

**Option B — Hyper-V + WHPX (recommended if Hyper-V already enabled):**
```powershell
# Enable Hyper-V (as Administrator)
dism /Online /Enable-Feature /All /FeatureName:HypervisorPlatform
# Reboot required
```
Android Emulator will auto-use Windows Hypervisor Platform (WHPX).

**Option C — AMD CPUs:**
- AMD CPUs use WHPX automatically — just enable Hyper-V above.

### 2.3 Create the Emulator (AVD)

1. Android Studio → **Device Manager** (right sidebar or **Tools → Device Manager**).
2. **Create Virtual Device** → Phone → **Pixel 7** (good balance, not too large).
3. System image: **API 34 (Android 14) — x86_64** → Download if needed.
4. AVD settings (click "Show Advanced Settings"):
   - RAM: **4096 MB** (you have 32 GB, this is fine)
   - VM heap: **512 MB**
   - Internal storage: **8 GB**
   - Graphics: **Hardware — GLES 2.0**
5. Name it `Pixel_7_API34` → Finish.

### 2.4 Build & Run the Android App

```powershell
# Open the Android project
# File → Open → navigate to AegisPay/apps/android
```

Or from command line (inside WSL2 or PowerShell with the SDK on PATH):
```bash
cd AegisPay/apps/android
./gradlew assembleDebug

# Install on running emulator
adb install app/build/outputs/apk/debug/app-debug.apk
```

From Android Studio: select `Pixel_7_API34` emulator → click **▶ Run** (Shift+F10).

### 2.5 Point Android App at Local Backend

The Android emulator uses `10.0.2.2` to reach the host machine's `localhost`.

In `apps/android/app/src/main/java/com/aegispay/android/di/NetworkModule.kt`, verify:
```kotlin
// For emulator — 10.0.2.2 routes to host localhost
private const val BASE_URL = "http://10.0.2.2:8080/"
```

> For a **physical Android device** on the same Wi-Fi: use your Windows machine's LAN IP
> (`ipconfig` → look for your Wi-Fi adapter's IPv4 address, e.g. `192.168.1.x`).

### 2.6 ADB Tips

```bash
# List connected devices/emulators
adb devices

# View live logcat filtered to AegisPay
adb logcat -s "AegisPay" "*:E"

# Simulate biometric auth
adb -e emu finger touch 1   # emulates fingerprint touch

# Clear app data (reset state)
adb shell pm clear com.aegispay.android

# Simulate airplane mode (offline testing)
adb shell cmd connectivity airplane-mode enable
adb shell cmd connectivity airplane-mode disable

# Install release APK
adb install -r app-release.apk
```

### 2.7 Android Studio Emulator Tips

| Task | How |
|------|-----|
| Emulator toolbar | Right side panel: rotate, volume, back, home |
| Simulate biometric | Extended Controls (⋯) → **Fingerprint** → tap "Touch the sensor" |
| Simulate network off | Extended Controls → **Cellular** → Network type: **None** |
| View app logs | **Logcat** tab → filter by package `com.aegispay.android` |
| Run on physical device | Enable USB debugging on phone → plug in → appears in Device Manager |

---

## 3. Backend Stack — Start Everything

Both the Android emulator (Windows) and iOS simulator (Mac) need the backend running.  
See [`docs/local-dev.md`](../local-dev.md) for the full guide. Quick-start summary:

```bash
# 1. Start infrastructure (Postgres, Redis, Kafka, Keycloak, ClickHouse, Grafana)
docker compose up -d
docker compose ps   # wait for all "healthy"

# 2. Source env
set -o allexport; source .env.local; set +o allexport

# 3. Start services (each in its own terminal)
mvn -pl services/user-service spring-boot:run         # :8081
mvn -pl services/transaction-service spring-boot:run  # :8082
mvn -pl services/ledger-service spring-boot:run       # :8083
mvn -pl services/payment-orchestrator spring-boot:run # :8084
mvn -pl services/risk-engine spring-boot:run          # :8085
mvn -pl services/notification-service spring-boot:run # :8086
mvn -pl services/reconciliation-service spring-boot:run # :8087
mvn -pl services/data-pipeline spring-boot:run        # :8089
mvn -pl services/ai-platform spring-boot:run          # :8091
mvn -pl services/api-gateway spring-boot:run          # :8080 — start last

# 4. Start web frontend
pnpm --filter @aegispay/web dev                       # :3000
```

**Health check all services at once:**
```bash
for port in 8080 8081 8082 8083 8084 8085 8086 8087 8089 8091; do
  status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$port/actuator/health)
  echo "Port $port → $status"
done
```

**On Windows for backend:** run inside WSL2 (see `local-dev.md` Windows section).  
**On Mac for iOS:** run backend locally on the Mac — simulator and backend share `localhost`.

---

## 4. Web Testing

### 4.1 Manual Browser Testing

Open **http://localhost:3000** in Chrome or Firefox.

Use Chrome DevTools throughout:
- **Network tab** — verify API calls, response codes, payload shapes
- **Console** — watch for JS errors
- **Application → Local Storage / Cookies** — inspect auth tokens
- **Responsive** mode (⌘⇧M) — test at 375px (iPhone SE) and 390px (iPhone 14) widths

### 4.2 Create Test Users

Create two users via the API (needed for sender + receiver in payment tests):

```bash
# User 1 — Sender
curl -s -X POST http://localhost:8080/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Alice","lastName":"Test","email":"alice@test.com","phone":"+919000000001","password":"Test@1234","currency":"INR"}' | jq .

# User 2 — Receiver
curl -s -X POST http://localhost:8080/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Bob","lastName":"Test","email":"bob@test.com","phone":"+919000000002","password":"Test@1234","currency":"INR"}' | jq .

# Save Bob's userId from the response — you'll need it as the payeeId in send-money tests
```

### 4.3 Web Feature Checklist

Work through these in order — each step feeds data into the next.

| # | Feature | Steps | Pass Criteria |
|---|---------|-------|---------------|
| W-01 | Login | Go to `/login`, enter alice@test.com / Test@1234 | Redirected to `/dashboard`, no console errors |
| W-02 | Dashboard load | Dashboard renders | Balance card shows, recent transactions list visible |
| W-03 | Send money | Click "Send" → enter Bob's userId → ₹500 → submit | Status card shows PROCESSING → COMPLETED within 5s |
| W-04 | Transaction detail | Click a transaction row | Detail modal shows amount, status, timestamp |
| W-05 | Wallet top-up | Click "Add Funds" → enter ₹1000 → Stripe test card `4242 4242 4242 4242`, exp `12/26`, CVC `123` | Balance increases by ₹1000 |
| W-06 | Notifications bell | After sending/receiving money | Badge count updates without page refresh |
| W-07 | KYC upload | Profile → Upload ID | Progress shown, result displayed (pass/fail) |
| W-08 | Back-office | Log in as BACK_OFFICE role user, visit `/backoffice` | Transaction table, risk flags visible |
| W-09 | Sign out | Click profile → Sign out | Redirected to login, token cleared from storage |
| W-10 | Session expiry | Wait for token to expire (or delete it from localStorage) | Automatic redirect to login |

---

## 5. Android Testing

### 5.1 Pre-Test Setup (do once per test session)

```bash
# Verify emulator is running
adb devices
# Expected: emulator-5554   device

# Clear previous app state for a clean run
adb shell pm clear com.aegispay.android

# Confirm network reachability from emulator
adb shell curl -s http://10.0.2.2:8080/actuator/health
# Expected: {"status":"UP",...}
```

### 5.2 Enable Biometrics in Emulator

1. Emulator → **Extended Controls (⋯)** → **Fingerprint**
2. In Android emulator **Settings → Security → Fingerprint** — enroll a fingerprint using the Extended Controls "Touch the sensor" button
3. Verify enrollment by returning to Extended Controls → Fingerprint → "Touch the sensor" again (it should say "Fingerprint accepted")

### 5.3 Android Feature Checklist

| # | Feature | Steps | Pass Criteria |
|---|---------|-------|---------------|
| A-01 | App launch | Cold start | Splash screen shows, fades to Login within 2 s |
| A-02 | OAuth login | Tap "Sign In" → Chrome Custom Tab opens Keycloak → enter credentials | Returns to app, dashboard loads |
| A-03 | Biometric gate | Background app (home), reopen | Lock overlay appears, "Unlock" button visible |
| A-04 | Biometric unlock | Tap "Unlock" → Extended Controls → Touch fingerprint | Overlay dismisses, dashboard visible |
| A-05 | Biometric lockout | Reject fingerprint 5× | Message "Too many attempts. Use your device passcode." shown |
| A-06 | Send money — happy path | Send ₹500 to Bob's UUID | STATUS step shows COMPLETED, green checkmark |
| A-07 | Send money — validation | Enter invalid UUID, blank amount, amount > 10,00,000 | Inline error messages, Next button disabled |
| A-08 | Send money — offline queue | Enable airplane mode → send ₹100 | "Payment Queued" offline card shown; disable airplane → WorkManager retries |
| A-09 | Dashboard balance | After completing a send | Balance decreases by correct amount |
| A-10 | Wallet top-up | Tap "Add Funds" → ₹1000 → Stripe test card | Success dialog, balance updates |
| A-11 | Transaction list | Tap "History" | List renders with correct amounts (no scientific notation e.g. 1.0E+3) |
| A-12 | Transaction detail | Tap a transaction | Detail screen shows BigDecimal-formatted amount, status badge |
| A-13 | KYC upload — camera | Profile → Upload ID → Camera | Camera permission dialog appears; grant → camera opens |
| A-14 | KYC upload — gallery | Profile → Upload ID → Gallery | Gallery picker opens, image selected, upload starts |
| A-15 | KYC result | After upload | Quality score, validation flags, tamper result shown; "Confirm" button only enabled if valid |
| A-16 | Notifications | Receive a payment | Push notification appears in Android notification tray |
| A-17 | Sign out | Menu → Sign Out | Back to login screen, token cleared |
| A-18 | Error resolution | Trigger a failed transaction (send to a non-existent UUID) | AI error explanation card appears on STATUS screen |

### 5.4 BigDecimal Regression Checks

These are high-value spots where `Double` → `BigDecimal` migration could surface bugs:

```bash
# After any transaction, query directly:
adb shell sqlite3 /data/data/com.aegispay.android/databases/aegispay_offline.db \
  "SELECT amount FROM offline_payment_queue LIMIT 5;"
# Amount should be stored as plain decimal string e.g. "500" not "500.0" or "5E+2"
```

In the UI, verify:
- ₹10,00,000 displays as `₹10,00,000.00` not `₹1000000.0` or `₹1.0E6`
- ₹0.50 displays as `₹0.50` not `₹0.5000000000000001`

---

## 6. iOS Testing

### 6.1 Pre-Test Setup (do once per test session)

```bash
# Free up RAM
sudo purge

# Boot simulator from CLI
xcrun simctl boot "iPhone 15"
open -a Simulator

# Check simulator is running
xcrun simctl list devices | grep "iPhone 15"
# Should show: iPhone 15 (UUID) (Booted)

# Reset app state
xcrun simctl uninstall booted com.aegispay.AegisPay
```

### 6.2 Enable Face ID in Simulator

1. Simulator menu → **Features → Face ID → Enrolled** (toggle on)
2. To simulate a successful match: **Features → Face ID → Matching Face**
3. To simulate a failed scan: **Features → Face ID → Non-matching Face**
4. To simulate lockout: trigger "Non-matching Face" 5× rapidly

### 6.3 iOS Feature Checklist

| # | Feature | Steps | Pass Criteria |
|---|---------|-------|---------------|
| I-01 | App launch | Cold start | Splash → Login within 2 s |
| I-02 | OAuth login | Tap "Sign In" → Safari VC opens Keycloak → enter credentials | App receives token, dashboard loads |
| I-03 | Biometric gate | Background app (⌘H), reopen | Lock overlay appears |
| I-04 | Biometric unlock | Tap "Unlock" → Features → Matching Face | Overlay dismisses |
| I-05 | Biometric lockout | Features → Non-matching Face × 5 | Message "Too many attempts. Use your device passcode." |
| I-06 | Biometric not enrolled | Features → Face ID → Enrolled (off) → attempt unlock | Message "No biometrics enrolled. Enable them in device Settings." |
| I-07 | Send money — happy path | Send ₹500 to Bob | STATUS shows COMPLETED |
| I-08 | Send money — validation | Blank payee, negative amount, > 10,00,000 | Inline errors, disabled Next |
| I-09 | Error code extraction | Trigger FAILED transaction | AI error card shows the code AFTER the colon (e.g. "INSUFFICIENT_FUNDS" not "PAYMENT") |
| I-10 | Wallet top-up | Add ₹1000 → Stripe test card | Balance updates |
| I-11 | KYC — camera permission | Profile → Upload → Camera (first run) | OS permission sheet appears |
| I-12 | KYC — permission denied | Deny camera, try again | Alert with "Open Settings" button; tap → iOS Settings opens to AegisPay permissions |
| I-13 | KYC upload & result | Upload a photo | Validation result shows all 17 fields; canConfirm gate works |
| I-14 | Transaction amounts | View history | No floating point noise (e.g. ₹499.9999999 instead of ₹500.00) |
| I-15 | Notifications | Receive payment | In-app notification badge updates |
| I-16 | Deep link | Open `aegispay://transaction/<id>` | Transaction detail screen opens directly |
| I-17 | Sign out | Profile → Sign Out | Back to login |

### 6.4 iOS-Specific Verify Points

**Confirm `.last` fix for error codes (I-09):**
1. Send money to a UUID that has insufficient funds.
2. On the STATUS screen, the AI resolution card should show the specific error code from after the colon in the failure reason string.
3. Check Xcode console for: `resolveError called with errorCode: INSUFFICIENT_FUNDS` (not `PAYMENT`).

**Confirm camera permission alert (I-12):**
1. In Simulator → Settings → Privacy → Camera → AegisPay → set to **Never**.
2. Go to Profile → Upload ID → tap Camera.
3. Verify the custom alert appears (not a crash or silent failure).
4. Tap "Open Settings" — iOS Settings app should open.

---

## 7. Feature Test Matrix

Full cross-platform matrix. For each row, test the scenario on all platforms you want to verify.

### 7.1 Auth & Onboarding

| Scenario | Web | Android | iOS | Notes |
|----------|-----|---------|-----|-------|
| Register new user | ✓ | — | — | Registration is web-only; mobile uses existing accounts |
| Login (email/password) | ✓ | ✓ | ✓ | Keycloak OIDC flow |
| Token refresh | ✓ | ✓ | ✓ | Make an API call after token TTL; should transparently refresh |
| Invalid credentials | ✓ | ✓ | ✓ | Error message shown, no crash |
| Sign out | ✓ | ✓ | ✓ | Token revoked, redirect to login |
| Session expiry (no refresh) | ✓ | ✓ | ✓ | Delete token → auto logout on next API call |

**How to test token refresh:**
```bash
# Get a token and make it expire by waiting (Keycloak default access token = 5 min)
# OR: edit Keycloak realm (http://localhost:8180 admin/admin) →
# Realm Settings → Tokens → Access Token Lifespan → set to 1 minute for testing
```

### 7.2 KYC / Profile

| Scenario | Web | Android | iOS | Notes |
|----------|-----|---------|-----|-------|
| Upload ID photo (gallery) | ✓ | ✓ | ✓ | |
| Upload ID photo (camera) | — | ✓ | ✓ | Web uses file input |
| Camera permission — first request | — | ✓ | ✓ | Android: runtime dialog. iOS: OS sheet |
| Camera permission — denied recovery | — | ✓ | ✓ | Both show "Open Settings" alert |
| KYC result displayed | ✓ | ✓ | ✓ | 17-field validation result |
| Tampered document blocked | ✓ | ✓ | ✓ | `canConfirm = false` if `tampered = true` |
| Invalid document blocked | ✓ | ✓ | ✓ | `canConfirm = false` if `overallValid = false` |
| Name mismatch flagged | ✓ | ✓ | ✓ | `registeredName` sent with request |
| KYC status gating Send Money | ✓ | ✓ | ✓ | Unverified users should see a prompt |

**Test tamper detection:**
Upload a screenshot of an ID card (JPEG saved from a web page). The AI's tamper detection should flag it as digitally manipulated. Verify the "Confirm" button is disabled.

### 7.3 Dashboard

| Scenario | Web | Android | iOS | Notes |
|----------|-----|---------|-----|-------|
| Available balance correct | ✓ | ✓ | ✓ | Cross-check with ledger service |
| Reserved balance shown | ✓ | ✓ | ✓ | Should show during in-flight transactions |
| Recent transactions list | ✓ | ✓ | ✓ | Last 5 transactions |
| Balance after send | ✓ | ✓ | ✓ | Decreases by send amount |
| Balance after receive | ✓ | ✓ | ✓ | Increases by received amount |
| Balance after top-up | ✓ | ✓ | ✓ | Increases |

**Verify against the ledger directly:**
```bash
TOKEN=$(curl -s -X POST "http://localhost:8180/realms/aegispay/protocol/openid-connect/token" \
  -d "client_id=aegispay-app&grant_type=password&username=alice@test.com&password=Test@1234" \
  | jq -r '.access_token')

curl -s http://localhost:8080/api/v1/accounts/me \
  -H "Authorization: Bearer $TOKEN" | jq '{available: .availableBalance, reserved: .reservedBalance}'
```

Cross-check this number against what the app displays.

### 7.4 Send Money

| Scenario | Web | Android | iOS | Notes |
|----------|-----|---------|-----|-------|
| Happy path — COMPLETED | ✓ | ✓ | ✓ | |
| Payee UUID validation | ✓ | ✓ | ✓ | Must be valid UUID format |
| Amount validation — zero | ✓ | ✓ | ✓ | Error shown |
| Amount validation — above limit | ✓ | ✓ | ✓ | Max ₹10,00,000 |
| Amount validation — decimal | ✓ | ✓ | ✓ | ₹500.50 should work |
| Same-currency transfer | ✓ | ✓ | ✓ | INR → INR |
| FAILED transaction | ✓ | ✓ | ✓ | Send to self; some systems reject this |
| AI error resolution | ✓ | ✓ | ✓ | Failure card with explanation appears |
| Error code — correct extraction | ✓ | ✓ | ✓ | Code is part AFTER colon in `SERVICE:CODE` |
| Idempotency — double tap | ✓ | ✓ | ✓ | Submit button disabled during submission; re-submit with same key doesn't double-charge |
| Offline queue | — | ✓ | — | Android-only for now |
| Live status via WebSocket | ✓ | ✓ | ✓ | Status updates without refresh |
| Polling fallback | ✓ | ✓ | ✓ | Status still updates if WS disconnects |

**Test idempotency:**
```bash
# Call createTransaction twice with the same idempotency key
IKEY=$(uuidgen)
for i in 1 2; do
  curl -s -X POST http://localhost:8080/api/v1/transactions \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Idempotency-Key: $IKEY" \
    -d '{"payeeId":"<bob-id>","amount":100,"currency":"INR"}' | jq .transactionId
done
# Both responses should return the SAME transactionId
```

**Test offline queue (Android):**
1. Enable airplane mode: **Extended Controls → Cellular → Network type: None**
2. Send ₹50 — STATUS screen should show the "Payment Queued" card (WifiOff icon).
3. Re-enable network: **Network type: LTE**
4. Wait ~30 seconds for WorkManager retry — send should succeed.
5. Check transaction appears in history.

### 7.5 Wallet & Top-Up

| Scenario | Web | Android | iOS | Notes |
|----------|-----|---------|-----|-------|
| Top-up ₹1000 (Stripe test) | ✓ | ✓ | ✓ | Card: `4242 4242 4242 4242` |
| Top-up — declined card | ✓ | ✓ | ✓ | Card: `4000 0000 0000 0002` — error shown |
| Top-up — 3DS required | ✓ | ✓ | ✓ | Card: `4000 0025 0000 3155` — 3DS modal appears |
| Balance reflects top-up | ✓ | ✓ | ✓ | Dashboard balance increases |
| Amount validation — zero | ✓ | ✓ | ✓ | |
| Amount validation — minimum | ✓ | ✓ | ✓ | ₹1 minimum |
| Reserved balance during processing | ✓ | ✓ | ✓ | Brief window during Stripe processing |

**Stripe test cards reference:**
```
Success:         4242 4242 4242 4242
Declined:        4000 0000 0000 0002
Insufficient:    4000 0000 0000 9995
3D Secure:       4000 0025 0000 3155
Exp: any future date (e.g. 12/26), CVC: any 3 digits
```

### 7.6 Transactions History

| Scenario | Web | Android | iOS | Notes |
|----------|-----|---------|-----|-------|
| List renders | ✓ | ✓ | ✓ | |
| Amount formatting | ✓ | ✓ | ✓ | No scientific notation, correct decimals |
| Status badges | ✓ | ✓ | ✓ | COMPLETED=green, FAILED=red, PROCESSING=amber |
| Tap to detail | ✓ | ✓ | ✓ | Full detail screen/modal |
| Detail shows all fields | ✓ | ✓ | ✓ | ID, amount, currency, status, note, timestamp |
| Pagination / load more | ✓ | ✓ | ✓ | Scroll to bottom — more items load |
| Filter by status | ✓ | — | — | Web only for now |

**Amount formatting regression test:**
Send amounts with specific edge cases and verify display:
- ₹500.00 → should display as `₹500.00`
- ₹1,234.56 → `₹1,234.56`
- ₹10,00,000.00 → `₹10,00,000.00`
- ₹0.01 → `₹0.01`

### 7.7 Notifications

| Scenario | Web | Android | iOS | Notes |
|----------|-----|---------|-----|-------|
| In-app badge on receive | ✓ | ✓ | ✓ | Real-time via WebSocket |
| Push notification — receive | — | ✓ | ✓ | FCM (Android) / APNs (iOS simulator limited) |
| Push notification — failure | — | ✓ | ✓ | Your own transaction failed |
| Notification permission prompt | — | ✓ | ✓ | Android 13+: runtime prompt. iOS: explicit request |
| Mark as read | ✓ | ✓ | ✓ | Badge count decreases |

**FCM testing note:**  
For local testing, FCM push notifications only work on physical devices (not simulators/emulators) unless you configure a local emulator using the Firebase Emulator Suite. In-app WebSocket notifications work on both.

**iOS APNs in simulator:**  
Since Xcode 11.4+, simulators support push notifications via `simctl`:
```bash
# Simulate a push notification
xcrun simctl push booted com.aegispay.AegisPay notification.json
```
Where `notification.json` contains:
```json
{
  "aps": {
    "alert": { "title": "Payment received", "body": "Bob sent you ₹500" },
    "badge": 1,
    "sound": "default"
  }
}
```

### 7.8 Biometric Lock

| Scenario | Web | Android | iOS | Notes |
|----------|-----|---------|-----|-------|
| Lock on background | — | ✓ | ✓ | Lock overlay appears on app resume |
| Successful unlock | — | ✓ | ✓ | Overlay dismisses |
| User-cancelled (tap Cancel) | — | ✓ | ✓ | Lock stays visible, no error message |
| Lockout message | — | ✓ | ✓ | "Too many attempts. Use device passcode." |
| Not-enrolled message | — | ✓ | ✓ | "No biometrics enrolled. Enable in Settings." |
| Generic failure message | — | ✓ | ✓ | Shows specific error from OS |
| Sign out from lock screen | — | ✓ | ✓ | "Sign out instead" returns to login |
| Lock when not authenticated | — | ✓ | ✓ | Biometric lock should NOT appear if user not logged in |

**How to trigger each biometric state:**

| State | Android (Extended Controls) | iOS (Simulator menu) |
|-------|-----------------------------|----------------------|
| Success | Extended Controls → Fingerprint → Touch the sensor | Features → Face ID → Matching Face |
| Cancel | Close the biometric dialog | Features → Face ID → (don't interact — timeout; or press Cancel) |
| Non-matching | n/a (use different finger ID) | Features → Face ID → Non-matching Face |
| Lockout | Non-matching face 5× | Non-matching Face 5× |
| Not enrolled | Settings → Security → remove fingerprint enrollment | Features → Face ID → Enrolled (off) |

### 7.9 Offline Queue

| Scenario | Android |
|----------|---------|
| Queue on no-network | ✓ — "Payment Queued" card shows |
| Queue persists app restart | ✓ — Room database persists |
| Auto-retry on network restore | ✓ — WorkManager fires |
| Success after retry | ✓ — Transaction appears in history |
| Queue shows correct amount | ✓ — BigDecimal stored as plain string in Room |
| Multiple queued payments | ✓ — Each retried independently |

**Verify queue contents directly:**
```bash
adb shell run-as com.aegispay.android sqlite3 databases/aegispay_offline.db \
  "SELECT payee_id, amount, currency, created_at FROM offline_payment_queue;"
```
The `amount` column should contain decimal strings like `"500"` or `"123.45"`, not `"500.0"` or `"5.0E2"`.

### 7.10 AI Triage Agent (ADMIN only)

The Triage screen is gated to users with the `ADMIN` Keycloak role. On **Web** and **Android** it is accessible from the Back-Office and from failed transaction detail screens. On **iOS** it appears as a dedicated tab and as a sheet from the transaction detail view.

**Create an ADMIN user first** (see §7.10 back-office setup — assign the `ADMIN` realm role instead of `BACK_OFFICE`).

#### Web

| # | Scenario | Steps | Pass Criteria |
|---|----------|-------|---------------|
| T-W-01 | Triage tab visible | Log in as ADMIN, navigate to `/back-office` | "AI Triage" tab visible in back-office nav |
| T-W-02 | Triage tab hidden for non-ADMIN | Log in as BACK_OFFICE user | Triage tab absent from nav |
| T-W-03 | Open triage from failed tx | Back-office → failed transaction row → "Triage Incident" | Triage page opens with tx ID and service pre-filled |
| T-W-04 | Send triage message | Enter a description, click Send | AI response appears within 45 s |
| T-W-05 | Session history | After getting a response, send another message | Prior turn visible; conversation continues in context |
| T-W-06 | Tool-use evidence | Ask "check Kafka lag for this transaction" | Response references real Kafka tool output or circuit-breaker fallback message |
| T-W-07 | Fallback on AI outage | Stop ai-platform service → send a triage message | Graceful error card shown, no 500 crash on the UI |

#### Android

| # | Scenario | Steps | Pass Criteria |
|---|----------|-------|---------------|
| T-A-01 | Triage route accessible | Log in as ADMIN, navigate to back-office → Triage | Triage screen opens (route: `triage?txId=&service=`) |
| T-A-02 | Route absent for non-ADMIN | Log in as regular USER | `Route.TRIAGE` composable not registered; deep link navigates up |
| T-A-03 | Pre-fill from transaction detail | Failed transaction → "Triage Incident" button | Triage opens with `txId` and `service=payment-orchestrator` pre-filled |
| T-A-04 | Pre-fill banner visible | Open triage with pre-filled values | Amber pre-fill banner shows transaction ID |
| T-A-05 | Submit query | Type a question, tap Send | Response card renders, loading indicator shown during fetch |
| T-A-06 | Back navigation | Press back from triage | Returns to transaction detail or back-office |

#### iOS

| # | Scenario | Steps | Pass Criteria |
|---|----------|-------|---------------|
| T-I-01 | Triage tab visible | Log in as ADMIN | "Triage" tab (stethoscope icon) appears in tab bar |
| T-I-02 | Triage tab hidden | Log in as regular USER | Triage tab absent from tab bar |
| T-I-03 | Open from transaction detail | Failed tx → "Triage Incident" button (stethoscope) | Sheet opens with tx ID pre-filled |
| T-I-04 | Pre-fill banner | Open triage with pre-fill | Amber banner shows "Pre-filled: <tx-id>" |
| T-I-05 | Submit query | Type a question, tap Send | Typing indicator appears, response appended to session |
| T-I-06 | Expandable session card | Tap a session card header | Detail expands with dark terminal-style content; tap again to collapse |
| T-I-07 | Clear sessions | Sessions exist → trash icon → confirm | All session cards removed |
| T-I-08 | Trash hidden when empty | No sessions | Trash icon absent from navigation bar |

**Test the pre-fill flow end-to-end:**
1. Send money to a non-existent UUID to trigger a FAILED transaction.
2. Open the failed transaction detail.
3. Verify the "Triage Incident" button appears (ADMIN only).
4. Tap it — confirm `transactionId` and `service=payment-orchestrator` are pre-filled in the triage input.
5. Submit "What caused this failure?" and wait for the AI response.

---

### 7.11 Risk Engine Rules

Tests for the enhanced risk rules: self-transfer detection, new-account flag, and repeated-failure escalation.

| # | Scenario | How to Trigger | Expected Risk Signal |
|---|----------|----------------|----------------------|
| R-01 | Self-transfer blocked | Send money where payeeId == your own userId | Rule `SELF_TRANSFER` flagged; HIGH risk; transaction likely FAILED |
| R-02 | New-account large transfer | Use a fresh account (< 24 h old), send ≥ ₹10,000 | Rule `NEW_ACCOUNT_LARGE_TRANSFER` flagged; risk escalated |
| R-03 | Repeated-failure escalation | Trigger 3 FAILED transactions on the same account within 1 hour | Rule `REPEATED_FAILURES` flagged; REVIEW or BLOCK decision |
| R-04 | Normal transaction — no flag | Send ₹500 between two established accounts | No high-risk rules fired; risk score LOW; transaction COMPLETED |
| R-05 | Risk case visible in back-office | After R-01–R-03 | Back-office shows `RiskCase` record with correct rule names |

**Trigger self-transfer:**
```bash
# Get Alice's UUID
ALICE_ID=$(curl -s http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN" | jq -r '.id')

# Send to herself
curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"payeeId\":\"$ALICE_ID\",\"amount\":500,\"currency\":\"INR\"}" | jq .
# Expected: FAILED or BLOCKED status
```

**Trigger repeated-failure escalation:**
```bash
# Send to a non-existent UUID 3 times rapidly
for i in 1 2 3; do
  curl -s -X POST http://localhost:8080/api/v1/transactions \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"payeeId":"00000000-0000-0000-0000-000000000000","amount":100,"currency":"INR"}' | jq .status
done
# After the 3rd failure, check risk-engine logs for REPEATED_FAILURES rule
```

**Verify in back-office:**
After each test, navigate to the Back-Office → select the flagged transaction → verify the `RiskCase` record shows the expected rule name in the `triggeredRules` field.

---

### 7.12 KYC Rate Limiter

The KYC document endpoint is rate-limited to **5 attempts per user per 10-minute window** to prevent abuse of the AI vision pipeline.

| # | Scenario | Steps | Pass Criteria |
|---|----------|-------|---------------|
| KR-01 | First 5 uploads accepted | Upload a document 5 times as the same user | All return 200 with validation results |
| KR-02 | 6th upload rejected | Immediately upload a 6th time | HTTP `429 Too Many Requests` returned |
| KR-03 | 429 body | Inspect the 429 response | Body contains `error: "KYC_RATE_LIMIT_EXCEEDED"` and `retryAfterSeconds` field |
| KR-04 | Rate limit resets | Wait 10 minutes after 5th upload | 6th upload succeeds with 200 |
| KR-05 | Rate limit is per-user | Upload 5 times as Alice, then upload as Bob | Bob's request succeeds (separate counter) |
| KR-06 | UI shows friendly message | Web/Android/iOS — 429 received | App shows "You've reached the document upload limit. Try again in X minutes." (not a raw error) |

**Test rate limiter via API:**
```bash
for i in {1..6}; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST http://localhost:8080/api/v1/ai/kyc/analyze \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"base64ImageData":"<base64>","mimeType":"image/jpeg","documentType":"PASSPORT","registeredName":"Alice Test"}')
  echo "Attempt $i: HTTP $STATUS"
done
# Expected: 200 200 200 200 200 429
```

---

### 7.13 Back-Office (Web only)

The back-office is only available to users with the `BACK_OFFICE` or `ADMIN` Keycloak role.

**Create a back-office user:**
```bash
# Via Keycloak admin API
ADMIN_TOKEN=$(curl -s -X POST \
  "http://localhost:8180/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli&grant_type=password&username=admin&password=admin" \
  | jq -r '.access_token')

# Create user
curl -s -X POST "http://localhost:8180/admin/realms/aegispay/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "backoffice@test.com",
    "email": "backoffice@test.com",
    "enabled": true,
    "credentials": [{"type":"password","value":"Admin@1234","temporary":false}]
  }'

# Get the new user's ID and assign BACK_OFFICE role
# (use Keycloak Admin Console at http://localhost:8180 for the role assignment GUI)
```

Or use the Keycloak Admin Console at **http://localhost:8180** (admin/admin):
1. Realm: `aegispay` → Users → Add user
2. After creating: User → Role Mapping → assign `BACK_OFFICE` realm role

| Scenario | Steps | Pass Criteria |
|----------|-------|---------------|
| Access back-office | Login as BACK_OFFICE user, navigate to `/backoffice` | Dashboard loads, not 403 |
| Transaction table | View list | All transactions visible with amounts, status, user IDs |
| Risk flags | View transactions with FAILED status | Risk score and triggered rules visible |
| Regular user blocked | Login as alice@test.com, navigate to `/backoffice` | 403 Forbidden page or redirect |

---

## 8. Observability — Verify in Grafana & Logs

After running test scenarios, verify the data made it through the pipeline.

### 8.1 Grafana Dashboards

Open **http://localhost:3100** (admin/admin) and check each dashboard:

**AegisPay — Payment Operations:**
- Transaction count increased
- Volume chart shows test amounts
- Failure rate is 0% (unless you tested failures)
- If you tested failures: failure code breakdown shows the correct code

**AegisPay — Fraud Intelligence:**
- Risk assessments created for each transaction
- Risk scores visible (should be LOW for normal test payments)
- No false-positive HIGH risk flags

**AegisPay — SLA & Latency:**
- P95 saga latency < 5 seconds for all test transactions
- No reconciliation breaks

### 8.2 Verify ClickHouse Directly

```bash
docker exec -it aegispay-clickhouse clickhouse-client

-- Count transactions ingested
SELECT count(), status FROM aegispay_analytics.transaction_facts GROUP BY status;

-- Check amounts are stored correctly (no float weirdness)
SELECT transaction_id, amount, currency FROM aegispay_analytics.transaction_facts
ORDER BY event_time DESC LIMIT 5;

-- Check risk assessments
SELECT transaction_id, risk_score, decision FROM aegispay_analytics.risk_assessments
ORDER BY assessed_at DESC LIMIT 5;
```

### 8.3 Service Logs — What to Watch

```bash
# Watch payment orchestrator saga flow
mvn -pl services/payment-orchestrator spring-boot:run 2>&1 | grep -E "SAGA|STEP|ERROR"

# Watch risk engine decisions
mvn -pl services/risk-engine spring-boot:run 2>&1 | grep -E "RISK|DECISION|SCORE"

# Watch notification delivery
mvn -pl services/notification-service spring-boot:run 2>&1 | grep -E "SENT|FAILED|EMAIL|SMS"
```

### 8.4 Kafka Topic Verification

Open **http://localhost:8090** (Kafka UI):

After each test transaction, verify these topics have new messages:
| Topic | Producer | When |
|-------|----------|------|
| `transaction.created` | Transaction Service | On `POST /transactions` |
| `transaction.completed` | Payment Orchestrator | On SAGA completion |
| `transaction.failed` | Payment Orchestrator | On SAGA failure |
| `risk.assessment.completed` | Risk Engine | After every transaction |
| `notification.send` | Payment Orchestrator | On terminal status |

---

## 9. Test Accounts & Seed Data

### Standard Test Users

| Name | Email | Password | Role |
|------|-------|----------|------|
| Alice (sender) | alice@test.com | Test@1234 | USER |
| Bob (receiver) | bob@test.com | Test@1234 | USER |
| Back-office | backoffice@test.com | Admin@1234 | BACK_OFFICE |
| Admin | admin@test.com | Admin@1234 | ADMIN |

### Stripe Test Cards

| Scenario | Card Number | Notes |
|----------|------------|-------|
| Success | `4242 4242 4242 4242` | Instant success |
| Declined | `4000 0000 0000 0002` | Generic decline |
| Insufficient funds | `4000 0000 0000 9995` | Insufficient funds |
| 3D Secure (passes) | `4000 0025 0000 3155` | 3DS modal, auto-passes |
| 3D Secure (fails) | `4000 0000 0000 3220` | 3DS modal, fails |

All test cards: Expiry = any future date, CVC = any 3 digits, Zip = any 5 digits.

### Reset All Test Data

```bash
# Nuclear reset: wipe everything and restart fresh
docker compose down -v
docker compose up -d
# Wait for healthy, then start services
# Re-run register commands from Section 4.2 to re-create test users
```

---

## 10. Known Prod-Blocking Gaps (Do Not Test as Working)

The features below are **code-complete and hardened** — circuit breakers, retries, server-side validation, and rate limiting are all in place. What they lack is **real-world data and external vendor integration** that no amount of code can substitute. Do not include the items in the "Do not test" column in production acceptance testing. See `remaining.md § What's Blocking Production` for full detail.

### AI Transaction Risk

| What works ✅ | What to not test as prod-ready ❌ |
|--------------|----------------------------------|
| 10 deterministic rules fire correctly (self-transfer, velocity, new-account large transfer, repeated-failure, blacklist, geo, time-of-day, new-device, Stripe Radar EFW, KYC gate) | Whether thresholds catch real fraud — they were set arbitrarily, not tuned from real transaction history |
| MANUAL_REVIEW routing for uncertain scores (40–60 band) | Accuracy of fraud/not-fraud decisions — no labelled dataset, no ML model |
| Risk engine 503 fallback — transactions still flow even if risk service is down | Long-term fraud pattern detection — 1-hour velocity window misses slow-burn ATO attacks |
| Risk cases visible in back-office with triggered rule names | Stripe Radar custom rules — EFW webhook is wired but no merchant-specific rules configured in Stripe Dashboard |

**Safe to test:** Rule triggering (send to self → SELF_TRANSFER fires, new account + large amount → NEW_ACCOUNT_LARGE_TRANSFER fires), MANUAL_REVIEW routing, back-office risk case display, fallback when risk engine is stopped.

---

### RAG Finance Knowledge Base

| What works ✅ | What to not test as prod-ready ❌ |
|--------------|----------------------------------|
| `/api/v1/ai/explain` endpoint returns a response | Accuracy of financial explanations — only ~10 fraud cases, ~40 error codes, ~15 incidents, 25 finance terms in corpus |
| Circuit breaker + retry — 503 from Anthropic returns "service temporarily unavailable", never crashes the app | Retrieval relevance — pgvector returns poor neighbours below ~500 docs/topic; queries about SWIFT gpi, SEPA recall, RTGS may return irrelevant results |
| 30s TimeLimiter — no infinite hang | Domain terminology accuracy — `text-embedding-3-small` does not understand finance-specific terms |
| Finance terminology corpus (25 RBI/NPCI/ISO terms) seeded | Triage explanations using real incident patterns — synthetic KB only |

**Safe to test:** Error card appearing when AI is unavailable, retry behaviour, response rendering, the 25 seeded finance terms.

---

### KYC Document Validation

| What works ✅ | What to not test as prod-ready ❌ |
|--------------|----------------------------------|
| 17-field AI validation result returned (format, MRZ pattern, expiry, age, security features, name match, tamper) | Liveness — a printed photo of a college ID on A4 paper will likely pass |
| Server-side hard blocks: low quality → rejected, tampered → rejected, invalid → rejected, age < 18 → rejected | Government DB cross-check — Aadhaar not verified against UIDAI, PAN not verified against NSDL |
| Rate limit: 5 attempts/user/24h enforced at API Gateway (6th attempt → HTTP 429) | NFC chip — passports and Aadhaar NFC chips not read |
| KYC state machine transitions (PENDING → DOCUMENT_SUBMITTED → AI_PROCESSING → APPROVED/REJECTED/MANUAL_REVIEW) | RBI compliance — AI vision alone is not an accepted full-KYC method for accounts > ₹50,000 |
| Send-money blocked at backend if `kycStatus != APPROVED` | Real fake ID rejection — a synthetically generated document may pass visual inspection |

**Safe to test:** Upload flow mechanics, quality/tamper field gate (Confirm button disabled), rate limit 429 after 5 uploads, KYC status badge in profile, send-money block when not verified.  
**Never use for:** Real user identity verification.

---

### AI Triage Agent

| What works ✅ | What to not test as prod-ready ❌ |
|--------------|----------------------------------|
| ADMIN-only screens on Web, Android, iOS — role gate enforced | Accuracy of root-cause diagnosis — based on 15 synthetic incidents, not real post-mortems |
| Pre-fill from failed transaction detail (txId + service pre-populated) | Live system awareness — agent cannot see current Prometheus metrics, ClickHouse data, or Kafka DLQ depth |
| Session history (expandable dark terminal cards) | Runbook execution — agent is advisory only, cannot kubectl, write to DB, or reset Kafka offsets |
| Circuit breaker fallback: if AI platform is down, returns structured manual steps (kubectl/Prometheus/Grafana) | Auto-trigger — agent must be opened manually; not wired to Grafana alert webhooks yet |
| 45s TimeLimiter — no infinite hang on tool calls | PagerDuty/Opsgenie integration — no bidirectional alert lifecycle management |

**Safe to test:** Role gating (triage tab absent for non-ADMIN), pre-fill banner content, session history display, degraded fallback message when ai-platform is stopped, expandable card animation.

---

*Last updated: May 2026 — AegisPay v1.0 — added §7.10 AI Triage (Web/Android/iOS), §7.11 Risk Engine Rules, §7.12 KYC Rate Limiter; §10 rewritten to reflect hardened state*
