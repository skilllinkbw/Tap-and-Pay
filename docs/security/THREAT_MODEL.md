# Tap & Pay — Threat Model

**Scope:** the Tap & Pay Android merchant app and its on-device handling of
payment attempts. Out of scope (assumed hardened, server-authoritative): the
AuthePay backend, acquirer/processor systems, card networks. Companion:
[`android/docs/THREAT_MODEL.md`](../../android/docs/THREAT_MODEL.md).

| # | Threat | Impact | Existing control | Residual risk | Mitigation / status |
|---|---|---|---|---|---|
| T1 | Stolen device | Attacker uses merchant's terminal | OTP + session expiry; Keystore-backed encrypted storage; backups off; wipe on logout | Medium | Biometric gate on sensitive actions (shipped); remote revoke (server, pending backend) |
| T2 | Compromised/rooted device | Trust signals forged, memory read | `DeviceSecurityChecker` trust score; hard-block below threshold; R8 obfuscation | Medium — client signals are hints | Server-side attestation (Play Integrity) at provider integration — **pending** |
| T3 | Malicious app on device | Overlay, accessibility abuse, intent hijack | No exported components except launcher; no deep links; FLAG_SECURE on payment screens | Medium | Tapjacking/overlay detection — future hardening |
| T4 | MITM / network tampering | Payment requests intercepted | HTTPS-only enforced in client; cleartext forbidden manifest-wide; no redirect following | Low-Medium | Certificate pinning — planned at provider integration |
| T5 | Credential/OTP theft | Account takeover | Server-verified OTP; 5-attempt client lockout + server 429; OTP never logged/persisted; `SecureLogger` redaction backstop | Low | Server anomaly detection (backend) |
| T6 | Replay attack | Old payment re-submitted | Idempotency key per attempt; atomic single-flight store; server dedup authoritative | Low | Provider-side nonce/timestamp binding at integration |
| T7 | Duplicate payment (double-tap, slow network) | Customer charged twice | Terminal mutex in engine (one payment at a time); `begin()` atomic single-flight; cached-result replay; regression-tested | Low | Covered by unit tests (concurrency suite) |
| T8 | QR manipulation | Fraudulent token/reference | Shape validation; PAN-shaped payloads rejected; values treated as tokens only — never URL-launched | Low | Server-side token verification (backend) |
| T9 | NFC manipulation / malformed tag | Crash or bogus read | ISO-DEP-only acceptance; malformed tags rejected with safe error; no EMV kernel in-app | Low | Certified MPoC/SoftPOS SDK — **pending external certification** |
| T10 | API abuse | Enumeration, fraud attempts | Bearer session token; correlation IDs; server rate limiting (429) | Medium | WAF/risk rules at backend (provider scope) |
| T11 | Account takeover (insider/merchant staff) | Unauthorised refunds | Role-based access (owner/manager/cashier); refunds role-gated + optional biometric; audit trail | Low-Medium | Backend anomaly alerts |
| T12 | Insider abuse at Braincade | Data misuse | Data minimisation (no CHD exists to misuse); redacted logs; least-privilege design | Low | Backend access controls + audit (backend scope) |
| T13 | Leaked credentials/secrets | Backend compromise | No secrets in app or repo; dev-only signing key labelled; processor boundary credential-free | Low | Rotation runbook in incident plan |
| T14 | Backend compromise | Mass fraud | Client treats server as authoritative — residual by design | Medium | Provider-side controls; app surfaces provider status only |
| T15 | Fraudulent merchant | Transaction laundering | Onboarding verification flow (pending-state screen); risk scoring per transaction | Medium | Acquirer KYC/merchant underwriting — partner scope |
| T16 | Denial of service | Merchant cannot trade | Bounded timeouts; fail-closed errors state "no money taken"; QR fallback path | Medium | Provider SLA — contractual |

**Legend:** "pending" = requires the backend/provider integration that is
currently *pending external partner approval*. No control here claims
certification it does not have.
