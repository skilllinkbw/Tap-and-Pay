package com.getauthepay.app.network

import com.getauthepay.app.core.models.Money
import com.getauthepay.app.core.models.PaymentStatus
import com.getauthepay.app.core.models.Refund
import com.getauthepay.app.core.models.RefundStatus
import com.getauthepay.app.core.models.RiskDecision
import com.getauthepay.app.core.models.SecurityAlert
import com.getauthepay.app.core.models.Settlement
import com.getauthepay.app.core.models.SettlementStatus
import com.getauthepay.app.core.models.Transaction
import com.getauthepay.app.core.models.TransactionStatus
import org.json.JSONObject

/**
 * Wire-format DTOs + JSON mapping. The server contract is described in
 * docs/API_CONTRACT.md; these helpers translate between the on-device
 * domain model and the wire shape.
 */
internal object ApiDtos {

    fun transactionFromJson(o: JSONObject): Transaction {
        val amount = o.optJSONObject("amount") ?: JSONObject()
        return Transaction(
            transactionId = o.getString("transactionId"),
            merchantId = o.getString("merchantId"),
            terminalId = o.optString("terminalId"),
            amount = Money.of(
                java.math.BigDecimal(amount.optDouble("value", 0.0)),
                amount.optString("currency", "BWP"),
            ),
            status = TransactionStatus.valueOf(o.optString("status", "NOT_COMPLETED")),
            createdAtMs = o.optLong("createdAtMs"),
            completedAtMs = o.optLong("completedAtMs").takeIf { it > 0 },
            maskedPan = o.optStringOrNull("maskedPan"),
            cardType = o.optStringOrNull("cardType"),
            authCode = o.optStringOrNull("authCode"),
            processorReference = o.optStringOrNull("processorReference"),
            reference = o.optStringOrNull("reference"),
            description = o.optStringOrNull("description"),
            correlationId = o.optStringOrNull("correlationId"),
            riskDecision = o.optJSONObject("risk")?.let { riskFromJson(it) },
            refundedAmount = o.optJSONObject("refundedAmount")?.let {
                Money.of(
                    java.math.BigDecimal(it.optDouble("value", 0.0)),
                    it.optString("currency", "BWP"),
                )
            },
            settlementId = o.optStringOrNull("settlementId"),
        )
    }

    fun riskFromJson(o: JSONObject): RiskDecision {
        return RiskDecision(
            score = o.optInt("score"),
            decision = RiskDecision.Decision.valueOf(o.optString("decision", "ALLOW")),
            reasonCodes = o.optJSONArray("reasons")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            modelVersion = o.optString("modelVersion", "server.v1"),
        )
    }

    fun refundFromJson(o: JSONObject): Refund {
        val amount = o.getJSONObject("amount")
        return Refund(
            refundId = o.getString("refundId"),
            transactionId = o.getString("transactionId"),
            merchantId = o.getString("merchantId"),
            amount = Money.of(
                java.math.BigDecimal(amount.optDouble("value", 0.0)),
                amount.optString("currency", "BWP"),
            ),
            status = RefundStatus.valueOf(o.optString("status", "REFUND_PENDING")),
            reasonCode = o.optString("reasonCode", "MERCHANT_INITIATED"),
            reasonNote = o.optStringOrNull("reasonNote"),
            createdAtMs = o.optLong("createdAtMs"),
            completedAtMs = o.optLong("completedAtMs").takeIf { it > 0 },
            processorReference = o.optStringOrNull("processorReference"),
            correlationId = o.optStringOrNull("correlationId"),
            requestedByUserId = o.optString("requestedByUserId"),
        )
    }

    fun settlementFromJson(o: JSONObject): Settlement {
        val currency = o.optString("currency", "BWP")
        return Settlement(
            settlementId = o.getString("settlementId"),
            merchantId = o.getString("merchantId"),
            periodStartMs = o.optLong("periodStartMs"),
            periodEndMs = o.optLong("periodEndMs"),
            grossAmount = Money.of(java.math.BigDecimal(o.optJSONObject("gross")?.optDouble("value", 0.0) ?: 0.0), currency),
            feeAmount = Money.of(java.math.BigDecimal(o.optJSONObject("fee")?.optDouble("value", 0.0) ?: 0.0), currency),
            netAmount = Money.of(java.math.BigDecimal(o.optJSONObject("net")?.optDouble("value", 0.0) ?: 0.0), currency),
            transactionCount = o.optInt("transactionCount"),
            status = SettlementStatus.valueOf(o.optString("status", "PENDING")),
            settledAtMs = o.optLong("settledAtMs").takeIf { it > 0 },
            bankReference = o.optStringOrNull("bankReference"),
        )
    }

    fun alertFromJson(o: JSONObject): SecurityAlert {
        return SecurityAlert(
            alertId = o.getString("alertId"),
            merchantId = o.getString("merchantId"),
            severity = SecurityAlert.Severity.valueOf(o.optString("severity", "INFO")),
            category = SecurityAlert.Category.valueOf(o.optString("category", "OTHER")),
            title = o.optString("title"),
            message = o.optString("message"),
            raisedAtMs = o.optLong("raisedAtMs"),
            acknowledgedAtMs = o.optLong("acknowledgedAtMs").takeIf { it > 0 },
            resolvedAtMs = o.optLong("resolvedAtMs").takeIf { it > 0 },
            actionUrl = o.optStringOrNull("actionUrl"),
        )
    }
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

/** Status helper for payment results coming back from the server. */
internal fun mapServerStatus(raw: String): PaymentStatus = when (raw.uppercase()) {
    "APPROVED" -> PaymentStatus.APPROVED
    "DECLINED" -> PaymentStatus.DECLINED
    "REVERSED" -> PaymentStatus.REVERSED
    "REFUNDED" -> PaymentStatus.REFUNDED
    else -> PaymentStatus.FAILED
}