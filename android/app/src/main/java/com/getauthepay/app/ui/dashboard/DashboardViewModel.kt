package com.getauthepay.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getauthepay.app.core.currency.CurrencyCatalog
import com.getauthepay.app.core.models.Money
import com.getauthepay.app.core.models.Transaction
import com.getauthepay.app.core.models.TransactionStatus
import com.getauthepay.app.data.MerchantRepository
import com.getauthepay.app.data.SessionManager
import com.getauthepay.app.ui.Async
import com.getauthepay.app.ui.isLoading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Dashboard read model.
 *
 * Every figure here is **derived** from [Transaction] records supplied by
 * [MerchantRepository] — the server ledger, or failing that this device's
 * own recorded attempts. There are no hardcoded sample figures anywhere in
 * this file. When there is no data, [UiState.todayTotal] is `null` and the
 * screen renders "No transactions yet".
 */
class DashboardViewModel(
    private val repository: MerchantRepository,
    private val session: SessionManager,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val degradedReason: String? = null,
        val dataSourceLabel: String = "",
        val merchantName: String = "",
        val merchantStatus: String = "",
        val role: String = "",
        val currencyCode: String = CurrencyCatalog.defaultCurrency,
        val todayTotal: Money? = null,
        val todayCount: Int = 0,
        val approvedCount: Int = 0,
        val declinedCount: Int = 0,
        val pendingCount: Int = 0,
        val refundedTotal: Money? = null,
        val openAlertCount: Int = 0,
        val pendingSettlementCount: Int = 0,
        val recent: List<Transaction> = emptyList(),
        val refreshError: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, refreshError = null)
            val snapshot = repository.refresh()
            val s = session.current()

            val txns = snapshot.transactions
            val currency = txns.firstOrNull()?.amount?.currency?.currencyCode
                ?: s.merchant.settlementCurrency.ifBlank { CurrencyCatalog.defaultCurrency }

            val startOfToday = Instant.now().atZone(ZoneId.systemDefault())
                .toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val today = txns.filter { it.createdAtMs >= startOfToday }
            val approvedToday = today.filter { it.status == TransactionStatus.APPROVED }

            val todayTotal = approvedToday
                .map { it.amount }
                .takeIf { it.isNotEmpty() }
                ?.reduceOrNull(::safeAdd)

            val refundedTotal = txns
                .mapNotNull { it.refundedAmount }
                .takeIf { it.isNotEmpty() }
                ?.reduceOrNull(::safeAdd)

            _state.value = UiState(
                loading = false,
                degradedReason = snapshot.degradedReason,
                dataSourceLabel = when (snapshot.source) {
                    MerchantRepository.DataSource.SERVER -> "Live from AuthePay"
                    MerchantRepository.DataSource.LOCAL_LEDGER -> "This device only"
                    MerchantRepository.DataSource.EMPTY -> ""
                },
                merchantName = s.merchant.businessName.ifBlank { "—" },
                merchantStatus = s.merchant.status.name,
                role = s.merchant.role.displayName,
                currencyCode = currency,
                todayTotal = todayTotal,
                todayCount = today.size,
                approvedCount = txns.count { it.status == TransactionStatus.APPROVED },
                declinedCount = txns.count { it.status == TransactionStatus.DECLINED },
                pendingCount = txns.count {
                    it.status == TransactionStatus.REFUND_PENDING ||
                        it.status == TransactionStatus.NOT_COMPLETED
                },
                refundedTotal = refundedTotal,
                openAlertCount = snapshot.alerts.count { it.isOpen },
                pendingSettlementCount = snapshot.settlements.count {
                    it.status == com.getauthepay.app.core.models.SettlementStatus.PENDING
                },
                recent = txns.take(5),
            )
        }
    }

    /** Adds two amounts, tolerating a currency mismatch rather than crashing. */
    private fun safeAdd(a: Money, b: Money): Money = runCatching { a.plus(b) }.getOrDefault(a)

    companion object {
        /** Exposed for unit tests: pure aggregation over a transaction list. */
        fun sumApproved(transactions: List<Transaction>): Money? =
            transactions
                .filter { it.status == TransactionStatus.APPROVED }
                .map { it.amount }
                .takeIf { it.isNotEmpty() }
                ?.reduceOrNull { a, b -> runCatching { a.plus(b) }.getOrDefault(a) }
    }
}

/** Convenience alias so screens can share one loading helper. */
val Async<*>.showSpinner: Boolean get() = isLoading
