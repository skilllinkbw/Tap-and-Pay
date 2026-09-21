# Tap & Pay — Final Release Report

**Date:** 2026-09-21 · **Version:** 1.0.0 (versionCode 1) ·
**App ID:** `com.getauthepay.app` · **Owner:** Braincade Holdings Pty Ltd

---

## 1. Executive Summary

A full production-hardening and bank-readiness pass was executed on the
existing Tap & Pay Android codebase: repository audit, bug hunt, security
review, privacy/legal documentation, bank documentation, full test suite,
lint, and clean debug/release builds (APK + AAB). One genuine
payment-integrity defect (double-charge race) was found and fixed with
regression tests. No secrets, no false compliance claims, and no fake payment
fabrication exist in the product after this pass.

**Final status: READY FOR PARTNER INTEGRATION — not yet a live payment
product.** External certification, provider integration and legal review
remain pending and are clearly marked throughout.

## 2. What Was Audited

- Entire repository structure, Git state and history (branch `main`, remote
  `origin` = github.com/skilllinkbw/Tap-and-Pay, in sync at `de990dd` before
  this pass)
- Gradle configuration, build types (debug/staging/demo/release), signing
  pipeline, ProGuard/R8, manifest, network security config, backup rules
- All Kotlin sources: payment engine, state machine, idempotency store,
  processors, risk service, NFC providers, ledger, receipts, session/auth,
  secure storage, security manager, HTTP client, all Compose screens
- Full-text scan for: TODO/FIXME, mock/fake/placeholder/demo, hardcoded
  secrets, passwords/tokens/API keys, localhost/127.0.0.1, cleartext HTTP,
  insecure flags — results: only legitimate labelled test/sandbox components
  and documentation references found; **no secrets in the repository**
  (keystore, `local.properties`, `.env` all untracked and git-ignored)
- Dependencies (`android/app/build.gradle`) — no vulnerable/duplicate/abandoned
  packages identified; only "newer version available" lint notices (see §10)
- Branding assets at every density (launcher, adaptive icon, splash, in-app
  `BrandLogo`) — genuine AuthePay "A" monogram, white on navy `#0B1B2F`
- All 19 technical docs in `android/docs/`

## 3. Bugs Found

| # | Bug | Severity |
|---|---|---|
| B1 | `IdempotencyStore.begin()` returned `true` for in-flight keys — a double-tap during a slow network read could drive **two authorisations for one sale**. The engine also ignored `begin()`'s return value, and concurrent `processPayment()` collections shared (and reset) one state machine mid-payment. | **Critical (payment integrity)** |
| B2 | `IdempotencyStoreTest` asserted the buggy behaviour (`assertTrue` on the second in-flight `begin()`), codifying the defect as "expected". | High (test integrity) |
| B3 | Leftover Capacitor template instrumented test asserted package `com.getcapacitor.app` — could never pass against this app. | Medium |
| B4 | Legacy web/Capacitor prototype files (`www/*.html`, copied `assets/public` web assets, root `package.json`/`package-lock.json`) shipped/stored with false claims ("PCI Compliant", camera card scanning, `alert('NFC Ready')` button) — unused by the native app but present in the repo and (for `assets/public`) bundled into APKs on this machine. | High (brand/compliance) |

## 4. Bugs Fixed

- **B1:** `IdempotencyStore.begin()` is now atomic single-flight
  (`ConcurrentHashMap.putIfAbsent`); the engine honours the result and rejects
  duplicate in-flight keys with `DUPLICATE_TRANSACTION`; a **terminal mutex**
  (`paymentMutex.tryLock()`) serialises all payment attempts — concurrent
  collectors get a clear `PAYMENT_IN_PROGRESS` result instead of corrupting
  the shared state machine; mutex released in `finally` so a finished/failed
  payment never wedges the terminal.
- **B2:** test corrected to assert rejection; added a 16-thread atomicity test.
- **B3:** template test replaced with `SmokeInstrumentedTest` asserting the
  real application ID.
- **B4:** legacy prototype files removed from the repository and from the
  on-disk APK assets path (history retains them); README updated to explain.

## 5. Security Improvements

- Closed the concurrent double-charge path (B1) — the most serious finding.
- Removed misleading "PCI Compliant" marketing artifacts from the product
  surface (B4).
- `android/.kotlin/` added to `.gitignore`; JVM crash dumps (`hs_err_pid*.log`)
  removed from the working tree.
- Re-verified existing controls: HTTPS-only client, cleartext forbidden
  manifest-wide, backups disabled, single exported launcher activity, no deep
  links, no WebView, FLAG_SECURE, Keystore-backed AES-256-GCM, redaction-
  enforced logging, OTP lockout, device trust gating, fail-closed payment
  engine, release build cannot reach sandbox rail or test OTP
  (test-enforced).

## 6. Privacy Improvements

- New [docs/legal/PRIVACY_POLICY.md](docs/legal/PRIVACY_POLICY.md) drafted from
  the actual product behaviour under Botswana's Data Protection Act, 2018 —
  with lawful-basis mapping and retention periods explicitly marked pending
  legal/regulatory confirmation (nothing invented).
