package com.getauthepay.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed preference storage for non-sensitive-but-private
 * values that must not live in plaintext SharedPreferences (e.g. session
 * tokens, device identifiers, onboarding drafts).
 *
 * Cardholder data — PAN, CVV, PIN, full track data, OTP — MUST NEVER be
 * stored here or anywhere else on-device. The merchant acceptance flow
 * never sees the PAN in the first place.
 */
class SecureStorage(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun putString(key: String, value: String?) {
        if (value == null) prefs.edit().remove(key).apply()
        else prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, default: String? = null): String? =
        prefs.getString(key, default)

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, default: Long = 0L): Long = prefs.getLong(key, default)

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE_NAME = "authepay_secure_prefs"

        const val KEY_SESSION_TOKEN = "session_token"
        const val KEY_SESSION_EXPIRES_AT = "session_expires_at_ms"
        const val KEY_MERCHANT_ID = "merchant_id"
        const val KEY_TERMINAL_ID = "terminal_id"
        const val KEY_USER_ROLE = "user_role"
        const val KEY_LAST_LOGIN_AT = "last_login_at"
        const val KEY_ONBOARDING_DRAFT = "onboarding_draft"
        const val KEY_PREFERRED_CURRENCY = "preferred_currency"
        const val KEY_BIOMETRIC_PROTECT_REFUNDS = "biometric_refunds"
    }
}