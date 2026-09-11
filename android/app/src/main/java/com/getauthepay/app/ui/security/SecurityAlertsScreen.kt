package com.getauthepay.app.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getauthepay.app.core.models.SecurityAlert
import com.getauthepay.app.data.MerchantRepository
import com.getauthepay.app.ui.components.EmptyState
import com.getauthepay.app.ui.components.ErrorState
import com.getauthepay.app.ui.components.LoadingState
import com.getauthepay.app.ui.components.ScreenScaffold
import com.getauthepay.app.ui.formatDateTime
import com.getauthepay.app.ui.theme.DangerRed
import com.getauthepay.app.ui.theme.SuccessGreen
import com.getauthepay.app.ui.theme.WarningAmber

/**
 * Security / fraud alerts.
 *
 * Alerts are raised by the AuthePay risk service. Each can be viewed,
 * acknowledged and resolved; acknowledgement and resolution are server-side
 * actions so the audit trail cannot be forged on-device.
 */
@Composable
fun SecurityAlertsScreen(onBack: () -> Unit) {
    val repository = com.getauthepay.app.ui.LocalLocator.current.merchantRepository
    var state by remember { mutableStateOf<AlertsState>(AlertsState.Loading) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(reload) {
        state = AlertsState.Loading
        state = runCatching { AlertsState.Loaded(load(repository)) }
            .getOrElse { AlertsState.Error(it.message ?: "Could not load alerts") }
    }

    ScreenScaffold(
        title = "Security alerts",
        subtitle = "Fraud and account-security notifications",
        onBack = onBack,
        actions = {
            IconButton(onClick = { reload++ }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh alerts")
            }
        },
    ) {
        when (val s = state) {
            AlertsState.Loading -> LoadingState(message ="Loading alerts…")
            is AlertsState.Error -> ErrorState(s.message) { reload++ }
            is AlertsState.Loaded -> {
                if (s.alerts.isEmpty()) {
                    EmptyState(
                        message = "No security alerts. " +
                            "We will notify you here if we detect anything unusual.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { Spacer(Modifier.height(8.dp)) }
                        items(s.alerts, key = { it.alertId }) { alert ->
                            AlertCard(
                                alert = alert,
                                onAcknowledge = { resolve ->
                                    scope.launch {
                                        repository.acknowledgeAlert(alert.alertId, resolve)
                                        reload++
                                    }
                                },
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: SecurityAlert,
    onAcknowledge: (resolve: Boolean) -> Unit,
) {
    val tint = when (alert.severity) {
        SecurityAlert.Severity.CRITICAL, SecurityAlert.Severity.HIGH -> DangerRed
        SecurityAlert.Severity.MEDIUM -> WarningAmber
        SecurityAlert.Severity.LOW, SecurityAlert.Severity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (alert.severity == SecurityAlert.Severity.INFO) {
                        Icons.Outlined.CheckCircle
                    } else {
                        Icons.Outlined.WarningAmber
                    },
                    contentDescription = null,
                    tint = tint,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(alert.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${alert.severity.name} • ${alert.category.name.replace('_', ' ')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Surface(
                    color = if (alert.isOpen) DangerRed.copy(alpha = 0.12f)
                    else SuccessGreen.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        if (alert.resolvedAtMs != null) "Resolved"
                        else if (alert.acknowledgedAtMs != null) "Acknowledged"
                        else "Open",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (alert.isOpen) DangerRed else SuccessGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(alert.message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Raised ${formatDateTime(alert.raisedAtMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (alert.isOpen) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onAcknowledge(false) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Acknowledge") }
                    OutlinedButton(
                        onClick = { onAcknowledge(true) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Mark resolved") }
                }
            }
        }
    }
}

private sealed interface AlertsState {
    data object Loading : AlertsState
    data class Loaded(val alerts: List<SecurityAlert>) : AlertsState
    data class Error(val message: String) : AlertsState
}

private suspend fun load(repository: MerchantRepository): List<SecurityAlert> =
    repository.refresh().alerts.sortedByDescending { it.raisedAtMs }
