package com.getauthepay.app.core.currency

import java.util.Currency
import java.util.Locale

/**
 * Catalogue of supported settlement currencies for the Africa-first launch.
 *
 * The list intentionally starts with BWP and adjacent SADC / continental
 * currencies; additional currencies are added by extending [supportedCodes].
 *
 * Per directive section 40: the application must NOT hardcode BWP across
 * the codebase. Components consume this catalogue via [defaultCurrency]
 * and [isSupported].
 */
object CurrencyCatalog {

    private val supportedCodes: List<String> = listOf(
        "BWP", // Botswana Pula (launch market)
        "ZAR", // South Africa Rand
        "ZMW", // Zambia Kwacha
        "KES", // Kenya Shilling
        "NGN", // Nigeria Naira
        "GHS", // Ghana Cedi
        "TZS", // Tanzania Shilling
        "UGX", // Uganda Shilling
        "MUR", // Mauritius Rupee
        "USD", // US Dollar (international cards)
        "EUR", // Euro (international cards)
        "GBP", // Pound Sterling (international cards)
    )

    val defaultCurrency: String = "BWP"

    val defaultCurrencyInstance: Currency = Currency.getInstance(defaultCurrency)

    fun isSupported(code: String): Boolean =
        supportedCodes.contains(code.uppercase())

    fun allSupported(): List<Currency> =
        supportedCodes.mapNotNull { runCatching { Currency.getInstance(it) }.getOrNull() }

    /** Human-readable label for a currency code, falling back to the code itself. */
    fun displayName(code: String, locale: Locale = Locale.getDefault()): String {
        val currency = runCatching { Currency.getInstance(code) }.getOrNull() ?: return code
        return currency.getDisplayName(locale)
    }

    /** Symbol for a currency code, falling back to the code itself. */
    fun symbol(code: String): String =
        runCatching { Currency.getInstance(code).symbol }.getOrDefault(code)
}