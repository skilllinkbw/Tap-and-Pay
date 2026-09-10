package com.getauthepay.app.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Log redaction (directive section 35).
 *
 * Production logs must never contain a PAN, CVV, PIN, OTP, access token or
 * private key. Because logcat is readable by other apps on a rooted device
 * and is frequently shipped to crash reporters, redaction is enforced in a
 * test rather than left to convention.
 *
 * NOTE: the literal sensitive strings below are constructed at runtime so
 * that this source file does not itself contain a card number.
 */
class SecureLoggerTest {

    private val pan = "4" + "11111" + "111111" + "1111" // 16 digits, no literal PAN in source

    @Test
    fun `pan is masked keeping first six and last four`() {
        val out = SecureLogger.sanitise("card $pan was used")
        assertFalse("PAN must not survive sanitisation", out.contains(pan))
        assertTrue(out.contains("411111"))
        assertTrue(out.contains("1111"))
        assertTrue(out.contains("******"))
    }

    @Test
    fun `cvv is redacted`() {
        val out = SecureLogger.sanitise("customer gave cvv 123")
        assertFalse(out.contains("123"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun `pin is redacted`() {
        val out = SecureLogger.sanitise("entered pin 4321")
        assertFalse(out.contains("4321"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun `otp is redacted`() {
        val out = SecureLogger.sanitise("otp code 987654")
        assertFalse(out.contains("987654"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun `authorization token is redacted`() {
        val out = SecureLogger.sanitise("Authorization: abcdefghijklmnop12")
        assertFalse(out.contains("abcdefghijklmnop12"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun `private key is redacted`() {
        val out = SecureLogger.sanitise("key -----BEGIN PRIVATE KEY----- end")
        assertFalse(out.contains("BEGIN PRIVATE KEY"))
        assertTrue(out.contains("[PRIVATE_KEY"))
    }

    @Test
    fun `ordinary text is left untouched`() {
        val input = "payment approved for reference ATX-12345678"
        assertEquals(input, SecureLogger.sanitise(input))
    }

    @Test
    fun `short digit runs are not treated as card numbers`() {
        val input = "amount 500 for invoice 1234"
        assertEquals(input, SecureLogger.sanitise(input))
    }

    // ---- assertSafe ---------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `assertSafe rejects a pan`() {
        SecureLogger.assertSafe("card $pan")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertSafe rejects a cvv`() {
        SecureLogger.assertSafe("cvv 999")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertSafe rejects a pin`() {
        SecureLogger.assertSafe("pin 1234")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertSafe rejects an otp`() {
        SecureLogger.assertSafe("otp code 123456")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `assertSafe rejects an access token`() {
        SecureLogger.assertSafe("access_token=abcdefghijklmnop12")
    }

    @Test
    fun `assertSafe accepts a clean line`() {
        SecureLogger.assertSafe("event=payment.approved transactionId=ATX-1 status=APPROVED")
    }

    // ---- structured events ---------------------------------------------------

    @Test
    fun `event includes correlation and status`() {
        val line = SecureLogger.event(
            event = "payment.start",
            correlationId = "corr-1",
            transactionId = "ATX-1",
            status = "CREATED",
        )
        assertTrue(line.contains("event=payment.start"))
        assertTrue(line.contains("correlationId=corr-1"))
        assertTrue(line.contains("transactionId=ATX-1"))
        assertTrue(line.contains("status=CREATED"))
    }

    @Test
    fun `event sanitises extra values before emitting`() {
        val line = SecureLogger.event(
            event = "risk.evaluated",
            extra = mapOf("note" to "otp code 445566"),
        )
        assertFalse(line.contains("445566"))
        assertTrue(line.contains("[REDACTED]"))
    }

    @Test
    fun `event never emits an empty correlation placeholder`() {
        val line = SecureLogger.event(event = "app.start")
        assertFalse(line.contains("null"))
    }
}
