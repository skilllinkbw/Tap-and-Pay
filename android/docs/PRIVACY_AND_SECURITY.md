# AuthePay — Privacy & Security Posture

## 1. Privacy Principles

- **Data minimisation:** only what is needed to accept and reconcile a payment.
- **No cardholder data:** the app never sees, stores, or transmits CHD.
- **Transparency:** sandbox builds show a `SandboxBanner` so merchants never
  mistake test mode for live.
- **User control:** biometric-refund toggle, screenshot protection, session end.

## 2. Personal Data Handled

| Category | Examples | Basis |
|---|---|---|
| Merchant identity | business name, contact, role | Contract / KYC |
| Device signals | OS version, security state | Risk + integrity |
| Behavioural | tx counts, velocity | Risk scoring |
| Auth | phone/email + OTP | Authentication |

No special-category (health/biometric template) data is stored — the biometric
**result** (success/failure) is used, never a template.

## 3. Security Controls Summary

| Control | Where | Status |
|---|---|---|
| At-rest crypto | `SecurityManager` (AES-256-GCM, Keystore) | Implemented |
| Encrypted prefs | `SecureStorage` | Implemented |
| Biometric gate | `BiometricGate` | Implemented |
| Screenshot block | `ScreenshotProtector` | Implemented |
| Redacting logs | `SecureLogger` | Implemented |
| HTTPS-only transport | `HttpClient` | Implemented |
| Fail-closed payments | `PaymentAcceptanceEngine` | Implemented |
| Risk hard-block (low trust) | `SandboxRiskService` | Implemented |
| Device attestation (server) | `DeviceSecurityChecker` → backend | Hint only; server enforces |
| EMV/MPoC kernel | `NfcReaderProvider` | **Not implemented** |

## 4. Consent & Notice

- Onboarding `CONSENT` step captures merchant agreement before submission.
- OTP verification is the authentication event; tokens are short-lived.
- The privacy notice should reference this document and the AuthePay privacy policy
  (server-side) — not bundled in the app.

## 5. User Rights

- Session end / logout wipes local Keystore key + session.
- Device revoke removes the terminal from the merchant fleet.
- Refund/transaction history is server-authoritative; local view is a cache.

## 6. Known Limitations

- Client integrity signals (`DeviceSecurityChecker`) are **hints**; a determined
  attacker can spoof them. The **server must** enforce Play Integrity / device
  attestation before allowing live payments.
- No persistent local ledger means offline history is unavailable until reconnect.

See `DATA_RETENTION.md`, `THREAT_MODEL.md`, `SECURITY_ARCHITECTURE.md`.