- [docs/legal/DATA_RETENTION_POLICY.md](docs/legal/DATA_RETENTION_POLICY.md)
  with **[CONFIRM]** markers instead of fabricated statutory periods.
- Confirmed data-minimising posture: no cardholder data, no analytics/ad SDKs,
  redacted logs, encrypted session storage.

## 7. UI/UX Improvements

- No redesign was needed: the Compose UI already implements a coherent navy/
  blue fintech design system with honest status surfaces, sandbox banners,
  environment labelling and error messages that state whether money moved.
  Verified during audit; unchanged to protect a tested product.

## 8. Commercialization Improvements

- New root [`docs/`](docs/) set: privacy policy, terms & conditions,
  acceptable-use policy, data retention, security architecture, threat model,
  incident response, bank partnership brief, bank due-diligence checklist,
  product readiness (WORKING / SANDBOX / PENDING PARTNER / PENDING
  CERTIFICATION / FUTURE).
- README updated: honest component status, docs map, test counts, removal of
  legacy prototypes documented.
- Licensing: **no client-only license system was added** (by design — a
  client-side-only key check would be trivially bypassed). Commercial gating
  is designed as backend-driven account feature flags, documented as FUTURE
  work in `docs/PRODUCT_READINESS.md`.

## 9. Bank-Readiness

- [docs/bank/BANK_PARTNERSHIP_BRIEF.md](docs/bank/BANK_PARTNERSHIP_BRIEF.md)
  and [docs/bank/BANK_DUE_DILIGENCE_CHECKLIST.md](docs/bank/BANK_DUE_DILIGENCE_CHECKLIST.md)
  created with factual statuses; existing `android/docs/PARTNER_BRIEF.md`,
  `BANK_SUBMISSION_READINESS.md`, `CERTIFICATION_READINESS.md` verified
  consistent.
- **External certification and partner approval: PENDING.** No certification,
  bank approval, PCI compliance or partnership is claimed anywhere.


## 10. Dependency Audit

- No vulnerable, duplicate, abandoned or unnecessary dependencies identified.
- Lint "newer version available" notices (deferred deliberately to avoid
  destabilising a verified build; scheduled for a controlled upgrade window):
  appcompat 1.7.1→1.8.0, compose-bom 2026.08.00→2026.09.00,
  navigation-compose 2.10.0→2.10.1, coroutines 1.10.2→1.11.0,
  Gradle 9.5.0→9.7.1.

## 11. Tests Run

| Command | Result |
|---|---|
| `gradlew --no-daemon testDebugUnitTest` | ✅ **210 tests, 0 failures, 0 errors, 0 skipped** (13 suites) — incl. 3 new concurrency regression tests |
| `gradlew --no-daemon lintDebug` | ✅ 0 errors (warnings: newer-version notices only) |
| `gradlew --no-daemon assembleDebug` | ✅ BUILD SUCCESSFUL |
| `gradlew --no-daemon assembleRelease` | ✅ BUILD SUCCESSFUL (R8, signed with dev build-verification key) |
| `gradlew --no-daemon bundleRelease` | ✅ BUILD SUCCESSFUL |
| Instrumentation tests | ⚠️ Not executed — requires an Android device/emulator (not available in this environment); smoke test corrected and ready |

Combined lint+build run: **BUILD SUCCESSFUL in 19m 8s**, exit code 0.

## 12. Build Results

| Artifact | Path | Size |
|---|---|---|
| Debug APK | `dist/TapAndPay-1.0.0-debug.apk` | 45.0 MB |
| Release APK | `dist/TapAndPay-1.0.0-release.apk` | 22.9 MB |
| Release AAB | `dist/TapAndPay-1.0.0-release.aab` | 15.3 MB |

Release config verified: `PRODUCTION_BUILD=true`, `SANDBOX_PAYMENTS=false`,
`TEST_OTP_ENABLED=false`, `ALLOW_CLEARTEXT=false`, production API host,
R8 + resource shrinking, signing via git-ignored `local.properties`
(development build-verification key — **production keystore still required**).

## 13. Logo/Branding Verification

- Launcher icon, adaptive icon, splash rasters (all densities), in-app
  `BrandLogo`: genuine AuthePay "A" monogram, white on navy `#0B1B2F`,
  vector-sourced, no template artifacts. App label "Tap & Pay". ✅

## 14. Remaining Risks / External Dependencies

1. Production payment rail, backend API, provider SDK — **pending partner
   integration**.
2. EMV/MPoC/SoftPOS certification — **pending external certification**.
3. Production signing keystore — deployment artifact, to be supplied via CI
   secrets (never committed).
4. Legal review of customer-facing documents — pending qualified counsel.
5. Server-side attestation, certificate pinning, remote revocation — planned at
   backend integration.
6. On-device instrumented/UI tests — require a device/emulator run.

## 15. Final Release Status

**READY FOR BANK DEMONSTRATION AND PARTNER INTEGRATION.**
Not "production live": money movement requires the pending external items
above. Everything completable locally has been completed, tested and verified.

