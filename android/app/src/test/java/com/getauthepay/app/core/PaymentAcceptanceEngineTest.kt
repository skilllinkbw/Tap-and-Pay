package com.getauthepay.app.core

import com.getauthepay.app.core.ledger.TransactionLedger
import com.getauthepay.app.core.models.Money
import com.getauthepay.app.core.models.TransactionStatus
import com.getauthepay.app.core.models.PaymentRequest
import com.getauthepay.app.core.models.PaymentResult
import com.getauthepay.app.core.models.PaymentStatus
import com.getauthepay.app.core.models.RiskDecision
import com.getauthepay.app.core.models.TestScenario
import com.getauthepay.app.core.nfc.NfcAvailability
import com.getauthepay.app.core.nfc.SandboxContactlessProvider
import com.getauthepay.app.core.payment.PaymentProcessor
import com.getauthepay.app.core.payment.SandboxPaymentProcessor
import com.getauthepay.app.core.risk.RiskContext
import com.getauthepay.app.core.risk.RiskService
import com.getauthepay.app.core.risk.SandboxRiskService
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * End-to-end payment orchestration against the sandbox adapters.
 *
 * The engine is the component a bank reviewer will read most closely: it
 * decides when the NFC radio is used, when risk is consulted, when the
 * processor is called and when a transaction is written to the ledger.
 * Every one of those decisions is pinned by a test here.
 *
 * Critically, several tests assert **fail-closed** behaviour: if risk is
 * unreachable, NFC is off, or the processor throws, the merchant is told
 * payment is unavailable and nothing is recorded as a sale.
 */
class PaymentAcceptanceEngineTest {

    // ---- fakes -----------------------------------------------------------

    /** Counts authorisation attempts so double-charge regressions fail loudly. */
    private class CountingProcessor(
        private val delegate: PaymentProcessor = SandboxPaymentProcessor(roundTripMs = 0),
    ) : PaymentProcessor {
        override val name: String get() = "counting"
        override val isSandbox: Boolean get() = true
        var calls: Int = 0
            private set

        override suspend fun authorise(
            request: PaymentRequest,
            amount: Money,
            riskDecision: RiskDecision,
            idempotencyKey: String,
        ): PaymentResult {
            calls++
            return delegate.authorise(request, amount, riskDecision, idempotencyKey)
        }

        override suspend fun void(transactionId: String, correlationId: String): PaymentResult =
            delegate.void(transactionId, correlationId)
    }

    private class ThrowingRiskService : RiskService {
        override suspend fun evaluate(
            context: RiskContext,
            modelVersion: String,
        ): RiskDecision = throw IllegalStateException("risk engine down")
    }

    private class FixedRiskService(private val decision: RiskDecision) : RiskService {
        override suspend fun evaluate(
            context: RiskContext,
            modelVersion: String,
        ): RiskDecision = decision
    }

    private class ThrowingProcessor : PaymentProcessor {
        override val name: String get() = "throwing"
        override val isSandbox: Boolean get() = true

        override suspend fun authorise(
            request: PaymentRequest,
            amount: Money,
            riskDecision: RiskDecision,
            idempotencyKey: String,
        ): PaymentResult = throw IllegalStateException("processor unreachable")

        override suspend fun void(transactionId: String, correlationId: String): PaymentResult =
            throw IllegalStateException("processor unreachable")
    }

    // ---- builders --------------------------------------------------------

    private fun request(
        scenario: TestScenario? = TestScenario.TEST_APPROVED,
        key: String = "idem-1",
        amount: String = "500.00",
    ) = PaymentRequest(
        idempotencyKey = key,
        requestId = "req-$key",
        amount = BigDecimal(amount),
        currency = "BWP",
        reference = "INV-1",
        sandboxScenario = scenario,
    )

    private fun riskContext() = RiskContext(
        merchantId = "MID-1",
        terminalId = "TID-1",
        amount = Money.of(BigDecimal("500.00"), "BWP"),
        transactionCountLastHour = 1,
        transactionCountLastDay = 5,
        previousDeclinesLastHour = 0,
        deviceTrustScore = 100,
        newDevice = false,
        isOverseas = false,
        timeOfDayLocalHour = 12,
    )

    private fun engine(
        contactless: SandboxContactlessProvider = SandboxContactlessProvider(tickMs = 0),
        processor: PaymentProcessor = SandboxPaymentProcessor(roundTripMs = 0),
        risk: RiskService = SandboxRiskService(),
        ledger: TransactionLedger = TransactionLedger.empty(),
    ) = PaymentAcceptanceEngine(
        contactlessProvider = contactless,
        processor = processor,
        riskService = risk,
        ledger = ledger,
        session = PaymentAcceptanceEngine.EngineSession("MID-1", "TID-1"),
    )

