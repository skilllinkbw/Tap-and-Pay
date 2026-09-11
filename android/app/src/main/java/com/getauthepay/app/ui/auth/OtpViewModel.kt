package com.getauthepay.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getauthepay.app.BuildConfig
import com.getauthepay.app.core.log.SecureLogger
import com.getauthepay.app.core.models.Merchant
import com.getauthepay.app.core.models.MerchantRole
import com.getauthepay.app.core.models.MerchantStatus
import com.getauthepay.app.core.validation.Validators
import com.getauthepay.app.data.SessionManager
import com.getauthepay.app.network.ApiException
import com.getauthepay.app.network.AuthePayApiClient
import com.getauthepay.app.network.NetworkUnavailable
import com.getauthepay.app.network.OtpRequestOutcome
import com.getauthepay.app.network.OtpVerifyResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One-time-code verification.
 *
 * Security contract (mirrors the build directive):
 *  - The code is one-time: it is consumed by the server on success.
 *  - It has a short expiry, enforced server-side; the client shows a
 *    countdown only so the merchant knows when to request a new one.
 *  - Attempts are limited: after [MAX_ATTEMPTS] failures the client locks
 *    and the server applies its own rate limit (HTTP 429).
 *  - The code is **never** logged, stored, or included in any error string.
 *    [SecureLogger] additionally redacts anything OTP-shaped as a backstop.
 *
 * A successful verification starts the session via [SessionManager], which
 * persists the token in Keystore-backed encrypted storage.
 */
