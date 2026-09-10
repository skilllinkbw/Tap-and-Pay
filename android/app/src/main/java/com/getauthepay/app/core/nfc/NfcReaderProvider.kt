package com.getauthepay.app.core.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.getauthepay.app.core.log.SecureLogger
import com.getauthepay.app.core.models.PaymentRequest
import com.getauthepay.app.core.models.PaymentResult
import com.getauthepay.app.core.models.PaymentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID

/**
 * Production NFC reader. Activates Android reader-mode and emits
 * lifecycle states up to [PaymentStatus.CARD_DETECTED].
 *
 * ## Kernel boundary (IMPORTANT)
 *
 * This provider deliberately does NOT implement a payment kernel.
 * The moment an ISO 14443 application is detected the EMV transaction
 * must be handled by an approved MPoC / SoftPOS SDK supplied by the
 * acquirer. Until that integration exists the provider surfaces
 * [PaymentStatus.FAILED] with code CERTIFIED_KERNEL_REQUIRED.
 *
 * Building a home-grown EMV kernel and presenting it as
 * "production-certified" would be the single most damaging error a
 * bank submission can make; this design keeps that risk visible.
 */
class NfcReaderProvider(
    private val activityProvider: () -> Activity?,
    private val contextProvider: () -> Context?,
) : ContactlessPaymentProvider {

    override val availability: NfcAvailability get() {
        val adapter = nfcAdapter() ?: return NfcAvailability.NotAvailable
        return if (adapter.isEnabled) NfcAvailability.AvailableEnabled
        else NfcAvailability.AvailableDisabled
    }

    override fun startReading(request: PaymentRequest): Flow<PaymentResult> = callbackFlow {
        val adapter = nfcAdapter()
        if (adapter == null) {
            trySend(PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.FAILED,
                errorCode = "NFC_UNAVAILABLE",
                errorMessage = "This device does not expose an NFC reader.",
            ))
            close()
            return@callbackFlow
        }
        if (!adapter.isEnabled) {
            trySend(PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.FAILED,
                errorCode = "NFC_DISABLED",
                errorMessage = "NFC is turned off. Enable NFC in Settings.",
            ))
            close()
            return@callbackFlow
        }
        val activity = activityProvider()
        if (activity == null) {
            trySend(PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.FAILED,
                errorCode = "NO_HOST_ACTIVITY",
                errorMessage = "Payment UI is not in the foreground.",
            ))
            close()
            return@callbackFlow
        }

        val correlationId = UUID.randomUUID().toString()
        trySend(PaymentResult(
            requestId = request.requestId,
            status = PaymentStatus.READY_FOR_TAP,
            correlationId = correlationId,
        ))

        val callback = NfcAdapter.ReaderCallback { tag: Tag ->
            handleTag(tag, request, correlationId).forEach { trySend(it) }
        }

        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

        try {
            adapter.enableReaderMode(activity, callback, flags, null)
        } catch (t: Throwable) {
            SecureLogger.event(
                event = "nfc.reader.enable_failed",
                correlationId = correlationId,
                status = PaymentStatus.FAILED.name,
                extra = mapOf("error" to t.javaClass.simpleName),
            )
            trySend(PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.FAILED,
                errorCode = "NFC_ENABLE_FAILED",
                errorMessage = "Could not activate the NFC reader.",
                correlationId = correlationId,
            ))
            close(t)
            return@callbackFlow
        }

        awaitClose {
            runCatching { adapter.disableReaderMode(activity) }
        }
    }.flowOn(Dispatchers.Main)

    /**
     * Handles one discovered ISO 14443 tag and returns the results to emit.
     *
     * Returning a list (rather than sending directly) keeps [handleTag] a plain
     * function that can be unit-tested without a channel, and keeps all
     * `trySend` calls inside the `callbackFlow` scope that owns them.
     */
    private fun handleTag(
        tag: Tag,
        request: PaymentRequest,
        correlationId: String,
    ): List<PaymentResult> {
        val iso = IsoDep.get(tag)
        if (iso == null) {
            return listOf(
                PaymentResult(
                    requestId = request.requestId,
                    status = PaymentStatus.FAILED,
                    errorCode = "UNSUPPORTED_CARD",
                    errorMessage = "Card is not a contactless payment card.",
                    correlationId = correlationId,
                ),
            )
        }

        // EMV kernel boundary. Without a certified MPoC / SoftPOS SDK the
        // transaction cannot be authorised on production hardware.
        return listOf(
            PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.CARD_DETECTED,
                correlationId = correlationId,
            ),
            PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.FAILED,
                errorCode = "CERTIFIED_KERNEL_REQUIRED",
                errorMessage = "Card detected. An approved EMV kernel is required to " +
                    "authorise this transaction; the merchant app does not implement a " +
                    "payment kernel locally.",
                correlationId = correlationId,
            ),
        )
    }

    private fun nfcAdapter(): NfcAdapter? {
        val ctx = contextProvider() ?: return null
        val manager = ctx.applicationContext
            .getSystemService(Context.NFC_SERVICE) as? NfcManager ?: return null
        return manager.defaultAdapter
    }

    /**
     * Tears down reader mode. Safe to call at any time, including before
     * [startReading] and after the flow has completed.
     */
    override suspend fun cancelReading() {
        val activity = activityProvider() ?: return
        val adapter = nfcAdapter() ?: return
        runCatching { adapter.disableReaderMode(activity) }
        SecureLogger.event(event = "nfc.reader.cancelled")
    }
}