package com.getauthepay.app.core.models

/**
 * A persisted transaction record. Derived from a successful [PaymentResult]
 * and stored in the in-app ledger (mirrored server-side).
 *
 * The ledger entry is the source of truth for transaction history, refunds,
 * settlements, and audit. Fields are immutable; transitions happen by
 * creating a new [TransactionStatus] and a new audit event.
 */
data class Transaction(
    val transactionId: String,
    val merchantId: String,
    val terminalId: String,
    val amount: Money,
    val status: TransactionStatus,
    val createdAtMs: Long,
    val completedAtMs: Long? = null,
    val maskedPan: String? = null,
    val cardType: String? = null,
    val authCode: String? = null,
    val processorReference: String? = null,
    val reference: String? = null,
    val description: String? = null,
    val correlationId: String? = null,
    val riskDecision: RiskDecision? = null,
    val refundedAmount: Money? = null,
    val settlementId: String? = null,
)

/** Coarser transaction status for list rendering (groups payment lifecycle states). */
enum class TransactionStatus {
    /** Authorised and not yet refunded or reversed. */
    APPROVED,
    /** Refused by issuer or risk. */
    DECLINED,
    /** Cancelled by merchant, timed out, or failed before authorisation. */
    NOT_COMPLETED,
    /** Approved then reversed (e.g. chargeback). */
    REVERSED,
    /** Approved, fully refunded. */
    REFUNDED,
    /** Approved, partially refunded. */
    PARTIALLY_REFUNDED,
    /** Refund is in flight against this transaction. */
    REFUND_PENDING;
}