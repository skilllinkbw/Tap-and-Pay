package com.getauthepay.app.ui.nav

/**
 * Single source of truth for navigation route ids. Centralising the
 * strings prevents typo drift across screens and keeps the nav graph
 * easy to audit.
 */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val OTP = "otp/{phone}"
    fun otp(phone: String) = "otp/${android.net.Uri.encode(phone)}"
    const val ONBOARDING = "onboarding"
    const val VERIFICATION_PENDING = "verification_pending"
    const val DASHBOARD = "dashboard"
    const val ACCEPT_PAYMENT = "accept_payment"
    const val TRANSACTIONS = "transactions"
    const val TRANSACTION_DETAIL = "transaction/{transactionId}"
    fun transactionDetail(id: String) = "transaction/${android.net.Uri.encode(id)}"
    const val REFUND = "refund/{transactionId}"
    fun refund(id: String) = "refund/${android.net.Uri.encode(id)}"
    const val SETTLEMENTS = "settlements"
    const val DEVICES = "devices"
    const val TEAM = "team"
    const val SECURITY_ALERTS = "security_alerts"
    const val SETTINGS = "settings"
    const val SECURITY_SETTINGS = "security_settings"
    const val HELP = "help"
    const val ABOUT = "about"
    const val QR_SCAN = "qr_scan"
}