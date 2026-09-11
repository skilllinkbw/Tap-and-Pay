package com.getauthepay.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getauthepay.app.core.models.Transaction
import com.getauthepay.app.core.models.TransactionStatus
import com.getauthepay.app.ui.LocalLocator
import com.getauthepay.app.ui.authePayViewModelFactory
import com.getauthepay.app.ui.components.EmptyState
import com.getauthepay.app.ui.components.ErrorState
import com.getauthepay.app.ui.components.LoadingState
import com.getauthepay.app.ui.components.ScreenScaffold
import com.getauthepay.app.ui.components.StatusBadge
import com.getauthepay.app.ui.components.TransactionStatusTone
import com.getauthepay.app.ui.formatDateTime
import com.getauthepay.app.ui.formatMoney

/**
 * Transaction history.
 *
 * Every row is a real [Transaction] from the AuthePay backend or from this
 * device's own ledger. When there is nothing to list the screen says
 * "No transactions yet" — it does not render placeholder rows.
 */
@Composable
fun TransactionHistoryScreen(
    onBack: () -> Unit,
    onTransaction: (String) -> Unit,
) {
    val locator = LocalLocator.current
    val vm: TransactionsViewModel = viewModel(
        factory = authePayViewModelFactory { TransactionsViewModel(locator.merchantRepository) },
    )
    val state by vm.state.collectAsState()
    var showFilters by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Transactions",
        subtitle = if (state.totalCount > 0) "${state.totalCount} transaction(s)" else null,
        onBack = onBack,
        actions = {
            IconButton(onClick = { showFilters = !showFilters }) {
                Icon(Icons.Outlined.FilterAlt, contentDescription = "Filter transactions")
            }
            IconButton(onClick = vm::refresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        if (showFilters) {
            FilterPanel(
                filters = state.filters,
                vm = vm,
                onClose = { showFilters = false },
            )
            Spacer(Modifier.height(8.dp))
        }

        if (state.degradedReason != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    state.degradedReason!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        when {
            state.loading && state.all.isEmpty() -> LoadingState(message ="Loading transactions…")
            state.error != null && state.all.isEmpty() ->
                ErrorState(state.error!!, onRetry = vm::refresh)
            state.visible.isEmpty() -> EmptyState(
                message = if (state.filters.isActive) {
                    "No transactions match these filters."
                } else {
                    "No transactions yet"
                },
                actionLabel = if (state.filters.isActive) "Clear filters" else null,
                onAction = if (state.filters.isActive) vm::clearFilters else null,
            )
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(state.visible, key = { it.transactionId }) { txn ->
                        TransactionRow(txn = txn, onClick = { onTransaction(txn.transactionId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(
    txn: Transaction,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatMoney(txn.amount),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    formatDateTime(txn.createdAtMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                txn.reference?.let {
                    Text(
                        "Ref $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(
                text = txn.status.name.replace('_', ' ').lowercase()
                    .replaceFirstChar { it.uppercase() },
                tone = TransactionStatusTone(txn.status),
            )
        }
    }
}

@Composable
private fun FilterPanel(
    filters: TransactionsViewModel.Filters,
    vm: TransactionsViewModel,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                if (filters.isActive) {
                    TextButton(onClick = vm::clearFilters) { Text("Clear") }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close filters")
                }
            }

            OutlinedTextField(
                value = filters.query,
                onValueChange = vm::setQuery,
                label = { Text("Search reference or id") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            Text("Status", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = filters.status == null,
                    onClick = { vm.setStatus(null) },
                    label = { Text("All") },
                )
                TransactionStatus.entries.take(3).forEach { status ->
                    FilterChip(
                        selected = filters.status == status,
                        onClick = { vm.setStatus(status) },
                        label = { Text(status.name.replace('_', ' ').lowercase()
                            .replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Date", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TransactionsViewModel.DateRange.entries.forEach { range ->
                    FilterChip(
                        selected = filters.dateRange == range,
                        onClick = { vm.setDateRange(range) },
                        label = { Text(range.label) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filters.minAmount,
                    onValueChange = vm::setMinAmount,
                    label = { Text("Min") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = filters.maxAmount,
                    onValueChange = vm::setMaxAmount,
                    label = { Text("Max") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
