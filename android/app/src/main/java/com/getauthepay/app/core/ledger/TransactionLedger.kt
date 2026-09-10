package com.getauthepay.app.core.ledger

import com.getauthepay.app.core.log.SecureLogger
import com.getauthepay.app.core.models.Money
import com.getauthepay.app.core.models.PaymentResult
import com.getauthepay.app.core.models.PaymentStatus
import com.getauthepay.app.core.models.Transaction
import com.getauthepay.app.core.models.TransactionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory transaction ledger. It is the on-device source of truth for the
 * dashboard / history / receipt screens and is reconciled with the
 * server-authoritative ledger once the network round-trip completes.
 *
 * Deliberately NOT persisted to disk: a [Transaction] carries a masked PAN
 * and card brand, and keeping the ledger in memory means no cardholder
 * reference data is ever written to the filesystem. On cold start the
 * history is re-fetched from the server. If a future release needs offline
 * history, a Room-backed implementation can be slotted in behind the same
 * method signatures — but it must store only masked/tokenised references.
 *
 * All mutation is serialised behind a [Mutex] so concurrent payments cannot
 * interleave and corrupt the terminal index.
 */
class TransactionLedger {

    private val mutex = Mutex()
    private val transactions = ConcurrentHashMap<String, Transaction>()
    private val byTerminal = ConcurrentHashMap<String, MutableList<String>>()

    private val _flow = MutableStateFlow<List<Transaction>>(emptyList())
    val flow: StateFlow<List<Transaction>> = _flow.asStateFlow()

    /**
     * Records the outcome of a payment attempt.
     *
     * @param amount The requested amount. [PaymentResult] does not carry it
     *   (the result is intentionally narrow for PCI reasons), so the caller
     *   supplies it from the originating [com.getauthepay.app.core.models.PaymentRequest].
     * @return the persisted [Transaction], or `null` if the result had no
     *   transaction id (should not happen for authorised results).
     */
    suspend fun record(
        result: PaymentResult,
        merchantId: String,
        terminalId: String,
        amount: Money,
        reference: String? = null,
        description: String? = null,
    ): Transaction? = mutex.withLock {
        val txnId = result.transactionId
        if (txnId.isNullOrBlank()) {
            // Nothing durable to key on — log and drop rather than invent an id.
            SecureLogger.event(
                event = "ledger.record.skipped",
                correlationId = result.correlationId,
                status = result.status.name,
                extra = mapOf("reason" to "missing transactionId"),
            )
            return@withLock null
        }

        val txn = Transaction(
            transactionId = txnId,
            merchantId = merchantId,
            terminalId = terminalId,
            amount = amount,
            status = statusFor(result.status),
            createdAtMs = result.timestampMs,
            completedAtMs = if (result.status.isTerminal()) result.timestampMs else null,
            maskedPan = result.maskedPan,
            cardType = result.cardType,
            authCode = result.authCode,
            processorReference = result.processorReference,
            reference = reference,
            description = description,
            correlationId = result.correlationId,
            riskDecision = result.riskDecision,
        )

        transactions[txnId] = txn
        val index = byTerminal.getOrPut(terminalId) { mutableListOf() }
        if (txnId !in index) index.add(0, txnId)
        publish()

        SecureLogger.event(
            event = "ledger.recorded",
            transactionId = txn.transactionId,
            correlationId = result.correlationId,
            status = txn.status.name,
        )
        txn
    }

    /**
     * Applies a refund against an existing transaction.
     *
     * A refund that covers the full remaining amount moves the transaction to
     * [TransactionStatus.REFUNDED]; anything less is
     * [TransactionStatus.PARTIALLY_REFUNDED]. Repeated partial refunds
     * accumulate in [Transaction.refundedAmount] so the UI can show the
     * remaining refundable balance.
     */
    suspend fun applyRefund(
        transactionId: String,
        refundAmount: Money,
    ): Transaction? = mutex.withLock {
        val existing = transactions[transactionId] ?: return@withLock null
        if (!refundAmount.isPositive()) return@withLock null

        val alreadyRefunded = existing.refundedAmount
        val totalRefunded = if (alreadyRefunded == null) refundAmount else {
            runCatching { alreadyRefunded.plus(refundAmount) }.getOrElse { alreadyRefunded }
        }
        val isFull = totalRefunded.compareTo(existing.amount) >= 0

        val updated = existing.copy(
            refundedAmount = totalRefunded,
            status = if (isFull) TransactionStatus.REFUNDED else TransactionStatus.PARTIALLY_REFUNDED,
        )
        transactions[transactionId] = updated
        publish()

        SecureLogger.event(
            event = "ledger.refund.applied",
            transactionId = transactionId,
            status = updated.status.name,
        )
        updated
    }

    /** Marks a transaction as having a refund in flight. */
    suspend fun markRefundPending(transactionId: String): Transaction? = mutex.withLock {
        val existing = transactions[transactionId] ?: return@withLock null
        val updated = existing.copy(status = TransactionStatus.REFUND_PENDING)
        transactions[transactionId] = updated
        publish()
        updated
    }

    /** Replaces the ledger contents with a server-authoritative list. */
    suspend fun replaceAll(items: List<Transaction>) = mutex.withLock {
        transactions.clear()
        byTerminal.clear()
        items.forEach { txn ->
            transactions[txn.transactionId] = txn
            byTerminal.getOrPut(txn.terminalId) { mutableListOf() }.add(0, txn.transactionId)
        }
        publish()
        SecureLogger.event(
            event = "ledger.replaced",
            extra = mapOf("count" to items.size.toString()),
        )
    }

    fun snapshot(): List<Transaction> = transactions.values.sortedByDescending { it.createdAtMs }

    fun byTerminal(terminalId: String): List<Transaction> =
        byTerminal[terminalId].orEmpty().mapNotNull { transactions[it] }

    fun find(transactionId: String): Transaction? = transactions[transactionId]

    fun size(): Int = transactions.size

    fun clear() {
        transactions.clear()
        byTerminal.clear()
        _flow.value = emptyList()
    }

    private fun publish() {
        _flow.value = snapshot()
    }

    companion object {
        fun empty(): TransactionLedger = TransactionLedger()

        /**
         * Maps the fine-grained payment lifecycle onto the coarser status used
         * for history rendering, refunds and settlements.
         */
        fun statusFor(status: PaymentStatus): TransactionStatus = when (status) {
            PaymentStatus.APPROVED -> TransactionStatus.APPROVED
            PaymentStatus.DECLINED -> TransactionStatus.DECLINED
            PaymentStatus.REVERSED -> TransactionStatus.REVERSED
            PaymentStatus.REFUNDED -> TransactionStatus.REFUNDED
            PaymentStatus.CANCELLED,
            PaymentStatus.TIMEOUT,
            PaymentStatus.FAILED -> TransactionStatus.NOT_COMPLETED

            // Non-terminal states should not normally be recorded, but if one
            // reaches the ledger (e.g. the flow was abandoned mid-tap) it is
            // treated as an incomplete attempt rather than a success.
            PaymentStatus.CREATED,
            PaymentStatus.READY_FOR_TAP,
            PaymentStatus.CARD_DETECTED,
            PaymentStatus.PROCESSING,
            PaymentStatus.AUTHORIZING -> TransactionStatus.NOT_COMPLETED
        }
    }
}
