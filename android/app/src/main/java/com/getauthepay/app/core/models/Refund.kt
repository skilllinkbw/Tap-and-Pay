package com.getauthepay.app.core.models

/**
 * A refund issued against an approved [Transaction]. The lifecycle:
 *
 *   REFUND_PENDING   — request submitted, awaiting processor confirmation
 *   REFUNDED         — full refund completed
 *   PARTIAL_REFUND   — partial refund completed (refundAmount < original)
 *   REFUND_FAILED    — processor rejected the refund
 *
 * Refunds require elevated merchant permission (SUPERVISOR or above) and
 * trigger a biometric prompt before submission.
 */
data class Refund(
    val refundId: String,
    val transactionId: String,
    val merchantId: String,
    val amount: Money,
    val status: RefundStatus,
    val reasonCode: String,
    val reasonNote: String? = null,
    val createdAtMs: Long,
    val completedAtMs: Long? = null,
    val processorReference: String? = null,
    val correlationId: String? = null,
    val requestedByUserId: String,
) {
    init {
        require(amount.isPositive() || amount.isZero()) { "Refund amount must be >= 0" }
        require(reasonCode.isNotBlank()) { "reasonCode must not be blank" }
        require(refundId.isNotBlank()) { "refundId must not be blank" }
    }
}

enum class RefundStatus {
    REFUND_PENDING,
    REFUNDED,
    PARTIAL_REFUND,
    REFUND_FAILED,
}