package com.getauthepay.app.core.tokenization

/**
 * Card data NEVER reaches this interface. The tokenization service is the
 * boundary at which the Android client exchanges opaque payment tokens
 * (e.g. cryptograms produced by a certified kernel, EMV tags, or mobile
 * money reference numbers) for the processor's network token.
 *
 * Implementations:
 *
 *   - `NetworkTokenizationService` — backed by the processor's token
 *     vault (network token / tokenised PAN). The cert/key material
 *     lives server-side; the Android app sees only tokens.
 *   - `MobileMoneyTokenizationService` — exchanges mobile-money
 *     reference numbers / QR payloads for the processor reference.
 *
 * Sandbox implementations are deliberately absent — tokenization is a
 * production-only concept and the sandbox provider does not generate
 * real tokens.
 */
interface TokenizationService {
    val name: String

    /**
     * Exchanges a sandbox/network token for a processor reference. The
     * client never sees the underlying PAN.
     */
    suspend fun tokenize(token: String, merchantId: String, terminalId: String): TokenizationResult
}

data class TokenizationResult(
    val token: String,
    val processorReference: String,
    val brand: String?,
    val expiryMonth: Int? = null,
    val expiryYear: Int? = null,
)