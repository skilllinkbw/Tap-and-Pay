# AuthePay — Threat Model

Scope: the AuthePay Android merchant app and its on-device handling of payment
attempts. Out of scope: the acquirer/processor backend, the card networks, and
the AuthePay server (assumed server-authoritative).

## 1. Assets

- Merchant session / bearer token (`SecureStorage`)
- Android Keystore crypto key (`SecurityManager`)
- Masked transaction records (ledger) — low sensitivity
- Device integrity signals (sent to risk engine)

## 2. Trust Boundaries

1. **App ↔ Cardholder** — card never touches app logic directly; only a certified
   kernel (future) would read it.
2. **App ↔ AuthePay server** — HTTPS + bearer; server authoritative for ledger,
   settlements, refunds, alerts, attestation.
3. **App ↔ Acquirer** — not present in this build; future drop-in.

## 3. Threats & Mitigations

| # | Threat | Vector | Mitigation | Residual |
|---|---|---|---|---|
| T1 | Stolen PAN/CVV at rest | Malicious export | Masked-PAN only; no CHD persisted | Low |
| T2 | Silent authorisation on risk failure | Risk engine down | **Fail closed** (`RISK_ENGINE_UNAVAILABLE`) | Low |
| T3 | Double charge | Retry / crash | `IdempotencyStore` single-flight; server dedupes | Low |
| T4 | Rooted/compromised device accepts live pay | Root/emulator | `DeviceSecurityChecker` + `DEVICE_UNTRUSTED` hard-block at trust<20; server attestation | Medium (client hints only) |
| T5 | Token / session theft | Logcat / backup | `SecureLogger` redaction; `EncryptedSharedPreferences`; Keystore non-exportable | Low |
| T6 | Cleartext interception | Rogue network | `HttpClient` https-only; release `ALLOW_CLEARTEXT=false` | Low |
| T7 | Unsandboxed production ship | Misconfig | `ServiceLocator` throws if `SANDBOX_PAYMENTS` in release; `SecurityConfigurationTest` | Low |
| T8 | Screenshot of receipt/PAN | OS capture | `ScreenshotProtector` `FLAG_SECURE` on sensitive screens | Low |
| T9 | Unauthorized refund | Low-priv user | `MerchantRole` SUPERVISOR+ + `BiometricGate` | Low |
| T10 | Forged audit trail | Compromised client | Alerts acked server-side; client cannot self-approve | Low |
| T11 | EMV relay / fake tap | No certified kernel | `NfcReaderProvider` returns `CERTIFIED_KERNEL_REQUIRED` until MPoC SDK | HIGH until integrated |
| T12 | Acquirer credential leak | Bundled secret | Processor interface credential-free; secrets server-side | Low |

## 4. Highest-Priority Items

- **T11** — live contactless is impossible without a certified MPoC/SoftPOS kernel.
  This is the gating item for any real card-present acceptance.
- **T4** — client integrity signals are hints; the **server must** enforce Play
  Integrity / device attestation before allowing live payments.

## 5. Assumptions

- The AuthePay backend is itself secure and server-authoritative.
- `https://api.authepay.co.bw` (and sandbox/staging) are the only endpoints.
- The merchant device is a managed, non-rooted terminal in normal operation.

See `SECURITY_ARCHITECTURE.md`, `CERTIFICATION_READINESS.md`.
