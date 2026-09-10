# AuthePay — Product Overview

**Application:** AuthePay Merchant App (Android)
**Package:** `com.getauthepay.app`
**Owner:** Braincade Holdings Pty Ltd — Botswana
**Version:** 1.0.0 (versionCode 1)
**Platform:** Android 8.0 (API 26) → Android 14+ (compileSdk / targetSdk 37)
**UI:** Jetpack Compose (Material 3)

---

## 1. What AuthePay Is

AuthePay is a merchant-facing Android application that lets a verified business
accept contactless (tap-to-pay) payments, issue refunds, review transaction
history and settlements, manage their device fleet, and acknowledge security
alerts.

This build is a **functional, test-covered merchant application** — not a UI
prototype. It contains real authentication, onboarding, risk, ledger, receipt,
device-security, and audit subsystems. It is wired to a **deterministic sandbox
payment provider** so the full lifecycle can be exercised end-to-end without a
live card network.

## 2. What Is Connected vs. What Is Deferred

| Capability | Status | Notes |
|---|---|---|
| Merchant authentication (OTP) | Sandbox | `AuthePayApiClient.requestOtp/verifyOtp` |
| Onboarding wizard | Implemented | Server-submitted; `submitOnboarding` |
| Contactless lifecycle (state machine) | Implemented | 12-state machine, deterministic |
| Risk scoring | Implemented | Additive rules engine (`SandboxRiskService`) |
| Ledger / history / receipts | Implemented | In-memory, masked-PAN only |
| Refunds (with biometric gate) | Implemented | Requires SUPERVISOR+ role |
| Settlements view | Implemented | Server-derived only |
| Device management / security alerts | Implemented | Server-backed |
| **Real acquiring processor** | **NOT bundled** | `ServiceLocator` throws in release; supplied by acquirer SDK |
| **EMV / MPoC payment kernel** | **NOT implemented** | `NfcReaderProvider` returns `CERTIFIED_KERNEL_REQUIRED` |
| **Encrypted/persistent ledger** | **Not implemented** | In-memory by design; Room is a future slot-in |

> The product is deliberately structured so the **production payment kernel and
> acquiring processor are drop-in integrations**. No cardholder data is ever
> constructed, stored, or transmitted by the app itself.

## 3. Core User Flows

1. **Onboarding** — business info → owner info → documents → bank info → consent → review → submit. Status reflected via `MerchantStatus`.
2. **Login** — phone/email OTP (`TEST_OTP_ENABLED` bypass in debug builds only).
3. **Accept payment** — amount entry → tap → state machine → risk → contactless → processor → ledger → receipt.
4. **Refund** — locate approved transaction → biometric prompt → submit (SUPERVISOR+).
5. **Dashboard** — transactions, settlements, devices, security alerts.
6. **Settings / Security** — biometric-refund toggle, screenshot protection, session end.

## 4. Environments

| Build type | `API_BASE_URL` | `SANDBOX_PAYMENTS` | `TEST_OTP_ENABLED` | `ALLOW_CLEARTEXT` | `PRODUCTION_BUILD` |
|---|---|---|---|---|---|
| `debug` | `https://sandbox-api.authepay.co.bw` | `true` | `true` | `true` | `false` |
| `staging` | `https://staging-api.authepay.co.bw` | `true` | `false` | `false` | `false` |
| `release` | `https://api.authepay.co.bw` | `false` | `false` | `false` | `true` |

These fields are enforced by `buildConfigField` in `app/build.gradle` and
verified by `SecurityConfigurationTest`.

## 5. Supported Currencies

`CurrencyCatalog` (Africa-first): **BWP, ZAR, ZWM, KES, NGN, GHS, TZS, UGX, MUR, USD, EUR, GBP**.
Default settlement currency is **BWP**. The application consumes the catalogue
rather than hardcoding BWP.

## 6. Build Verification (current)

See `TEST_RESULTS.md` and `STATUS_REPORT.md` for the latest verification state.
The release build signs with a keystore sourced from `local.properties` /
CI secrets; no production keystore is committed to the repository.

---

*See also:* `ARCHITECTURE.md`, `SECURITY_ARCHITECTURE.md`, `API_CONTRACT.md`,
`DEPLOYMENT_GUIDE.md`.
