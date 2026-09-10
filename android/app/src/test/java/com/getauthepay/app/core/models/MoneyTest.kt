package com.getauthepay.app.core.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Money arithmetic. Getting currency handling wrong is a direct financial
 * loss, so currency mixing is a hard error rather than a silent conversion.
 */
class MoneyTest {

    @Test
    fun `of builds money from a currency code`() {
        val m = Money.of(BigDecimal("10.00"), "BWP")
        assertEquals("BWP", m.currency.currencyCode)
        assertEquals(BigDecimal("10.00"), m.amount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid currency code is rejected`() {
        Money.of(BigDecimal.ONE, "NOT_A_CURRENCY")
    }

    @Test
    fun `addition keeps the same currency`() {
        val a = Money.of(BigDecimal("10.50"), "BWP")
        val b = Money.of(BigDecimal("4.25"), "BWP")
        assertEquals(BigDecimal("14.75"), a.plus(b).amount)
    }

    @Test
    fun `subtraction works`() {
        val a = Money.of(BigDecimal("10.00"), "BWP")
        val b = Money.of(BigDecimal("4.00"), "BWP")
        assertEquals(BigDecimal("6.00"), a.minus(b).amount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `adding different currencies is rejected`() {
        Money.of(BigDecimal.ONE, "BWP").plus(Money.of(BigDecimal.ONE, "ZAR"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `subtracting different currencies is rejected`() {
        Money.of(BigDecimal.ONE, "BWP").minus(Money.of(BigDecimal.ONE, "USD"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `comparing different currencies is rejected`() {
        Money.of(BigDecimal.ONE, "BWP").compareTo(Money.of(BigDecimal.ONE, "ZAR"))
    }

    @Test
    fun `comparison orders amounts correctly`() {
        val small = Money.of(BigDecimal("1.00"), "BWP")
        val large = Money.of(BigDecimal("2.00"), "BWP")
        assertTrue(small < large)
        assertEquals(0, small.compareTo(Money.of(BigDecimal("1.00"), "BWP")))
    }

    @Test
    fun `zero detection`() {
        assertTrue(Money.of(BigDecimal.ZERO, "BWP").isZero())
        assertTrue(Money.of(BigDecimal("0.01"), "BWP").isPositive())
    }

    @Test
    fun `normalised rounds to the currency fraction digits`() {
        val m = Money.of(BigDecimal("10.567"), "BWP")
        assertEquals(BigDecimal("10.57"), m.normalised().amount)
    }

    @Test
    fun `zero helper returns zero in the same currency`() {
        val z = Money.of(BigDecimal("99.99"), "ZAR").zero()
        assertTrue(z.isZero())
        assertEquals("ZAR", z.currency.currencyCode)
    }

    @Test
    fun `multiple currencies are supported without code changes`() {
        listOf("BWP", "ZAR", "USD", "ZMW", "KES", "NGN").forEach { code ->
            val m = Money.of(BigDecimal("1.00"), code)
            assertEquals(code, m.currency.currencyCode)
        }
    }
}