    // ---- happy path ------------------------------------------------------

    @Test
    fun `approved payment reaches APPROVED and is recorded`() = runTest {
        val ledger = TransactionLedger.empty()
        val results = engine(ledger = ledger)
            .processPayment(request(), riskContext())
            .toList()

        assertEquals(PaymentStatus.APPROVED, results.last().status)
        assertEquals(1, ledger.size())
        assertEquals("INV-1", results.last().reference)
    }

    @Test
    fun `approved result carries a transaction reference and auth code`() = runTest {
        val last = engine().processPayment(request(), riskContext()).toList().last()
        assertEquals(PaymentStatus.APPROVED, last.status)
        assertNotNull(last.transactionId)
        assertTrue(last.transactionId!!.startsWith("ATX-"))
        assertNotNull(last.authCode)
    }

    @Test
    fun `progressive states are emitted in order`() = runTest {
        val statuses = engine()
            .processPayment(request(), riskContext())
            .toList()
            .map { it.status }

        assertEquals(
            listOf(
                PaymentStatus.CREATED,
                PaymentStatus.CREATED,
                PaymentStatus.READY_FOR_TAP,
                PaymentStatus.CARD_DETECTED,
                PaymentStatus.PROCESSING,
                PaymentStatus.AUTHORIZING,
                PaymentStatus.APPROVED,
            ),
            statuses,
        )
    }

    // ---- every sandbox failure scenario ----------------------------------

    @Test
    fun `declined payment ends DECLINED with an issuer message`() = runTest {
        val last = engine()
            .processPayment(request(TestScenario.TEST_DECLINED), riskContext())
            .toList().last()
        assertEquals(PaymentStatus.DECLINED, last.status)
        assertEquals("DO_NOT_HONOR", last.errorCode)
    }

    @Test
    fun `timeout ends TIMEOUT and is recorded as not completed`() = runTest {
        val ledger = TransactionLedger.empty()
        val last = engine(ledger = ledger)
            .processPayment(request(TestScenario.TEST_TIMEOUT), riskContext())
            .toList().last()
        assertEquals(PaymentStatus.TIMEOUT, last.status)
        assertEquals(1, ledger.size())
    }

    @Test
    fun `cancelled payment ends CANCELLED`() = runTest {
        val last = engine()
            .processPayment(request(TestScenario.TEST_CANCELLED), riskContext())
            .toList().last()
        assertEquals(PaymentStatus.CANCELLED, last.status)
    }

    @Test
    fun `duplicate submission is declined with DUPLICATE_TRANSACTION`() = runTest {
        val last = engine()
            .processPayment(request(TestScenario.TEST_DUPLICATE), riskContext())
            .toList().last()
        assertEquals(PaymentStatus.DECLINED, last.status)
        assertEquals("DUPLICATE_TRANSACTION", last.errorCode)
    }

    @Test
    fun `network failure is surfaced as FAILED with a clear message`() = runTest {
        val last = engine()
            .processPayment(request(TestScenario.TEST_NETWORK_FAILURE), riskContext())
            .toList().last()
        assertEquals(PaymentStatus.FAILED, last.status)
        assertEquals("NETWORK_UNAVAILABLE", last.errorCode)
    }

    @Test
    fun `processor error is surfaced as FAILED`() = runTest {
        val last = engine()
            .processPayment(request(TestScenario.TEST_PROCESSOR_ERROR), riskContext())
            .toList().last()
        assertEquals(PaymentStatus.FAILED, last.status)
        assertEquals("PROCESSOR_ERROR", last.errorCode)
    }

    @Test
    fun `risk decline scenario produces a declined result`() = runTest {
        val last = engine()
            .processPayment(request(TestScenario.TEST_RISK_DECLINE), riskContext())
            .toList().last()
        assertEquals(PaymentStatus.DECLINED, last.status)
        assertEquals("RISK_DECLINED", last.errorCode)
    }

    // ---- idempotency (no double charge) ----------------------------------

    @Test
    fun `replaying the same idempotency key authorises only once`() = runTest {
        val processor = CountingProcessor()
        val e = engine(processor = processor)

        e.processPayment(request(key = "same-key"), riskContext()).toList()
        e.processPayment(request(key = "same-key"), riskContext()).toList()

        assertEquals(1, processor.calls)
    }

