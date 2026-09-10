# AuthePay — API Contract

This document is the **client-side contract** the app expects from the AuthePay
backend. It is not the server spec; it documents what the app sends and what it
accepts, with the PCI field rules that the app enforces locally.

## 1. Transport & Auth

- **TLS only.** Base URL must be `https://`. Cleartext is rejected by `HttpClient`.
- **Auth:** `Authorization: Bearer <sessionToken>` on every request.
- **Client id:** `X-AuthePay-Client: android-merchant`.
- **Correlation:** `X-AuthePay-Correlation-Id` (UUID) per request.

## 2. PCI Field Rules (enforced client-side)

| Field | Rule |
|---|---|
| Full PAN | **NEVER** sent or stored by the app |
| CVV / PIN / track | **NEVER** sent or stored by the app |
| `maskedPan` | Only permitted card field. Must contain `*` and have **4..10 digits** (first6/last4). Enforced by `PaymentResult.init` |
| `cardType` | Coarse brand label only (e.g. "VISA") for receipts |
| Success | `PaymentResult` requires a non-blank `transactionId` **or** `processorReference` |

`SecureLogger` redacts PAN/CVV/PIN/OTP/token/key patterns; `assertSafe` throws if
any appear.

## 3. Authentication

### Request OTP — `POST v1/auth/otp/request`
Request: `{ "identifier": "<phone|email>" }`
Response (app consumes): outcome `SENT | RATE_LIMITED | FAILED`.

### Verify OTP — `POST v1/auth/otp/verify`
Request: `{ "identifier": "...", "code": "123456" }`
Response (app consumes): `Success(token, expiresAtMs, merchant)` / `Failed(reason)`.
Debug builds allow `TEST_OTP_ENABLED` shortcut; release does **not**.

## 4. Payment Submission — `POST v1/payments`
App sends (no CHD):
```json
{
  "requestId": "uuid",
  "idempotencyKey": "uuid",
  "amount": "123.45",
  "currency": "BWP",
  "reference": "optional",
  "merchantId": "m_...",
  "terminalId": "m_...-T1",
  "riskDecision": { "score": 12, "decision": "ALLOW", "reasonCodes": [], "modelVersion": "rules.v1" }
}
```
`sandboxScenario` is a sandbox-only hint; **production MUST ignore it.**

Server response (app accepts, masked only):
```json
{
  "transactionId": "ATX-...",
  "status": "APPROVED",
  "authCode": "SBX123456",
  "maskedPan": "412345******1234",
  "cardType": "VISA",
  "processorReference": "REF...",
  "correlationId": "uuid"
}
```

## 5. Transactions & Receipts

- `GET v1/transactions` → list of `Transaction` (maskedPan only).
- `GET v1/transactions/{id}` → single `Transaction`.
- `GET v1/transactions/{id}/receipt` → receipt JSON (maskedPan only).

## 6. Refunds — `POST v1/refunds`

App requires `SUPERVISOR+` role and a biometric prompt before calling.
Request: `{ "transactionId": "...", "amount": "10.00", "reasonCode": "...", "requestedByUserId": "..." }`
Server is authoritative for `REFUNDED / PARTIAL_REFUND / REFUND_FAILED`.

## 7. Settlements — `GET v1/settlements`

App renders server-derived `Settlement` (gross/fee/net, count, status, bankReference).
**The app MUST NOT fabricate settlement figures.**

## 8. Devices & Alerts

- `GET v1/devices` → enrolled device list; `revoke`/`rename` mutate server-side.
- `GET v1/merchant/alerts` → `SecurityAlert` list; `ack` is a server action so the
  audit trail cannot be forged by a compromised client.

## 9. Idempotency

- `idempotencyKey` is sent on payment submission. The app also de-dupes locally
  via `IdempotencyStore`; the **server is the authoritative** deduplicator.

See `API_DOCUMENTATION.md`, `PRIVACY_AND_SECURITY.md`.
