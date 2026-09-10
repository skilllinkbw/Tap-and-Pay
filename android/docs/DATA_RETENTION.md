# AuthePay — Data Retention

## 1. Data Minimisation Posture

The app is designed to **hold no cardholder data (CHD)** at any time:
no full PAN, CVV, PIN, or track data is constructed, stored, or transmitted.

## 2. What Is Retained On-Device

| Data | Store | Lifetime | Sensitive? |
|---|---|---|---|
| Session token + expiry | `SecureStorage` (EncryptedSharedPreferences) | Until logout / expiry | Yes (bearer) |
| Merchant id, terminal id, user role | `SecureStorage` | Session | Low |
| Preferred currency, biometric-refund flag | `SecureStorage` | Persistent | Low |
| Onboarding draft (in progress) | `SecureStorage` | Until submitted/cancelled | Low |
| Transaction ledger | **In-memory** (`TransactionLedger`) | App process; re-fetched from server on cold start | **Masked only** |
| Android Keystore crypto key | `AndroidKeyStore` | Until `wipe()` | High (non-exportable) |

The ledger carries only `maskedPan` + brand + auth code + references. Because it
is in-memory, **no CHD reference reaches the filesystem**.

## 3. What Is Never Retained

- Full PAN / PAN slice beyond first6/last4
- CVV, PIN, PIN block
- Track 1/2 data
- OTP values (after verification)
- Acquirer / processor credentials (none exist client-side)

## 4. Server-Side Retention (app perspective)

- The authoritative ledger, settlements, refunds, alerts, and audit events live
  server-side. The app fetches and reconciles; it does **not** own retention
  policy for those. Retention periods are governed by the AuthePay backend and the
  acquirer/PCI expectations — out of scope for the client.

## 5. Deletion / Wipe

- `SecurityManager.wipe()` deletes the Keystore entry (logout / device revoke).
- `SessionManager.end()` clears the in-memory session.
- `TransactionLedger.clear()` is available but normally the ledger simply does not
  survive process death.
- `SecureStorage.clear()` removes all encrypted prefs.

## 6. Compliance Notes

- Masked-PAN-only local storage supports PCI DSS "minimise stored account data".
- A future Room-backed ledger (if added) **must** store only masked/tokenised
  references — enforced by design, not by the current in-memory impl.

See `PRIVACY_AND_SECURITY.md`, `SECURITY_ARCHITECTURE.md`.
