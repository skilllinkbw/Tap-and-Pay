package com.getauthepay.app.core

import com.getauthepay.app.core.ledger.TransactionLedger
import com.getauthepay.app.core.log.SecureLogger
import com.getauthepay.app.core.models.PaymentRequest
import com.getauthepay.app.core.models.PaymentResult
import com.getauthepay.app.core.models.PaymentStatus
import com.getauthepay.app.core.models.RiskDecision
import com.getauthepay.app.core.nfc.ContactlessPaymentProvider
import com.getauthepay.app.core.nfc.NfcAvailability
import com.getauthepay.app.core.payment.IdempotencyStore
import com.getauthepay.app.core.payment.PaymentProcessor
import com.getauthepay.app.core.payment.PaymentStateMachine
import com.getauthepay.app.core.risk.RiskContext
import com.getauthepay.app.core.risk.RiskService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import java.util.UUID

/**
 * Orchestrates the complete payment lifecycle. This is the ONLY component
 * the UI ViewModel talks to — the UI never touches the NFC adapter, the
 * EMV kernel adapter, or the processor directly.
 *
 * ```
 *   UI (Compose)
 *    └─ PaymentViewModel
 *         └─ PaymentAcceptanceEngine        ← you are here
 *              ├─ RiskService               (pre-authorisation screening)
 *              ├─ ContactlessPaymentProvider (NFC transport / EMV kernel seam)
 *              ├─ PaymentProcessor           (acquirer authorisation)
 *              └─ TransactionLedger          (persistence + receipts)
 * ```
 *
 * Sequence for one payment attempt:
 *
 *   0. Pre-flight — NFC availability is checked before the radio is used.
 *   1. Idempotency — a repeated [PaymentRequest.idempotencyKey] replays the
 *      cached terminal result instead of starting a second payment.
 *   2. Risk evaluation — a BLOCK decision short-circuits before any NFC work.
 *   3. Contactless transport — emits progressive [PaymentStatus] updates and
 *      terminates either at AUTHORIZING (hand off to the processor) or at a
 *      terminal failure state.
 *   4. Processor authorisation — only entered from AUTHORIZING.
 *   5. Ledger persistence + idempotency commit.
 *
 * The engine has no Android dependencies, so the whole flow is exercised
 * by JVM unit tests using the sandbox provider and processor.
 */
