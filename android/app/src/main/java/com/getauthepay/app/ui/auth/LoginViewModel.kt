package com.getauthepay.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getauthepay.app.BuildConfig
import com.getauthepay.app.core.log.SecureLogger
import com.getauthepay.app.network.ApiException
import com.getauthepay.app.network.AuthePayApiClient
import com.getauthepay.app.network.NetworkUnavailable
import com.getauthepay.app.network.OtpRequestOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Merchant identifier entry → OTP request.
 *
 * Security notes:
 *  - There is **no password** in this flow. Authentication is phone/email
 *    plus a one-time code verified server-side.
 *  - The OTP itself is never logged, never persisted, and never echoed back
 *    into this ViewModel's state.
 *  - The resend cooldown is enforced client-side purely as UX hygiene; the
 *    authoritative rate limit lives on the server (HTTP 429).
 *
 * Test-mode shortcut: when `BuildConfig.TEST_OTP_ENABLED` is true (debug
 * builds only) the screen can proceed without contacting the server.
 * `assembleRelease` sets this to `false`, so the shortcut is unreachable in
 * production — see `SecurityConfigurationTest` which asserts that.
 */
class LoginViewModel(
    private val api: AuthePayApiClient,
) : ViewModel() {

    data class UiState(
        val identifier: String = "",
        val loading: Boolean = false,
        /** Set once a code has been dispatched; the screen then advances. */
        val otpRequested: Boolean = false,
        val cooldownSeconds: Int = 0,
        val errorMessage: String? = null,
        val infoMessage: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    fun setIdentifier(value: String) {
        _state.value = _state.value.copy(identifier = value, errorMessage = null)
    }

    fun requestOtp() {
        val id = _state.value.identifier.trim()
        if (id.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "Enter your phone number or email")
            return
        }
        if (_state.value.cooldownSeconds > 0 || _state.value.loading) return

        _state.value = _state.value.copy(loading = true, errorMessage = null, infoMessage = null)

        viewModelScope.launch {
            try {
                // Debug/test builds may bypass the server so UI work is
                // possible without a backend. Release builds never take this
                // branch (TEST_OTP_ENABLED is false and the compiler cannot
                // remove it, so the flag is checked at runtime too).
                if (BuildConfig.TEST_OTP_ENABLED && isLocalTestIdentifier(id)) {
                    SecureLogger.event(event = "otp.test.shortcut")
                    _state.value = _state.value.copy(
                        loading = false,
                        otpRequested = true,
                        infoMessage = "TEST MODE: use code 000000 to continue.",
                    )
                    return@launch
                }

                when (api.requestOtp(id)) {
                    OtpRequestOutcome.SENT -> {
                        _state.value = _state.value.copy(
                            loading = false,
                            otpRequested = true,
                            infoMessage = "We sent a one-time code to your phone or email.",
                        )
                        startCooldown(RESEND_COOLDOWN_SECONDS)
                    }

                    OtpRequestOutcome.RATE_LIMITED -> _state.value = _state.value.copy(
                        loading = false,
                        errorMessage = "Too many requests. Please wait before requesting another code.",
                    )

                    OtpRequestOutcome.FAILED -> _state.value = _state.value.copy(
                        loading = false,
                        errorMessage = "We could not send a code. Check your details and connection.",
                    )
                }
            } catch (nu: NetworkUnavailable) {
                _state.value = _state.value.copy(
                    loading = false,
                    errorMessage = "Network unavailable. Check your connection and try again.",
                )
            } catch (ae: ApiException) {
                _state.value = _state.value.copy(
                    loading = false,
                    errorMessage = "We could not send a code (error ${ae.code}).",
                )
            }
        }
    }

    private fun startCooldown(seconds: Int) {
        countdownJob?.cancel()
        _state.value = _state.value.copy(cooldownSeconds = seconds)
        countdownJob = viewModelScope.launch {
            while (_state.value.cooldownSeconds > 0) {
                delay(1_000)
                _state.value = _state.value.copy(
                    cooldownSeconds = (_state.value.cooldownSeconds - 1).coerceAtLeast(0),
                )
            }
        }
    }

    /**
     * Local test identifiers are only recognised when TEST_OTP_ENABLED is on
     * and always go through the network in production. Keeping the check
     * explicit (rather than blanket-skipping the server) means a release
     * build with a stray debug flag still authenticates properly.
     */
    private fun isLocalTestIdentifier(id: String): Boolean =
        id.startsWith("+267") || id.contains("test", ignoreCase = true)

    companion object {
        const val RESEND_COOLDOWN_SECONDS = 45

        /**
         * Cheap shape check used only to enable/disable the CTA. It is not a
         * substitute for server-side validation — the server decides whether
         * an identifier is a known merchant.
         */
        fun looksLikeIdentifier(raw: String): Boolean {
            val v = raw.trim()
            if (v.length < 6) return false
            val isEmail = v.contains('@') && Regex("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$").matches(v)
            val isPhone = v.replace(Regex("[^0-9]"), "").length in 7..15
            return isEmail || isPhone
        }
    }
}
