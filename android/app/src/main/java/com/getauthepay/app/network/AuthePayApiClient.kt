package com.getauthepay.app.network

import com.getauthepay.app.core.log.SecureLogger
import com.getauthepay.app.core.models.Merchant
import com.getauthepay.app.core.models.Refund
import com.getauthepay.app.core.models.SecurityAlert
import com.getauthepay.app.core.models.Settlement
import com.getauthepay.app.core.models.Transaction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Unified AuthePay API client. Each method maps to one service listed
 * in the API contract:
 *
 *   AuthService        requestOtp / verifyOtp / logout
 *   MerchantService    getCurrent / submitOnboarding / listAlerts / ackAlert
 *   PaymentService     submit (forwarded by engine when the bank-side
 *                       processor needs to authorise, e.g. for Mobile Money)
 *   TransactionService list / get
 *   RefundService      create / get
 *   SettlementService  list / get
 *   RiskService        (server-side; client consults via MerchantService)
 *   ReceiptService     get
 *   DeviceService      list / revoke / rename
 *
 * No secrets or service-role keys are stored on-device. The bearer
 * token is fetched from [SecureStorage] on every request.
 */
class AuthePayApiClient(private val http: HttpClient) {

    // ----- AuthService -----

    suspend fun requestOtp(phoneOrEmail: String): OtpRequestOutcome {
        val body = JSONObject().put("identifier", phoneOrEmail)
        val r = http.post("v1/auth/otp/request", body)
        return when (r.code) {
            in 200..299 -> OtpRequestOutcome.SENT
            429 -> OtpRequestOutcome.RATE_LIMITED
            else -> OtpRequestOutcome.FAILED
        }
    }

    suspend fun verifyOtp(phoneOrEmail: String, code: String): OtpVerifyResult {
        val body = JSONObject().put("identifier", phoneOrEmail).put("code", code)
        val r = http.post("v1/auth/otp/verify", body)
        return when (r.code) {
            in 200..299 -> {
                val json = r.jsonBody() ?: return OtpVerifyResult.Failed("empty response")
                val token = json.optString("token")
                val expiresAt = json.optLong("expiresAtMs")
                val merchant = ApiDtos.let { json.optJSONObject("merchant")?.let(::merchantFromJson) }
                if (token.isBlank() || merchant == null) OtpVerifyResult.Failed("malformed response")
                else OtpVerifyResult.Success(token, expiresAt, merchant)
            }
            401, 403 -> OtpVerifyResult.Failed("invalid or expired code")
            429 -> OtpVerifyResult.Failed("too many attempts")
            else -> OtpVerifyResult.Failed("server error (${r.code})")
        }
    }

    suspend fun logout(): Boolean {
        val r = http.post("v1/auth/logout", JSONObject())
        return r.code in 200..299
    }

    // ----- MerchantService -----

    suspend fun getCurrentMerchant(): Merchant? =
        http.get("v1/merchant/me").jsonBody()?.let(::merchantFromJson)

    suspend fun submitOnboarding(draftJson: JSONObject): Boolean {
        val r = http.post("v1/merchant/onboarding", draftJson)
        return r.code in 200..299
    }

    suspend fun listAlerts(): List<SecurityAlert> {
        val r = http.get("v1/merchant/alerts")
        val arr = r.jsonBody()?.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).map { ApiDtos.alertFromJson(arr.getJSONObject(it)) }
    }

    suspend fun acknowledgeAlert(alertId: String): Boolean {
        val r = http.post("v1/merchant/alerts/$alertId/ack", JSONObject())
        return r.code in 200..299
    }

    // ----- PaymentService (acquirer-side, when needed) -----

    suspend fun submitPayment(paymentJson: JSONObject): HttpResponse =
        http.post("v1/payments", paymentJson)

    // ----- TransactionService -----

    suspend fun listTransactions(): List<Transaction> {
        val r = http.get("v1/transactions?limit=200")
        return parseTransactionList(r.jsonBody())
    }

    suspend fun getTransaction(id: String): Transaction? {
        val r = http.get("v1/transactions/$id")
        return r.jsonBody()?.let(ApiDtos::transactionFromJson)
    }

    // ----- RefundService -----

    suspend fun createRefund(
        transactionId: String,
        amountValue: String,
        currency: String,
        reasonCode: String,
        reasonNote: String?,
    ): Refund? {
        val body = JSONObject()
            .put("transactionId", transactionId)
            .put("amount", JSONObject().put("value", amountValue).put("currency", currency))
            .put("reasonCode", reasonCode)
            .put("reasonNote", reasonNote ?: JSONObject.NULL)
        val r = http.post("v1/refunds", body)
        return r.jsonBody()?.let(ApiDtos::refundFromJson)
    }

    suspend fun getRefund(id: String): Refund? =
        http.get("v1/refunds/$id").jsonBody()?.let(ApiDtos::refundFromJson)

    // ----- SettlementService -----

    suspend fun listSettlements(): List<Settlement> {
        val r = http.get("v1/settlements?limit=60")
        val arr = r.jsonBody()?.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).map { ApiDtos.settlementFromJson(arr.getJSONObject(it)) }
    }

    // ----- ReceiptService -----

    suspend fun getReceipt(transactionId: String): JSONObject? =
        http.get("v1/transactions/$transactionId/receipt").jsonBody()

    // ----- DeviceService -----

    suspend fun listDevices(): JSONArray =
        http.get("v1/devices").jsonBody()?.optJSONArray("items") ?: JSONArray()

    suspend fun revokeDevice(deviceId: String): Boolean {
        val r = http.delete("v1/devices/$deviceId")
        return r.code in 200..299
    }

    suspend fun renameDevice(deviceId: String, name: String): Boolean {
        val r = http.put("v1/devices/$deviceId", JSONObject().put("displayName", name))
        return r.code in 200..299
    }

    // ----- helpers -----

    private fun parseTransactionList(json: JSONObject?): List<Transaction> {
        val arr = json?.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).map { ApiDtos.transactionFromJson(arr.getJSONObject(it)) }
    }

    private fun merchantFromJson(o: JSONObject): Merchant {
        return Merchant(
            merchantId = o.getString("merchantId"),
            businessName = o.optString("businessName"),
            tradingName = o.optStringOrNull("tradingName"),
            businessType = com.getauthepay.app.core.models.BusinessType.fromWire(
                o.optString("businessType"),
            ),
            country = o.optString("country", "BW"),
            contactEmail = o.optString("contactEmail"),
            contactPhone = o.optString("contactPhone"),
            role = com.getauthepay.app.core.models.MerchantRole.fromWire(
                o.optString("role"),
            ),
            status = com.getauthepay.app.core.models.MerchantStatus.valueOf(
                o.optString("status", "ACTIVE"),
            ),
            createdAtMs = o.optLong("createdAtMs"),
            verifiedAtMs = o.optLong("verifiedAtMs").takeIf { it > 0 },
            suspendedAtMs = o.optLong("suspendedAtMs").takeIf { it > 0 },
            settlementCurrency = o.optString("settlementCurrency", "BWP"),
        )
    }

    init {
        SecureLogger.event(event = "api.client.constructed")
    }
}

sealed class OtpRequestOutcome {
    object SENT : OtpRequestOutcome()
    object RATE_LIMITED : OtpRequestOutcome()
    object FAILED : OtpRequestOutcome()
}

sealed class OtpVerifyResult {
    data class Success(val token: String, val expiresAtMs: Long, val merchant: Merchant) : OtpVerifyResult()
    data class Failed(val reason: String) : OtpVerifyResult()
}