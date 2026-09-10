package com.getauthepay.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getauthepay.app.core.models.Money
import com.getauthepay.app.core.models.Transaction
import com.getauthepay.app.core.models.TransactionStatus
import com.getauthepay.app.data.MerchantRepository
import com.getauthepay.app.data.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransactionDetailViewModel(
    private val transactionId: String,
    private val repository: MerchantRepository,
    private val session: SessionManager,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val transaction: Transaction? = null,
        val notFound: Boolean = false,
        /** Null until the role is known; controls whether REFUND is offered. */
        val canRefund: Boolean = false,
    )

    sealed interface Refundable {
        data class Yes(val remaining: Money) : Refundable
        data object No : Refundable
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState(loading = true)
            val txn = repository.findTransaction(transactionId)
            val role = session.current().merchant.role
            _state.value = UiState(
                loading = false,
                transaction = txn,
                notFound = txn == null,
                canRefund = txn != null &&
                    role.canIssueRefunds() &&
                    (txn.status == TransactionStatus.APPROVED ||
                        txn.status == TransactionStatus.PARTIALLY_REFUNDED),
            )
        }
    }

    /**
     * Remaining amount available to refund. A transaction is only refundable
     * once it has been approved, and only up to the outstanding balance —
     * this prevents refunding more than was taken.
     */
    fun refundable(): Refundable {
        val txn = _state.value.transaction ?: return Refundable.No
        if (txn.status != TransactionStatus.APPROVED &&
            txn.status != TransactionStatus.PARTIALLY_REFUNDED
        ) return Refundable.No
        val remaining = txn.refundedAmount?.let { runCatching { txn.amount.minus(it) }.getOrNull() }
            ?: txn.amount
        return if (remaining.isPositive()) Refundable.Yes(remaining) else Refundable.No
    }
}
