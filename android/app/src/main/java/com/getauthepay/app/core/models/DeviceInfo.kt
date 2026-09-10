package com.getauthepay.app.core.models

/**
 * A device enrolled against a merchant account. Each merchant may have
 * many devices, each device has its own secure keystore-backed identity
 * and its own lifecycle.
 */
data class DeviceInfo(
    val deviceId: String,
    val merchantId: String,
    val displayName: String,
    val platform: String,
    val osVersion: String,
    val appVersion: String,
    val securityState: DeviceSecurityState,
    val registeredAtMs: Long,
    val lastActiveAtMs: Long? = null,
    val status: DeviceStatus,
)

enum class DeviceStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    SUSPENDED,
    REVOKED;

    fun canAcceptPayments(): Boolean = this == ACTIVE
}

enum class DeviceSecurityState {
    SECURE,
    NEEDS_ATTENTION,
    UNSAFE;

    val displayLabel: String
        get() = when (this) {
            SECURE -> "Secure"
            NEEDS_ATTENTION -> "Needs attention"
            UNSAFE -> "Unsafe"
        }
}