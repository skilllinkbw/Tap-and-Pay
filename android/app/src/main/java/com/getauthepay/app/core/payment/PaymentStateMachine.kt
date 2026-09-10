package com.getauthepay.app.core.payment

import com.getauthepay.app.core.models.PaymentStatus

/**
 * Pure, deterministic state machine for payment lifecycle transitions.
 *
 * The state machine has no Android dependencies and is exhaustively
 * unit-tested in PaymentStateMachineTest.
 *
 * Transition table:
 *
 *   CREATED          → READY_FOR_TAP, CANCELLED
 *   READY_FOR_TAP    → CARD_DETECTED, CANCELLED, TIMEOUT
 *   CARD_DETECTED    → PROCESSING, CANCELLED, TIMEOUT, FAILED
 *   PROCESSING       → AUTHORIZING, FAILED, TIMEOUT
 *   AUTHORIZING      → APPROVED, DECLINED, FAILED, TIMEOUT
 *   APPROVED         → REVERSED, REFUNDED
 *   REVERSED         → (terminal)
 *   REFUNDED         → (terminal)
 *   DECLINED         → (terminal)
 *   CANCELLED        → (terminal)
 *   TIMEOUT          → (terminal)
 *   FAILED           → (terminal)
 */
class PaymentStateMachine(initial: PaymentStatus = PaymentStatus.CREATED) {

    @Volatile
    private var _current: PaymentStatus = initial

    val current: PaymentStatus get() = _current

    /** Returns true if the transition was applied, false if rejected. */
    @Synchronized
    fun transitionTo(next: PaymentStatus): Boolean {
        if (!isAllowed(_current, next)) return false
        _current = next
        return true
    }

    fun reset(to: PaymentStatus = PaymentStatus.CREATED) {
        _current = to
    }

    companion object {
        fun isAllowed(from: PaymentStatus, to: PaymentStatus): Boolean {
            if (from == to) return true
            // APPROVED is otherwise terminal (it is persisted and cannot be cancelled),
            // but an approved payment may still be reversed or refunded — those are the
            // only transitions permitted out of APPROVED.
            if (from.isTerminal() && !(from == PaymentStatus.APPROVED &&
                (to == PaymentStatus.REVERSED || to == PaymentStatus.REFUNDED))
            ) {
                return false
            }
            return when (from) {
                PaymentStatus.CREATED -> to == PaymentStatus.READY_FOR_TAP ||
                    to == PaymentStatus.CANCELLED
                PaymentStatus.READY_FOR_TAP -> to == PaymentStatus.CARD_DETECTED ||
                    to == PaymentStatus.CANCELLED || to == PaymentStatus.TIMEOUT
                PaymentStatus.CARD_DETECTED -> to == PaymentStatus.PROCESSING ||
                    to == PaymentStatus.CANCELLED || to == PaymentStatus.TIMEOUT ||
                    to == PaymentStatus.FAILED
                PaymentStatus.PROCESSING -> to == PaymentStatus.AUTHORIZING ||
                    to == PaymentStatus.FAILED || to == PaymentStatus.TIMEOUT
                PaymentStatus.AUTHORIZING -> to == PaymentStatus.APPROVED ||
                    to == PaymentStatus.DECLINED || to == PaymentStatus.FAILED ||
                    to == PaymentStatus.TIMEOUT
                PaymentStatus.APPROVED -> to == PaymentStatus.REVERSED ||
                    to == PaymentStatus.REFUNDED
                else -> false
            }
        }
    }
}