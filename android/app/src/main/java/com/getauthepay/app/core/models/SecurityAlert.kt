package com.getauthepay.app.core.models

/**
 * A server-originated security alert that the merchant should review.
 * Surface examples:
 *   - suspicious login
 *   - new device enrolled
 *   - repeated failed OTP
 *   - unusual payment velocity
 *   - unusual refund activity
 *   - risk-engine decline
 *   - account compromise indicator
 */
data class SecurityAlert(
    val alertId: String,
    val merchantId: String,
    val severity: Severity,
    val category: Category,
    val title: String,
    val message: String,
    val raisedAtMs: Long,
    val acknowledgedAtMs: Long? = null,
    val resolvedAtMs: Long? = null,
    val actionUrl: String? = null,
) {
    enum class Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }
    enum class Category {
        SUSPICIOUS_LOGIN,
        NEW_DEVICE,
        FAILED_OTP,
        PAYMENT_VELOCITY,
        REFUND_ACTIVITY,
        RISK_DECLINE,
        ACCOUNT_COMPROMISE,
        OTHER,
    }

    val isOpen: Boolean get() = resolvedAtMs == null
}