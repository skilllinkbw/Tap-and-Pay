# Tap & Pay — Braincade / AuthePay

Tap & Pay is a contactless (NFC + QR) merchant payment application for Botswana and the wider
African market, part of the **Braincade Holdings Pty Ltd / AuthePay** ecosystem.

- **Application ID:** `com.getauthepay.app`
- **Version:** `1.0.0` (versionCode `1`)
- **Primary currency:** BWP (Pula), designed multi-currency
- **Platforms:** Android (native, Kotlin + Jetpack Compose), min SDK 26 / target SDK 37

---

## 1. What is in this repository

| Path | Contents |
|---|---|
| `android/` | Canonical Android application (Kotlin, Jetpack Compose, Material 3) |
| `android/docs/` | Architecture, security, testing and certification documentation |
| `docs/` | Commercial/legal/bank documentation: privacy, terms, acceptable use, retention, security architecture, threat model, incident response, bank brief + due-diligence checklist, product readiness |
| `dist/` | Built release artifacts (APK/AAB) for review |

The Android app is the single canonical implementation. Early web/Capacitor
prototype files (`www/`, root `package.json`, copied `assets/public` web assets)
were removed in the final hardening pass: they were unused by the native app and
contained outdated marketing claims (e.g. "PCI Compliant", camera card scanning)
that the product deliberately does not make. They remain available in Git
history if ever needed for reference.

## 2. Component status

| Component | Status | Notes |
|---|---|---|
| Android app (splash, onboarding, login/OTP, dashboard, Accept Payment, QR scan, history, detail, refunds, settlements, devices, team, security alerts, settings, help, about) | **LIVE** | Fully implemented, 210 unit tests |
| Payments rail (sandbox) | **SANDBOX** | `SandboxPaymentProvider` simulates provider responses end-to-end via a real state machine; clearly labelled in-app |
| Payments rail (AuthePay production API) | **NOT YET CONNECTED** | `PaymentProvider` interface + `AuthePayProvider` boundary ready for real credentials/contract |
| Bank / mobile-money (Orange Money BW etc.) rails | **NOT YET CONNECTED** | Isolated `MobileMoneyProvider`/`BankProvider` adapter boundaries only |
| Backend API | **NOT YET CONNECTED** | App ships with per-build `API_BASE_URL` (`https://api.authepay.co.bw` for release, sandbox/staging hosts for other build types). No backend service is included in this repository |
| Certified SoftPOS / card acceptance | **NOT YET CONNECTED** | Requires partner SDK + certification. NFC/QR here is *payment initiation*, not card-emulation acquiring |

No screen fabricates a live transaction. Sandbox/demo transactions are labelled, and the app
never claims regulatory approval, bank partnership or PCI certification.

## 3. Architecture (summary)

```
UI (Compose screens)            ui/  — no business logic
    ↓
ViewModels / state              per-feature state holders
    ↓
ServiceLocator (DI)             di/  — single composition root
    ↓
Repositories                    data/ — sessions, merchants, transactions
    ↓
Payment orchestration           payment/ — PaymentProvider state machine
    ↓                            (CREATED→PENDING→PROCESSING→SUCCESS/FAILED/CANCELLED→REVERSED/REFUNDED)
Providers                       SandboxPaymentProvider | AuthePayProvider boundary
    ↓
HttpClient (https-only)         network/ — BuildConfig.API_BASE_URL per build type
```

Security layer: `security/` — Android Keystore keys, session handling, screenshot protection
(`FLAG_SECURE`), device integrity checks, PIN safety.

## 4. Build setup

Requirements: JDK 17 (Android Studio JBR), Android SDK 37, Gradle wrapper (provided).

```powershell
# from android/
.\gradlew.bat --no-daemon assembleDebug          # debug APK  → app\build\outputs\apk\debug\
.\gradlew.bat --no-daemon assembleRelease        # release APK → app\build\outputs\apk\release\
.\gradlew.bat --no-daemon bundleRelease          # release AAB → app\build\outputs\bundle\release\
.\gradlew.bat --no-daemon testDebugUnitTest      # unit tests
.\gradlew.bat --no-daemon lintDebug              # Android lint
.\gradlew.bat --no-daemon assembleDemo           # signed bank-review demo build
```

### Build types

