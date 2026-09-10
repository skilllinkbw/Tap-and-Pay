# AuthePay — Bank Demo Checklist

A practical script for a bank/acquirer demonstration. The build is wired to the
**deterministic sandbox**, so the full lifecycle can be shown without a live card
network. **Be explicit about what is simulated.**

## 1. Before the Demo

- [ ] Build `assembleDebug` (or `assembleRelease` if signing is configured).
- [ ] Confirm device/emulator has NFC (or rely on `SandboxContactlessProvider`).
- [ ] Note: release build will **not** run a payment without the acquirer kernel —
      use `debug`/`staging` for the live demo.
- [ ] Prepare `TestScenario` outcomes to showcase (see `core/models/TestScenario.kt`):
      `TEST_APPROVED, TEST_DECLINED, TEST_TIMEOUT, TEST_CANCELLED, TEST_DUPLICATE,
       TEST_NETWORK_FAILURE, TEST_PROCESSOR_ERROR, TEST_RISK_DECLINE`.

## 2. Demo Script

1. **Onboarding** — run the wizard; show `MerchantStatus` progression.
2. **Login** — OTP (debug allows test OTP); show bearer session in `SecureStorage`.
3. **Accept payment (approved)** — enter amount, tap (sandbox), watch the
   12-state machine in the UI: `READY_FOR_TAP → CARD_DETECTED → PROCESSING →
   AUTHORIZING → APPROVED`. Show receipt with **masked PAN**.
4. **Risk BLOCK** — drive `TEST_RISK_DECLINE` or low `deviceTrustScore`; show
   `DECLINED(RISK_BLOCKED)` and `DEVICE_UNTRUSTED` hard-block.
5. **Fail closed** — show that a processor exception yields `FAILED(PROCESSOR_UNAVAILABLE)`
   with *"No money has been taken."*
6. **Idempotency** — repeat the same payment; show cached terminal result replay
   (no double charge).
7. **Refund** — locate approved txn, trigger biometric prompt (SUPERVISOR+), submit.
8. **Dashboard** — transactions, settlements (server-derived), devices, alerts.
9. **Security** — show `SecurityAlertsScreen`, device revoke, screenshot protection.

## 3. Honesty Notes to State Aloud

- Contactless is **simulated** (`SandboxContactlessProvider`); no real card read.
- Payments go to the **sandbox processor**, not a live acquirer.
- **No certified EMV/MPoC kernel** is bundled — live acceptance requires the
  partner SDK (see `CERTIFICATION_READINESS.md`).
- All card data shown is masked; no CHD exists in the app.

## 4. Evidence to Capture

- Screen recording of the approved flow + receipt (masked).
- `lint` + `test` green output (see `TEST_RESULTS.md`).
- APK path + signing info (see `DEPLOYMENT_GUIDE.md`).

See `PRODUCT_OVERVIEW.md`, `STATUS_REPORT.md`, `DEPLOYMENT_GUIDE.md`.
