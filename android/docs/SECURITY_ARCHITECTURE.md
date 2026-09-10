# AuthePay — Security Architecture

## 1. Security Principles

1. **Fail closed.** Any unreachable or failing security control (risk engine,
   NFC, processor) must *not* produce a sale. The `PaymentAcceptanceEngine`
   never calls the processor on a fail-closed path and never records a sale.
2. **No cardholder data on device.** Full PAN, CVV, PIN, and track data are
   never constructed, stored, or logged by the app. Only a masked PAN
   (first6/last4) may appear, and only in the receipt/ledger.
3. **Credential-free client.** The `PaymentProcessor` interface carries no
   acquirer credentials. Secrets live server-side.
4. **Secrets in the Android Keystore.** At-rest crypto keys are non-exportable.
5. **HTTPS only.** `HttpClient` rejects any non-`https://` base URL and the
   release build disables cleartext (`ALLOW_CLEARTEXT=false`).

## 2. At-Rest Protection — `SecurityManager`

- Key alias `AuthePaySecureKey`, algorithm **AES/GCM/NoPadding**, **256-bit** key.
- Key generated in `AndroidKeyStore` (PURPOSE_ENCRYPT|DECRYPT, GCM, no padding).
- 12-byte IV, 128-bit GCM tag.
- `deviceTrustScore()` delegates to `DeviceSecurityChecker`.
- `wipe()` deletes the Keystore entry on logout / device revoke.

## 3. Secure Preferences — `SecureStorage`

- Backed by `EncryptedSharedPreferences` with a `MasterKey` (AES256-GCM) rooted
  in the Android Keystore.
- Stores: session token, expiry, merchant id, terminal id, user role, last-login,
  onboarding draft, preferred currency, biometric-refund flag.
- KDoc contract: **cardholder data MUST NEVER be stored here.**

## 4. Runtime Protections

| Control | Mechanism |
|---|---|
| Biometric gate | `BiometricGate` (`BiometricPrompt`, `BIOMETRIC_STRONG|WEAK`); payload never persisted |
| Screenshot protection | `ScreenshotProtector` toggles `FLAG_SECURE` on sensitive activities |
| Device integrity hints | `DeviceSecurityChecker` (root/debug/emulator/adb/bootloader) → risk engine only |
| Redacting logs | `SecureLogger` masks PAN, CVV, PIN, OTP, tokens, keys |

## 5. Payment Security — `PaymentAcceptanceEngine` (fail-closed)

The engine returns before any processor call / sale when:

- **NFC unavailable/disabled** → `FAILED` (`NFC_NOT_AVAILABLE` / `NFC_DISABLED`)
- **Risk engine unreachable** → `FAILED` (`RISK_ENGINE_UNAVAILABLE`); *"if the risk
  engine is unreachable we do not silently authorise"*
- **Risk BLOCK** → `DECLINED` (`RISK_BLOCKED`) before contactless transport
- **Processor exception** → `FAILED` (`PROCESSOR_UNAVAILABLE`); *"No money has been taken"*
- **Transport ended before AUTHORIZING** → `FAILED` (`TRANSPORT_ENDED`), idempotency discarded
- **Idempotency replay** → cached terminal result replayed, no second charge

`TransactionLedger.record(...)` is only called on a **terminal** result, so a sale
is never persisted on any fail-closed branch.

## 6. Risk Engine — `SandboxRiskService`

Baseline score 5, additive weights:

| Signal | Weight |
|---|---|
| `VELOCITY_HIGH` (≥20 tx/h) | +25 |
| `VELOCITY_DAILY` (≥150 tx/day) | +15 |
| `DECLINE_BURST` (≥3 declines/h) | +20 |
| `NEW_DEVICE` | +15 |
| `OVERSEAS` | +10 |
| `ODD_HOURS` (23:00–05:59) | +10 |
| `DEVICE_LOW_TRUST` (<50) | +20 |

Decision bands: `score < 30 → ALLOW`, `30..70 → CHALLENGE`, `> 70 → BLOCK`.
**Hard fail-closed:** `deviceTrustScore < 20 → BLOCK` (`DEVICE_UNTRUSTED`)
regardless of additive score.

## 7. Transport Security

- `HttpClient` requires `https://`; sets `Authorization: Bearer`, `X-AuthePay-Client`,
  `X-AuthePay-Correlation-Id`. Bearer token read from `SecureStorage` per request.
- Release build: `ALLOW_CLEARTEXT=false` (enforced in `network_security_config.xml`
  + `buildConfigField`).

## 8. Known Security Gaps (must close before production)

- **No EMV/MPoC kernel** — `NfcReaderProvider` returns `CERTIFIED_KERNEL_REQUIRED`.
- **No real acquirer** — `ServiceLocator` throws in release until partner SDK added.
- **Client attestation** (`Play Integrity`/`SafetyNet`) is a hint only; server must enforce.
- **Persistent ledger** not implemented (in-memory) — acceptable for masking posture
  but a Room-backed, masked-only store is the production target.

See `THREAT_MODEL.md`, `PRIVACY_AND_SECURITY.md`, `DATA_RETENTION.md`.
