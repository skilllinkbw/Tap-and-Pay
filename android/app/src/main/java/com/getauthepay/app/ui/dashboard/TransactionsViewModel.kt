package com.getauthepay.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getauthepay.app.core.models.Transaction
import com.getauthepay.app.core.models.TransactionStatus
import com.getauthepay.app.data.MerchantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Transaction history with server-side-style filtering.
 *
 * Filtering happens over the real list returned by [MerchantRepository].
 * When the repository yields an empty list the screen shows
 * "No transactions yet" — it must never show sample rows.
 */
class TransactionsViewModel(
    private val repository: MerchantRepository,
) : ViewModel() {

    enum class DateRange(val label: String) {
        TODAY("Today"),
        LAST_7_DAYS("Last 7 days"),
        LAST_30_DAYS("Last 30 days"),
        ALL("All time"),
    }

    data class Filters(
        val query: String = "",
        val status: TransactionStatus? = null,
        val dateRange: DateRange = DateRange.ALL,
        val minAmount: String = "",
        val maxAmount: String = "",
    ) {
        val isActive: Boolean
            get() = query.isNotBlank() || status != null ||
                dateRange != DateRange.ALL ||
                minAmount.isNotBlank() || maxAmount.isNotBlank()
    }

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val degradedReason: String? = null,
        val all: List<Transaction> = emptyList(),
        val filters: Filters = Filters(),
    ) {
        val visible: List<Transaction> get() = applyFilters(all, filters)

        val totalCount: Int get() = visible.size

        val visibleTotal: String?
            get() = visible
                .filter { it.status == TransactionStatus.APPROVED }
                .map { it.amount }
                .takeIf { it.isNotEmpty() }
                ?.reduceOrNull { a, b -> runCatching { a.plus(b) }.getOrDefault(a) }
                ?.toString()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val snapshot = repository.refresh()
                _state.value = _state.value.copy(
                    loading = false,
                    all = snapshot.transactions,
                    degradedReason = snapshot.degradedReason,
                )
            }.onFailure { t ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = t.message ?: "Could not load transactions",
                )
            }
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(filters = _state.value.filters.copy(query = value))
    }

    fun setStatus(status: TransactionStatus?) {
        _state.value = _state.value.copy(filters = _state.value.filters.copy(status = status))
    }

    fun setDateRange(range: DateRange) {
        _state.value = _state.value.copy(filters = _state.value.filters.copy(dateRange = range))
    }

    fun setMinAmount(value: String) {
        _state.value = _state.value.copy(filters = _state.value.filters.copy(minAmount = value))
    }

    fun setMaxAmount(value: String) {
        _state.value = _state.value.copy(filters = _state.value.filters.copy(maxAmount = value))
    }

    fun clearFilters() {
        _state.value = _state.value.copy(filters = Filters())
    }

    companion object {
        /**
         * Pure filter function — extracted so it can be unit-tested without
         * coroutines or Android dependencies.
         */
        fun applyFilters(
            source: List<Transaction>,
            filters: Filters,
        ): List<Transaction> {
            val now = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val startMs = when (filters.dateRange) {
                DateRange.TODAY -> now.atStartOfDay(zone).toInstant().toEpochMilli()
                DateRange.LAST_7_DAYS -> now.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
                DateRange.LAST_30_DAYS -> now.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
                DateRange.ALL -> Long.MIN_VALUE
            }
            val min = filters.minAmount.toBigDecimalOrNull()
            val max = filters.maxAmount.toBigDecimalOrNull()
            val q = filters.query.trim()

            return source.filter { txn ->
                if (txn.createdAtMs < startMs) return@filter false
                if (filters.status != null && txn.status != filters.status) return@filter false
                if (min != null && txn.amount.amount < min) return@filter false
                if (max != null && txn.amount.amount > max) return@filter false
                if (q.isNotBlank()) {
                    val haystack = listOfNotNull(
                        txn.transactionId,
                        txn.reference,
                        txn.description,
                        txn.maskedPan,
                        txn.authCode,
                        txn.processorReference,
                    ).joinToString(" ")
                    if (!haystack.contains(q, ignoreCase = true)) return@filter false
                }
                true
            }.sortedByDescending { it.createdAtMs }
        }

        private fun String.toBigDecimalOrNull(): BigDecimal? =
            trim().takeIf { it.isNotEmpty() }?.let { runCatching { BigDecimal(it) }.getOrNull() }

        /** Exposed for tests: start-of-day epoch for a range. */
        fun rangeStartMs(range: DateRange, now: Instant = Instant.now()): Long {
            val zone = ZoneId.systemDefault()
            val today = now.atZone(zone).toLocalDate()
            return when (range) {
                DateRange.TODAY -> today.atStartOfDay(zone).toInstant().toEpochMilli()
                DateRange.LAST_7_DAYS -> today.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
                DateRange.LAST_30_DAYS -> today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
                DateRange.ALL -> Long.MIN_VALUE
            }
        }
    }
}
