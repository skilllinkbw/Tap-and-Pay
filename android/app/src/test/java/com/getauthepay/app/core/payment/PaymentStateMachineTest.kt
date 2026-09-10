package com.getauthepay.app.core.payment

import com.getauthepay.app.core.models.PaymentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive verification of the payment lifecycle transitions.
 *
 * A payment terminal that can move from DECLINED back to APPROVED would be
 * an accounting incident, so the transition table is pinned here. These
 * tests are the reason [PaymentStateMachine] carries no Android dependency.
 */
class PaymentStateMachineTest {

    // ---- happy path -----------------------------------------------------

    @Test
    fun `full happy path transitions are all allowed`() {
        val sm = PaymentStateMachine()
        assertEquals(PaymentStatus.CREATED, sm.current)

        assertTrue(sm.transitionTo(PaymentStatus.READY_FOR_TAP))
        assertTrue(sm.transitionTo(PaymentStatus.CARD_DETECTED))
        assertTrue(sm.transitionTo(PaymentStatus.PROCESSING))
        assertTrue(sm.transitionTo(PaymentStatus.AUTHORIZING))
        assertTrue(sm.transitionTo(PaymentStatus.APPROVED))
        assertEquals(PaymentStatus.APPROVED, sm.current)
    }

    @Test
    fun `decline path is allowed from AUTHORIZING`() {
        val sm = PaymentStateMachine(PaymentStatus.AUTHORIZING)
        assertTrue(sm.transitionTo(PaymentStatus.DECLINED))
        assertEquals(PaymentStatus.DECLINED, sm.current)
    }

    @Test
    fun `approved payment can be reversed or refunded`() {
        assertTrue(PaymentStateMachine(PaymentStatus.APPROVED).transitionTo(PaymentStatus.REVERSED))
        assertTrue(PaymentStateMachine(PaymentStatus.APPROVED).transitionTo(PaymentStatus.REFUNDED))
    }

    @Test
    fun `reversed payment is terminal and cannot be refunded again`() {
        // A reversed payment has reached a final state; it must not move onward.
        assertFalse(PaymentStateMachine(PaymentStatus.REVERSED).transitionTo(PaymentStatus.REFUNDED))
        assertFalse(PaymentStateMachine(PaymentStatus.REVERSED).transitionTo(PaymentStatus.APPROVED))
    }

    @Test
    fun `refunded payment is terminal`() {
        assertFalse(PaymentStateMachine(PaymentStatus.REFUNDED).transitionTo(PaymentStatus.REVERSED))
        assertFalse(PaymentStateMachine(PaymentStatus.REFUNDED).transitionTo(PaymentStatus.APPROVED))
    }

    // ---- cancellation ---------------------------------------------------

    @Test
    fun `merchant can cancel while waiting for a tap`() {
        assertTrue(PaymentStateMachine(PaymentStatus.READY_FOR_TAP).transitionTo(PaymentStatus.CANCELLED))
        assertTrue(PaymentStateMachine(PaymentStatus.CARD_DETECTED).transitionTo(PaymentStatus.CANCELLED))
    }

    @Test
    fun `merchant can cancel from CREATED before the reader opens`() {
        assertTrue(PaymentStateMachine(PaymentStatus.CREATED).transitionTo(PaymentStatus.CANCELLED))
    }

    @Test
    fun `merchant cannot cancel after authorisation has started`() {
        assertFalse(PaymentStateMachine(PaymentStatus.AUTHORIZING).transitionTo(PaymentStatus.CANCELLED))
    }

    // ---- timeouts and failures ------------------------------------------

    @Test
    fun `timeout allowed while waiting for card and during processing`() {
        assertTrue(PaymentStateMachine(PaymentStatus.READY_FOR_TAP).transitionTo(PaymentStatus.TIMEOUT))
        assertTrue(PaymentStateMachine(PaymentStatus.CARD_DETECTED).transitionTo(PaymentStatus.TIMEOUT))
        assertTrue(PaymentStateMachine(PaymentStatus.PROCESSING).transitionTo(PaymentStatus.TIMEOUT))
        assertTrue(PaymentStateMachine(PaymentStatus.AUTHORIZING).transitionTo(PaymentStatus.TIMEOUT))
    }

