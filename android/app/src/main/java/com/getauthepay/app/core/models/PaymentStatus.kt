package com.getauthepay.app.core.models

/**
 * Lifecycle of a payment attempt. The transitions are enforced by
 * [com.getauthepay.app.core.payment.PaymentStateMachine] and the
 * UI reflects every state.
 *
 *   CREATED          — request built but not yet offered to a card
 *   READY_FOR_TAP    — NFC reader is active, waiting for a card
 *   CARD_DETECTED    — an ISO 14443 card has been presented
 *   PROCESSING       — APDU exchange with the card is in flight
 *   AUTHORIZING      — request is being sent to the processor/acquirer
 *   APPROVED         — the issuer authorised the transaction
 *   DECLINED         — the issuer declined the transaction
 *   CANCELLED        — the merchant cancelled the attempt
 *   TIMEOUT          — the attempt timed out (NFC window, network, etc.)
 *   FAILED           — an unrecoverable failure occurred
 *   REVERSED         — an approved payment was reversed (chargeback/duplicate)
 *   REFUNDED         — an approved payment was refunded (full or partial)
 *
 * Every state maps to an exact, deterministic UI screen in
 * ui/payment/PaymentScreen.kt and ui/payment/ReadyToTapScreen.kt.
 */
enum class PaymentStatus {
    CREATED,
    READY_FOR_TAP,
    CARD_DETECTED,
    PROCESSING,
    AUTHORIZING,
    APPROVED,
    DECLINED,
    CANCELLED,
    TIMEOUT,
    FAILED,
    REVERSED,
    REFUNDED;

    /** Terminal states cannot transition further. */
    fun isTerminal(): Boolean = when (this) {
        APPROVED, DECLINED, CANCELLED, TIMEOUT, FAILED, REVERSED, REFUNDED -> true
        else -> false
    }

    /** True if the merchant was successfully charged. */
    fun isSuccess(): Boolean = this == APPROVED

    /** True if the attempt ended without success but with a recoverable cause. */
    fun isRetryable(): Boolean = this == TIMEOUT || this == DECLINED || this == CANCELLED
}