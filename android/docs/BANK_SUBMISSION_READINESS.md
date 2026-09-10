# AuthePay — Bank Submission Readiness

Prepared for the acquiring bank / Bank of Botswana engagement. This is an honest
readiness assessment; items still open are called out explicitly.

## 1. Readiness Scorecard

| Area | Status | Evidence |
|---|---|---|
| Merchant app (auth, onboarding, dashboard) | ✅ Ready (sandbox) | Implemented + unit-tested |
| Risk scoring + fail-closed | ✅ Ready | `SandboxRiskService`, `PaymentAcceptanceEngine` |
| Ledger / receipts / refunds | ✅ Ready | In-memory, masked-PAN |
| Build pipeline (test/lint/APK) | ✅ See `TEST_RESULTS.md` | Gradle `clean test lint assembleDebug assembleRelease` |
| Release security contract | ✅ Enforced | `SecurityConfigurationTest` + `buildConfigField` |
| HTTPS-only transport | ✅ Enforced | `HttpClient` https-only; cleartext off in release |
| No CHD on device | ✅ By design | Masked-PAN only |
| **Certified contactless kernel** | ❌ Open | `CERTIFIED_KERNEL_REQUIRED` until MPoC SDK |
| **Real acquiring processor** | ❌ Open | `ServiceLocator` throws in release |
| **Server device attestation** | ⚠️ Partial | Client hints only; server must enforce |
| **Pen-test / final SCA** | ⚠️ Pending | External |

## 2. What We Can Demonstrate Today

- Full sandbox payment lifecycle (tap → approve/decline/timeout/cancel/duplicate/error).
- Fail-closed guarantees (no sale on risk/processor/NFC failure).
- Risk hard-block for untrusted devices.
- Refunds with biometric + role gating.
- Dashboard, settlements (server-derived), device management, security alerts.
- Signed debug + release APKs (release signs with a keystore from `local.properties`/CI).

## 3. What the Bank Must Provide / Approve

1. **Acquiring processor integration** (real `PaymentProcessor`) + endpoint.
2. **Certified MPoC/SoftPOS kernel** for live contactless.
3. **Device attestation** policy (Play Integrity / SafetyNet) enforced server-side.
4. **Settlement & KYC** backend aligned to Bank of Botswana / NPS.
5. **Pen-test** sign-off and PCI scope confirmation.

## 4. Submission Package

- `TEST_RESULTS.md` — latest test + lint + build output.
- `DEPLOYMENT_GUIDE.md` — signing, build types, CI secrets.
- `ARCHITECTURE.md`, `SECURITY_ARCHITECTURE.md`, `THREAT_MODEL.md`.
- `API_CONTRACT.md` — client/server contract.
- `CERTIFICATION_READINESS.md` — certification gaps.
- `STATUS_REPORT.md` — honest, current status (13-item checklist).

## 5. Honest Caveats (must not be omitted in submission)

- Physical NFC **not tested** on hardware; contactless is simulated.
- Sandbox payments only; **no real acquirer connected** in this build.
- PCI/EMV/MPoC certifications are **external** and not yet obtained.
- Release build intentionally refuses to run a payment without the partner kernel.

See `STATUS_REPORT.md`, `CERTIFICATION_READINESS.md`, `DEPLOYMENT_GUIDE.md`.
