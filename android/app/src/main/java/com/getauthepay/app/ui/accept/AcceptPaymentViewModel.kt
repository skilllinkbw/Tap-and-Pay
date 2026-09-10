package com.getauthepay.app.ui.accept

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getauthepay.app.BuildConfig
import com.getauthepay.app.core.PaymentAcceptanceEngine
import com.getauthepay.app.core.currency.CurrencyCatalog
import com.getauthepay.app.core.ledger.TransactionLedger
import com.getauthepay.app.core.log.SecureLogger
import com.getauthepay.app.core.models.PaymentRequest
import com.getauthepay.app.core.models.PaymentResult
import com.getauthepay.app.core.models.PaymentStatus
import com.getauthepay.app.core.models.TestScenario
import com.getauthepay.app.core.models.TransactionStatus
import com.getauthepay.app.core.nfc.NfcAvailability
import com.getauthepay.app.core.risk.RiskContext
import com.getauthepay.app.core.validation.Validators
import com.getauthepay.app.data.SessionManager
import com.getauthepay.app.di.ServiceLocator
import com.getauthepay.app.security.DeviceSecurityChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.ZoneId
import java.util.UUID

/**
 * Drives the "Accept Payment" screen.
 *
 * Layering rule from the build directive: **UI code never touches NFC or
 * payment-processing code.** This ViewModel talks only to
 * [PaymentAcceptanceEngine]; the engine owns the NFC provider, the EMV
 * kernel seam, the processor, the risk service and the ledger.
 *
 * Idempotency: one [AcceptPaymentViewModel] instance generates a single
 * [PaymentRequest.idempotencyKey] per logical attempt. Pressing "Charge"
 * repeatedly while the attempt is in flight is a no-op, and if the merchant
 * manages to start a second run the engine replays the cached result rather
 * than creating a second payment. The key is only regenerated after a
 * terminal outcome plus an explicit "New payment".
 */
