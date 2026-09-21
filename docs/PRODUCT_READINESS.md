# Tap & Pay — Product Readiness

**As of:** 2026-09-21 · **Version:** 1.0.0 (versionCode 1) ·
**App:** `com.getauthepay.app`

This document separates what genuinely works from what depends on external
parties. Categories are never blurred.

## WORKING — implemented and tested

- Android app end-to-end: splash, onboarding, login (OTP request/verify),
  dashboard, Accept Payment, QR scan, transaction history + detail, refunds,
  settlements, devices, team, security alerts, security settings, help, about.
- Payment state machine (CREATED→…→APPROVED/DECLINED/FAILED/CANCELLED/TIMEOUT
  →REVERSED/REFUNDED) — exhaustive unit tests, no backward transitions.
- Duplicate-payment protection: atomic single-flight idempotency store,
  terminal mutex (one payment at a time), cached-result replay — regression
  tested under concurrency.
- Risk screening with fail-closed behaviour (risk engine down ⇒ no sale).
- Validation: amount (min/max/scale per ISO 4217), currency catalogue
  (BWP-first, 12 currencies), reference, OTP shape.
- Receipts (masked PAN only), transaction ledger, refunds with role gating.
- Security: Keystore-backed encrypted session storage, session expiry, OTP
  lockout + cooldown, biometric gate option, device trust scoring, FLAG_SECURE,
  redaction-enforced logging, HTTPS-only client.
- Build system: debug/staging/demo/release build types, R8, signing pipeline,
  210 unit tests (0 failures), lint, APK + AAB outputs.
- Offline/poor-network failure behaviour: bounded timeouts, fail-closed errors
  that state whether money moved, no duplicate submission on retry.

## TEST / SANDBOX — implemented, dependent on test infrastructure

- Sandbox payment rail: `SandboxPaymentProcessor` / `SandboxContactlessProvider`
  simulate provider responses incl. the full failure matrix (decline, timeout,
  cancel, duplicate, network failure, processor error, risk decline). Clearly
  labelled SANDBOX/DEMO in-app; release build cannot reach it
  (`SANDBOX_PAYMENTS=false`, test-enforced).
- Test OTP shortcut (`000000`): debug/demo builds only, `TEST_OTP_ENABLED`
  false in release.
- Demo build type: release-grade hardening + sandbox rail for bank demos.

## PENDING PARTNER — requires bank / payment-provider integration

- Production payment rail (AuthePay API → acquirer): `PaymentProcessor`
  boundary ready; credentials and contract come from the partner.
- AuthePay backend services (OTP issuance, sessions, ledger, risk, audit
  ingestion) per `android/docs/API_CONTRACT.md`.
- Mobile-money rails (Orange Money BW etc.): adapter boundaries only.
- Production signing keystore (deployment artifact, supplied via CI secrets).
- Server-side attestation (Play Integrity), certificate pinning, remote
  device revocation.

## PENDING CERTIFICATION — requires external certification/approval

- Certified SoftPOS/MPoC card acceptance (EMV kernel via partner SDK).
- Any PCI DSS / scheme assessment (to be conducted with the acquirer; **not
  claimed**).
- Regulatory engagement (Bank of Botswana etc.) as advised by counsel.

## FUTURE — not currently implemented

- Room-backed persistent on-device ledger (current ledger is in-memory for the
  running session; server ledger is authoritative).
- Setswana localisation (strings centralised; translation not shipped).
- Billing/subscription management for merchants (clean integration point:
  account-level feature flags from the backend — no client-only licensing, by
  design).
- Consumer-facing companion app.

---

**Bottom line:** the app is a tested, hardened merchant terminal front-end with
an honest sandbox. It becomes a live payment product when the partner backend,
provider SDK and certifications land — each behind an existing, tested seam.
