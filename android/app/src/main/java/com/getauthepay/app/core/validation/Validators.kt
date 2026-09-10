package com.getauthepay.app.core.validation

import com.getauthepay.app.core.currency.CurrencyCatalog
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure-Kotlin validators for payment input. All functions throw
 * [IllegalArgumentException] for invalid input so the UI can render the
 * message verbatim.
 *
 * Limits here are deliberately conservative — they are enforced locally
 * and again authoritatively by the processor.
 */
object Validators {

    /** Minimum amount the merchant can submit (1 minor unit). */
    val MIN_AMOUNT: BigDecimal = BigDecimal("0.01")

    /** Maximum single-tap amount (default 250,000.00 BWP). Overridable later. */
    val MAX_AMOUNT: BigDecimal = BigDecimal("250000.00")

    fun validateAmount(raw: String, currencyCode: String = CurrencyCatalog.defaultCurrency): BigDecimal {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Amount is required" }
        val parsed = runCatching { BigDecimal(trimmed) }.getOrElse {
            throw IllegalArgumentException("Amount must be a number")
        }
        if (parsed.signum() <= 0) throw IllegalArgumentException("Amount must be greater than zero")
        require(parsed >= MIN_AMOUNT) {
            "Amount must be at least ${MIN_AMOUNT.toPlainString()}"
        }
        require(parsed <= MAX_AMOUNT) {
            "Amount exceeds the per-transaction limit of ${MAX_AMOUNT.toPlainString()}"
        }
        // Reject more than 2 fraction digits for normal fiat currencies.
        val currency = runCatching { java.util.Currency.getInstance(currencyCode) }.getOrNull()
        val fractionDigits = currency?.defaultFractionDigits ?: 2
        require(parsed.scale() <= fractionDigits.coerceAtLeast(0)) {
            "Amount has too many decimal places (max $fractionDigits)"
        }
        return parsed.setScale(fractionDigits.coerceAtLeast(0), RoundingMode.HALF_EVEN)
    }

    fun validateCurrency(code: String): String {
        val upper = code.uppercase()
        require(CurrencyCatalog.isSupported(upper)) {
            "Currency $upper is not supported on this terminal"
        }
        return upper
    }

    fun validateReference(reference: String?): String? {
        if (reference.isNullOrBlank()) return null
        val trimmed = reference.trim()
        require(trimmed.length in 1..64) { "Reference must be 1-64 characters" }
        require(trimmed.all { it.isLetterOrDigit() || it in "-_./:# " }) {
            "Reference contains unsupported characters"
        }
        return trimmed
    }

    /** Validates an OTP shape (4-8 digits). Does NOT verify the code. */
    fun validateOtpShape(code: String) {
        require(code.length in 4..8) { "OTP must be 4-8 digits" }
        require(code.all { it.isDigit() }) { "OTP must contain only digits" }
    }
}