package com.getauthepay.app.core.receipt

import com.getauthepay.app.core.models.Money
import com.getauthepay.app.core.models.Transaction

/**
 * Renders a [Transaction] into a printable, shareable, receipt.
 *
 * The receipt model is a pure data class so it can be rendered to:
 *   - the in-app receipt screen (Compose)
 *   - a sharable text body (SMS / email)
 *   - a printable layout (Bluetooth/USB receipt printers)
 *   - a QR code (for merchant-issued receipts)
 *
 * Sensitive card data (full PAN, CVV, PIN, OTP) is NEVER rendered.
 * Only the masked PAN produced by the tokenized payment result is
 * exposed.
 */
interface ReceiptService {
    fun build(transaction: Transaction, merchantBusinessName: String): Receipt
    fun shareText(receipt: Receipt): String
}

data class Receipt(
    val receiptId: String,
    val merchantName: String,
    val amount: Money,
    val maskedPan: String?,
    val cardType: String?,
    val transactionId: String,
    val authCode: String?,
    val reference: String?,
    val status: String,
    val timestampMs: Long,
    val correlationId: String?,
)

class DefaultReceiptService : ReceiptService {
    override fun build(transaction: Transaction, merchantBusinessName: String): Receipt =
        Receipt(
            receiptId = "RCT-${transaction.transactionId}",
            merchantName = merchantBusinessName,
            amount = transaction.amount,
            maskedPan = transaction.maskedPan,
            cardType = transaction.cardType,
            transactionId = transaction.transactionId,
            authCode = transaction.authCode,
            reference = transaction.reference,
            status = transaction.status.name,
            timestampMs = transaction.completedAtMs ?: transaction.createdAtMs,
            correlationId = transaction.correlationId,
        )

    override fun shareText(receipt: Receipt): String = buildString {
        appendLine("AuthePay Receipt")
        appendLine("Merchant: ${receipt.merchantName}")
        appendLine("Amount: ${receipt.amount}")
        if (receipt.maskedPan != null) appendLine("Card: ${receipt.maskedPan} (${receipt.cardType ?: ""})".trim())
        appendLine("Transaction: ${receipt.transactionId}")
        if (receipt.authCode != null) appendLine("Auth: ${receipt.authCode}")
        if (!receipt.reference.isNullOrBlank()) appendLine("Reference: ${receipt.reference}")
        appendLine("Status: ${receipt.status}")
        appendLine("Issued: ${receipt.timestampMs}")
        appendLine("Keep this receipt for your records.")
    }
}