class OtpViewModel(
    private val api: AuthePayApiClient,
    private val session: SessionManager,
    private val identifier: String,
) : ViewModel() {

    data class UiState(
        val code: String = "",
        val loading: Boolean = false,
        val attemptsUsed: Int = 0,
        val lockedOut: Boolean = false,
        val secondsRemaining: Int = OTP_TTL_SECONDS,
        val resendCooldownSeconds: Int = 0,
        val errorMessage: String? = null,
        val infoMessage: String? = null,
        /** Set once the server has verified the code and a session exists. */
        val verified: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    init {
        startCountdowns()
    }

    fun setCode(value: String) {
        _state.value = _state.value.copy(code = value, errorMessage = null)
    }

    fun verify() {
        val s = _state.value
        if (s.loading || s.lockedOut || s.verified) return

        val code = s.code.trim()
        val shapeError = runCatching { Validators.validateOtpShape(code) }.exceptionOrNull()
        if (shapeError != null) {
            _state.value = s.copy(errorMessage = shapeError.message)
            return
        }
        if (s.secondsRemaining <= 0) {
            _state.value = s.copy(
                errorMessage = "This code has expired. Request a new one.",
                code = "",
            )
            return
        }

        _state.value = s.copy(loading = true, errorMessage = null, infoMessage = null)

        viewModelScope.launch {
            try {
                // Debug-only convenience. Unreachable in release builds
                // because BuildConfig.TEST_OTP_ENABLED is false there.
                if (BuildConfig.TEST_OTP_ENABLED && code == TEST_CODE) {
                    SecureLogger.event(event = "otp.test.verify.shortcut")
                    openSession(testMerchant(identifier))
                    return@launch
                }

                when (val result = api.verifyOtp(identifier, code)) {
                    is OtpVerifyResult.Success -> {
                        SecureLogger.event(
                            event = "otp.verified",
                            extra = mapOf("merchantId" to result.merchant.merchantId),
                        )
                        openSession(result.merchant, result.token, result.expiresAtMs)
                    }

                    is OtpVerifyResult.Failed -> registerFailure(result.reason)
                }
            } catch (nu: NetworkUnavailable) {
                _state.value = _state.value.copy(
                    loading = false,
                    errorMessage = "Network unavailable. Check your connection and try again.",
                )
            } catch (ae: ApiException) {
                registerFailure("Verification failed (error ${ae.code})")
            }
        }
    }

    fun resend() {
        val s = _state.value
        if (s.loading || s.resendCooldownSeconds > 0) return

        _state.value = s.copy(
            loading = true,
            errorMessage = null,
            infoMessage = null,
            code = "",
            attemptsUsed = 0,
            lockedOut = false,
        )
        viewModelScope.launch {
            try {
                // Debug-only convenience, mirrors LoginViewModel's test
                // shortcut so offline debug demos can complete the flow.
                // Unreachable in release builds because
                // BuildConfig.TEST_OTP_ENABLED is false there.
                if (BuildConfig.TEST_OTP_ENABLED && isLocalTestIdentifier(identifier)) {
                    SecureLogger.event(event = "otp.test.resend.shortcut")
                    _state.value = _state.value.copy(
                        loading = false,
                        secondsRemaining = OTP_TTL_SECONDS,
                        resendCooldownSeconds = RESEND_COOLDOWN_SECONDS,
                        infoMessage = "TEST MODE: use code 000000 to continue.",
                    )
                    startCountdowns()
                    return@launch
                }

                when (api.requestOtp(identifier)) {
                    OtpRequestOutcome.SENT -> {
                        _state.value = _state.value.copy(
                            loading = false,
                            secondsRemaining = OTP_TTL_SECONDS,
                            resendCooldownSeconds = RESEND_COOLDOWN_SECONDS,
                            infoMessage = "A new code has been sent.",
                        )
                        startCountdowns()
                    }

                    OtpRequestOutcome.RATE_LIMITED -> _state.value = _state.value.copy(
                        loading = false,
                        errorMessage = "Too many requests. Please wait before requesting another code.",
                    )

                    OtpRequestOutcome.FAILED -> _state.value = _state.value.copy(
                        loading = false,
                        errorMessage = "We could not send a new code. Please try again.",
                    )
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    errorMessage = "We could not send a new code. Please try again.",
                )
            }
        }
    }

    /**
     * Local test identifiers are only recognised when TEST_OTP_ENABLED is on
     * (debug builds) and always go through the network in production.
     */
    private fun isLocalTestIdentifier(id: String): Boolean =
        id.startsWith("+267") || id.contains("test", ignoreCase = true)

    private fun registerFailure(reason: String) {
        val used = _state.value.attemptsUsed + 1
        val locked = used >= MAX_ATTEMPTS
        _state.value = _state.value.copy(
            loading = false,
            attemptsUsed = used,
            lockedOut = locked,
            code = "",
            errorMessage = if (locked) {
                "Too many incorrect attempts. Request a new code to continue."
            } else {
                "$reason — ${MAX_ATTEMPTS - used} attempt(s) remaining."
            },
        )
        SecureLogger.event(
            event = "otp.verify.failed",
            extra = mapOf("attemptsUsed" to used.toString()),
        )
    }

    private fun openSession(
        merchant: Merchant,
        token: String? = null,
        expiresAtMs: Long = 0L,
    ) {
        if (token != null) {
            session.begin(merchant, token, expiresAtMs)
        } else {
            // Test-mode path: no server token exists. Start a clearly-scoped
            // session so the rest of the app works offline.
            session.begin(merchant, "sandbox-test-session", System.currentTimeMillis() + 8 * 60 * 60 * 1000L)
        }
        _state.value = _state.value.copy(loading = false, verified = true, errorMessage = null)
    }

    private fun startCountdowns() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (!_state.value.verified) {
                delay(1_000)
                val s = _state.value
                val ttl = (s.secondsRemaining - 1).coerceAtLeast(0)
                val cooldown = (s.resendCooldownSeconds - 1).coerceAtLeast(0)
                val expired = ttl == 0 && !s.loading
                _state.value = s.copy(
                    secondsRemaining = ttl,
                    resendCooldownSeconds = cooldown,
                    errorMessage = if (expired && s.errorMessage == null) {
                        "This code has expired. Request a new one."
                    } else {
                        s.errorMessage
                    },
                )
                if (ttl == 0 && cooldown == 0) break
            }
        }
    }

    companion object {
        /** Server-side TTL is authoritative; this is the client-side mirror. */
        const val OTP_TTL_SECONDS = 180
        const val RESEND_COOLDOWN_SECONDS = 45
        const val MAX_ATTEMPTS = 5
        const val OTP_LENGTH = 6
        const val TEST_CODE = "000000"

        /**
         * The merchant record synthesised for the TEST-OTP shortcut. It is
         * only reachable in debug builds and is intentionally labelled so no
         * one mistakes it for a real account.
         */
        fun testMerchant(identifier: String): Merchant = Merchant(
            merchantId = "TEST-MERCHANT",
            businessName = "Sandbox Test Merchant",
            tradingName = "Sandbox Test Merchant",
            businessType = com.getauthepay.app.core.models.BusinessType.OTHER,
            country = "BW",
            contactEmail = identifier,
            contactPhone = identifier,
            role = MerchantRole.OWNER,
            status = MerchantStatus.ACTIVE,
            createdAtMs = System.currentTimeMillis(),
        )
    }
}
