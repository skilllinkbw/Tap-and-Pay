# AuthePay — API Documentation

Client: `AuthePayApiClient` over `HttpClient` (a minimal `HttpURLConnection`-based
HTTPS client — no OkHttp/Retrofit dependency).

## 1. HTTP Client (`HttpClient`)

- Constructor: `HttpClient(baseUrl, sessionTokenProvider, correlationIdProvider,
  connectTimeoutMs=15000, readTimeoutMs=20000)`.
- **`baseUrl` MUST start with `https://`** — otherwise the constructor throws
  ("HTTP base URL must use https://"). Cleartext is rejected outright.
- Default `SSLSocketFactory` for `HttpsURLConnection`.
- Per-request headers:
  - `Authorization: Bearer <token>` (token from `SecureStorage`, read per request)
  - `Accept: application/json`
  - `User-Agent: AuthePay-Android/1.0`
  - `X-AuthePay-Client: android-merchant`
  - `X-AuthePay-Correlation-Id: <uuid>`
- Methods: `get`, `post(body: JSONObject?)`, `put`, `delete`.
- `HttpResponse(code, body, headers)` with `jsonBody(): JSONObject?`.
- Exceptions: `ApiException`, `NetworkUnavailable`, `UnauthorizedException`,
  `ForbiddenException`, `NotFoundException`, `RateLimitedException`, `ServerException`.

## 2. Endpoints (observed in `AuthePayApiClient`)

| Method | Path | Client method | Notes |
|---|---|---|---|
| POST | `v1/auth/otp/request` | `requestOtp` | Returns `OtpRequestOutcome` (SENT/RATE_LIMITED/FAILED) |
| POST | `v1/auth/otp/verify` | `verifyOtp` | Returns `OtpVerifyResult.Success(token, expiresAtMs, merchant)` / `Failed(reason)` |
| POST | `v1/auth/logout` | `logout` | |
| GET | `v1/merchant/me` | `getCurrentMerchant` | |
| POST | `v1/merchant/onboarding` | `submitOnboarding` | Body = onboarding draft JSON |
| GET | `v1/merchant/alerts` | `listAlerts` | |
| POST | `v1/merchant/alerts/$id/ack` | `acknowledgeAlert` | Server-side audit; client cannot self-approve |
| POST | `v1/payments` | `submitPayment` | |
| GET | `v1/transactions` | `listTransactions` | |
| GET | `v1/transactions/$id` | `getTransaction` | |
| GET | `v1/transactions/$id/receipt` | `getReceipt` | Returns `JSONObject?` |
| GET | `v1/refunds` | `createRefund` / `getRefund` | |
| GET | `v1/settlements` | `listSettlements` | |
| GET | `v1/devices` | `listDevices` | |
| POST/PUT | `v1/devices/$id` | `revokeDevice` / `renameDevice` | |

## 3. DTO Mapping (`ApiDtos`)

`internal object ApiDtos` maps wire JSON → domain:
- `transactionFromJson`, `riskFromJson`, `refundFromJson`, `settlementFromJson`,
  `alertFromJson`.
- `mapServerStatus(raw)` → `PaymentStatus` (APPROVED/DECLINED/REVERSED/REFUNDED →
  else FAILED).
- Mappers read **only `maskedPan`**; never PAN/CVV/PIN. Amounts default to `BWP`
  when currency missing.

## 4. Auth Model

- Bearer token stored in `SecureStorage` (`KEY_SESSION_TOKEN`, `KEY_SESSION_EXPIRES_AT`).
- `SessionManager.isActive` = token non-blank AND (no expiry OR not expired).
- **No service-role / acquirer secrets are stored on device.**

## 5. Correlation & Audit

- Every request carries `X-AuthePay-Correlation-Id`; `PaymentResult.correlationId`
  threads the same id through engine → ledger → receipt for traceability.

## 6. Error Handling

- 401 → `UnauthorizedException` (UI triggers re-auth).
- 403 → `ForbiddenException` (role/permission).
- 429 → `RateLimitedException` (OTP rate limit surfaced to UI).
- 5xx → `ServerException`.

See `API_CONTRACT.md` for request/response shapes and the PCI field rules.
