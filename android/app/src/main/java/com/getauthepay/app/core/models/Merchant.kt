package com.getauthepay.app.core.models

/**
 * The merchant account. Mirrors what the server returns after
 * onboarding/verification; no fields here are ever editable on-device
 * (settlement bank details etc. are server-side only).
 *
 * @property status Verification lifecycle:
 *   - PENDING        : submitted, awaiting review
 *   - UNDER_REVIEW   : documents received, in active review
 *   - VERIFIED       : KYC/business verification complete
 *   - ACTIVE         : may accept live transactions
 *   - SUSPENDED      : temporarily disabled
 *   - REJECTED       : verification denied
 */
data class Merchant(
    val merchantId: String,
    val businessName: String,
    val tradingName: String? = null,
    val businessType: BusinessType,
    val country: String,
    val contactEmail: String,
    val contactPhone: String,
    val role: MerchantRole,
    val status: MerchantStatus,
    val createdAtMs: Long,
    val verifiedAtMs: Long? = null,
    val suspendedAtMs: Long? = null,
    val settlementCurrency: String = "BWP",
)

enum class BusinessType {
    SOLE_TRADER,
    PARTNERSHIP,
    PRIVATE_COMPANY,
    PUBLIC_COMPANY,
    NON_PROFIT,
    OTHER;

    companion object {
        fun fromWire(raw: String?): BusinessType = when (raw?.uppercase()) {
            "SOLE_TRADER", "SOLE" -> SOLE_TRADER
            "PARTNERSHIP" -> PARTNERSHIP
            "PRIVATE_COMPANY", "PTY_LTD", "LTD" -> PRIVATE_COMPANY
            "PUBLIC_COMPANY", "PLC" -> PUBLIC_COMPANY
            "NON_PROFIT", "NGO" -> NON_PROFIT
            else -> OTHER
        }
    }
}

enum class MerchantStatus {
    PENDING,
    UNDER_REVIEW,
    VERIFIED,
    ACTIVE,
    SUSPENDED,
    REJECTED;

    fun canAcceptPayments(): Boolean = this == ACTIVE
    fun canAccessDashboard(): Boolean = this != REJECTED
}