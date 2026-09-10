# AuthePay — Certification Readiness

Status of payment-industry certifications as they relate to the Android app.
External certifications are owned by the acquirer/processor and assessed by
qualified labs; this document states what the app does and does not yet satisfy.

## 1. PCI / MPoC (Contactless)

| Requirement | App state | Action |
|---|---|---|
| Certified contactless kernel | **Not present** | `NfcReaderProvider` returns `CERTIFIED_KERNEL_REQUIRED`; integrate acquirer MPoC/SoftPOS SDK |
| No CHD in app | Met | Masked-PAN only; no PAN/CVV/PIN/track |
| Secure element / Keystore key | Met (client crypto) | `SecurityManager` AES-256-GCM in Android Keystore |
| Tamper / root resistance | Partial | `DeviceSecurityChecker` hints + hard-block <20; server attestation required |
| Audit trail | Partial | Alerts acked server-side; client cannot forge |

**Verdict:** App is architected for MPoC but **cannot accept live contactless
until a certified kernel is integrated and re-certified by the lab.**

## 2. EMV (Card-Present)

- No EMV transaction processing exists in-app; the certified kernel owns it.
- `SandboxContactlessProvider` is a simulation only and must never be used for live.

## 3. PCI DSS (Client-Side)

- Data minimisation: **met** (no CHD stored).
- Encryption at rest: **met** (Keystore + EncryptedSharedPreferences).
- TLS: **met** (https-only, cleartext disabled in release).
- Logging hygiene: **met** (`SecureLogger` redaction).
- Secure coding / SCA: partial — depends on final review + pen-test.

## 4. PCI CP (Card-Present) — if applicable

- Same kernel dependency as MPoC. Not decoupled from the MPoC path.

## 5. Local / Regulatory (Botswana)

- Bank of Botswana / NPS alignment is the acquirer's responsibility.
- AuthePay (Braincade Holdings) onboarding, KYC, and settlement are server-side.

## 6. What Blocks "Production-Ready" Certification

1. **Certified contactless kernel** (T11 in `THREAT_MODEL.md`).
2. **Real acquiring processor** wired in `ServiceLocator` (release currently throws).
3. **Server-side device attestation** (Play Integrity / SafetyNet) enforced.
4. **Independent pen-test** + final SCA review.

See `NFC_ARCHITECTURE.md`, `THREAT_MODEL.md`, `BANK_SUBMISSION_READINESS.md`.
