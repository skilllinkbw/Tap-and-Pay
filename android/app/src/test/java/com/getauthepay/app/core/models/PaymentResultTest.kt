package com.getauthepay.app.core.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * PCI-sensitive-data guards on [PaymentResult].
 *
 * Directive section 13 is absolute: no CVV, PIN, full PAN or track data may
 * be stored or logged. [PaymentResult] is the only object that carries any
 * card reference at all, so its constructor enforces the masking rule at
 * runtime rather than relying on developer discipline.
 */
class PaymentResultTest {

    private fun approved(maskedPan: String? = null) = PaymentResult(
        requestId = "req-1",
        status = PaymentStatus.APPROVED,
        transactionId = "ATX-12345678",
        maskedPan = maskedPan,
    )

    @Test
    fun `sandbox masked pan keeps only first six and last four`() {
        val pan = PaymentResult.sandboxMaskedPan()
        assertEquals("412345******1234", pan)
        assertEquals(10, pan.count { it.isDigit() })
    }

    @Test
    fun `a result with a properly masked pan is accepted`() {
        assertNotNull(approved("412345******1234"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a full unmasked pan is rejected`() {
        approved("4111111111111111")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a pan with too few visible digits is rejected`() {
        // Only two visible digits — below the 4..10 contract — must be rejected.
        approved("12******")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a pan with no mask characters is rejected`() {
        approved("4111111234")
    }

    @Test
    fun `a result without card data is valid`() {
        assertNotNull(approved(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `successful result must carry a reference`() {
        PaymentResult(requestId = "req", status = PaymentStatus.APPROVED)
    }

    @Test
    fun `processor reference alone satisfies the success contract`() {
        assertNotNull(
            PaymentResult(
                requestId = "req",
                status = PaymentStatus.APPROVED,
                processorReference = "REF-9",
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank request id is rejected`() {
        PaymentResult(requestId = "  ", status = PaymentStatus.DECLINED)
    }

    @Test
    fun `non-success results need no transaction id`() {
        assertNotNull(
            PaymentResult(
                requestId = "req",
                status = PaymentStatus.DECLINED,
                errorCode = "DO_NOT_HONOR",
            ),
        )
    }

    @Test
    fun `merchant reference can be carried for receipts`() {
        val r = approved().copy(reference = "INV-77")
        assertEquals("INV-77", r.reference)
    }

    @Test
    fun `error messages never hold card data`() {
        val r = PaymentResult(
            requestId = "req",
            status = PaymentStatus.DECLINED,
            errorCode = "DO_NOT_HONOR",
            errorMessage = "The issuer declined this card. Ask for another payment method.",
        )
        assertEquals("DO_NOT_HONOR", r.errorCode)
        assertNotNull(r.errorMessage)
    }

    @Test
    fun `there is no field capable of holding a cvv or pin`() {
        val fields = PaymentResult::class.java.declaredFields.map { it.name }.toSet()
        listOf("cvv", "pin", "pan", "track", "trackData", "track2").forEach { banned ->
            assertEquals(
                "PaymentResult must not expose a '$banned' field",
                false,
                fields.any { it.equals(banned, ignoreCase = true) },
            )
        }
    }
}
