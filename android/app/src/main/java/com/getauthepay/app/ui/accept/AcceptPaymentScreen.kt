package com.getauthepay.app.ui.accept

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getauthepay.app.BuildConfig
import com.getauthepay.app.core.currency.CurrencyCatalog
import com.getauthepay.app.core.models.PaymentStatus
import com.getauthepay.app.core.models.TestScenario
import com.getauthepay.app.ui.LocalLocator
import com.getauthepay.app.ui.authePayViewModelFactory
import com.getauthepay.app.ui.components.AmountKeypad
import com.getauthepay.app.ui.components.NfcPulse
import com.getauthepay.app.ui.components.SandboxBanner
import com.getauthepay.app.ui.formatAmount
import com.getauthepay.app.ui.formatDateTime
import com.getauthepay.app.ui.theme.BrandBlue
import com.getauthepay.app.ui.theme.DangerRed
import com.getauthepay.app.ui.theme.SuccessGreen
import com.getauthepay.app.ui.theme.WarningAmber
import java.math.BigDecimal

/**
 * The primary merchant flow:
 *
 * ```
 *   Amount → (reference) → CONFIRM AMOUNT → READY TO TAP → card presented
 *          → PROCESSING → AUTHORIZING → APPROVED / DECLINED / FAILED
 *          → receipt
 * ```
 *
 * Every one of the twelve [PaymentStatus] values has an explicit rendering
 * here — the merchant is never left looking at an unexplained spinner.
 *
 * Sensitive-data rule: this screen displays the transaction reference, the
 * amount, and (on an approval) the masked card reference only. It never has
 * access to a PAN, CVV, PIN or track data, because none of those ever enter
 * the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptPaymentScreen(
    onCompleted: () -> Unit,
    onScanQr: () -> Unit,
) {
    val context = LocalContext.current
    val locator = LocalLocator.current
    val vm: AcceptPaymentViewModel = viewModel(
        factory = authePayViewModelFactory {
            AcceptPaymentViewModel.create(locator, context)
        },
    )
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            Column {
                SandboxBanner()
                androidx.compose.material3.TopAppBar(
                    title = { Text(if (state.stage == AcceptPaymentViewModel.Stage.EnterAmount) "Accept payment" else "Payment") },
                    navigationIcon = {
                        IconButton(
                            onClick = onCompleted,
                            enabled = !state.inFlight,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (state.stage == AcceptPaymentViewModel.Stage.EnterAmount) {
                            IconButton(onClick = onScanQr) {
                                Icon(
                                    Icons.Outlined.QrCodeScanner,
                                    contentDescription = "Scan a payment QR code",
                                )
                            }
                        }
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (state.stage) {
            AcceptPaymentViewModel.Stage.EnterAmount ->
                AmountEntry(
                    state = state,
                    vm = vm,
                    modifier = Modifier.padding(padding),
                )

            AcceptPaymentViewModel.Stage.InProgress ->
                InProgressPane(
                    state = state,
                    onCancel = vm::cancelPayment,
                    modifier = Modifier.padding(padding),
                )

            AcceptPaymentViewModel.Stage.Result ->
                ResultPane(
                    state = state,
                    context = context,
                    onDone = onCompleted,
                    onNewPayment = vm::startNewPayment,
                    onRetry = vm::retryPayment,
                    modifier = Modifier.padding(padding),
                )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1 — amount entry
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountEntry(
    state: AcceptPaymentViewModel.UiState,
    vm: AcceptPaymentViewModel,
    modifier: Modifier = Modifier,
) {
    val amount = runCatching { BigDecimal(state.amountInput) }.getOrNull()
    val currencies = remember { CurrencyCatalog.allSupported() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Amount display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Amount to charge",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (state.amountInput.isBlank()) {
                        formatAmount(BigDecimal.ZERO, state.currencyCode)
                    } else {
                        formatAmount(amount ?: BigDecimal.ZERO, state.currencyCode)
                    },
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics {
                        contentDescription = "Amount " + (state.amountInput.ifBlank { "zero" })
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    state.currencyCode,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.amountError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                state.amountError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Currency selector — never hardcoded to BWP.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScrollSafe(),
        ) {
            currencies.forEach { currency ->
                val selected = currency.currencyCode == state.currencyCode
                TextButton(onClick = { vm.setCurrency(currency.currencyCode) }) {
                    Text(
                        currency.currencyCode,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.reference,
            onValueChange = vm::setReference,
            label = { Text("Reference (optional)") },
            placeholder = { Text("Invoice or order number") },
            singleLine = true,
            isError = state.referenceError != null,
            supportingText = state.referenceError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        AmountKeypad(
            value = state.amountInput,
            onValueChange = vm::setAmount,
        )

        Spacer(Modifier.height(16.dp))

        if (BuildConfig.SANDBOX_PAYMENTS) {
            SandboxScenarioPicker(
                selected = state.scenario,
                onSelect = vm::setScenario,
            )
            Spacer(Modifier.height(12.dp))
        }

        if (state.nfcBlockedReason != null) {
            NfcWarning(state.nfcBlockedReason!!)
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = vm::startPayment,
            enabled = state.amountInput.isNotBlank() && state.nfcBlockedReason == null,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .semantics { contentDescription = "Confirm amount and start contactless payment" },
            shape = MaterialTheme.shapes.large,
        ) {
            Text("CONFIRM AMOUNT", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SandboxScenarioPicker(
    selected: TestScenario,
    onSelect: (TestScenario) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "SANDBOX RESULT",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = WarningAmber,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose the outcome the simulator should produce. This control does " +
                    "not exist in production builds.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            TestScenario.entries.forEach { scenario ->
                OutlinedButton(
                    onClick = { onSelect(scenario) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    colors = if (scenario == selected) {
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = BrandBlue.copy(alpha = 0.12f),
                            contentColor = BrandBlue,
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                ) {
                    Text(
                        scenario.name.removePrefix("TEST_").replace('_', ' '),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun NfcWarning(reason: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Nfc, contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Text(
                reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2 — tap / processing
// ---------------------------------------------------------------------------

@Composable
private fun InProgressPane(
    state: AcceptPaymentViewModel.UiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (headline, sub) = when (state.status) {
        PaymentStatus.CREATED -> "Preparing…" to "Starting the contactless reader"
        PaymentStatus.READY_FOR_TAP -> "READY TO TAP" to "Hold the customer's card or phone near the back of this device"
        PaymentStatus.CARD_DETECTED -> "Card detected" to "Reading the card…"
        PaymentStatus.PROCESSING -> "PROCESSING PAYMENT" to "Exchanging data with the card"
        PaymentStatus.AUTHORIZING -> "PROCESSING PAYMENT" to "Requesting authorisation…"
        else -> "PROCESSING PAYMENT" to "Please wait"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
            NfcPulse(sizeDp = 240.dp, tint = BrandBlue)
            Icon(
                imageVector = Icons.Filled.Nfc,
                contentDescription = null,
                tint = BrandBlue,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = sub,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Cancel payment")
        }
    }
}

// ---------------------------------------------------------------------------
// Step 3 — result
// ---------------------------------------------------------------------------

@Composable
private fun ResultPane(
    state: AcceptPaymentViewModel.UiState,
    context: android.content.Context,
    onDone: () -> Unit,
    onNewPayment: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val result = state.result
    val approved = state.status == PaymentStatus.APPROVED
    val amount = runCatching { BigDecimal(state.amountInput) }.getOrNull() ?: BigDecimal.ZERO
    val currency = state.currencyCode

    val (icon, tint, headline) = when {
        approved -> Triple(Icons.Filled.CheckCircle, SuccessGreen, "PAYMENT APPROVED")
        state.status == PaymentStatus.DECLINED ->
            Triple(Icons.Filled.ErrorOutline, DangerRed, "PAYMENT DECLINED")
        state.status == PaymentStatus.CANCELLED ->
            Triple(Icons.Outlined.Cancel, WarningAmber, "PAYMENT CANCELLED")
        state.status == PaymentStatus.TIMEOUT ->
            Triple(Icons.Outlined.Cancel, WarningAmber, "PAYMENT TIMED OUT")
        else -> Triple(Icons.Filled.ErrorOutline, DangerRed, "PAYMENT UNAVAILABLE")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon as ImageVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(88.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = tint,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Assertive },
        )

        if (approved) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatAmount(amount, currency),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = result?.transactionId ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))

        val message = when {
            approved -> "The payment was authorised. Offer the customer a receipt."
            else -> result?.errorMessage
                ?: state.fatalError
                ?: "The payment was not completed. No money was taken."
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (approved && result != null) {
            Spacer(Modifier.height(20.dp))
            ResultDetailCard(result = result, amountText = formatAmount(amount, currency))
        }

        Spacer(Modifier.height(28.dp))

        if (approved) {
            Button(
                onClick = {
                    val text = buildReceiptText(
                        reference = result?.transactionId ?: "—",
                        amount = formatAmount(amount, currency),
                        dateTime = formatDateTime(result?.timestampMs ?: System.currentTimeMillis()),
                        maskedCard = result?.maskedPan,
                        authCode = result?.authCode,
                        merchantRef = result?.reference,
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                        putExtra(Intent.EXTRA_SUBJECT, "AuthePay receipt")
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(send, "Send receipt"))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("SHARE RECEIPT", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onNewPayment,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("NEW PAYMENT", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDone) { Text("Done") }
        } else {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("TRY AGAIN", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDone) { Text("Back to home") }
        }
    }
}

@Composable
private fun ResultDetailCard(
    result: com.getauthepay.app.core.models.PaymentResult,
    amountText: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ResultRow("Amount", amountText)
            ResultRow("Reference", result.transactionId ?: "—")
            result.authCode?.let { ResultRow("Auth code", it) }
            // Only the masked reference is ever shown — never a full PAN.
            result.maskedPan?.let { ResultRow("Card", "$it${result.cardType?.let { t -> " ($t)" } ?: ""}") }
            result.processorReference?.let { ResultRow("Processor ref", it) }
            result.reference?.let { ResultRow("Merchant ref", it) }
            result.correlationId?.let { ResultRow("Correlation", it.take(8) + "…") }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

/**
 * Builds the plain-text receipt body.
 *
 * It contains no PAN, CVV, PIN or track data — only the masked card
 * reference returned by the tokenised payment result.
 */
private fun buildReceiptText(
    reference: String,
    amount: String,
    dateTime: String,
    maskedCard: String?,
    authCode: String?,
    merchantRef: String?,
): String = buildString {
    appendLine("AUTHEPAY")
    appendLine("Tap & Pay receipt")
    appendLine()
    appendLine("Amount:  $amount")
    appendLine("Date:    $dateTime")
    appendLine("Ref:     $reference")
    if (authCode != null) appendLine("Auth:    $authCode")
    if (maskedCard != null) appendLine("Card:    $maskedCard")
    if (!merchantRef.isNullOrBlank()) appendLine("Your ref: $merchantRef")
    appendLine()
    appendLine("Status:  APPROVED")
    if (BuildConfig.SANDBOX_PAYMENTS) {
        appendLine()
        appendLine("*** SANDBOX TRANSACTION — NO REAL MONEY WAS MOVED ***")
    }
    appendLine()
    appendLine("Thank you for your payment.")
}

/**
 * Horizontal scroll helper that keeps the currency chips on one line.
 *
 * Marked [Composable] because it allocates a scroll state, which must be
 * remembered across recompositions.
 */
@Composable
private fun Modifier.horizontalScrollSafe(): Modifier =
    this.horizontalScroll(rememberScrollState())
