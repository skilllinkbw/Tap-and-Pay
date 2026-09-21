# Tap & Pay — Bank Due-Diligence Checklist

**Braincade Holdings Pty Ltd · Tap & Pay / AuthePay**
Evidence pointers are to this repository. Status legend: ✅ available ·
🟡 partial · ⏳ pending external party.

## 1. Company information
- ✅ Product owner: Braincade Holdings Pty Ltd (Botswana) — see README.
- ⏳ Registration documents, shareholding, tax clearance: provided through the
  commercial engagement process (not stored in the code repository).

## 2. Product description
- ✅ [BANK_PARTNERSHIP_BRIEF.md](BANK_PARTNERSHIP_BRIEF.md);
  `android/docs/PRODUCT_OVERVIEW.md`; [../PRODUCT_READINESS.md](../PRODUCT_READINESS.md).

## 3. Architecture
- ✅ `android/docs/ARCHITECTURE.md`, `android/docs/PAYMENT_DATA_FLOW.md`,
  `android/docs/NFC_ARCHITECTURE.md`.

## 4. Security
- ✅ [../security/SECURITY_ARCHITECTURE.md](../security/SECURITY_ARCHITECTURE.md);
  enforced by `SecurityConfigurationTest` (release: no sandbox rail, no test
  OTP, no cleartext, production flag).
- ⏳ Penetration test report: recommended before production rollout — not yet
  commissioned.

## 5. Privacy
- ✅ [../legal/PRIVACY_POLICY.md](../legal/PRIVACY_POLICY.md) (draft, pending
  legal review); data-minimising design (no CHD, no analytics SDK).

## 6. Terms
- ✅ [../legal/TERMS_AND_CONDITIONS.md](../legal/TERMS_AND_CONDITIONS.md)
  (draft, pending legal review);
  [../legal/ACCEPTABLE_USE_POLICY.md](../legal/ACCEPTABLE_USE_POLICY.md).

## 7. Transaction lifecycle
- ✅ Deterministic state machine (`PaymentStateMachine`) with exhaustive tests;
  no backward transitions; terminal-state integrity.

## 8. API / integration model
- ✅ `android/docs/API_CONTRACT.md`, `android/docs/API_DOCUMENTATION.md`;
  HTTPS-only client; correlation IDs; credential-free processor boundary.

## 9. Authentication
- ✅ OTP (server-verified), session expiry, lockout, biometric option;
  Keystore-backed storage.

## 10. Fraud controls
- ✅ Risk screening (fail-closed), idempotency/duplicate protection
  (concurrency-tested), device trust scoring, role-gated refunds.

## 11. Audit logging
- ✅ `SecureLogger` redaction contract (unit-tested); payment/auth/security
  audit events; correlation IDs end-to-end.

## 12. Incident response
- ✅ [../security/INCIDENT_RESPONSE_PLAN.md](../security/INCIDENT_RESPONSE_PLAN.md).

## 13. Business continuity / disaster recovery
- ⏳ Backend RPO/RTO, redundancy and failover are defined with the backend /
  provider during integration; app-side failure behaviour is fail-closed and
  offline-safe (no duplicate transactions on retry).

## 14. Data retention
- ✅ [../legal/DATA_RETENTION_POLICY.md](../legal/DATA_RETENTION_POLICY.md);
  periods marked **[CONFIRM]** where they depend on banking/tax/AML rules.

## 15. Vendor & third-party dependencies
- ✅ Dependency set is small and declared (`android/app/build.gradle`);
  no analytics/ad SDKs. ⏳ Provider SDKs added during integration.

## 16. Testing evidence
- ✅ 210 JVM unit tests, 0 failures (payment state machine, idempotency,
  concurrency, refunds, receipts, HTTP client, security config, redaction);
  Android lint; debug+release+Demo builds; see `android/docs/TEST_RESULTS.md`
  and the repo-root `FINAL_RELEASE_REPORT.md`.

## 17. Release process
- ✅ Gradle wrapper builds (debug/release/demo; APK + AAB); R8 minification;
  signing via CI secrets/local.properties; dev build-verification key clearly
  labelled, production keystore never committed.

## 18. Known limitations
- ✅ README §6 and [../PRODUCT_READINESS.md](../PRODUCT_READINESS.md): sandbox
  rail is simulated; production rail, backend and certified kernel are pending
  partner integration.

## 19. Certifications
- ⏳ **External certification and partner approval: PENDING** (EMV/MPoC via
  acquirer SDK; scheme compliance via acquirer). No certification is claimed.

## 20. Pending approvals
- ⏳ Acquiring bank / processor contract; mobile-money provider agreements;
  legal review of customer-facing documents; Bank of Botswana engagement as
  advised by counsel.