| Type | App ID suffix | Payments | OTP test codes | Cleartext | Purpose |
|---|---|---|---|---|---|
| `debug` | `.debug` | sandbox | enabled | dev only | development |
| `staging` | `.staging` | sandbox | disabled | no | internal staging |
| `release` | *(none)* | production | disabled | no | Play / production |
| `demo` | `.demo` | sandbox | disabled | no | signed bank/partner demos |

Every build type carries `ENVIRONMENT_NAME` + `API_BASE_URL` in `BuildConfig`; the UI displays the
environment where relevant. Release never points at localhost.

### Signing (never commit secrets)

Release signing is read from `android/local.properties` (git-ignored):

```properties
RELEASE_STORE_FILE=release-keystore.jks
RELEASE_STORE_PASSWORD=********
RELEASE_KEY_ALIAS=authepay-release
RELEASE_KEY_PASSWORD=********
```

The keystore currently configured is a **development build-verification key**
(`CN=AuthePay Build Verification, O=Braincade Holdings Pty Ltd, C=BW`) — it proves the pipeline
and lets release builds install on devices for review. It is **not** a production distribution
key: before Play release, replace it with the merchant production keystore supplied via
`local.properties` or CI secrets (never committed). If `RELEASE_STORE_FILE` is absent,
`assembleRelease` fails loudly by design.


## 5. Testing

- **Unit tests:** `.\gradlew.bat testDebugUnitTest` — 210 tests covering payment state machine,
  idempotency (including concurrent double-tap single-flight), OTP/session handling,
  refund/reversal logic, receipt generation, HTTP client and security managers.
- **Lint:** `.\gradlew.bat lintDebug` — must report **0 errors**.
- **Smoke test on device:** enable USB debugging, then

```powershell
adb devices          # confirm authorised device
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.getauthepay.app.debug -c android.intent.category.LAUNCHER 1
adb logcat           # watch for crashes
```

## 6. Known limitations / honest boundaries

- Sandbox/demo transactions simulate the provider leg. They are never represented as live money
  movement.
- The production API hosts (`*.authepay.co.bw`) are reserved endpoints; the AuthePayProvider
  adapter is the single integration point once API credentials and a provider contract exist.
- True card *acceptance* (contactless terminal / SoftPOS) requires certified partner SDK and
  scheme certification — the architecture isolates this behind the provider layer so it can be
  added without app rewrites.
- Setswana localisation: UI strings are centralised for translation; Setswana is prepared-for,
  not shipped.

## 7. Security notes

- HTTPS-only enforced at the HTTP client (requires `https://`, release forbids cleartext).
- No secrets, API keys, provider credentials or service-role keys in the app or this repository.
- Keystore/session keys via Android Keystore; no plaintext PIN/password storage or logging.
- `FLAG_SECURE` on sensitive screens; debug and export surface minimised in the manifest.
- Never disclose your PIN, password, OTP or biometric credentials to anyone — AuthePay staff will
  never ask for them.

See `android/docs/PRIVACY_AND_SECURITY.md` and `android/docs/DATA_RETENTION.md`.
Commercial/legal documents (privacy policy, terms, acceptable use, retention) live in
[`docs/legal/`](docs/legal/); security architecture, threat model and incident response in
[`docs/security/`](docs/security/); bank-facing material in [`docs/bank/`](docs/bank/);
honest status in [`docs/PRODUCT_READINESS.md`](docs/PRODUCT_READINESS.md) and
[`FINAL_RELEASE_REPORT.md`](FINAL_RELEASE_REPORT.md).

## 8. Branding

The brand system is built from the genuine AuthePay monogram (byte-preserved from the original
launcher foreground, template artifacts removed):

- **Mark:** white AuthePay monogram, used consistently by launcher, adaptive icon and in-app
  `BrandLogo`
- **Surface:** brand navy `#0B1B2F` (launcher background, adaptive icon, OS launch splash,
  in-app splash) — replaces the template grey `#333433`
- **Action blue:** `#2B6CB0` for interactive elements
- **Assets:** all launcher/splash rasters regenerated at every density (mdpi → xxxhdpi) from the
  vector mark; store icon 512px; no template/stock assets remain in active UI

## 9. Company

**Braincade Holdings Pty Ltd** — Tap & Pay / AuthePay.
Legal, terms and in-app disclosures: see the in-app *Settings → About / Terms* screens.

