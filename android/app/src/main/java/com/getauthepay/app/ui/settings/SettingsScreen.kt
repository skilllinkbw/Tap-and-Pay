package com.getauthepay.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getauthepay.app.core.currency.CurrencyCatalog
import com.getauthepay.app.ui.components.ScreenScaffold
import com.getauthepay.app.ui.components.SectionHeader
import com.getauthepay.app.ui.theme.DangerRed

/**
 * Merchant settings.
 *
 * Settlement/bank details are intentionally absent: per directive section 7
 * ("Do not collect unnecessary banking information") those changes are
 * server-side flows with their own verification, not a free-text field in
 * a mobile app.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSecuritySettings: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var biometricRefunds by remember { mutableStateOf(true) }
    var receiptPrompt by remember { mutableStateOf(true) }

    ScreenScaffold(
        title = "Settings",
        subtitle = "Terminal and account preferences",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SectionHeader("Security")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column {
                    SettingsNavRow(
                        icon = Icons.Outlined.Shield,
                        title = "Security settings",
                        subtitle = "Device integrity, biometrics, session",
                        onClick = onSecuritySettings,
                    )
                    HorizontalDivider()
                    SettingsToggleRow(
                        title = "Require biometric for refunds",
                        subtitle = "Ask for fingerprint or face before a refund is submitted",
                        checked = biometricRefunds,
                        onCheckedChange = { biometricRefunds = it },
                    )
                }
            }

            SectionHeader("Payments")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column {
                    SettingsToggleRow(
                        title = "Offer receipt after approval",
                        subtitle = "Show the receipt actions on the approval screen",
                        checked = receiptPrompt,
                        onCheckedChange = { receiptPrompt = it },
                    )
                    HorizontalDivider()
                    SettingsValueRow(
                        title = "Default currency",
                        subtitle = "New payments start in this currency",
                        value = CurrencyCatalog.defaultCurrency,
                    )
                    HorizontalDivider()
                    SettingsValueRow(
                        title = "Supported currencies",
                        subtitle = "Configured on this terminal",
                        value = CurrencyCatalog.allSupported().size.toString(),
                    )
                }
            }

            SectionHeader("Support")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column {
                    SettingsNavRow(
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        title = "Help",
                        subtitle = "Counter problems and payment errors",
                        onClick = onHelp,
                    )
                    HorizontalDivider()
                    SettingsNavRow(
                        icon = Icons.Outlined.Info,
                        title = "About",
                        subtitle = "Version, environment, compliance status",
                        onClick = onAbout,
                    )
                }
            }

            SectionHeader("Account")
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Outlined.Logout, contentDescription = null, tint = DangerRed)
                Spacer(Modifier.width(8.dp))
                Text("Sign out", color = DangerRed, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "This ends the session on this device and clears the stored " +
                        "session token. You will need to sign in again with a one-time " +
                        "code to accept payments.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) { Text("Sign out", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) { Text("Open") }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    subtitle: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
