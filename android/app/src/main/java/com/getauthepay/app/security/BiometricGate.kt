package com.getauthepay.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * BiometricPrompt wrapper. Sensitive merchant actions (opening the
 * payment acceptance screen, refunds, settlement bank changes,
 * security setting changes) require a biometric authentication step.
 *
 * The biometric payload is never persisted; the prompt result only
 * conveys success/failure.
 */
object BiometricGate {

    enum class Capability {
        AVAILABLE,
        NO_HARDWARE,
        UNAVAILABLE,
        NONE_ENROLLED,
        SECURITY_UPDATE_REQUIRED,
    }

    fun capability(context: Context): Capability {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        return when (BiometricManager.from(context).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Capability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Capability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Capability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> Capability.SECURITY_UPDATE_REQUIRED
            else -> Capability.UNAVAILABLE
        }
    }

    suspend fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        description: String,
        negativeButton: String = "Cancel",
    ): Boolean = suspendCancellableCoroutine { cont ->
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(false)
                }
                override fun onAuthenticationFailed() {
                    // Single attempt failure — the prompt continues.
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setNegativeButtonText(negativeButton)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK,
            )
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
        cont.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}