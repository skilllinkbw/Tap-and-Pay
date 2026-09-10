package com.getauthepay.app.core.nfc

import com.getauthepay.app.core.models.PaymentRequest
import com.getauthepay.app.core.models.PaymentResult
import kotlinx.coroutines.flow.Flow

/**
 * The narrow contact point between the payment engine and the
 * contactless transport.
 *
 *  - [startReading] returns a cold Flow that emits incremental
 *    [PaymentResult] updates describing the state transitions
 *    ([com.getauthepay.app.core.models.PaymentStatus]) until a terminal
 *    state is reached.
 *  - [cancelReading] must be safe to call at any time, including before
 *    [startReading] and after the flow has completed.
 *  - [availability] describes whether the device exposes NFC hardware
 *    and whether it is currently enabled.
 *
 * Production implementations are expected to delegate to a certified EMV
 * contactless kernel (acquirer / MPoC SDK); the sandbox implementation
 * is a deterministic simulator used for UI tests and bank demos.
 */
interface ContactlessPaymentProvider {

    val availability: NfcAvailability

    fun startReading(request: PaymentRequest): Flow<PaymentResult>

    suspend fun cancelReading()
}

sealed interface NfcAvailability {
    /** NFC hardware present and radio enabled. */
    data object AvailableEnabled : NfcAvailability
    /** NFC hardware present but radio is disabled in Settings. */
    data object AvailableDisabled : NfcAvailability
    /** Device has no NFC hardware, or NFC has been revoked by an MDM policy. */
    data object NotAvailable : NfcAvailability
}