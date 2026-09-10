package com.getauthepay.app.data

import com.getauthepay.app.core.models.Merchant
import com.getauthepay.app.core.models.MerchantRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the active merchant session. The session token + expiry live in
 * [SecureStorage]; this object exposes the derived UI state.
 *
 * The merchant record is the server-supplied snapshot and is refreshed
 * on every login. The session is considered inactive when the token is
 * missing or the expiry has passed.
 */
class SessionManager(private val storage: SecureStorage) {

    private val _state = MutableStateFlow(loadSession())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun current(): SessionState = _state.value

    fun begin(merchant: Merchant, token: String, expiresAtMs: Long) {
        storage.putString(SecureStorage.KEY_MERCHANT_ID, merchant.merchantId)
        storage.putString(SecureStorage.KEY_TERMINAL_ID, merchant.merchantId + "-T1")
        storage.putString(SecureStorage.KEY_SESSION_TOKEN, token)
        storage.putLong(SecureStorage.KEY_SESSION_EXPIRES_AT, expiresAtMs)
        storage.putString(SecureStorage.KEY_USER_ROLE, merchant.role.name)
        storage.putLong(SecureStorage.KEY_LAST_LOGIN_AT, System.currentTimeMillis())
        _state.value = SessionState(
            merchant = merchant,
            token = token,
            expiresAtMs = expiresAtMs,
            terminalId = merchant.merchantId + "-T1",
        )
    }

    fun end() {
        storage.remove(SecureStorage.KEY_SESSION_TOKEN)
        storage.remove(SecureStorage.KEY_SESSION_EXPIRES_AT)
        storage.remove(SecureStorage.KEY_LAST_LOGIN_AT)
        _state.value = SessionState.INACTIVE
    }

    fun touch() {
        // Re-emit so observers see refreshed expiry if needed.
        _state.value = _state.value
    }

    private fun loadSession(): SessionState {
        val merchantId = storage.getString(SecureStorage.KEY_MERCHANT_ID) ?: return SessionState.INACTIVE
        val token = storage.getString(SecureStorage.KEY_SESSION_TOKEN) ?: return SessionState.INACTIVE
        val expiresAt = storage.getLong(SecureStorage.KEY_SESSION_EXPIRES_AT)
        if (expiresAt > 0 && expiresAt < System.currentTimeMillis()) return SessionState.INACTIVE
        val terminalId = storage.getString(SecureStorage.KEY_TERMINAL_ID) ?: (merchantId + "-T1")
        val roleName = storage.getString(SecureStorage.KEY_USER_ROLE)
        val role = MerchantRole.fromWire(roleName)
        // Note: full merchant details aren't reloaded from network here; the
        // UI triggers a refresh on app start.
        val placeholder = Merchant(
            merchantId = merchantId,
            businessName = merchantId,
            businessType = com.getauthepay.app.core.models.BusinessType.OTHER,
            country = "BW",
            contactEmail = "",
            contactPhone = "",
            role = role,
            status = com.getauthepay.app.core.models.MerchantStatus.ACTIVE,
            createdAtMs = 0L,
        )
        return SessionState(placeholder, token, expiresAt, terminalId)
    }
}

data class SessionState(
    val merchant: Merchant,
    val token: String,
    val expiresAtMs: Long,
    val terminalId: String,
) {
    val isActive: Boolean
        get() = token.isNotBlank() && (expiresAtMs == 0L || expiresAtMs > System.currentTimeMillis())

    companion object {
        val INACTIVE = SessionState(
            merchant = Merchant(
                merchantId = "",
                businessName = "",
                businessType = com.getauthepay.app.core.models.BusinessType.OTHER,
                country = "",
                contactEmail = "",
                contactPhone = "",
                role = MerchantRole.CASHIER,
                status = com.getauthepay.app.core.models.MerchantStatus.PENDING,
                createdAtMs = 0L,
            ),
            token = "",
            expiresAtMs = 0L,
            terminalId = "",
        )
    }
}