package com.getauthepay.app.core.payment

import com.getauthepay.app.core.models.Money
import com.getauthepay.app.core.models.PaymentRequest
import com.getauthepay.app.core.models.PaymentResult
import com.getauthepay.app.core.models.RiskDecision

/**
 * The processor is the network-side component that submits a
 * risk-evaluated request to the acquirer and returns the issuer
 * authorisation outcome.
 *
 * The interface is the boundary between the Android client and the
 * certified backend. Implementations include:
 *
 *   - [SandboxPaymentProcessor] — deterministic simulator used for
 *     bank demos and automated tests. Never connected to a real
 *     card network.
 *   - `BankProcessor` — wire-format adapter to the acquirer's
 *     production endpoint (credentials live server-side).
 *   - `CardProcessor` / `MobileMoneyProcessor` — provider-specific
 *     adapters (Orange Money, MyZaka, Smega, etc., to be wired in
 *     per partner contract).
 *
 * The client MUST keep secrets server-side; this interface is
 * deliberately credential-free so a leaked APK never exposes
 * processor credentials.
 */
interface PaymentProcessor {

    val name: String

    val isSandbox: Boolean

    suspend fun authorise(
        request: PaymentRequest,
        amount: Money,
        riskDecision: RiskDecision,
        idempotencyKey: String,
    ): PaymentResult

    suspend fun void(transactionId: String, correlationId: String): PaymentResult
}