package com.getauthepay.app.core.payment

import com.getauthepay.app.core.ReferenceIds
import com.getauthepay.app.core.log.SecureLogger
import com.getauthepay.app.core.models.Money
import com.getauthepay.app.core.models.PaymentRequest
import com.getauthepay.app.core.models.PaymentResult
import com.getauthepay.app.core.models.PaymentStatus
import com.getauthepay.app.core.models.RiskDecision
import com.getauthepay.app.core.models.TestScenario
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Deterministic processor adapter used for UI development, automated tests
 * and bank demonstrations.
 *
 * It is NEVER connected to a real card network, and every result it produces
 * is rendered behind a SANDBOX banner in the UI. [isSandbox] is `true`, and
 * release builds do not construct this class at all (see `ServiceLocator`).
 *
 * The sandbox honours [PaymentRequest.sandboxScenario] so the full failure
 * matrix required by the build directive can be exercised on demand:
 *
 *   TEST_APPROVED, TEST_DECLINED, TEST_TIMEOUT, TEST_CANCELLED,
 *   TEST_DUPLICATE, TEST_NETWORK_FAILURE, TEST_PROCESSOR_ERROR,
 *   TEST_RISK_DECLINE
 *
 * Every terminal outcome carries a transaction reference, exactly as a real
 * acquirer would (declines are referenceable for reconciliation and dispute
 * handling), so the local ledger and history screens behave identically to
 * production.
 */
class SandboxPaymentProcessor(
    override val name: String = "sandbox.processor",
    override val isSandbox: Boolean = true,
    private val roundTripMs: Long = 350L,
) : PaymentProcessor {

    override suspend fun authorise(
        request: PaymentRequest,
        amount: Money,
        riskDecision: RiskDecision,
        idempotencyKey: String,
    ): PaymentResult {
        delay(roundTripMs)
        val correlationId = UUID.randomUUID().toString()
        val reference = ReferenceIds.transaction(request.requestId)

        SecureLogger.event(
            event = "sandbox.processor.authorise",
            correlationId = correlationId,
            transactionId = reference,
            status = PaymentStatus.AUTHORIZING.name,
            extra = mapOf(
                "idempotencyKey" to idempotencyKey,
                "scenario" to (request.sandboxScenario?.name ?: "TEST_APPROVED"),
            ),
        )

        // Risk BLOCK is enforced again here so a sandbox build wired without
        // the engine's short-circuit still fails closed.
        if (riskDecision.decision == RiskDecision.Decision.BLOCK) {
            return PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.DECLINED,
                transactionId = reference,
                correlationId = correlationId,
                errorCode = "RISK_BLOCKED",
                errorMessage = "Blocked by risk engine (sandbox)",
                riskDecision = riskDecision,
            )
        }

        return when (request.sandboxScenario ?: TestScenario.TEST_APPROVED) {
            TestScenario.TEST_APPROVED -> PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.APPROVED,
                transactionId = reference,
                authCode = "SBX${(100000..999999).random()}",
                maskedPan = PaymentResult.sandboxMaskedPan(),
                cardType = "VISA",
                processorReference = "REF${(100000000..999999999).random()}",
                correlationId = correlationId,
                riskDecision = riskDecision,
            )

            TestScenario.TEST_DECLINED -> PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.DECLINED,
                transactionId = reference,
                correlationId = correlationId,
                errorCode = "DO_NOT_HONOR",
                errorMessage = "The issuer declined this card (sandbox). Ask for another payment method.",
                riskDecision = riskDecision,
            )

            TestScenario.TEST_RISK_DECLINE -> PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.DECLINED,
                transactionId = reference,
                correlationId = correlationId,
                errorCode = "RISK_DECLINED",
                errorMessage = "This payment was stopped by the risk engine (sandbox).",
                riskDecision = riskDecision,
            )

            TestScenario.TEST_TIMEOUT -> PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.TIMEOUT,
                transactionId = reference,
                correlationId = correlationId,
                errorCode = "TIMEOUT",
                errorMessage = "The processor did not respond in time (sandbox). No money was taken.",
                riskDecision = riskDecision,
            )

            TestScenario.TEST_CANCELLED -> PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.CANCELLED,
                transactionId = reference,
                correlationId = correlationId,
                errorCode = "CANCELLED",
                errorMessage = "The payment was cancelled (sandbox).",
                riskDecision = riskDecision,
            )

            TestScenario.TEST_DUPLICATE -> PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.DECLINED,
                transactionId = reference,
                correlationId = correlationId,
                errorCode = "DUPLICATE_TRANSACTION",
                errorMessage = "Duplicate transaction — this payment was already submitted (sandbox).",
                riskDecision = riskDecision,
            )

            TestScenario.TEST_NETWORK_FAILURE -> PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.FAILED,
                transactionId = reference,
                correlationId = correlationId,
                errorCode = "NETWORK_UNAVAILABLE",
                errorMessage = "No network connection. Payment unavailable (sandbox).",
                riskDecision = riskDecision,
            )

            TestScenario.TEST_PROCESSOR_ERROR -> PaymentResult(
                requestId = request.requestId,
                status = PaymentStatus.FAILED,
                transactionId = reference,
                correlationId = correlationId,
                errorCode = "PROCESSOR_ERROR",
                errorMessage = "The processor returned an error (sandbox). Please retry.",
                riskDecision = riskDecision,
            )
        }
    }

    override suspend fun void(transactionId: String, correlationId: String): PaymentResult {
        delay(roundTripMs)
        SecureLogger.event(
            event = "sandbox.processor.void",
            correlationId = correlationId,
            transactionId = transactionId,
            status = PaymentStatus.REVERSED.name,
        )
        return PaymentResult(
            requestId = transactionId,
            status = PaymentStatus.REVERSED,
            transactionId = transactionId,
            correlationId = correlationId,
        )
    }
}
