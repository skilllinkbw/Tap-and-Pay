package com.getauthepay.app.core.risk

import com.getauthepay.app.core.models.Money
import com.getauthepay.app.core.models.RiskDecision

/**
 * Pluggable risk evaluation. The sandbox implementation ([SandboxRiskService])
 * runs deterministic rules so demos and tests are reproducible. A real
 * deployment swaps in a server-side ML scorer behind the same interface.
 *
 * Decision policy:
 *   - score < 30          → ALLOW
 *   - score in 30..70     → CHALLENGE (allow but flag for review)
 *   - score > 70          → BLOCK
 */
interface RiskService {
    suspend fun evaluate(
        context: RiskContext,
        modelVersion: String = "rules.v1",
    ): RiskDecision
}

data class RiskContext(
    val merchantId: String,
    val terminalId: String,
    val amount: Money,
    val transactionCountLastHour: Int,
    val transactionCountLastDay: Int,
    val previousDeclinesLastHour: Int,
    val deviceTrustScore: Int, // 0..100, derived from device integrity checks
    val newDevice: Boolean,
    val isOverseas: Boolean,
    val timeOfDayLocalHour: Int,
) {
    init {
        require(merchantId.isNotBlank()) { "merchantId required" }
        require(terminalId.isNotBlank()) { "terminalId required" }
        require(deviceTrustScore in 0..100) { "deviceTrustScore must be 0..100" }
        require(timeOfDayLocalHour in 0..23) { "timeOfDayLocalHour must be 0..23" }
        require(transactionCountLastHour >= 0)
        require(transactionCountLastDay >= 0)
        require(previousDeclinesLastHour >= 0)
    }
}