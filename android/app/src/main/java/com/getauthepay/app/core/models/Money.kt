package com.getauthepay.app.core.models

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

/**
 * Currency-aware money type for the AuthePay domain.
 *
 * Amounts are stored as [BigDecimal] with the currency's standard fraction
 * digits (e.g. 2 for BWP/USD/ZAR, 0 for JPY-style). The class is immutable
 * and validates that the currency code is ISO 4217.
 *
 * This type deliberately has no Android dependencies so the payment logic
 * can be unit-tested on the JVM without instrumentation.
 */
data class Money(
    val amount: BigDecimal,
    val currency: Currency
) : Comparable<Money> {

    init {
        require(currency.currencyCode.length == 3) {
            "Currency code must be ISO 4217 3-letter (was '${currency.currencyCode}')"
        }
        require(amount.scale() >= 0) { "Amount must have non-negative scale" }
    }

    /** Zero in this currency. */
    fun zero(): Money = copy(amount = BigDecimal.ZERO.setScale(scale(), RoundingMode.UNNECESSARY))

    /** Amount normalised to the currency's standard fraction digits. */
    fun normalised(): Money {
        val target = scale()
        val current = amount.scale()
        if (current == target) return this
        return copy(amount = amount.setScale(target, RoundingMode.HALF_EVEN))
    }

    fun isPositive(): Boolean = amount.signum() > 0
    fun isZero(): Boolean = amount.signum() == 0

    fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amount = amount.add(other.amount))
    }

    fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amount = amount.subtract(other.amount))
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    override fun toString(): String = format(Locale.getDefault())

    fun format(locale: Locale): String {
        val fmt = java.text.NumberFormat.getCurrencyInstance(locale)
        fmt.currency = currency
        fmt.maximumFractionDigits = scale()
        fmt.minimumFractionDigits = scale()
        return fmt.format(amount)
    }

    private fun scale(): Int = currency.defaultFractionDigits.coerceAtLeast(0)

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: ${currency.currencyCode} vs ${other.currency.currencyCode}"
        }
    }

    companion object {
        val ZERO_BWP: Money = of(BigDecimal.ZERO, "BWP")
        val ZERO_USD: Money = of(BigDecimal.ZERO, "USD")

        fun of(amount: BigDecimal, code: String): Money =
            Money(amount, Currency.getInstance(code))
    }
}