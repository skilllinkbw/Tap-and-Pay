package com.getauthepay.app.core.models

import java.math.BigDecimal
import java.util.UUID

/**
 * A merchant-initiated payment acceptance request.
 *
 * @property idempotencyKey Used by the processor to deduplicate accidental
 *   double-submissions. Must be stable per logical attempt; the engine
 *   reuses the same key if the merchant presses "Pay" twice within the
 *   same window.
 * @property requestId Unique identifier for this request instance, used
 *   for correlating engine events with processor responses.
 * @property amount Must be strictly positive.
 * @property currency Currency of [amount] as a 3-letter ISO 4217 code.
 * @property reference Optional merchant-supplied reference (invoice, PO, etc.).
 * @property description Optional human-readable description shown on the receipt.
 * @property timestampMs Creation timestamp, in epoch milliseconds.
 * @property merchantId Resolved merchant identity; validated server-side.
 * @property terminalId Resolved device/terminal identity; bound to the
 *   device's secure keystore when the merchant enrols the device.
 */
data class PaymentRequest(
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val requestId: String = UUID.randomUUID().toString(),
    val amount: BigDecimal,
    val currency: String = "BWP",
    val reference: String? = null,
    val description: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val merchantId: String? = null,
    val terminalId: String? = null,
    /** Sandbox-only scenario hint. Production providers MUST ignore this field. */
    val sandboxScenario: TestScenario? = null,
) {
    init {
        require(amount.signum() > 0) { "PaymentRequest amount must be strictly positive (was $amount)" }
        require(currency.length == 3) { "Currency must be ISO 4217 3-letter (was '$currency')" }
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(requestId.isNotBlank()) { "requestId must not be blank" }
    }

    fun toMoney(): Money = Money.of(amount, currency)
}