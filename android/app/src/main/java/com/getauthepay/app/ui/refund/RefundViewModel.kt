package com.getauthepay.app.ui.refund

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getauthepay.app.core.log.SecureLogger
import com.getauthepay.app.core.models.Money
import com.getauthepay.app.core.models.Refund
import com.getauthepay.app.core.models.RefundStatus
import com.getauthepay.app.core.models.TransactionStatus
import com.getauthepay.app.core.validation.Validators
import com.getauthepay.app.data.MerchantRepository
import com.getauthepay.app.data.SessionManager
import com.getauthepay.app.network.ApiException
import com.getauthepay.app.network.AuthePayApiClient
import com.getauthepay.app.network.NetworkUnavailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Refund issuance.
 *
 * Two gates must both pass before a refund can be submitted:
 *
 *  1. **Role** — the merchant role must satisfy [MerchantRole.canIssueRefunds]
 *     (Supervisor or above). The server enforces this again; the client only
 *     hides the affordance.
 *  2. **Biometric** — [androidx.biometric.BiometricPrompt] must succeed. The
 *     prompt result is never stored; it only unlocks the submission.
 *
 * Resulting states mirror the directive: REFUND_PENDING → REFUNDED /
 * PARTIAL_REFUND / REFUND_FAILED. A refund is only reported as complete when
 * the server confirms it.
 */
class RefundViewModel(
    private val transactionId: String,
    private val repository: MerchantRepository,
    private val session: SessionManager,
    private val api: AuthePayApiClient,
) : ViewModel() {

    sealed interface Stage {
        data object Form : Stage
        data object Submitting : Stage
        data object Result : Stage
    }

    data class UiState(
        val loading: Boolean = true,
        val notFound: Boolean = false,
        val permissionDenied: Boolean = false,
        val stage: Stage = Stage.Form,
        val originalAmount: Money? = null,
        val alreadyRefunded: Money? = null,
        val remaining: Money? = null,
        val amountInput: String = "",
        val reasonCode: String = REASONS.first().first,
        val reasonNote: String = "",
        val amountError: String? = null,
        val errorMessage: String? = null,
        val resultStatus: RefundStatus? = null,
        val refundId: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState()
            val txn = repository.findTransaction(transactionId)
            val role = session.current().merchant.role

            if (txn == null) {
                _state.value = UiState(loading = false, notFound = true)
                return@launch
            }
            if (!role.canIssueRefunds()) {
                _state.value = UiState(loading = false, permissionDenied = true)
                return@launch
            }
            if (txn.status != TransactionStatus.APPROVED &&
                txn.status != TransactionStatus.PARTIALLY_REFUNDED
            ) {
                _state.value = UiState(
                    loading = false,
                    notFound = false,
                    permissionDenied = false,
                    errorMessage = "Only approved payments can be refunded.",
                )
                return@launch
            }

            val refunded = txn.refundedAmount
            val remaining = refunded?.let {
                runCatching { txn.amount.minus(it) }.getOrDefault(txn.amount)
            } ?: txn.amount

            _state.value = UiState(
                loading = false,
                originalAmount = txn.amount,
                alreadyRefunded = refunded,
                remaining = remaining,
                amountInput = remaining.amount
                    .setScale(txn.amount.currency.defaultFractionDigits.coerceAtLeast(0),
                        RoundingMode.HALF_EVEN)
                    .toPlainString(),
            )
        }
    }

    fun setAmount(raw: String) {
        _state.value = _state.value.copy(amountInput = raw, amountError = null)
    }

    fun setReason(code: String) {
        _state.value = _state.value.copy(reasonCode = code)
    }

    fun setNote(raw: String) {
        _state.value = _state.value.copy(reasonNote = raw)
    }

    /** Validates the amount client-side. Returns null and records an error if invalid. */
    fun validate(): BigDecimal? {
        val s = _state.value
        val remaining = s.remaining ?: return null
        val parsed = try {
            Validators.validateAmount(s.amountInput, remaining.currency.currencyCode)
        } catch (e: IllegalArgumentException) {
            _state.value = s.copy(amountError = e.message)
            return null
        }
        if (parsed > remaining.amount) {
            _state.value = s.copy(
                amountError = "You cannot refund more than the outstanding amount " +
                    "(${remaining.amount.toPlainString()}).",
            )
            return null
        }
        return parsed
    }

    /**
     * Submits the refund. Callers must have already passed the biometric
     * prompt; this method does not re-prompt.
     */
    fun submit() {
        val s = _state.value
        if (s.stage == Stage.Submitting) return

        val amount = validate() ?: return
        val remaining = s.remaining ?: return

        _state.value = s.copy(stage = Stage.Submitting, errorMessage = null)

        viewModelScope.launch {
            try {
                val refund = api.createRefund(
                    transactionId = transactionId,
                    amountValue = amount.toPlainString(),
                    currency = remaining.currency.currencyCode,
                    reasonCode = s.reasonCode,
                    reasonNote = s.reasonNote.trim().takeIf { it.isNotBlank() },
                )

                if (refund == null) {
                    _state.value = _state.value.copy(
                        stage = Stage.Result,
                        resultStatus = RefundStatus.REFUND_FAILED,
                        errorMessage = "The refund could not be submitted. Please try again.",
                    )
                    return@launch
                }

                // Reflect the server-confirmed state in the local ledger.
                when (refund.status) {
                    RefundStatus.REFUNDED, RefundStatus.PARTIAL_REFUND ->
                        repository.applyRefundLocally(transactionId, refund.amount)

                    RefundStatus.REFUND_PENDING ->
                        repository.markRefundPendingLocally(transactionId)

                    RefundStatus.REFUND_FAILED -> Unit
                }

                SecureLogger.event(
                    event = "refund.created",
                    transactionId = transactionId,
                    status = refund.status.name,
                    extra = mapOf("refundId" to refund.refundId),
                )

                _state.value = _state.value.copy(
                    stage = Stage.Result,
                    resultStatus = refund.status,
                    refundId = refund.refundId,
                    errorMessage = if (refund.status == RefundStatus.REFUND_FAILED) {
                        "The refund was rejected. No money was returned."
                    } else {
                        null
                    },
                )
            } catch (nu: NetworkUnavailable) {
                _state.value = _state.value.copy(
                    stage = Stage.Form,
                    errorMessage = "Network unavailable. The refund was not submitted.",
                )
            } catch (ae: ApiException) {
                _state.value = _state.value.copy(
                    stage = Stage.Form,
                    errorMessage = if (ae.code == 403) {
                        "You do not have permission to issue refunds."
                    } else {
                        "The refund could not be submitted (error ${ae.code})."
                    },
                )
            }
        }
    }

    companion object {
        /** Reason codes accepted by the refund endpoint. */
        val REASONS: List<Pair<String, String>> = listOf(
            "CUSTOMER_REQUEST" to "Customer requested a refund",
            "DUPLICATE_CHARGE" to "Duplicate charge",
            "INCORRECT_AMOUNT" to "Incorrect amount charged",
            "GOODS_RETURNED" to "Goods returned",
            "SERVICE_NOT_RENDERED" to "Service not provided",
            "FRAUD_SUSPECTED" to "Suspected fraud",
        )
    }
}