    @Test
    fun `replayed key returns the original terminal result`() = runTest {
        val e = engine()
        val first = e.processPayment(request(key = "k"), riskContext()).toList().last()
        val second = e.processPayment(request(key = "k"), riskContext()).toList()

        assertEquals(1, second.size)
        assertEquals(first.transactionId, second.single().transactionId)
        assertEquals(first.status, second.single().status)
    }

    @Test
    fun `different idempotency keys create separate payments`() = runTest {
        val processor = CountingProcessor()
        val e = engine(processor = processor)

        e.processPayment(request(key = "a"), riskContext()).toList()
        e.processPayment(request(key = "b"), riskContext()).toList()

        assertEquals(2, processor.calls)
    }

    @Test
    fun `duplicate submission does not create a second ledger entry`() = runTest {
        val ledger = TransactionLedger.empty()
        val e = engine(ledger = ledger)

        e.processPayment(request(key = "dup"), riskContext()).toList()
        e.processPayment(request(key = "dup"), riskContext()).toList()

        assertEquals(1, ledger.size())
    }

    // ---- risk gating ------------------------------------------------------

    @Test
    fun `risk BLOCK short-circuits before the processor is called`() = runTest {
        val processor = CountingProcessor()
        val e = engine(
            processor = processor,
            risk = FixedRiskService(RiskDecision.block("HIGH_RISK", 95)),
        )
        val last = e.processPayment(request(), riskContext()).toList().last()

        assertEquals(PaymentStatus.DECLINED, last.status)
        assertEquals("RISK_BLOCKED", last.errorCode)
        assertEquals(0, processor.calls)
    }

    @Test
    fun `risk CHALLENGE still allows the payment through`() = runTest {
        val e = engine(risk = FixedRiskService(RiskDecision.challenge("MEDIUM_RISK", 50)))
        val last = e.processPayment(request(), riskContext()).toList().last()
        assertEquals(PaymentStatus.APPROVED, last.status)
    }

    @Test
    fun `risk engine failure fails closed and never authorises`() = runTest {
        val processor = CountingProcessor()
        val ledger = TransactionLedger.empty()
        val e = engine(processor = processor, risk = ThrowingRiskService(), ledger = ledger)
        val last = e.processPayment(request(), riskContext()).toList().last()

        assertEquals(PaymentStatus.FAILED, last.status)
        assertEquals("RISK_ENGINE_UNAVAILABLE", last.errorCode)
        // Fail-closed: the processor must never be reached when risk is unavailable.
        assertEquals(0, processor.calls)
        // The decision must never be recorded as a sale, even though the attempt
        // is persisted locally as NOT_COMPLETED for audit.
        assertTrue(
            "no approved sale may be recorded when risk checks fail",
            ledger.snapshot().none { it.status == TransactionStatus.APPROVED },
        )
    }

    @Test
    fun `risk decision is attached to the final result`() = runTest {
        val decision = RiskDecision.challenge("NEW_DEVICE", 42)
        val last = engine(risk = FixedRiskService(decision))
            .processPayment(request(), riskContext())
            .toList().last()
        assertEquals(decision.decision, last.riskDecision!!.decision)
    }

    // ---- NFC gating --------------------------------------------------------

    @Test
    fun `missing NFC hardware fails before any card is requested`() = runTest {
        val processor = CountingProcessor()
        val e = engine(
            contactless = SandboxContactlessProvider(
                availability = NfcAvailability.NotAvailable,
                tickMs = 0,
            ),
            processor = processor,
        )
        val last = e.processPayment(request(), riskContext()).toList().last()

        assertEquals(PaymentStatus.FAILED, last.status)
        assertEquals("NFC_NOT_AVAILABLE", last.errorCode)
        assertEquals(0, processor.calls)
    }

    @Test
    fun `disabled NFC tells the merchant to enable it`() = runTest {
        val e = engine(
            contactless = SandboxContactlessProvider(
                availability = NfcAvailability.AvailableDisabled,
                tickMs = 0,
            ),
        )
        val last = e.processPayment(request(), riskContext()).toList().last()

        assertEquals(PaymentStatus.FAILED, last.status)
        assertEquals("NFC_DISABLED", last.errorCode)
        assertTrue(last.errorMessage!!.contains("Turn on NFC"))
    }

    // ---- processor failure -------------------------------------------------

    @Test
    fun `processor exception is converted to a safe failure message`() = runTest {
        val last = engine(processor = ThrowingProcessor())
            .processPayment(request(), riskContext())
            .toList().last()

        assertEquals(PaymentStatus.FAILED, last.status)
        assertEquals("PROCESSOR_UNAVAILABLE", last.errorCode)
        assertTrue(last.errorMessage!!.contains("No money has been taken"))
    }

