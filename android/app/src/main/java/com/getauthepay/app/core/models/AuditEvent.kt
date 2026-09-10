package com.getauthepay.app.core.models

/**
 * A server-side audit event. The Android app records the same event
 * locally and POSTs it to the audit endpoint.
 */
data class AuditEvent(
    val eventId: String,
    val occurredAtMs: Long,
    val actorUserId: String,
    val merchantId: String,
    val deviceId: String?,
    val action: AuditAction,
    val correlationId: String? = null,
    val notes: String? = null,
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(actorUserId.isNotBlank()) { "actorUserId must not be blank" }
        require(merchantId.isNotBlank()) { "merchantId must not be blank" }
    }
}

/**
 * Closed enum of all auditable actions. Anything outside this list is
 * either a UI-only interaction or a development-only log.
 */
enum class AuditAction(val displayName: String) {
    LOGIN_ATTEMPTED("Login attempted"),
    LOGIN_SUCCEEDED("Login succeeded"),
    LOGIN_FAILED("Login failed"),
    OTP_REQUESTED("OTP requested"),
    OTP_VERIFIED("OTP verified"),
    OTP_FAILED("OTP failed"),
    DEVICE_REGISTERED("Device registered"),
    DEVICE_REVOKED("Device revoked"),
    PAYMENT_CREATED("Payment created"),
    PAYMENT_APPROVED("Payment approved"),
    PAYMENT_DECLINED("Payment declined"),
    PAYMENT_CANCELLED("Payment cancelled"),
    REFUND_CREATED("Refund created"),
    REFUND_COMPLETED("Refund completed"),
    SETTINGS_CHANGED("Settings changed"),
    SECURITY_ALERT_ACKED("Security alert acknowledged"),
    SECURITY_ALERT_RESOLVED("Security alert resolved"),
    TEAM_MEMBER_ADDED("Team member added"),
    TEAM_MEMBER_REMOVED("Team member removed"),
}