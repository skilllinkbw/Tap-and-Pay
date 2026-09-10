package com.getauthepay.app.core.models

/**
 * Outcome of a risk evaluation performed prior to (or in parallel with)
 * authorisation. The decision is captured alongside the transaction so
 * support and audit can reconstruct why a payment was allowed or blocked.
 *
 * @property score 0..100 risk score produced by the rules engine.
 * @property decision Coarse decision label: ALLOW / CHALLENGE / BLOCK.
 * @property reasonCodes Machine-readable reason codes (e.g. "VELOCITY_HIGH",
 *   "BIN_BLACKLISTED").
 * @property modelVersion Version of the rules/ML model that produced the score.
 * @property timestampMs Epoch milliseconds when the decision was produced.
 */
data class RiskDecision(
    val score: Int,
    val decision: Decision,
    val reasonCodes: List<String> = emptyList(),
    val modelVersion: String,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    init {
        require(score in 0..100) { "risk score must be in 0..100 (was $score)" }
        require(modelVersion.isNotBlank()) { "modelVersion must not be blank" }
    }

    enum class Decision { ALLOW, CHALLENGE, BLOCK }

    companion object {
        fun allow(modelVersion: String = "rules.v1"): RiskDecision =
            RiskDecision(score = 5, decision = Decision.ALLOW, modelVersion = modelVersion)

        fun challenge(reason: String, score: Int, modelVersion: String = "rules.v1"): RiskDecision =
            RiskDecision(score = score, decision = Decision.CHALLENGE,
                reasonCodes = listOf(reason), modelVersion = modelVersion)

        fun block(reason: String, score: Int, modelVersion: String = "rules.v1"): RiskDecision =
            RiskDecision(score = score, decision = Decision.BLOCK,
                reasonCodes = listOf(reason), modelVersion = modelVersion)
    }
}