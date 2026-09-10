package com.getauthepay.app.core.models

/**
 * A daily or batch settlement batch that the acquirer pays out to the
 * merchant's settlement account.
 *
 * Settlements are always server-derived — the app MUST NOT fabricate
 * amounts, dates, or statuses. Until the backend confirms a batch, the
 * UI shows "No settlements yet" rather than guess at totals.
 */
data class Settlement(
    val settlementId: String,
    val merchantId: String,
    val periodStartMs: Long,
    val periodEndMs: Long,
    val grossAmount: Money,
    val feeAmount: Money,
    val netAmount: Money,
    val transactionCount: Int,
    val status: SettlementStatus,
    val settledAtMs: Long? = null,
    val bankReference: String? = null,
)

enum class SettlementStatus {
    PENDING,
    SETTLED,
    FAILED,
}