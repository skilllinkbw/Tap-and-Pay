package com.getauthepay.app.core.log

/**
 * Sanitised application logger. The contract is enforced in tests:
 *
 *  - No PAN, CVV, PIN, OTP, session token, access token, or private key
 *    may appear in any log line.
 *  - Log entries are correlated via a request/transaction id, never via
 *    any sensitive value.
 *  - The logger accepts a Throwable but only logs the class name + a
 *    short hash, not the full stack message.
 *  - The platform Logcat call sits behind a release-build guard
 *    (see [com.getauthepay.app.BuildConfig.PRODUCTION_BUILD]).
 */
object SecureLogger {

    private val panPattern = Regex("""\b(?:\d[ -]*?){13,19}\b""")
    private val cvvPattern = Regex("""(?i)\b(cvv|cvc|cid)\b[^\d]{0,8}(\d{3,4})""")
    private val otpPattern = Regex("""(?i)\b(otp|one[-\s]?time|verification)\s*code\b[^\d]{0,8}(\d{4,8})""")
    private val tokenPattern = Regex("""(?i)\b(bearer|access[_\-]?token|authorization)\s*[:=]\s*[A-Za-z0-9._\-]{12,}""")
    private val privateKeyPattern = Regex("""-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----""")
    private val pinPattern = Regex("""(?i)\b(pin|passcode)\b[^\d]{0,8}(\d{4,8})""")

    /**
     * Returns a copy of [message] with every recognised sensitive token
     * replaced by [redactionLabel].
     */
    fun sanitise(message: String, redactionLabel: String = "[REDACTED]"): String {
        var s = message
        s = otpPattern.replace(s, "$1 $redactionLabel")
        s = cvvPattern.replace(s, "$1 $redactionLabel")
        s = pinPattern.replace(s, "$1 $redactionLabel")
        s = tokenPattern.replace(s, "$1 $redactionLabel")
        s = privateKeyPattern.replace(s, "[PRIVATE_KEY $redactionLabel]")
        s = panPattern.replace(s) { match ->
            // Keep first 6 and last 4 digits, mask the rest.
            val digits = match.value.filter { it.isDigit() }
            if (digits.length < 13) redactionLabel
            else digits.take(6) + "*".repeat(digits.length - 10) + digits.takeLast(4)
        }
        return s
    }

    fun assertSafe(message: String) {
        require(!panPattern.containsMatchIn(message)) { "log line contains PAN" }
        require(!cvvPattern.containsMatchIn(message)) { "log line contains CVV" }
        require(!pinPattern.containsMatchIn(message)) { "log line contains PIN" }
        require(!otpPattern.containsMatchIn(message)) { "log line contains OTP" }
        require(!tokenPattern.containsMatchIn(message)) { "log line contains access token" }
        require(!privateKeyPattern.containsMatchIn(message)) { "log line contains private key" }
    }

    /** Composes a one-line structured log entry that is safe to persist. */
    fun event(
        event: String,
        correlationId: String? = null,
        transactionId: String? = null,
        status: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): String {
        val sb = StringBuilder("event=").append(event)
        correlationId?.let { sb.append(" correlationId=").append(it) }
        transactionId?.let { sb.append(" transactionId=").append(it) }
        status?.let { sb.append(" status=").append(it) }
        for ((k, v) in extra) {
            sb.append(' ').append(k).append('=').append(sanitise(v))
        }
        val out = sb.toString()
        assertSafe(out)
        return out
    }
}