# AuthePay — Test Plan

## 1. Strategy

The core domain (`core/**`) is **Android-free** and unit-tested on the JVM with
JUnit 4 + `kotlinx-coroutines-test` (`runTest`). UI is covered by Compose
previews; instrumented (`androidTest`) hooks exist but no device-farm CI is
configured yet.

Run: `./gradlew test` (module `app`).

## 2. Suites (current)

| Test file | Area | Coverage |
|---|---|---|
| `SecurityConfigurationTest` | Build contract | Asserts release `API_BASE_URL=https://api.authepay.co.bw`, `SANDBOX_PAYMENTS=false`, `TEST_OTP_ENABLED=false`, `ALLOW_CLEARTEXT=false`, `PRODUCTION_BUILD=true` |
| `PaymentAcceptanceEngineTest` | Orchestration | Fail-closed paths: risk down, NFC off, processor throws, idempotency replay, transport-ended → no sale recorded |
| `PaymentStateMachineTest` | State machine | 12-state transitions; `APPROVED→REVERSED/REFUNDED`; terminal immutability; regression: reversed/refunded cannot be re-exited |
| `SandboxRiskServiceTest` | Risk | Scoring weights, bands, `DEVICE_UNTRUSTED` hard-block (<20); regression: trust==20 not auto-blocked |
| `TransactionLedgerTest` | Ledger | Records only terminal with id; blank id not invented |
| `PaymentResultTest` | PCI contract | maskedPan 4..10 digits, contains `*`, success requires id/ref |
| `RefundTest` | Refunds | Amount/role invariants |
| `IdempotencyStoreTest` | Dedup | single-flight, replay, discard |
| `ValidatorsTest` | Input | amount/currency/reference/OTP-shape |
| `MoneyTest` | Money | arithmetic/compare/format |
| `MerchantRoleTest` | RBAC | permission levels |
| `CurrencyCatalogTest` | Currencies | supported set, default BWP |
| `SecureLoggerTest` | Logging | redaction of PAN/CVV/PIN/OTP/token |

## 3. Edge Cases Covered / Recommended

Already covered: idempotency replay, risk-engine-down, processor exception,
NFC unavailable, transport-ended, device trust hard-block, masked-PAN contract.

**Additional edge cases to add** (suggested, from QA review):
1. `PaymentAcceptanceEngine` — amount exactly at `Validators.MAX_AMOUNT`
   (250,000.00 BWP): ensure it still routes to processor and not silently capped.
2. `SandboxRiskService` — boundary `score == 30` and `score == 70` map to the
   exact intended band (ALLOW vs CHALLENGE, CHALLENGE vs BLOCK).
3. `TransactionLedger` — concurrent `record` + `applyRefund` under load (Mutex
   guarantees serialisation; add a stress test with `kotlinx.atomicfu`/repeat).

## 4. Re-run Only Failed Tests (local)

After a failure, target the specific class:
```
./gradlew testDebugUnitTest --tests "com.getauthepay.app.core.risk.SandboxRiskServiceTest"
./gradlew testDebugUnitTest --tests "com.getauthepay.app.core.PaymentAcceptanceEngineTest"
./gradlew testDebugUnitTest --tests "com.getauthepay.app.SecurityConfigurationTest"
```

## 5. Verification Gates (directive §58)

`./gradlew clean`, `test`, `lint`, `assembleDebug`, `assembleRelease`.
**Errors are not suppressed to obtain a green build.** See `TEST_RESULTS.md`
for the latest run, and `STATUS_REPORT.md` for the honest build state.

See `ARCHITECTURE.md`, `SECURITY_ARCHITECTURE.md`.
