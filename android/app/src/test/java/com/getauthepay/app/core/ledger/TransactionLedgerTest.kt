package com.getauthepay.app.core.ledger

import com.getauthepay.app.core.models.Money
import com.getauthepay.app.core.models.PaymentResult
import com.getauthepay.app.core.models.PaymentStatus
import com.getauthepay.app.core.models.TransactionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Ledger recording and refund state transitions.
 *
 * Two properties matter most for a bank submission: a payment is never
 * recorded as a sale unless it was authorised, and refunds accumulate
 * correctly so a merchant cannot refund more than the original sale.
 */
class TransactionLedgerTest {

    private val amount = Money.of(BigDecimal("100.00"), "BWP")

    private fun result(
        status: PaymentStatus,
        txnId: String? = "ATX-1",
    ) = PaymentResult(
        requestId = "req-1",
        status = status,
        transactionId = txnId,
        correlationId = "corr-1",
    )

    // ---- recording -------------------------------------------------------

    @Test
    fun `approved payment is recorded as approved`() = runTest {
        val ledger = TransactionLedger.empty()
        val txn = ledger.record(result(PaymentStatus.APPROVED), "MID", "TID", amount)
        assertNotNull(txn)
        assertEquals(TransactionStatus.APPROVED, txn!!.status)
        assertEquals(1, ledger.size())
    }

    @Test
    fun `declined payment is recorded but not as a sale`() = runTest {
        val ledger = TransactionLedger.empty()
        val txn = ledger.record(result(PaymentStatus.DECLINED), "MID", "TID", amount)
        assertEquals(TransactionStatus.DECLINED, txn!!.status)
    }

    @Test
    fun `result without a transaction id is not invented`() = runTest {
        val ledger = TransactionLedger.empty()
        // A terminal, non-success result legitimately carries no transaction id (e.g. a
        // risk-preflight failure). The ledger must drop it rather than invent one.
        val txn = ledger.record(result(PaymentStatus.FAILED, txnId = null), "MID", "TID", amount)
        assertNull(txn)
        assertEquals(0, ledger.size())
    }

    @Test
    fun `status mapping covers every payment state`() = runTest {
        assertEquals(TransactionStatus.APPROVED, TransactionLedger.statusFor(PaymentStatus.APPROVED))
        assertEquals(TransactionStatus.DECLINED, TransactionLedger.statusFor(PaymentStatus.DECLINED))
        assertEquals(TransactionStatus.REVERSED, TransactionLedger.statusFor(PaymentStatus.REVERSED))
        assertEquals(TransactionStatus.REFUNDED, TransactionLedger.statusFor(PaymentStatus.REFUNDED))
        listOf(
            PaymentStatus.CANCELLED, PaymentStatus.TIMEOUT, PaymentStatus.FAILED,
            PaymentStatus.CREATED, PaymentStatus.READY_FOR_TAP,
            PaymentStatus.CARD_DETECTED, PaymentStatus.PROCESSING,
            PaymentStatus.AUTHORIZING,
        ).forEach {
            assertEquals("$it must map to NOT_COMPLETED", TransactionStatus.NOT_COMPLETED, TransactionLedger.statusFor(it))
        }
    }

    @Test
    fun `recording the same transaction twice does not duplicate it`() = runTest {
        val ledger = TransactionLedger.empty()
        ledger.record(result(PaymentStatus.APPROVED), "MID", "TID", amount)
        ledger.record(result(PaymentStatus.APPROVED), "MID", "TID", amount)
        assertEquals(1, ledger.size())
    }

    // ---- refunds ---------------------------------------------------------

    @Test
    fun `full refund moves the transaction to REFUNDED`() = runTest {
        val ledger = TransactionLedger.empty()
        ledger.record(result(PaymentStatus.APPROVED), "MID", "TID", amount)
        val refunded = ledger.applyRefund("ATX-1", amount)
        assertEquals(TransactionStatus.REFUNDED, refunded!!.status)
    }

