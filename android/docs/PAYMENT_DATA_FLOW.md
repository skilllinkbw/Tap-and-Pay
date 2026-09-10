# AuthePay — Payment Data Flow

## 1. End-to-End Sequence

```
[UI] AcceptPaymentViewModel
  │  build PaymentRequest(amount, currency, reference, sandboxScenario?)
  ▼
[Engine] PaymentAcceptanceEngine.processPayment(request, riskContext)
  │   emits Flow<PaymentResult> through each phase:
  │
  ├─(0) NFC pre-flight ............ availability check → FAILED if N/A or disabled
  ├─(1) IdempotencyStore.begin(key)  single-flight; replay cached if present
  ├─(2) RiskService.evaluate(ctx)    ── exception ⇒ FAILED(RISK_ENGINE_UNAVAILABLE)
  │                                ── BLOCK    ⇒ DECLINED(RISK_BLOCKED)
  ├─(3) ContactlessPaymentProvider.startReading
  │        CREATED→READY_FOR_TAP→CARD_DETECTED→PROCESSING→AUTHORIZING
  ├─(4) PaymentProcessor.authorise(...)  ── exception ⇒ FAILED(PROCESSOR_UNAVAILABLE)
  └─(5) finish(result)
        terminal ⇒ TransactionLedger.record(...) + ReceiptService.build(...)
        non-terminal ⇒ TRANSPORT_ENDED / discard idempotency
  ▼
[UI] collects Flow, renders states, shows receipt on APPROVED
```

## 2. Data Carried at Each Stage

| Stage | Fields present | Cardholder data? |
|---|---|---|
| `PaymentRequest` | amount, currency, reference, idempotencyKey, requestId, sandboxScenario | None |
| `RiskContext` | counts, deviceTrustScore, newDevice, isOverseas, hour | None |
| `Contactless` (sandbox) | simulated states only | None |
| `PaymentResult` (success) | transactionId, authCode, **maskedPan** (first6/last4), cardType, processorReference, correlationId | **Masked only** |
| `Transaction` (ledger) | maskedPan, cardType, authCode, refs | **Masked only** |
| `Receipt` | maskedPan, amount, authCode, transactionId | **Masked only** |

**At no point does a full PAN, CVV, PIN, or track exist in app memory or storage.**

## 3. The 12-State Machine (`PaymentStateMachine`)

States: `CREATED, READY_FOR_TAP, CARD_DETECTED, PROCESSING, AUTHORIZING,
APPROVED, DECLINED, CANCELLED, TIMEOUT, FAILED, REVERSED, REFUNDED`.

Allowed transitions (terminal states have no outgoing edges except the two
explicit reverse/refund exits from `APPROVED`):

```
CREATED            → READY_FOR_TAP | CANCELLED
READY_FOR_TAP      → CARD_DETECTED | CANCELLED | TIMEOUT
CARD_DETECTED      → PROCESSING | CANCELLED | TIMEOUT | FAILED
PROCESSING         → AUTHORIZING | FAILED | TIMEOUT
AUTHORIZING        → APPROVED | DECLINED | FAILED | TIMEOUT
APPROVED           → REVERSED | REFUNDED
(all terminal)     → (none)
```

`isTerminal()` is true for `APPROVED, DECLINED, CANCELLED, TIMEOUT, FAILED,
REVERSED, REFUNDED`. `isAllowed` permits exactly `APPROVED→REVERSED` and
`APPROVED→REFUNDED` as the only exits from a terminal `APPROVED`.

## 4. Idempotency

`IdempotencyStore<PaymentResult>` (in-memory, `ConcurrentHashMap`):
- `begin(key)` → `true` if new (single-flight), `false` if in-flight/done.
- On terminal result, `complete(key, result)` caches it.
- Next attempt with same key **replays** the cached terminal result (no second charge).
- Cleared on app restart; the **server processor is the authoritative** deduper.

`PaymentAcceptanceEngine.ReferenceIds.transaction(seed)` derives an
`ATX-XXXXXXXX` reference deterministically (no card data, no secrets).

## 5. Failure Semantics (fail closed)

- A FAILED/DECLINED/CANCELLED/TIMEOUT result is still recorded (as a non-success
  `Transaction`) for audit, but **no sale is booked**.
- `FAILED(PROCESSOR_UNAVAILABLE)` explicitly means *"No money has been taken."*
- `TRANSPORT_ENDED` before `AUTHORIZING` discards the idempotency key and records
  nothing as a sale.

## 6. Refund Flow

`Refund` requires `SUPERVISOR+` (`MerchantRole.canIssueRefunds()`) and a biometric
prompt (`BiometricGate`). `MerchantRepository.applyRefundLocally`/`markRefundPendingLocally`
update the ledger optimistically; the server is authoritative for final status.

See `ARCHITECTURE.md`, `SECURITY_ARCHITECTURE.md`, `NFC_ARCHITECTURE.md`.
