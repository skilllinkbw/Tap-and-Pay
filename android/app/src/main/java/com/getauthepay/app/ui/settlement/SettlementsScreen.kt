package com.getauthepay.app.ui.settlement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getauthepay.app.data.MerchantRepository
import com.getauthepay.app.ui.components.EmptyState
import com.getauthepay.app.ui.components.ErrorState
import com.getauthepay.app.ui.components.LoadingState
import com.getauthepay.app.ui.components.ScreenScaffold
import com.getauthepay.app.ui.components.SectionHeader
import com.getauthepay.app.ui.formatDate
import com.getauthepay.app.ui.formatMoney

/**
 * Settlements (payouts).
 *
 * Hard rule: settlement figures are **server-derived only**. The app must
 * never infer a payout amount or date from local transactions, because fees,
 * chargebacks and cut-off times all live on the acquirer side. If the backend
 * has no batches, the screen says "No settlements yet".
 */
@Composable
fun SettlementsScreen(onBack: () -> Unit) {
    val repository = com.getauthepay.app.ui.LocalLocator.current.merchantRepository
    var state by remember { mutableStateOf<SettleState>(SettleState.Loading) }
    var reload by remember { mutableStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(reload) {
        state = SettleState.Loading
        state = runCatching { load(repository) }
            .getOrElse { SettleState.Error(it.message ?: "Could not load settlements") }
    }

    ScreenScaffold(
        title = "Settlements",
        subtitle = "Payouts to your settlement account",
        onBack = onBack,
        actions = {
            IconButton(onClick = { reload++ }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh settlements")
            }
        },
    ) {
        when (val s = state) {
            SettleState.Loading -> LoadingState(message ="Loading settlements…")
            is SettleState.Error -> ErrorState(s.message) { reload++ }
            is SettleState.Loaded -> {
                if (s.settlements.isEmpty()) {
                    EmptyState(
                        message = "No settlements yet",
                        actionLabel = null,
                        onAction = null,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { Spacer(Modifier.height(8.dp)) }
                        items(s.settlements, key = { it.settlementId }) { settlement ->
                            SettlementCard(settlement = settlement)
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettlementCard(settlement: SettlementRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        formatMoney(settlement.netAmount),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "${formatDate(settlement.periodStartMs)} – ${formatDate(settlement.periodEndMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        settlement.statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = when (settlement.statusLabel.uppercase()) {
                            "SETTLED" -> com.getauthepay.app.ui.theme.SuccessGreen
                            "FAILED" -> com.getauthepay.app.ui.theme.DangerRed
                            else -> com.getauthepay.app.ui.theme.WarningAmber
                        },
                    )
                    settlement.settledAtMs?.let {
                        Text(
                            formatDate(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Gross", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(settlement.grossAmount), style = MaterialTheme.typography.bodyMedium)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fees", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(settlement.feeAmount), style = MaterialTheme.typography.bodyMedium)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Transactions", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(settlement.transactionCount.toString(),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            settlement.bankReference?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Bank reference: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private sealed interface SettleState {
    data object Loading : SettleState
    data class Loaded(val settlements: List<SettlementRow>) : SettleState
    data class Error(val message: String) : SettleState
}

private data class SettlementRow(
    val settlementId: String,
    val periodStartMs: Long,
    val periodEndMs: Long,
    val grossAmount: com.getauthepay.app.core.models.Money,
    val feeAmount: com.getauthepay.app.core.models.Money,
    val netAmount: com.getauthepay.app.core.models.Money,
    val transactionCount: Int,
    val statusLabel: String,
    val settledAtMs: Long?,
    val bankReference: String?,
)

private suspend fun load(repository: MerchantRepository): SettleState {
    val rows = repository.refresh().settlements.map {
        SettlementRow(
            settlementId = it.settlementId,
            periodStartMs = it.periodStartMs,
            periodEndMs = it.periodEndMs,
            grossAmount = it.grossAmount,
            feeAmount = it.feeAmount,
            netAmount = it.netAmount,
            transactionCount = it.transactionCount,
            statusLabel = it.status.name,
            settledAtMs = it.settledAtMs,
            bankReference = it.bankReference,
        )
    }
    return SettleState.Loaded(rows)
}