    @Test
    fun `failure allowed from card detected processing and authorizing`() {
        assertTrue(PaymentStateMachine(PaymentStatus.CARD_DETECTED).transitionTo(PaymentStatus.FAILED))
        assertTrue(PaymentStateMachine(PaymentStatus.PROCESSING).transitionTo(PaymentStatus.FAILED))
        assertTrue(PaymentStateMachine(PaymentStatus.AUTHORIZING).transitionTo(PaymentStatus.FAILED))
    }

    @Test
    fun `failure not allowed before a card is detected`() {
        assertFalse(PaymentStateMachine(PaymentStatus.READY_FOR_TAP).transitionTo(PaymentStatus.FAILED))
    }

    // ---- terminal states ------------------------------------------------

    @Test
    fun `terminal states reject every further transition`() {
        val terminals = listOf(
            PaymentStatus.APPROVED, PaymentStatus.DECLINED, PaymentStatus.CANCELLED,
            PaymentStatus.TIMEOUT, PaymentStatus.FAILED, PaymentStatus.REVERSED,
            PaymentStatus.REFUNDED,
        )
        for (terminal in terminals) {
            for (next in PaymentStatus.entries) {
                if (terminal == PaymentStatus.APPROVED &&
                    (next == PaymentStatus.REVERSED || next == PaymentStatus.REFUNDED)
                ) continue
                if (terminal == next) continue
                assertFalse(
                    "$terminal must not transition to $next",
                    PaymentStateMachine.isAllowed(terminal, next),
                )
            }
        }
    }

    @Test
    fun `a terminal state machine silently rejects a late approval`() {
        val sm = PaymentStateMachine(PaymentStatus.DECLINED)
        assertFalse(sm.transitionTo(PaymentStatus.APPROVED))
        assertEquals(PaymentStatus.DECLINED, sm.current)
    }

    @Test
    fun `approved cannot be downgraded to cancelled`() {
        assertFalse(PaymentStateMachine(PaymentStatus.APPROVED).transitionTo(PaymentStatus.CANCELLED))
    }

    // ---- illegal forward jumps ------------------------------------------

    @Test
    fun `cannot jump straight to APPROVED without authorising`() {
        assertFalse(PaymentStateMachine(PaymentStatus.CREATED).transitionTo(PaymentStatus.APPROVED))
        assertFalse(PaymentStateMachine(PaymentStatus.PROCESSING).transitionTo(PaymentStatus.APPROVED))
    }

    @Test
    fun `cannot skip CARD_DETECTED`() {
        assertFalse(PaymentStateMachine(PaymentStatus.READY_FOR_TAP).transitionTo(PaymentStatus.PROCESSING))
    }

    // ---- reset -----------------------------------------------------------

    @Test
    fun `reset returns the machine to a known state`() {
        val sm = PaymentStateMachine()
        sm.transitionTo(PaymentStatus.READY_FOR_TAP)
        sm.reset(PaymentStatus.CREATED)
        assertEquals(PaymentStatus.CREATED, sm.current)
    }

    // ---- status helpers --------------------------------------------------

    @Test
    fun `isTerminal matches the documented set`() {
        val terminal = setOf(
            PaymentStatus.APPROVED, PaymentStatus.DECLINED, PaymentStatus.CANCELLED,
            PaymentStatus.TIMEOUT, PaymentStatus.FAILED, PaymentStatus.REVERSED,
            PaymentStatus.REFUNDED,
        )
        PaymentStatus.entries.forEach {
            assertEquals("$it terminal flag", it in terminal, it.isTerminal())
        }
    }

    @Test
    fun `only APPROVED counts as a successful charge`() {
        PaymentStatus.entries.forEach {
            assertEquals("$it success flag", it == PaymentStatus.APPROVED, it.isSuccess())
        }
    }

    @Test
    fun `retryable states are exactly timeout declined and cancelled`() {
        val retryable = setOf(
            PaymentStatus.TIMEOUT, PaymentStatus.DECLINED, PaymentStatus.CANCELLED,
        )
        PaymentStatus.entries.forEach {
            assertEquals("$it retryable flag", it in retryable, it.isRetryable())
        }
    }

    @Test
    fun `all twelve directive states exist`() {
        assertEquals(12, PaymentStatus.entries.size)
    }
}
