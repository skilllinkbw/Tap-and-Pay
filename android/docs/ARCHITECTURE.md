# AuthePay — Architecture

## 1. Layering

```
UI (Jetpack Compose)                app/src/main/java/.../ui/**
  └─ ViewModels (StateFlow)          ui/**/*ViewModel.kt
       └─ ServiceLocator (DI)        di/ServiceLocator.kt
            ├─ PaymentAcceptanceEngine   core/payment/**
            │    ├─ ContactlessPaymentProvider   core/nfc/**
            │    ├─ PaymentProcessor            core/payment/**
            │    ├─ RiskService                 core/risk/**
            │    ├─ IdempotencyStore            core/payment/**
            │    └─ TransactionLedger           core/ledger/**
            ├─ MerchantRepository              data/MerchantRepository.kt
            ├─ AuthePayApiClient                network/**
            ├─ SecureStorage / SessionManager   data/**
            └─ SecurityManager                  security/**
```

The core domain (`core/**`) is **Android-free** and **unit-testable on the JVM**.
This is intentional: the payment state machine, risk engine, ledger, and models
have zero `android.*` dependencies and are exercised by 200+ JVM tests.

## 2. Key Modules

| Module | Path | Responsibility |
|---|---|---|
| `PaymentAcceptanceEngine` | `core/payment` | Single orchestration entry point; emits a `Flow<PaymentResult>`; **fails closed** |
| `PaymentStateMachine` | `core/payment` | Deterministic 12-state lifecycle |
| `PaymentProcessor` | `core/payment` | Credential-free network boundary to acquirer (interface) |
| `SandboxPaymentProcessor` | `core/payment` | Deterministic simulator; `isSandbox=true` |
| `IdempotencyStore` | `core/payment` | In-memory single-flight dedupe |
| `RiskService` / `SandboxRiskService` | `core/risk` | Additive rules scoring |
| `ContactlessPaymentProvider` | `core/nfc` | Seam to contactless transport |
| `NfcReaderProvider` | `core/nfc` | Production reader-mode; **no EMV kernel** |
| `SandboxContactlessProvider` | `core/nfc` | Deterministic tap simulator |
| `TransactionLedger` | `core/ledger` | In-memory source of truth; masked-PAN only |
| `TokenizationService` | `core/tokenization` | Opaque token → processor reference (interface) |
| `ReceiptService` | `core/receipt` | Masked-PAN receipt rendering |
| `CurrencyCatalog` | `core/currency` | Supported currencies (Africa-first) |
| `Validators` | `core/validation` | Pure amount/currency/reference/OTP-shape checks |
| `SecureLogger` | `core/log` | Redacting logger |
| `SecurityManager` | `security` | AES-GCM at-rest via Android Keystore |
| `DeviceSecurityChecker` | `security` | Client-side integrity hints (non-authoritative) |
| `BiometricGate` | `security` | `BiometricPrompt` wrapper |
| `ScreenshotProtector` | `security` | `FLAG_SECURE` toggle |
| `SecureStorage` | `data` | `EncryptedSharedPreferences` (Android Keystore master key) |
| `SessionManager` | `data` | Active session state |
| `MerchantRepository` | `data` | Dashboard read-model (server-authoritative) |
| `AuthePayApiClient` / `HttpClient` | `network` | HTTPS REST client; **https-only**; bearer injected |

## 3. Dependency Injection

`ServiceLocator` is a manual, `by lazy` container (no Dagger/Hilt). It is the
single place that decides **sandbox vs. production** wiring:

- `riskService` is always `SandboxRiskService()`.
- `processor = if (BuildConfig.SANDBOX_PAYMENTS) SandboxPaymentProcessor() else error(...)`
- `contactlessProvider = if (BuildConfig.SANDBOX_PAYMENTS) SandboxContactlessProvider() else NfcReaderProvider(...)`

A production build therefore **refuses to construct a payment engine** until the
acquiring partner supplies the real `PaymentProcessor` and (for live contactless)
an approved MPoC/SoftPOS SDK. This is by design — it makes an unintentionally
shipped sandbox impossible.

## 4. Data Flow (high level)

1. UI → `AcceptPaymentViewModel` builds a `PaymentRequest` and calls
   `PaymentAcceptanceEngine.processPayment(...)`.
2. Engine runs: idempotency check → risk → contactless → processor → ledger.
3. Each transition is emitted as a `PaymentResult` via `StateFlow` to the UI.
4. On terminal success, `TransactionLedger.record(...)` stores the masked
   transaction; `ReceiptService` renders the receipt.
5. `MerchantRepository` reconciles local ledger with the server on refresh.

## 5. Persistence Posture

- **No cardholder data is persisted** (no PAN/CVV/PIN/track anywhere on disk).
- `TransactionLedger` is in-memory; re-fetched from server on cold start.
- `SecureStorage` (`EncryptedSharedPreferences`) holds only session/role/preference
  values, never secrets of cardholders.
- `SecurityManager` keeps an AES-256-GCM key in the Android Keystore.

## 6. Testing Architecture

- JVM unit tests: `app/src/test/**` (JUnit 4 + `kotlinx-coroutines-test`).
- `SecurityConfigurationTest` asserts the release build config contract.
- UI is covered by Compose previews; instrumented (`androidTest`) hooks exist but
  no device-farm runs are configured in CI yet.

See `TEST_PLAN.md` and `TEST_RESULTS.md`.
