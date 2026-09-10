package com.getauthepay.app.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getauthepay.app.ui.LocalLocator
import com.getauthepay.app.ui.authePayViewModelFactory
import com.getauthepay.app.ui.components.DetailRow
import com.getauthepay.app.ui.components.EmptyState
import com.getauthepay.app.ui.components.LoadingState
import com.getauthepay.app.ui.components.ScreenScaffold
import com.getauthepay.app.ui.components.SectionHeader
import com.getauthepay.app.ui.components.StatusBadge
import com.getauthepay.app.ui.components.TransactionStatusTone
import com.getauthepay.app.ui.formatDateTime
import com.getauthepay.app.ui.formatMoney

/**
 * Full detail view for one transaction.
 *
 * Only the **masked** card reference is shown. The app never receives a
 * full PAN, so there is nothing sensitive to leak here — the masked value
 * is the widest card representation that exists anywhere in the app.
 */
@Composable
fun TransactionDetailScreen(
    transactionId: String,
    onBack: () -> Unit,
    onRefund: () -> Unit,
) {
    val locator = LocalLocator.current
    val vm: TransactionDetailViewModel = viewModel(
        factory = authePayViewModelFactory {
            TransactionDetailViewModel(
                transactionId,
                locator.merchantRepository,
                locator.sessionManager,
            )
        },
    )
    val state by vm.state.collectAsState()

    ScreenScaffold(
        title = "Transaction",
        subtitle = transactionId,
        onBack = onBack,
        actions = {
            androidx.compose.material3.IconButton(onClick = vm::load) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Reload")
            }
        },
    ) {
        when {
            state.loading -> LoadingState("Loading transaction…")
            state.notFound || state.transaction == null ->
                EmptyState("We could not find that transaction.")
            else -> {
                val txn = state.transaction!!
                val refundable = vm.refundable()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = formatMoney(txn.amount),
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Spacer(Modifier.height(8.dp))
                            StatusBadge(
                                text = txn.status.name.replace('_', ' ').lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                tone = TransactionStatusTone(txn.status),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = formatDateTime(txn.createdAtMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    SectionHeader("Transaction")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            DetailRow("Transaction ID", txn.transactionId)
                            HorizontalDivider()
                            DetailRow("Merchant ID", txn.merchantId)
                            HorizontalDivider()
                            DetailRow("Terminal / device", txn.terminalId)
                            HorizontalDivider()
                            DetailRow("Created", formatDateTime(txn.createdAtMs))
                            txn.completedAtMs?.let {
                                HorizontalDivider()
                                DetailRow("Completed", formatDateTime(it))
                            }
                            txn.reference?.let {
                                HorizontalDivider()
                                DetailRow("Merchant reference", it)
                            }
                            txn.description?.let {
                                HorizontalDivider()
                                DetailRow("Description", it)
                            }
                        }
                    }

                    SectionHeader("Payment")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            // Masked reference only — the full PAN never reaches the app.
                            DetailRow("Card", txn.maskedPan ?: "—")
                            HorizontalDivider()
                            DetailRow("Card type", txn.cardType ?: "—")
                            HorizontalDivider()
                            DetailRow("Auth code", txn.authCode ?: "—")
                            HorizontalDivider()
                            DetailRow("Processor reference", txn.processorReference ?: "—")
                            txn.correlationId?.let {
                                HorizontalDivider()
                                DetailRow("Correlation ID", it)
                            }
                        }
                    }

                    txn.riskDecision?.let { risk ->
                        SectionHeader("Risk")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                DetailRow("Decision", risk.decision.name)
                                HorizontalDivider()
                                DetailRow("Score", "${risk.score} / 100")
                                HorizontalDivider()
                                DetailRow("Model / rules", risk.modelVersion)
                                if (risk.reasonCodes.isNotEmpty()) {
                                    HorizontalDivider()
                                    DetailRow("Reason codes", risk.reasonCodes.joinToString(", "))
                                }
                                HorizontalDivider()
                                DetailRow("Evaluated", formatDateTime(risk.timestampMs))
                            }
                        }
                    }

                    if (txn.refundedAmount != null) {
                        SectionHeader("Refunds")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                DetailRow("Refunded to date", formatMoney(txn.refundedAmount!!))
                                when (refundable) {
                                    is TransactionDetailViewModel.Refundable.Yes -> {
                                        HorizontalDivider()
                                        DetailRow(
                                            "Remaining refundable",
                                            formatMoney(
                                                (refundable as TransactionDetailViewModel.Refundable.Yes)
                                                    .remaining,
                                            ),
                                        )
                                    }
                                    TransactionDetailViewModel.Refundable.No -> Unit
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    if (state.canRefund && refundable is TransactionDetailViewModel.Refundable.Yes) {
                        Button(
                            onClick = onRefund,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Text("REFUND THIS PAYMENT", style = MaterialTheme.typography.titleMedium)
                        }
                    } else if (txn.status == com.getauthepay.app.core.models.TransactionStatus.DECLINED) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "This payment was declined, so there is nothing to refund.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Back") }
                }
            }
        }
    }
}