    @Test
    fun `partial refund moves the transaction to PARTIALLY_REFUNDED`() = runTest {
        val ledger = TransactionLedger.empty()
        ledger.record(result(PaymentStatus.APPROVED), "MID", "TID", amount)
        val refunded = ledger.applyRefund("ATX-1", Money.of(BigDecimal("40.00"), "BWP"))
        assertEquals(TransactionStatus.PARTIALLY_REFUNDED, refunded!!.status)
        assertEquals(BigDecimal("40.00"), refunded.refundedAmount!!.amount)
    }

    @Test
    fun `repeated partial refunds accumulate`() = runTest {
        val ledger = TransactionLedger.empty()
        ledger.record(result(PaymentStatus.APPROVED), "MID", "TID", amount)
        ledger.applyRefund("ATX-1", Money.of(BigDecimal("40.00"), "BWP"))
        val second = ledger.applyRefund("ATX-1", Money.of(BigDecimal("30.00"), "BWP"))
        assertEquals(BigDecimal("70.00"), second!!.refundedAmount!!.amount)
        assertEquals(TransactionStatus.PARTIALLY_REFUNDED, second.status)
    }

    @Test
    fun `refunds reaching the original amount complete the refund`() = runTest {
        val ledger = TransactionLedger.empty()
        ledger.record(result(PaymentStatus.APPROVED), "MID", "TID", amount)
        ledger.applyRefund("ATX-1", Money.of(BigDecimal("60.00"), "BWP"))
        val done = ledger.applyRefund("ATX-1", Money.of(BigDecimal("40.00"), "BWP"))
        assertEquals(TransactionStatus.REFUNDED, done!!.status)
    }

    @Test
    fun `zero refund is ignored`() = runTest {
        val ledger = TransactionLedger.empty()
        ledger.record(result(PaymentStatus.APPROVED), "MID", "TID", amount)
        assertNull(ledger.applyRefund("ATX-1", Money.of(BigDecimal.ZERO, "BWP")))
    }

    @Test
    fun `refund on an unknown transaction is a no-op`() = runTest {
        assertNull(TransactionLedger.empty().applyRefund("nope", amount))
    }

    @Test
    fun `refund pending marks the transaction`() = runTest {
        val ledger = TransactionLedger.empty()
        ledger.record(result(PaymentStatus.APPROVED), "MID", "TID", amount)
        val pending = ledger.markRefundPending("ATX-1")
        assertEquals(TransactionStatus.REFUND_PENDING, pending!!.status)
    }

    // ---- queries ---------------------------------------------------------

    @Test
    fun `transactions are found by id`() = runTest {
        val ledger = TransactionLedger.empty()
        ledger.record(result(PaymentStatus.APPROVED), "MID", "TID", amount)
        assertNotNull(ledger.find("ATX-1"))
        assertNull(ledger.find("missing"))
    }

    @Test
    fun `transactions are grouped by terminal`() = runTest {
        val ledger = TransactionLedger.empty()
        ledger.record(result(PaymentStatus.APPROVED, "ATX-1"), "MID", "TID-A", amount)
        ledger.record(result(PaymentStatus.APPROVED, "ATX-2"), "MID", "TID-B", amount)
        assertEquals(1, ledger.byTerminal("TID-A").size)
        assertEquals(1, ledger.byTerminal("TID-B").size)
    }

    @Test
    fun `snapshot is ordered newest first`() = runTest {
        val ledger = TransactionLedger.empty()
        ledger.record(result(PaymentStatus.APPROVED, "ATX-1"), "MID", "TID", amount)
        ledger.record(result(PaymentStatus.APPROVED, "ATX-2"), "MID", "TID", amount)
        assertEquals(2, ledger.snapshot().size)
    }

    @Test
    fun `replaceAll installs a server-authoritative list`() = runTest {
        val ledger = TransactionLedger.empty()
        ledger.record(result(PaymentStatus.APPROVED, "ATX-1"), "MID", "TID", amount)
        val server = ledger.snapshot().map { it.copy(status = TransactionStatus.DECLINED) }
        ledger.replaceAll(server)
        assertEquals(TransactionStatus.DECLINED, ledger.find("ATX-1")!!.status)
    }

    @Test
    fun `clear empties the ledger`() = runTest {
        val ledger = TransactionLedger.empty()
        ledger.record(result(PaymentStatus.APPROVED), "MID", "TID", amount)
        ledger.clear()
        assertEquals(0, ledger.size())
    }
}
