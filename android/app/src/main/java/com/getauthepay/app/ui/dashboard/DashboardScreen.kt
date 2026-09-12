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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DeviceHub
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getauthepay.app.core.models.Transaction
import com.getauthepay.app.ui.LocalLocator
import com.getauthepay.app.ui.authePayViewModelFactory
import com.getauthepay.app.ui.components.SandboxBanner
import com.getauthepay.app.ui.components.StatTile
import com.getauthepay.app.ui.components.StatusBadge
import com.getauthepay.app.ui.components.TransactionStatusTone
import com.getauthepay.app.ui.formatDateTime
import com.getauthepay.app.ui.formatMoney
import com.getauthepay.app.ui.theme.BrandNavy

/**
 * The merchant home screen.
 *
 * Data honesty: this screen renders **only** values returned by
 * [MerchantRepository]. If there are no transactions, the totals row shows
 * "No transactions yet" rather than zeroes dressed up as activity, and the
 * recent list shows an empty state. No figure here is a placeholder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAcceptPayment: () -> Unit,
    onTransactions: () -> Unit,
    onSettlements: () -> Unit,
    onDevices: () -> Unit,
    onTeam: () -> Unit,
    onSecurity: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    val locator = LocalLocator.current
    val vm: DashboardViewModel = viewModel(
        factory = authePayViewModelFactory {
            DashboardViewModel(locator.merchantRepository, locator.sessionManager)
        },
    )
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                SandboxBanner()
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = state.merchantName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                            Text(
                                text = "${state.merchantStatus} • ${state.role}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = vm::refresh) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh dashboard")
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.degradedReason != null) {
                Spacer(Modifier.height(8.dp))
                DegradedBanner(state.degradedReason!!)
            }

            Spacer(Modifier.height(16.dp))

            // ---- Primary action -----------------------------------------
            Button(
                onClick = onAcceptPayment,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .semantics { contentDescription = "Accept payment" },
                shape = MaterialTheme.shapes.large,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "ACCEPT PAYMENT",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Tap the customer's card — or scan their QR",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---- Today's summary ---------------------------------------
            SummaryCard(state = state)

            Spacer(Modifier.height(16.dp))

            // ---- Secondary actions -------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryAction(
                    label = "Transactions",
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    onClick = onTransactions,
                    modifier = Modifier.weight(1f),
                )
                SecondaryAction(
                    label = "Refunds",
                    icon = Icons.Outlined.AccountBalanceWallet,
                    onClick = onTransactions,
                    modifier = Modifier.weight(1f),
                )
                SecondaryAction(
                    label = "Settlements",
                    icon = Icons.Outlined.ShoppingCart,
                    onClick = onSettlements,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryAction(
                    label = "Receipts",
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    onClick = onTransactions,
                    modifier = Modifier.weight(1f),
                )
                SecondaryAction(
                    label = "Settings",
                    icon = Icons.Outlined.Settings,
                    onClick = onSettings,
                    modifier = Modifier.weight(1f),
                )
                SecondaryAction(
                    label = "Log out",
                    icon = Icons.Outlined.Lock,
                    onClick = onLogout,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---- Recent activity ---------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Recent transactions",
                    style = MaterialTheme.typography.titleMedium,
                )
                androidx.compose.material3.TextButton(onClick = onTransactions) {
                    Text("View all")
                }
            }
            Spacer(Modifier.height(8.dp))

            if (state.loading) {
                Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.recent.isEmpty()) {
                EmptyRecent()
            } else {
                state.recent.forEach { txn -> RecentRow(txn) }
            }

            Spacer(Modifier.height(24.dp))

            // ---- Management --------------------------------------------
            Text("Manage", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryAction(
                    label = "Devices",
                    icon = Icons.Outlined.DeviceHub,
                    onClick = onDevices,
                    modifier = Modifier.weight(1f),
                )
                SecondaryAction(
                    label = "Team",
                    icon = Icons.Outlined.Groups,
                    onClick = onTeam,
                    modifier = Modifier.weight(1f),
                )
                SecondaryAction(
                    label = "Security",
                    icon = Icons.Outlined.Shield,
                    onClick = onSecurity,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DegradedBanner(reason: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun SummaryCard(state: DashboardViewModel.UiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                "Today's sales",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (state.todayTotal == null) {
                Text(
                    text = "No transactions yet",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Payments you take today will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = formatMoney(state.todayTotal!!),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = BrandNavy,
                )
                Text(
                    text = "${state.todayCount} transaction(s) today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatTile(
                    label = "Approved",
                    value = state.approvedCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Declined",
                    value = state.declinedCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Pending",
                    value = state.pendingCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatTile(
                    label = "Refunded",
                    value = state.refundedTotal?.let(::formatMoney) ?: "—",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Open alerts",
                    value = state.openAlertCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Pending payouts",
                    value = state.pendingSettlementCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SecondaryAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyRecent() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "No transactions yet",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap ACCEPT PAYMENT to take your first payment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentRow(txn: Transaction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatMoney(txn.amount),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = formatDateTime(txn.createdAtMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

/** Kept for preview parity with the outline button used elsewhere. */
@Suppress("unused")
@Composable
private fun DashboardOutlineButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(text) }
}
