package com.getauthepay.app.core.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Refund domain rules (directive section 23).
 *
 * Refunds move money out of the merchant's account, so the model refuses to
 * be constructed without an auditable reason, and refuses negative amounts.
 */
class RefundTest {

    private fun refund(
        amount: String = "50.00",
        status: RefundStatus = RefundStatus.REFUND_PENDING,
        reason: String = "CUSTOMER_REQUEST",
    ) = Refund(
        refundId = "RF-1",
        transactionId = "ATX-1",
        merchantId = "MID-1",
        amount = Money.of(BigDecimal(amount), "BWP"),
        status = status,
        reasonCode = reason,
        createdAtMs = 1L,
        requestedByUserId = "user-1",
    )

    @Test
    fun `a valid refund is constructed`() {
        assertEquals(RefundStatus.REFUND_PENDING, refund().status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank refund id is rejected`() {
        refund().copy(refundId = " ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank reason code is rejected`() {
        refund(reason = "  ")
    }

    @Test
    fun `zero-amount refund is permitted`() {
        assertEquals(BigDecimal.ZERO.setScale(2), refund(amount = "0.00").amount.amount)
    }

    @Test
    fun `all four refund statuses exist`() {
        assertEquals(
            setOf("REFUND_PENDING", "REFUNDED", "PARTIAL_REFUND", "REFUND_FAILED"),
            RefundStatus.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `refund carries an audit trail of who requested it`() {
        assertEquals("user-1", refund().requestedByUserId)
    }

    @Test
    fun `partial refund is represented explicitly`() {
        assertEquals(RefundStatus.PARTIAL_REFUND, refund(status = RefundStatus.PARTIAL_REFUND).status)
    }

    @Test
    fun `refund amount keeps the transaction currency`() {
        val r = Refund(
            refundId = "RF-2",
            transactionId = "ATX-1",
            merchantId = "MID-1",
            amount = Money.of(BigDecimal("10.00"), "ZAR"),
            status = RefundStatus.REFUNDED,
            reasonCode = "CUSTOMER_REQUEST",
            createdAtMs = 1L,
            requestedByUserId = "user-1",
        )
        assertEquals("ZAR", r.amount.currency.currencyCode)
    }

    @Test
    fun `full refund amount matches the original`() {
        val original = Money.of(BigDecimal("50.00"), "BWP")
        val r = refund(amount = "50.00")
        assertEquals(0, original.compareTo(r.amount))
        assertTrue(r.amount.isPositive())
    }
}
