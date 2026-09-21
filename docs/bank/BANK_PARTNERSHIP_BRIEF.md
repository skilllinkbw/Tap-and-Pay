# Tap & Pay — Bank Partnership Brief

**Braincade Holdings Pty Ltd · Tap & Pay / AuthePay · Botswana**
Companion: [`android/docs/PARTNER_BRIEF.md`](../../android/docs/PARTNER_BRIEF.md),
[`android/docs/BANK_SUBMISSION_READINESS.md`](../../android/docs/BANK_SUBMISSION_READINESS.md).

## 1. Executive summary

Tap & Pay is a merchant payment-acceptance application for Android that lets a
merchant accept **contactless (NFC) card taps and QR-based payments** from a
phone, built for Botswana (BWP-first) and the wider African market. The Android
application, payment state machine, risk screening, idempotency/duplicate
protection, receipts, history, refunds and audit logging are implemented and
tested. Live money movement requires integration with an acquiring bank /
payment processor / mobile-money provider — **that integration and all external
certifications are pending partner approval.**

## 2. Problem

Botswana's small merchants face high terminal costs and friction accepting
card and wallet payments. A phone-based acceptance product lowers the barrier:
no dedicated POS hardware, BWP-native, with offline-aware failure behaviour for
real Botswana connectivity conditions.

## 3. Solution

A merchant signs in with phone/email + one-time code, enters an amount, and the
customer taps a contactless card (NFC) or the merchant scans a payment QR. The
app runs pre-authorisation risk screening, prevents duplicate submissions,
drives a strict transaction state machine, and produces a receipt and audit
trail. Settlements, devices, team roles and security alerts are managed in-app.

## 4. Product architecture (high level)

```
Compose UI → ViewModels → ServiceLocator (DI) → PaymentAcceptanceEngine
   → RiskService (pre-auth screening)
   → ContactlessPaymentProvider (NFC reader seam | certified kernel seam)
   → PaymentProcessor (credential-free acquirer boundary)
   → TransactionLedger + SecureLogger (redacted audit trail)
   → HTTPS-only HttpClient → AuthePay API / provider backend
```

The domain core (`core/**`) is Android-free and JVM unit-tested
(210 tests, 0 failures — see TEST_RESULTS).

## 5. Security

HTTPS-only transport; Android Keystore-backed encrypted session storage;
biometric option for sensitive actions; device integrity scoring with
fail-closed payment blocking; role-based access (owner/manager/cashier);
no cardholder data anywhere in the app; redaction-enforced logging; no secrets
in the app or repository. Details:
[../security/SECURITY_ARCHITECTURE.md](../security/SECURITY_ARCHITECTURE.md).

## 6. Transaction flow

```
Customer → Tap & Pay app (amount + tap/scan) → risk screen → provider
authorisation request (idempotency key) → provider response →
APPROVED/DECLINED/FAILED/CANCELLED/TIMEOUT → receipt + ledger + audit event
```

The provider's status is authoritative; the app never fabricates success.

## 7. NFC

The app uses Android NFC reader mode to detect ISO 14443 contactless cards.
It deliberately contains **no home-grown EMV kernel**: production card
acceptance requires the partner's certified MPoC/SoftPOS SDK, which plugs into
the existing `ContactlessPaymentProvider` seam without app rewrites.

## 8. QR fallback

QR scanning covers tokenised payment requests, payment references, merchant
identifiers and receipts. Payloads are shape-validated, PAN-shaped content is
rejected, and scanned values are never executed as URLs.

## 9. Data protection

Data-minimising by design (no CHD, no analytics SDKs, redacted logs, backups
off). Policy set under Botswana's Data Protection Act, 2018 is drafted in
[../legal/](../legal/) — pending final legal review.

## 10. Auditability

Every payment lifecycle transition, authentication event and security event
produces a redacted, correlation-ID-linked audit record; receipts carry
transaction reference, auth code and masked PAN only.

## 11. Risk controls

Pre-authorisation risk scoring (fail-closed), duplicate-payment protection
(atomic single-flight + idempotency keys + server dedup), role-gated refunds,
device trust gating, session expiry, OTP attempt lockout.

## 12. Deployment model

| Build | Purpose | Payment rail |
|---|---|---|
| release (`com.getauthepay.app`) | Production | Provider integration — **pending** |
| demo (`….demo`) | Bank demonstrations | Deterministic sandbox, clearly labelled |
| staging / debug | Development | Sandbox |

The production APK builds, signs (dev verification key) and passes lint and all
tests today; distribution awaits the production keystore and provider contract.

## 13. Integration requirements — what we need from partners

- **Acquiring bank / processor:** certified SoftPOS/MPoC SDK (or gateway API),
  merchant onboarding/KYC flow, settlement schedule, dispute/chargeback process,
  sandbox credentials for integration testing.
- **Mobile-money providers (e.g. Orange Money BW):** collection API credentials,
  webhook/callback contract, reconciliation reports.
- **Backend (AuthePay):** OTP issuance/verification, session service,
  transaction ledger API, risk service, audit ingestion — per
  `android/docs/API_CONTRACT.md`.

## 14. Certification status

| Item | Status |
|---|---|
| EMV L1/L2 kernel, MPoC/SoftPOS certification | **Pending external certification/partner approval** |
| PCI DSS / scheme compliance | Not claimed — assessed with the acquirer during integration |
| Bank partnership | Not claimed — this brief initiates that process |
| Regulatory approvals (Bank of Botswana etc.) | **Pending external approval** |

We make no claim of certification, approval or partnership that has not been
formally granted.
