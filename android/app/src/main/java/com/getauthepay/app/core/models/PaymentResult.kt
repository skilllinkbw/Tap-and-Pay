package com.getauthepay.app.core.models

/**
 * The outcome of a payment attempt, emitted by the engine and consumed by
 * the UI / ledger / receipt services.
 *
 * IMPORTANT — PCI / sensitive data contract:
 *  - [maskedPan] is the only card-related field permitted here. It must
 *    hold at most the first six and last four digits, all other digits
 *    masked (e.g. "412345******1234"). Never populate the full PAN, CVV,
 *    PIN, or track data.
 *  - [cardType] is a coarse label (e.g. "VISA", "MASTERCARD") used only
 *    for receipt rendering.
 *
 * @property requestId The PaymentRequest this result corresponds to.
 * @property status The terminal/progress state of the attempt.
 * @property transactionId Assigned by the processor when the request was
 *   authorised. Null for non-success states.
 * @property authCode Issuer authorisation code, when supplied.
 * @property maskedPan Coarse card fingerprint, only for authorised receipts.
 * @property cardType Coarse card brand label.
 * @property processorReference Acquirer/processor reference (RR-number, etc.).
 * @property correlationId End-to-end correlation identifier for support.
 * @property errorCode Machine-readable error code (e.g. "INSUFFICIENT_FUNDS").
 * @property errorMessage Human-readable error message. Must NOT include
 *   card numbers, PINs, or OTP values.
 * @property riskDecision The risk decision captured before authorisation.
 */
data class PaymentResult(
    val requestId: String,
    val status: PaymentStatus,
    val transactionId: String? = null,
    val authCode: String? = null,
    val maskedPan: String? = null,
    val cardType: String? = null,
    val processorReference: String? = null,
    /** Merchant's own reference (invoice/order number), echoed back for receipts. */
    val reference: String? = null,
    val correlationId: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val riskDecision: RiskDecision? = null,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    init {
        require(requestId.isNotBlank()) { "requestId must not be blank" }
        if (status.isSuccess()) {
            require(!transactionId.isNullOrBlank() || !processorReference.isNullOrBlank()) {
                "Successful PaymentResult must carry a transactionId or processorReference"
            }
        }
        maskedPan?.let { assertMaskedPanFormat(it) }
    }

    companion object {
        /** Builds a sandbox/dev-only masked PAN, asserting it is already masked. */
        fun sandboxMaskedPan(): String = "412345******1234"

        private val PAN_DIGITS = Regex("""\d""")

        private fun assertMaskedPanFormat(pan: String) {
            require(pan.contains('*')) {
                "maskedPan must contain mask characters (got '$pan')"
            }
            val digitCount = PAN_DIGITS.findAll(pan).count()
            require(digitCount in 4..10) {
                "maskedPan must keep between 4 and 10 digits visible (got $digitCount in '$pan')"
            }
        }
    }
}