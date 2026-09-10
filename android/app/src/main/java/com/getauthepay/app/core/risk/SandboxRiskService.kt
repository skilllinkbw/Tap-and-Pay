package com.getauthepay.app.core.risk

import com.getauthepay.app.core.models.RiskDecision
import kotlin.math.min

/**
 * Deterministic rules engine used in sandbox / bank-demo environments.
 *
 * Scoring is a transparent additive model with the following reasons and
 * weights:
 *
 *   - "VELOCITY_HIGH"     +25  when transactions in the last hour ≥ 20
 *   - "VELOCITY_DAILY"    +15  when transactions in the last day  ≥ 150
 *   - "DECLINE_BURST"     +20  when declines in the last hour  ≥ 3
 *   - "NEW_DEVICE"        +15  when this device is new to the merchant
 *   - "OVERSEAS"          +10  when context.isOverseas is true
 *   - "ODD_HOURS"         +10  when transaction is between 23:00–05:00 local
 *   - "DEVICE_LOW_TRUST"  +20  when device trust score < 50
 *
 * The decision is derived from the final score via the policy in
 * [RiskService] KDoc.
 */
class SandboxRiskService : RiskService {

    override suspend fun evaluate(
        context: RiskContext,
        modelVersion: String,
    ): RiskDecision {
        val reasons = mutableListOf<String>()
        var score = 5 // baseline

        if (context.transactionCountLastHour >= 20) {
            score += 25; reasons += "VELOCITY_HIGH"
        }
        if (context.transactionCountLastDay >= 150) {
            score += 15; reasons += "VELOCITY_DAILY"
        }
        if (context.previousDeclinesLastHour >= 3) {
            score += 20; reasons += "DECLINE_BURST"
        }
        if (context.newDevice) {
            score += 15; reasons += "NEW_DEVICE"
        }
        if (context.isOverseas) {
            score += 10; reasons += "OVERSEAS"
        }
        if (context.timeOfDayLocalHour in 23..23 || context.timeOfDayLocalHour in 0..5) {
            score += 10; reasons += "ODD_HOURS"
        }
        if (context.deviceTrustScore < 50) {
            score += 20; reasons += "DEVICE_LOW_TRUST"
        }

        val clamped = min(score, 100)

        // A device whose trust score is critically low (rooted, debuggable, or running on
        // an emulator) must never be allowed to accept live payments, regardless of the
        // additive score. Fail closed: trust < 20 means multiple severe integrity signals
        // fired at once, so we block rather than let the additive model talk us into a
        // CHALLENGE.
        if (context.deviceTrustScore < 20) {
            return RiskDecision(
                score = clamped,
                decision = RiskDecision.Decision.BLOCK,
                reasonCodes = (reasons + "DEVICE_UNTRUSTED").distinct(),
                modelVersion = modelVersion,
            )
        }

        val decision = when {
            clamped > 70 -> RiskDecision.block(reasons.joinToString(",").ifBlank { "HIGH_RISK" },
                clamped, modelVersion)
            clamped in 30..70 -> RiskDecision.challenge(reasons.joinToString(",").ifBlank { "MEDIUM_RISK" },
                clamped, modelVersion)
            else -> RiskDecision(clamped, RiskDecision.Decision.ALLOW, reasons, modelVersion)
        }
        return decision
    }
}