class PaymentAcceptanceEngine(
    private val contactlessProvider: ContactlessPaymentProvider,
    private val processor: PaymentProcessor,
    private val riskService: RiskService,
    private val ledger: TransactionLedger,
    private val session: EngineSession,
    private val idempotency: IdempotencyStore<PaymentResult> = IdempotencyStore(),
) {

    /**
     * Merchant/terminal identity resolved from the active session. The engine
     * itself is stateless with respect to "who is logged in".
     */
    data class EngineSession(
        val merchantId: String,
        val terminalId: String,
    )

    private val stateMachine = PaymentStateMachine()

    /**
     * One payment at a time per terminal. The engine shares a state machine,
     * the NFC radio and the cancellation flag, so two concurrent collections
     * of [processPayment] would corrupt each other (a second collector used
     * to reset the shared state machine mid-payment). A terminal is a
     * single-lane device by definition, so concurrent attempts are rejected
     * with a clear, non-destructive result instead of being queued.
     */
    private val paymentMutex = Mutex()

    @Volatile
    private var cancellationRequested: Boolean = false

    fun currentStatus(): PaymentStatus = stateMachine.current

    /** Requests cancellation of the in-flight attempt. Cooperative, not immediate. */
    suspend fun cancel() {
        if (stateMachine.current.isTerminal()) return
        cancellationRequested = true
        SecureLogger.event(
            event = "engine.payment.cancel.requested",
            status = stateMachine.current.name,
        )
        runCatching { contactlessProvider.cancelReading() }
    }

    /**
     * Runs one payment attempt and emits every state transition.
     *
     * The flow is cold: nothing happens until it is collected, and cancelling
     * the collecting coroutine cancels the NFC read.
     */
    fun processPayment(
        request: PaymentRequest,
        riskContext: RiskContext,
    ): Flow<PaymentResult> = flow {
        if (!paymentMutex.tryLock()) {
            // Another payment attempt is actively using this terminal (shared
            // state machine + NFC radio). Never run a second attempt over the
            // top of it, and never touch the ledger/idempotency store here —
            // the in-flight attempt owns those writes.
            SecureLogger.event(
                event = "engine.payment.concurrent_rejected",
                transactionId = request.requestId,
                status = PaymentStatus.DECLINED.name,
            )
            emit(
                PaymentResult(
                    requestId = request.requestId,
                    status = PaymentStatus.DECLINED,
                    transactionId = ReferenceIds.transaction(request.requestId),
                    errorCode = "PAYMENT_IN_PROGRESS",
                    errorMessage = "Another payment is already in progress on this terminal. " +
                        "Wait for it to finish before starting the next one.",
                ),
            )
            return@flow
        }
        try {
            runPaymentAttempt(request, riskContext).collect { emit(it) }
        } finally {
            paymentMutex.unlock()
        }
    }

    private fun runPaymentAttempt(
        request: PaymentRequest,
        riskContext: RiskContext,
    ): Flow<PaymentResult> = flow {
        cancellationRequested = false
        stateMachine.reset(PaymentStatus.CREATED)

        val correlationId = UUID.randomUUID().toString()
        val amount = request.toMoney()

        // ---- (1) Idempotency replay -------------------------------------
        idempotency.cached(request.idempotencyKey)?.let { cached ->
            SecureLogger.event(
                event = "engine.payment.idempotent_replay",
                correlationId = cached.correlationId ?: correlationId,
                transactionId = cached.transactionId,
                status = cached.status.name,
            )
            stateMachine.reset(cached.status)
            emit(cached)
            return@flow
        }
        if (!idempotency.begin(request.idempotencyKey)) {
            // Defensive: with the terminal mutex above this is only reachable
            // if a slot exists without a completed result (e.g. a prior
            // attempt was interrupted mid-flight on this same engine). Never
            // start a second authorisation for a key that is already claimed.
            SecureLogger.event(
                event = "engine.payment.duplicate_in_flight",
                correlationId = correlationId,
                transactionId = request.requestId,
                status = PaymentStatus.DECLINED.name,
            )
            emit(
                PaymentResult(
                    requestId = request.requestId,
                    status = PaymentStatus.DECLINED,
                    transactionId = ReferenceIds.transaction(request.requestId),
                    correlationId = correlationId,
                    errorCode = "DUPLICATE_TRANSACTION",
                    errorMessage = "This payment was already submitted and is still being processed. " +
                        "No second charge was made.",
                ),
            )
            return@flow
        }

        SecureLogger.event(
            event = "engine.payment.start",
            correlationId = correlationId,
            transactionId = request.requestId,
            status = PaymentStatus.CREATED.name,
            extra = mapOf(
                "amount" to amount.amount.toPlainString(),
                "currency" to request.currency,
                "merchantId" to session.merchantId,
                "terminalId" to session.terminalId,
            ),
        )

        suspend fun finish(result: PaymentResult) {
            stateMachine.reset(result.status)
            idempotency.complete(request.idempotencyKey, result)
            if (result.status.isTerminal()) {
                ledger.record(
                    result = result,
                    merchantId = session.merchantId,
                    terminalId = session.terminalId,
                    amount = amount,
                    reference = request.reference,
                    description = request.description,
                )
            }
            emit(result)
        }

        suspend fun fail(status: PaymentStatus, code: String, message: String, risk: RiskDecision? = null) {
            finish(
                PaymentResult(
                    requestId = request.requestId,
                    status = status,
                    transactionId = ReferenceIds.transaction(request.requestId),
                    correlationId = correlationId,
                    errorCode = code,
                    errorMessage = message,
                    riskDecision = risk,
                ),
            )
        }

        // ---- (0) Pre-flight: NFC availability ---------------------------
        // A merchant app must never present a "ready to tap" surface when the
        // device cannot read cards; doing so would cause silent failure at the
        // counter. Fail loudly and explain what to do instead.
        when (val availability = contactlessProvider.availability) {
            NfcAvailability.NotAvailable -> {
                fail(
                    status = PaymentStatus.FAILED,
                    code = "NFC_NOT_AVAILABLE",
                    message = "This device has no NFC reader, so it cannot accept contactless card payments.",
                )
                return@flow
            }

            NfcAvailability.AvailableDisabled -> {
                fail(
                    status = PaymentStatus.FAILED,
                    code = "NFC_DISABLED",
                    message = "Turn on NFC in Android Settings to accept contactless payments.",
                )
                return@flow
            }

            NfcAvailability.AvailableEnabled -> Unit
        }

        emit(
            PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.CREATED,
                correlationId = correlationId,
            ),
        )

        // ---- (2) Risk evaluation ----------------------------------------
        val risk = try {
            riskService.evaluate(riskContext)
        } catch (t: Throwable) {
            // Fail closed: if the risk engine is unreachable we do not
            // silently authorise. The merchant can retry.
            fail(
                status = PaymentStatus.FAILED,
                code = "RISK_ENGINE_UNAVAILABLE",
                message = "Risk checks are unavailable right now. Please retry.",
            )
            return@flow
        }

        SecureLogger.event(
            event = "engine.risk.evaluated",
            correlationId = correlationId,
            transactionId = request.requestId,
            status = PaymentStatus.CREATED.name,
            extra = mapOf(
                "score" to risk.score.toString(),
                "decision" to risk.decision.name,
                "modelVersion" to risk.modelVersion,
            ),
        )

        if (risk.decision == RiskDecision.Decision.BLOCK) {
            fail(
                status = PaymentStatus.DECLINED,
                code = "RISK_BLOCKED",
                message = buildRiskMessage(risk),
                risk = risk,
            )
            return@flow
        }
        if (cancellationRequested) {
            fail(PaymentStatus.CANCELLED, "CANCELLED", "Cancelled by merchant.", risk)
            return@flow
        }

        // ---- (3) Contactless transport ----------------------------------
        var reachedAuthorising = false

        try {
            contactlessProvider.startReading(request).collect { update ->
                if (cancellationRequested) {
                    fail(PaymentStatus.CANCELLED, "CANCELLED", "Cancelled by merchant.", risk)
                    return@collect
                }
                // Only surface transitions the state machine accepts; this
                // filters out-of-order or duplicated emissions from an adapter.
                if (stateMachine.transitionTo(update.status)) {
                    emit(update.copy(riskDecision = update.riskDecision ?: risk))
                }
                if (update.status == PaymentStatus.AUTHORIZING) reachedAuthorising = true
                if (update.status.isTerminal()) {
                    finish(update.copy(riskDecision = update.riskDecision ?: risk))
                    return@collect
                }
            }
        } catch (ce: CancellationException) {
            fail(PaymentStatus.CANCELLED, "CANCELLED", "Cancelled by merchant.", risk)
            return@flow
        } catch (t: Throwable) {
            fail(
                status = PaymentStatus.FAILED,
                code = "CONTACTLESS_ERROR",
                message = "Contactless read failed: ${t.message ?: "unknown error"}",
                risk = risk,
            )
            return@flow
        }

        if (!reachedAuthorising) {
            // Transport ended before the authorisation handoff and has not yet
            // emitted a terminal result. Nothing was authorised, so nothing is
            // recorded as a sale.
            if (!stateMachine.current.isTerminal()) {
                fail(
                    status = PaymentStatus.FAILED,
                    code = "TRANSPORT_ENDED",
                    message = "The card read did not complete. Please try again.",
                    risk = risk,
                )
            }
            idempotency.discard(request.idempotencyKey)
            return@flow
        }

        if (cancellationRequested) {
            fail(PaymentStatus.CANCELLED, "CANCELLED", "Cancelled by merchant.", risk)
            return@flow
        }

        // ---- (4) Processor authorisation --------------------------------
        val authorised = try {
            processor.authorise(
                request = request,
                amount = amount,
                riskDecision = risk,
                idempotencyKey = request.idempotencyKey,
            )
        } catch (t: Throwable) {
            SecureLogger.event(
                event = "engine.processor.exception",
                correlationId = correlationId,
                transactionId = request.requestId,
                status = PaymentStatus.FAILED.name,
                extra = mapOf("type" to (t::class.simpleName ?: "Unknown")),
            )
            fail(
                status = PaymentStatus.FAILED,
                code = "PROCESSOR_UNAVAILABLE",
                message = "Could not reach the payment processor. No money has been taken.",
                risk = risk,
            )
            return@flow
        }

        // ---- (5) Persist + commit ---------------------------------------
        // The merchant's own reference is echoed onto the result so the
        // receipt can print it. It carries no cardholder data.
        finish(
            authorised.copy(
                riskDecision = authorised.riskDecision ?: risk,
                reference = authorised.reference ?: request.reference,
            ),
        )
    }

    private fun buildRiskMessage(risk: RiskDecision): String {
        val reasons = risk.reasonCodes.joinToString(", ").ifBlank { "risk policy" }
        return "Blocked by the risk engine (score ${risk.score}/100): $reasons."
    }
}

/**
 * Generates the human-readable references used on receipts and in the
 * transaction list. The format `ATX-XXXXXXXX` is stable, sortable and safe
 * to display — it contains no card data and no merchant secrets.
 */
object ReferenceIds {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTUVWXYZ"

    /** `ATX-XXXXXXXX` transaction reference, derived deterministically from a seed id. */
    fun transaction(seed: String): String {
        val digits = seed.fold(0L) { acc, c -> acc * 31 + c.code }
        var value = digits and 0x7FFFFFFFFFFFFFFF
        val out = StringBuilder(8)
        repeat(8) {
            out.append(ALPHABET[(value % ALPHABET.length).toInt()])
            value /= ALPHABET.length
        }
        return "ATX-$out"
    }
}
