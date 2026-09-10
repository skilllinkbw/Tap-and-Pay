package com.getauthepay.app.ui

import com.getauthepay.app.core.models.Money
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Display formatting helpers.
 *
 * Currency handling is deliberately driven by [Money.currency] rather than a
 * hardcoded "BWP" string. The app launches in Botswana but the architecture
 * must support ZAR, USD, ZMW, KES, NGN and future currencies without code
 * changes — see `core/currency/CurrencyCatalog.kt`.
 */

private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH)

/** Formats a monetary amount using the currency embedded in [money]. */
fun formatMoney(money: Money): String {
    val currency = money.currency
    val fmt = NumberFormat.getCurrencyInstance(Locale.ENGLISH)
    fmt.currency = currency
    fmt.minimumFractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
    fmt.maximumFractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
    return fmt.format(money.amount)
}

/** Formats an amount for large display (no currency symbol grouping games). */
fun formatAmount(value: java.math.BigDecimal, currencyCode: String): String {
    val currency = runCatching { java.util.Currency.getInstance(currencyCode) }
        .getOrElse { java.util.Currency.getInstance("BWP") }
    val fmt = NumberFormat.getCurrencyInstance(Locale.ENGLISH)
    fmt.currency = currency
    fmt.minimumFractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
    fmt.maximumFractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
    return fmt.format(value)
}

fun formatDateTime(epochMs: Long): String = try {
    dateTimeFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
} catch (t: Throwable) {
    "—"
}

fun formatDate(epochMs: Long): String = try {
    dateFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
} catch (t: Throwable) {
    "—"
}

fun formatTime(epochMs: Long): String = try {
    timeFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
} catch (t: Throwable) {
    "—"
}

fun formatRelative(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val diff = (nowMs - epochMs).coerceAtLeast(0)
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        else -> formatDate(epochMs)
    }
}

/** Renders a duration in mm:ss form, used for OTP resend countdowns. */
fun formatCountdown(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return String.format(Locale.ENGLISH, "%02d:%02d", s / 60, s % 60)
}