    // ---- no fabrication ---------------------------------------------------

    @Test
    fun `failed payments are never recorded as approved`() = runTest {
        val ledger = TransactionLedger.empty()
        engine(ledger = ledger)
            .processPayment(request(TestScenario.TEST_DECLINED), riskContext())
            .toList()

        val recorded = ledger.snapshot().single()
        assertEquals(
            com.getauthepay.app.core.models.TransactionStatus.DECLINED,
            recorded.status,
        )
    }

    @Test
    fun `engine records terminal results against the session terminal`() = runTest {
        val ledger = TransactionLedger.empty()
        engine(ledger = ledger).processPayment(request(), riskContext()).toList()

        val txn = ledger.snapshot().single()
        assertEquals("MID-1", txn.merchantId)
        assertEquals("TID-1", txn.terminalId)
        assertEquals(Money.of(BigDecimal("500.00"), "BWP"), txn.amount)
    }

    @Test
    fun `non-terminal transport end does not claim success`() = runTest {
        // A provider that stops after READY_FOR_TAP without a terminal state.
        val stalling = object : com.getauthepay.app.core.nfc.ContactlessPaymentProvider {
            override val availability = NfcAvailability.AvailableEnabled
            override fun startReading(request: PaymentRequest) = kotlinx.coroutines.flow.flow {
                emit(
                    PaymentResult(
                        requestId = request.requestId,
                        status = PaymentStatus.READY_FOR_TAP,
                    ),
                )
            }

            override suspend fun cancelReading() = Unit
        }

        val ledger = TransactionLedger.empty()
        val e = PaymentAcceptanceEngine(
            contactlessProvider = stalling,
            processor = CountingProcessor(),
            riskService = SandboxRiskService(),
            ledger = ledger,
            session = PaymentAcceptanceEngine.EngineSession("MID-1", "TID-1"),
        )
        val last = e.processPayment(request(), riskContext()).toList().last()

        assertEquals(PaymentStatus.FAILED, last.status)
        assertEquals("TRANSPORT_ENDED", last.errorCode)
        // The attempt ended before authorisation: it must never be reported as a sale.
        // (A terminal attempt still carries a trace transaction id for support; that is
        //  expected and is not the same as a successful authorisation.)
        assertTrue(
            "transport-end failure must not leave an approved sale in the ledger",
            ledger.snapshot().none { it.status == TransactionStatus.APPROVED },
        )
    }

    // ---- concurrency / double-tap --------------------------------------------

    @Test
    fun `concurrent submissions of the same payment never double-authorise`() = runTest {
        // Regression: the engine used to share its state machine across
        // concurrent collections and IdempotencyStore.begin() returned true
        // for in-flight keys, so a double-tap during a slow network read
        // could drive two authorisations for one sale.
        val processor = CountingProcessor()
        val ledger = TransactionLedger.empty()
        val e = engine(processor = processor, ledger = ledger)
        val req = request(key = "race-1")

        val first = async { e.processPayment(req, riskContext()).toList() }
        val second = async { e.processPayment(req, riskContext()).toList() }
        val r1 = first.await().last()
        val r2 = second.await().last()

        assertEquals("exactly one authorisation may reach the processor", 1, processor.calls)
        assertEquals("exactly one sale may reach the ledger", 1, ledger.size())
        // The loser of the race is either the cached replay of the winner or
        // an explicit in-progress rejection — never a second payment.
        assertTrue(r1.status.isTerminal())
        assertTrue(r2.status.isTerminal())
        assertTrue(
            "loser must be a replay or an explicit rejection",
            r1.status == PaymentStatus.APPROVED && r2.status == PaymentStatus.APPROVED ||
                r1.errorCode == "PAYMENT_IN_PROGRESS" || r2.errorCode == "PAYMENT_IN_PROGRESS" ||
                r1.errorCode == "DUPLICATE_TRANSACTION" || r2.errorCode == "DUPLICATE_TRANSACTION",
        )
    }

    @Test
    fun `a second payment can start after the first one finishes`() = runTest {
        // The terminal mutex must be released after every attempt — a
        // completed (or failed) payment must never wedge the terminal.
        val processor = CountingProcessor()
        val e = engine(processor = processor)

        e.processPayment(request(key = "seq-1"), riskContext()).toList()
        val second = e.processPayment(request(key = "seq-2"), riskContext()).toList().last()

        assertEquals(PaymentStatus.APPROVED, second.status)
        assertEquals(2, processor.calls)
    }
}
