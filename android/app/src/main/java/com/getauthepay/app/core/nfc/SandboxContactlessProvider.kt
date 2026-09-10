package com.getauthepay.app.core.nfc

import com.getauthepay.app.core.log.SecureLogger
import com.getauthepay.app.core.models.PaymentRequest
import com.getauthepay.app.core.models.PaymentResult
import com.getauthepay.app.core.models.PaymentStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlin.coroutines.coroutineContext

/**
 * Deterministic sandbox NFC simulator used for:
 *
 *   - UI development and preview
 *   - Bank demonstrations (must be visually labelled SANDBOX)
 *   - Automated UI/integration tests
 *   - Phone-to-phone development flows
 *
 * It is NEVER connected to a real card network. The flow stops at
 * [PaymentStatus.AUTHORIZING] — the engine then hands the request to
 * the [com.getauthepay.app.core.payment.PaymentProcessor] which
 * produces the terminal outcome. This mirrors the production split
 * between the contactless transport and the processor authoriser.
 *
 * Scenarios are surfaced to the engine via the supplied
 * [PaymentRequest.sandboxScenario] so the engine can route them to
 * the matching processor behaviour.
 */
class SandboxContactlessProvider(
    override val availability: NfcAvailability = NfcAvailability.AvailableEnabled,
    private val tickMs: Long = DEFAULT_TICK_MS,
) : ContactlessPaymentProvider {

    @Volatile
    private var cancelled: Boolean = false

    override fun startReading(request: PaymentRequest): Flow<PaymentResult> = flow {
        cancelled = false
        val correlationId = java.util.UUID.randomUUID().toString()
        SecureLogger.event(
            event = "sandbox.payment.started",
            correlationId = correlationId,
            transactionId = request.requestId,
            status = PaymentStatus.CREATED.name,
            extra = mapOf("scenario" to (request.sandboxScenario?.name ?: "DEFAULT")),
        )

        emit(PaymentResult(
            requestId = request.requestId,
            status = PaymentStatus.CREATED,
            correlationId = correlationId,
        ))

        val emitFn: suspend (PaymentResult) -> Unit = { emit(it) }
        if (!tick(PaymentStatus.READY_FOR_TAP, request.requestId, correlationId, emitFn)) return@flow
        if (!tick(PaymentStatus.CARD_DETECTED, request.requestId, correlationId, emitFn)) return@flow
        if (!tick(PaymentStatus.PROCESSING, request.requestId, correlationId, emitFn)) return@flow

        // Hand off to the processor. The contactless flow completes here
        // and the engine authorises via the configured PaymentProcessor.
        emit(PaymentResult(
            requestId = request.requestId,
            status = PaymentStatus.AUTHORIZING,
            correlationId = correlationId,
        ))
    }.onCompletion {
        cancelled = false
    }

    override suspend fun cancelReading() {
        cancelled = true
    }

    private suspend fun tick(
        status: PaymentStatus,
        requestId: String,
        correlationId: String,
        emit: suspend (PaymentResult) -> Unit,
    ): Boolean {
        if (cancelled) return false
        delay(tickMs)
        if (cancelled) throw CancellationException("merchant cancelled")
        emit(PaymentResult(requestId, status, correlationId = correlationId))
        return true
    }

    companion object {
        const val DEFAULT_TICK_MS: Long = 600L
    }
}