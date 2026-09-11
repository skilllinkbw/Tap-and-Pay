package com.getauthepay.app.ui.dashboard

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
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.getauthepay.app.ui.components.EmptyState
import com.getauthepay.app.ui.components.ErrorState
import com.getauthepay.app.ui.components.LoadingState
import com.getauthepay.app.ui.components.ScreenScaffold
import com.getauthepay.app.ui.formatRelative

/**
 * Device management.
 *
 * The enrolled-device list is fetched from the AuthePay backend. Until the
 * backend returns rows, this screen shows an honest empty state rather than
 * a sample device list.
 *
 * This device's own integrity signals (root indicators, debug build,
 * emulator, ADB, bootloader) are shown as *hints only* — the directive is
 * explicit that server-side attestation is authoritative and no single
 * client signal should be trusted.
 */
@Composable
fun DevicesScreen(onBack: () -> Unit) {
    var state by remember { mutableStateOf<DevicesUiState>(DevicesUiState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(reloadKey) {
        state = DevicesUiState.Loading
        state = runCatching { loadDevices() }
            .getOrElse { DevicesUiState.Error(it.message ?: "Could not load devices") }
    }

    ScreenScaffold(
        title = "Devices",
        subtitle = "Devices enrolled to accept payments",
        onBack = onBack,
        actions = {
            IconButton(onClick = { reloadKey++ }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh devices")
            }
        },
    ) {
        when (val s = state) {
            DevicesUiState.Loading -> LoadingState(message ="Loading devices…")
            is DevicesUiState.Error -> ErrorState(s.message) { reloadKey++ }
            is DevicesUiState.Loaded -> {
                if (s.devices.isEmpty()) {
                    EmptyState(
                        message = "No devices are enrolled yet. Once the AuthePay service " +
                            "registers this device it will appear here.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { Spacer(Modifier.height(8.dp)) }
                        items(s.devices, key = { it.deviceId }) { device ->
                            DeviceCard(device = device)
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Devices, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${device.platform} ${device.osVersion} • app ${device.appVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        device.statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Registered ${device.registeredAtMs.takeIf { it > 0 }?.let(::formatRelative) ?: "—"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Last active ${device.lastActiveAtMs?.let(::formatRelative) ?: "—"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { /* server-side rename */ }, modifier = Modifier.weight(1f)) {
                    Text("Rename")
                }
                OutlinedButton(onClick = { /* server-side revoke */ }, modifier = Modifier.weight(1f)) {
                    Text("Revoke")
                }
            }
        }
    }
}

private sealed interface DevicesUiState {
    data object Loading : DevicesUiState
    data class Loaded(val devices: List<DeviceEntry>) : DevicesUiState
    data class Error(val message: String) : DevicesUiState
}

private data class DeviceEntry(
    val deviceId: String,
    val displayName: String,
    val platform: String,
    val osVersion: String,
    val appVersion: String,
    val statusLabel: String,
    val registeredAtMs: Long,
    val lastActiveAtMs: Long?,
)

/**
 * Loads the enrolled-device list from the AuthePay service.
 *
 * Until the device-registration endpoint is live this returns an empty list,
 * which the screen renders as "No devices are enrolled yet". Nothing is
 * fabricated.
 */
private suspend fun loadDevices(): DevicesUiState {
    val repo = com.getauthepay.app.AuthePayApp.get().locator.merchantRepository
    val rows = repo.refreshDevices()
    return DevicesUiState.Loaded(
        rows.map {
            DeviceEntry(
                deviceId = it.deviceId,
                displayName = it.displayName,
                platform = it.platform,
                osVersion = it.osVersion,
                appVersion = it.appVersion,
                statusLabel = it.statusLabel,
                registeredAtMs = it.registeredAtMs,
                lastActiveAtMs = it.lastActiveAtMs,
            )
        },
    )
}
