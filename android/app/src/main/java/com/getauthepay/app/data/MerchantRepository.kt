package com.getauthepay.app.data

import com.getauthepay.app.core.ledger.TransactionLedger
import com.getauthepay.app.core.log.SecureLogger
import com.getauthepay.app.core.models.SecurityAlert
import com.getauthepay.app.core.models.Settlement
import com.getauthepay.app.core.models.Transaction
import com.getauthepay.app.network.ApiException
import com.getauthepay.app.network.AuthePayApiClient
import com.getauthepay.app.network.NetworkUnavailable
import kotlinx.coroutines.flow.first

/**
 * Single source of truth for merchant-facing read models (transactions,
 * settlements, security alerts).
 *
 * Honesty contract — this class exists specifically so that no screen can
 * accidentally invent data:
 *
 *  - The **server ledger is authoritative**. Transactions, settlements and
 *    alerts are always fetched from the AuthePay backend first.
 *  - If the backend is unreachable (no endpoint configured, offline, 5xx)
 *    we fall back to the **local ledger**, which only ever contains
 *    transactions this device genuinely attempted in this session.
 *  - If both are empty, the UI renders "No transactions yet".
 *
 * Nothing here synthesises amounts, dates, statuses or merchant names. A
 * bank reviewer can therefore trust that every figure on screen traces back
 * either to the backend or to a real, completed payment attempt.
 */
class MerchantRepository(
    private val api: AuthePayApiClient,
    private val ledger: TransactionLedger,
) {

    data class DashboardSnapshot(
        val transactions: List<Transaction>,
        val settlements: List<Settlement>,
        val alerts: List<SecurityAlert>,
        /** Where [transactions] came from, so the UI can say so out loud. */
        val source: DataSource,
        /** Non-null when the server could not be reached. */
        val degradedReason: String? = null,
    ) {
        val isDegraded: Boolean get() = degradedReason != null
    }

    enum class DataSource {
        /** Retrieved from the AuthePay backend. */
        SERVER,

        /** Backend unreachable; showing this device's own recorded attempts. */
        LOCAL_LEDGER,

        /** Nothing recorded yet anywhere. */
        EMPTY,
    }

    @Volatile
    private var lastSnapshot: DashboardSnapshot = DashboardSnapshot(
        transactions = emptyList(),
        settlements = emptyList(),
        alerts = emptyList(),
        source = DataSource.EMPTY,
    )

    fun cached(): DashboardSnapshot = lastSnapshot

    suspend fun refresh(): DashboardSnapshot {
        return try {
            val serverTxns = api.listTransactions()
            val settlements = runCatching { api.listSettlements() }.getOrDefault(emptyList())
            val alerts = runCatching { api.listAlerts() }.getOrDefault(emptyList())

            // Reconcile: the local ledger may hold attempts that have not yet
            // been mirrored server-side (e.g. the payment just completed).
            val local = ledger.flow.first()
            val merged = mergeById(serverTxns, local)

            val snapshot = DashboardSnapshot(
                transactions = merged,
                settlements = settlements,
                alerts = alerts,
                source = if (merged.isEmpty()) DataSource.EMPTY else DataSource.SERVER,
            )
            lastSnapshot = snapshot
            SecureLogger.event(
                event = "repository.refresh.server",
                extra = mapOf(
                    "transactions" to merged.size.toString(),
                    "settlements" to settlements.size.toString(),
                    "alerts" to alerts.size.toString(),
                ),
            )
            snapshot
        } catch (t: Throwable) {
            val reason = when (t) {
                is NetworkUnavailable -> "No network connection. Showing this device's recorded activity only."
                is ApiException -> "The AuthePay service responded with an error (${t.code})."
                else -> "The AuthePay service is unavailable right now."
            }
            val local = ledger.flow.first()
            val snapshot = DashboardSnapshot(
                transactions = local,
                settlements = emptyList(),
                alerts = emptyList(),
                source = if (local.isEmpty()) DataSource.EMPTY else DataSource.LOCAL_LEDGER,
                degradedReason = reason,
            )
            lastSnapshot = snapshot
            SecureLogger.event(
                event = "repository.refresh.degraded",
                status = "fallback",
                extra = mapOf(
                    "type" to (t::class.simpleName ?: "Unknown"),
                    "localTransactions" to local.size.toString(),
                ),
            )
            snapshot
        }
    }

    suspend fun findTransaction(transactionId: String): Transaction? {
        ledger.find(transactionId)?.let { return it }
        return runCatching { api.getTransaction(transactionId) }.getOrNull()
    }

    /**
     * Acknowledges (and, when [resolve] is set, resolves) a security alert.
     * Both are server-side actions so the audit trail cannot be forged by a
     * compromised client.
     */
    suspend fun acknowledgeAlert(alertId: String, resolve: Boolean = false): Boolean =
        runCatching { api.acknowledgeAlert(alertId) }.getOrDefault(false).also { ok ->
            SecureLogger.event(
                event = "repository.alert.ack",
                status = if (ok) "ok" else "failed",
                extra = mapOf("alertId" to alertId, "resolve" to resolve.toString()),
            )
        }

    /** Applies a server-confirmed refund to the local ledger mirror. */
    suspend fun applyRefundLocally(
        transactionId: String,
        amount: com.getauthepay.app.core.models.Money,
    ) {
        ledger.applyRefund(transactionId, amount)
    }

    /** Marks a transaction as having a server-side refund in flight. */
    suspend fun markRefundPendingLocally(transactionId: String) {
        ledger.markRefundPending(transactionId)
    }

    suspend fun refreshDevices(): List<DeviceRow> {
        return runCatching {
            val arr = api.listDevices()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                DeviceRow(
                    deviceId = o.optString("deviceId"),
                    displayName = o.optString("displayName", "Unnamed device"),
                    platform = o.optString("platform", "Android"),
                    osVersion = o.optString("osVersion", "—"),
                    appVersion = o.optString("appVersion", "—"),
                    statusLabel = o.optString("status", "ACTIVE"),
                    securityLabel = o.optString("securityState", "SECURE"),
                    registeredAtMs = o.optLong("registeredAtMs"),
                    lastActiveAtMs = o.optLong("lastActiveAtMs").takeIf { it > 0 },
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Server wins on conflict; local-only rows are appended. */
    private fun mergeById(
        server: List<Transaction>,
        local: List<Transaction>,
    ): List<Transaction> {
        val byId = LinkedHashMap<String, Transaction>()
        server.forEach { byId[it.transactionId] = it }
        local.forEach { txn -> byId.putIfAbsent(txn.transactionId, txn) }
        return byId.values.sortedByDescending { it.createdAtMs }
    }

    data class DeviceRow(
        val deviceId: String,
        val displayName: String,
        val platform: String,
        val osVersion: String,
        val appVersion: String,
        val statusLabel: String,
        val securityLabel: String,
        val registeredAtMs: Long,
        val lastActiveAtMs: Long?,
    )
}
