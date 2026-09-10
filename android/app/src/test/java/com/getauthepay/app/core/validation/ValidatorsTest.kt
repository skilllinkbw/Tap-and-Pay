package com.getauthepay.app.core.validation

import com.getauthepay.app.core.currency.CurrencyCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Amount, currency, reference and OTP-shape validation.
 *
 * These rules run before a payment is offered to a card, so a bad amount can
 * never reach the NFC reader or the processor. They are enforced locally and
 * again authoritatively server-side.
 */
class ValidatorsTest {

    // ---- amount ---------------------------------------------------------

    @Test
    fun `valid amount is accepted and scaled to the currency`() {
        assertEquals(
            BigDecimal("500.00"),
            Validators.validateAmount("500", "BWP"),
        )
    }

    @Test
    fun `amount with two decimals is accepted`() {
        assertEquals(BigDecimal("12.34"), Validators.validateAmount("12.34"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank amount is rejected`() {
        Validators.validateAmount("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-numeric amount is rejected`() {
        Validators.validateAmount("12abc")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero amount is rejected`() {
        Validators.validateAmount("0")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative amount is rejected`() {
        Validators.validateAmount("-10.00")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `amount above the single-tap limit is rejected`() {
        Validators.validateAmount("250000.01")
    }

    @Test
    fun `amount exactly at the limit is accepted`() {
        assertEquals(Validators.MAX_AMOUNT, Validators.validateAmount("250000.00"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `amount with too many decimal places is rejected`() {
        Validators.validateAmount("10.005", "BWP")
    }

    @Test
    fun `zero-decimal currency rejects fractional input`() {
        try {
            Validators.validateAmount("10.5", "JPY")
            throw AssertionError("JPY should reject a fractional amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("decimal"))
        }
    }

    @Test
    fun `minimum amount boundary`() {
        assertEquals(BigDecimal("0.01"), Validators.validateAmount("0.01"))
    }

    // ---- currency -------------------------------------------------------

    @Test
    fun `supported currencies are accepted`() {
        assertEquals("BWP", Validators.validateCurrency("BWP"))
        assertEquals("ZAR", Validators.validateCurrency("zar"))
        assertEquals("USD", Validators.validateCurrency("USD"))
        assertEquals("KES", Validators.validateCurrency("KES"))
        assertEquals("NGN", Validators.validateCurrency("NGN"))
        assertEquals("ZMW", Validators.validateCurrency("ZMW"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported currency is rejected`() {
        Validators.validateCurrency("XYZ")
    }

    @Test
    fun `every catalogued currency validates`() {
        CurrencyCatalog.allSupported().forEach { c ->
            assertEquals(c.currencyCode, Validators.validateCurrency(c.currencyCode))
        }
    }

    // ---- reference ------------------------------------------------------

    @Test
    fun `blank reference becomes null`() {
        assertEquals(null, Validators.validateReference(null))
        assertEquals(null, Validators.validateReference("   "))
    }

    @Test
    fun `normal reference is trimmed`() {
        assertEquals("INV-100", Validators.validateReference("  INV-100  "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `over-long reference is rejected`() {
        Validators.validateReference("A".repeat(65))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reference with unsupported characters is rejected`() {
        Validators.validateReference("INV@100!")
    }

    @Test
    fun `reference allows the documented punctuation`() {
        assertEquals("a-b_c.d/e:f#1", Validators.validateReference("a-b_c.d/e:f#1"))
    }

    // ---- OTP shape ------------------------------------------------------

    @Test
    fun `four to eight digit otp shapes are accepted`() {
        Validators.validateOtpShape("1234")
        Validators.validateOtpShape("123456")
        Validators.validateOtpShape("12345678")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `otp shorter than four digits is rejected`() {
        Validators.validateOtpShape("123")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `otp longer than eight digits is rejected`() {
        Validators.validateOtpShape("123456789")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `otp with letters is rejected`() {
        Validators.validateOtpShape("12a4")
    }

    @Test
    fun `maximum limit is a sane single-tap ceiling`() {
        assertEquals(BigDecimal("250000.00"), Validators.MAX_AMOUNT)
        assertEquals(BigDecimal("0.01"), Validators.MIN_AMOUNT)
    }
}
