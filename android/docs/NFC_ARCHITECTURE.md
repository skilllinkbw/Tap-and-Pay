# AuthePay — NFC / Contactless Architecture

## 1. The Seam

```kotlin
interface ContactlessPaymentProvider {
    val availability: NfcAvailability
    fun startReading(request: PaymentRequest): Flow<PaymentResult>
    suspend fun cancelReading()
}

sealed interface NfcAvailability {
    data object AvailableEnabled
    data object AvailableDisabled
    data object NotAvailable
}
```

The engine talks only to this interface. Two implementations exist:

- **`SandboxContactlessProvider`** — deterministic simulator (debug/sandbox).
- **`NfcReaderProvider`** — production Android reader-mode (release).

## 2. `SandboxContactlessProvider`

- Emits `CREATED → READY_FOR_TAP → CARD_DETECTED → PROCESSING → AUTHORIZING`.
- Stops at `AUTHORIZING` and hands off to the (sandbox) processor.
- **Never connected to a real card network.** Used for demos, tests, and
  local development.

## 3. `NfcReaderProvider` (production reader-mode)

- Uses Android `NfcAdapter` reader mode with
  `FLAG_READER_NFC_A | FLAG_READER_NFC_B | FLAG_READER_NFC_F | FLAG_READER_NFC_V`
  and `SKIP_NDEF_CHECK`.
- Detects an ISO 14443 application and would begin a tap.
- **Critically: it does NOT implement a payment kernel.** The moment a card
  application is selected, the EMV transaction must be handed to an approved
  MPoC / SoftPOS SDK. Until that integration exists, the provider emits:
  ```
  FAILED(CERTIFIED_KERNEL_REQUIRED)
  ```
- `NfcReaderProvider.availability` reports `AvailableEnabled`, `AvailableDisabled`,
  or `NotAvailable`; the engine fails closed if NFC is unavailable/disabled.

## 4. Why There Is No Kernel

A contactless EMV transaction requires a PCI-CP / MPoC certified kernel. Bundling
or faking one is out of scope and a compliance liability. AuthePay therefore:

1. Surfaces `CERTIFIED_KERNEL_REQUIRED` rather than pretending to process.
2. Expects the acquiring partner to supply the certified kernel/SDK, wired into
   `ServiceLocator` (replacing `NfcReaderProvider` or wrapping it).

## 5. Integration Contract for the Partner Kernel

A production contactless provider must:
- Expose `startReading(request): Flow<PaymentResult>` honouring the same states.
- Emit `AUTHORIZING` only after the certified kernel confirms card engagement.
- Never expose PAN/CVV/PIN/track to app code — return only a token / maskedPan.
- Fail closed on any kernel error (engine already does this at the processor step).

## 6. Physical NFC Status

**Not tested on hardware.** No live device tap has been performed in this build.
All contactless behaviour is exercised via `SandboxContactlessProvider` and unit
tests. Live contactless acceptance requires the certified kernel + a test card in
a lab environment (see `CERTIFICATION_READINESS.md`, `BANK_DEMO_CHECKLIST.md`).

See `PAYMENT_DATA_FLOW.md`, `THREAT_MODEL.md` (T11).