class AcceptPaymentViewModel(
    private val engine: PaymentAcceptanceEngine,
    private val ledger: TransactionLedger,
    private val session: SessionManager,
    private val nfcAvailability: NfcAvailability,
    private val deviceTrustScore: Int,
) : ViewModel() {

    /** Where the merchant is in the acceptance flow. */
    sealed interface Stage {
        data object EnterAmount : Stage
        data object InProgress : Stage
        data object Result : Stage
    }

    data class UiState(
        val stage: Stage = Stage.EnterAmount,
        val amountInput: String = "",
        val reference: String = "",
        val currencyCode: String = CurrencyCatalog.defaultCurrency,
        val amountError: String? = null,
        val referenceError: String? = null,
        val nfcBlockedReason: String? = null,
        val status: PaymentStatus? = null,
        val result: PaymentResult? = null,
        val scenario: TestScenario = TestScenario.TEST_APPROVED,
        val inFlight: Boolean = false,
        val fatalError: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Stable across re-submissions of the *same* logical payment. Regenerated
     * only by [startNewPayment].
     */
    private var idempotencyKey: String = UUID.randomUUID().toString()

    private var collectJob: Job? = null

    init {
        _state.value = _state.value.copy(
            nfcBlockedReason = when (nfcAvailability) {
                NfcAvailability.AvailableEnabled -> null
                NfcAvailability.AvailableDisabled ->
                    "NFC is turned off. Enable it in Android Settings to accept contactless payments."
                NfcAvailability.NotAvailable ->
                    "This device has no NFC reader, so it cannot accept contactless card payments."
            },
            currencyCode = session.current().merchant.settlementCurrency
                .takeIf { CurrencyCatalog.isSupported(it) }
                ?: CurrencyCatalog.defaultCurrency,
        )
    }

    fun setAmount(raw: String) {
        _state.value = _state.value.copy(amountInput = raw, amountError = null)
    }

    fun setReference(raw: String) {
        _state.value = _state.value.copy(reference = raw, referenceError = null)
    }

    fun setCurrency(code: String) {
        _state.value = _state.value.copy(currencyCode = code, amountError = null)
    }

    fun setScenario(scenario: TestScenario) {
        _state.value = _state.value.copy(scenario = scenario)
    }

    /** Parses the amount, or records a validation error and returns null. */
    fun parseAmount(): BigDecimal? {
        val s = _state.value
        return try {
            Validators.validateAmount(s.amountInput, s.currencyCode)
        } catch (e: IllegalArgumentException) {
            _state.value = s.copy(amountError = e.message)
            null
        }
    }

    /** Begins the NFC read. Safe to call repeatedly — guarded by [UiState.inFlight]. */
    fun startPayment() {
        val s = _state.value
        if (s.inFlight) return
        if (s.nfcBlockedReason != null) {
            _state.value = s.copy(fatalError = s.nfcBlockedReason)
            return
        }

        val amount = parseAmount() ?: return
        val reference = try {
            Validators.validateReference(s.reference)
        } catch (e: IllegalArgumentException) {
            _state.value = s.copy(referenceError = e.message)
            return
        }

        val sessionState = session.current()
        val request = PaymentRequest(
            idempotencyKey = idempotencyKey,
            requestId = UUID.randomUUID().toString(),
            amount = amount,
            currency = s.currencyCode,
            reference = reference,
            merchantId = sessionState.merchant.merchantId,
            terminalId = sessionState.terminalId,
            sandboxScenario = if (BuildConfig.SANDBOX_PAYMENTS) s.scenario else null,
        )

        _state.value = s.copy(
            stage = Stage.InProgress,
            inFlight = true,
            status = PaymentStatus.CREATED,
            result = null,
            fatalError = null,
        )

        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            try {
                engine.processPayment(request, buildRiskContext(request)).collect { result ->
                    _state.value = _state.value.copy(
                        status = result.status,
                        result = result,
                        stage = if (result.status.isTerminal()) Stage.Result else Stage.InProgress,
                        inFlight = !result.status.isTerminal(),
                        fatalError = if (result.status.isTerminal() && !result.status.isSuccess()) {
                            result.errorMessage
                        } else {
                            null
                        },
                    )
                }
                // Flow completed without a terminal emission — treat as failed
                // rather than leaving the merchant watching a spinner. Money is
                // never claimed in this path.
                if (_state.value.status?.isTerminal() != true) {
                    _state.value = _state.value.copy(
                        stage = Stage.Result,
                        inFlight = false,
                        fatalError = "The payment did not complete. No money was taken.",
                    )
                }
            } catch (t: Throwable) {
                SecureLogger.event(
                    event = "ui.payment.exception",
                    status = PaymentStatus.FAILED.name,
                    extra = mapOf("type" to (t::class.simpleName ?: "Unknown")),
                )
                _state.value = _state.value.copy(
                    stage = Stage.Result,
                    inFlight = false,
                    fatalError = "The payment could not be completed. No money was taken.",
                )
            }
        }
    }

    fun cancelPayment() {
        if (!_state.value.inFlight) return
        viewModelScope.launch {
            runCatching { engine.cancel() }
        }
    }

    /** Clears the outcome and issues a fresh idempotency key for a new sale. */
    fun startNewPayment() {
        collectJob?.cancel()
        idempotencyKey = UUID.randomUUID().toString()
        _state.value = _state.value.copy(
            stage = Stage.EnterAmount,
            status = null,
            result = null,
            fatalError = null,
            inFlight = false,
            amountInput = "",
            reference = "",
            amountError = null,
            referenceError = null,
        )
    }

    /** Re-attempts the same amount with a NEW idempotency key (new logical sale). */
    fun retryPayment() {
        collectJob?.cancel()
        idempotencyKey = UUID.randomUUID().toString()
        _state.value = _state.value.copy(
            stage = Stage.EnterAmount,
            status = null,
            result = null,
            fatalError = null,
            inFlight = false,
        )
    }

    private fun buildRiskContext(request: PaymentRequest): RiskContext {
        val nowMs = System.currentTimeMillis()
        val hour = java.time.LocalDateTime.now(ZoneId.systemDefault()).hour
        val terminalId = session.current().terminalId

        val all = ledger.snapshot()
        val lastHour = all.count { nowMs - it.createdAtMs <= 3_600_000L }
        val lastDay = all.count { nowMs - it.createdAtMs <= 86_400_000L }
        val declinesLastHour = all.count {
            it.status == TransactionStatus.DECLINED && nowMs - it.createdAtMs <= 3_600_000L
        }

        return RiskContext(
            merchantId = request.merchantId ?: session.current().merchant.merchantId,
            terminalId = terminalId,
            amount = request.toMoney(),
            transactionCountLastHour = lastHour,
            transactionCountLastDay = lastDay,
            previousDeclinesLastHour = declinesLastHour,
            deviceTrustScore = deviceTrustScore,
            newDevice = session.current().merchant.createdAtMs == 0L,
            isOverseas = false,
            timeOfDayLocalHour = hour,
        )
    }

    companion object {
        /** Builds a ViewModel wired from the app's service locator. */
        fun create(locator: ServiceLocator, context: Context): AcceptPaymentViewModel =
            AcceptPaymentViewModel(
                engine = locator.buildPaymentEngine(),
                ledger = locator.transactionLedger,
                session = locator.sessionManager,
                nfcAvailability = locator.nfcAvailability(),
                deviceTrustScore = DeviceSecurityChecker.trustScore(context),
            )
    }
}
