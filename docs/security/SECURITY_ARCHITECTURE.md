# Tap & Pay — Security Architecture

**Product:** Tap & Pay, a Braincade Holdings Pty Ltd product
**Scope:** Android merchant app (`com.getauthepay.app`) and its integration
boundaries. Companion detail: [`android/docs/SECURITY_ARCHITECTURE.md`](../../android/docs/SECURITY_ARCHITECTURE.md),
[`android/docs/THREAT_MODEL.md`](../../android/docs/THREAT_MODEL.md).

## 1. Principles

1. **Fail closed.** Any failing security control (risk engine unreachable, NFC
   unavailable, processor error) must never produce a sale.
2. **Server/provider authoritative.** The client never decides final financial
   state; APPROVED is only shown on provider confirmation.
3. **No cardholder data.** The app never constructs, stores or transmits full
   PAN, CVV, PIN or track data. Receipts carry masked PAN only (enforced by
   `PaymentResult` init validation).
4. **No client secrets.** The app ships no API keys, processor credentials or
   private keys; the processor boundary (`PaymentProcessor`) is deliberately
   credential-free.
5. **Least privilege.** Permissions: NFC, INTERNET, NETWORK_STATE, BIOMETRIC,
   CAMERA (QR only). No contacts/location/SMS.

## 2. Authentication & session

- Passwordless: phone/email + server-verified OTP (6 digits, 180s TTL mirror,
  5-attempt lockout, 45s resend cooldown; server rate limit authoritative).
- Session token + expiry in `EncryptedSharedPreferences` (AES256_SIV/GCM,
  Keystore master key). Expired sessions are rejected on load.
- Optional biometric gate (`BiometricGate`) for sensitive actions such as
  refunds, per merchant security settings.
- Debug-only OTP shortcut exists **only** when `BuildConfig.TEST_OTP_ENABLED`
  (false in release; asserted by `SecurityConfigurationTest`).

## 3. Application & device security

- Release: non-debuggable, R8-minified, resource-shrunk, cleartext forbidden
  (`usesCleartextTraffic=false` + `network_security_config`), backups disabled.
- `FLAG_SECURE` screenshot protection on the payment surface.
- Device integrity: root/emulator/debug indicators feed a trust score; a low
  score hard-blocks acceptance (`DEVICE_UNTRUSTED`). Client-side signals only —
  server attestation is the planned authoritative layer.
- Exported surface: single launcher activity; FileProvider non-exported;
  no exported services/receivers; no deep links; no WebView.
- Keystore-backed AES-256-GCM (`SecurityManager`) for at-rest encryption needs;
  key wiped on logout/revoke.

## 4. Network security

- HTTPS-only `HttpClient` (init rejects non-HTTPS base URLs), 15s/20s bounded
  timeouts, no redirect following, response body cap, bearer token per request,
  `X-AuthePay-Correlation-Id` end-to-end tracing.
- Production base URL `https://api.authepay.co.bw`; sandbox/staging hosts per
  build type. Certificate pinning is **planned** at provider integration.

## 5. Payment security

- Deterministic state machine: CREATED → READY_FOR_TAP → CARD_DETECTED →
  PROCESSING → AUTHORIZING → APPROVED/DECLINED/FAILED/CANCELLED/TIMEOUT →
  REVERSED/REFUNDED. Illegal transitions are rejected; terminal states cannot
  move backwards.
- **Duplicate protection:** per-attempt idempotency key; atomic single-flight
  store (`ConcurrentHashMap.putIfAbsent`); terminal mutex in the engine (one
  payment at a time per terminal); server dedup remains authoritative.
- **Risk engine:** pre-authorisation scoring; BLOCK short-circuits before the
  NFC radio is used; risk-engine failure fails closed (`RISK_ENGINE_UNAVAILABLE`).
- Refunds/reversals are role-gated and (optionally) biometric-gated.

## 6. NFC & QR security

- NFC: runtime capability + enabled-state detection, reader-mode with
  foreground activity binding, non-ISO-DEP tags rejected, duplicate scans
  collapse into the single in-flight attempt, cancel tears down reader mode.
  No EMV kernel in the app — production card acceptance requires a certified
  MPoC/SoftPOS SDK (**pending external certification/partner approval**).
- QR: camera permission with rationale, on-device ML Kit decoding, payload
  shape validation, PAN-shaped payloads rejected outright, no URL execution —
  scanned values are treated as tokens/references, never launched.

## 7. Logging & monitoring

- `SecureLogger`: structured events; regex redaction of PAN/CVV/PIN/OTP/tokens/
  private keys with fail-safe assertions (unit-tested). Release builds guard
  platform log output.
- Audit events for authentication, payment lifecycle, security events and
  configuration changes; correlated by transaction/correlation ID.

## 8. Secrets & signing

- No secrets in the repository (audited; `.gitignore` blocks keystores,
  `local.properties`, `.env`).
- Release signing from `local.properties`/CI secrets only; the committed
  pipeline uses a clearly-labelled **development build-verification key** —
  the production keystore is a deployment artifact, never committed.
- If a secret is ever committed, follow
  [INCIDENT_RESPONSE_PLAN.md](INCIDENT_RESPONSE_PLAN.md): revoke/rotate first,
  then purge history.

## 9. Third-party integrations (pending)

AuthePay production API, acquirer/processor SDK, mobile-money rails
(Orange Money BW etc.) — adapter boundaries exist; credentials live
server-side per partner contract. **Pending external partner approval.**

## 10. Incident response & continuity

See [INCIDENT_RESPONSE_PLAN.md](INCIDENT_RESPONSE_PLAN.md) and
[../bank/BANK_DUE_DILIGENCE_CHECKLIST.md](../bank/BANK_DUE_DILIGENCE_CHECKLIST.md)
(business continuity / disaster recovery items marked where provider-dependent).
