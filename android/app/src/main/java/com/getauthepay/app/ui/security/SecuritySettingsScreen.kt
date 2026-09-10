package com.getauthepay.app.ui.security

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getauthepay.app.BuildConfig
import com.getauthepay.app.core.nfc.NfcAvailability
import com.getauthepay.app.security.BiometricGate
import com.getauthepay.app.security.DeviceSecurityChecker
import com.getauthepay.app.ui.LocalLocator
import com.getauthepay.app.ui.components.ScreenScaffold
import com.getauthepay.app.ui.components.SectionHeader
import com.getauthepay.app.ui.theme.DangerRed
import com.getauthepay.app.ui.theme.SuccessGreen
import com.getauthepay.app.ui.theme.WarningAmber

/**
 * Device security posture.
 *
 * Important honesty note surfaced in the UI: every signal on this page is a
 * **client-side hint**. Root detection, emulator detection and debug flags
 * can all be spoofed, so none of them is used as the basis for an
 * authorisation decision. The authoritative check is server-side device
 * attestation performed by the AuthePay service.
 */
@Composable
fun SecuritySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val locator = LocalLocator.current

    val trustScore = remember { DeviceSecurityChecker.trustScore(context) }
    val biometric = remember { BiometricGate.capability(context) }
    val nfc = remember { locator.nfcAvailability() }

    var requireBiometricForRefunds by remember {
        mutableStateOf(locator.secureStorage.getBoolean(
            com.getauthepay.app.data.SecureStorage.KEY_BIOMETRIC_PROTECT_REFUNDS, true,
        ))
    }

    ScreenScaffold(title = "Security", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "These are device-side signals only. Authoritative device " +
                            "attestation is performed by the AuthePay service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            SectionHeader("Device posture")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SignalRow("Device trust score", "$trustScore / 100", scoreTone(trustScore))
                    HorizontalDivider()
                    SignalRow("Android version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", null)
                    HorizontalDivider()
                    SignalRow("Secure lock screen",
                        if (locator.securityManager.isDeviceSecure()) "Enabled" else "Not set",
                        if (locator.securityManager.isDeviceSecure()) SuccessGreen else DangerRed)
                    HorizontalDivider()
                    SignalRow("Biometrics", biometricLabel(biometric),
                        if (biometric == BiometricGate.Capability.AVAILABLE) SuccessGreen else WarningAmber)
                    HorizontalDivider()
                    SignalRow("NFC reader", nfcLabel(nfc),
                        when (nfc) {
                            NfcAvailability.AvailableEnabled -> SuccessGreen
                            NfcAvailability.AvailableDisabled -> WarningAmber
                            NfcAvailability.NotAvailable -> DangerRed
                        })
                    HorizontalDivider()
                    SignalRow("Debug build",
                        if (DeviceSecurityChecker.isDebugBuild(context)) "Yes" else "No",
                        if (DeviceSecurityChecker.isDebugBuild(context)) WarningAmber else SuccessGreen)
                    HorizontalDivider()
                    SignalRow("Emulator detected",
                        if (DeviceSecurityChecker.isEmulator(context)) "Yes" else "No",
                        if (DeviceSecurityChecker.isEmulator(context)) WarningAmber else SuccessGreen)
                    HorizontalDivider()
                    SignalRow("Root indicators",
                        if (DeviceSecurityChecker.hasObviousRootIndicators(context)) "Found" else "None",
                        if (DeviceSecurityChecker.hasObviousRootIndicators(context)) DangerRed else SuccessGreen)
                    HorizontalDivider()
                    SignalRow("Environment", BuildConfig.ENVIRONMENT_NAME, null)
                }
            }

            SectionHeader("Protections")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ToggleRow(
                        title = "Require biometrics for refunds",
                        subtitle = "Ask for fingerprint or face before a refund is submitted.",
                        checked = requireBiometricForRefunds,
                        onCheckedChange = { value ->
                            requireBiometricForRefunds = value
                            locator.secureStorage.putBoolean(
                                com.getauthepay.app.data.SecureStorage.KEY_BIOMETRIC_PROTECT_REFUNDS,
                                value,
                            )
                        },
                    )
                    HorizontalDivider()
                    SignalRow("Screenshot protection", "Enabled on payment screens", SuccessGreen)
                    HorizontalDivider()
                    SignalRow("Cleartext traffic",
                        if (BuildConfig.ALLOW_CLEARTEXT) "Allowed (non-production)" else "Blocked",
                        if (BuildConfig.ALLOW_CLEARTEXT) WarningAmber else SuccessGreen)
                    HorizontalDivider()
                    SignalRow("Card data storage", "Never stored", SuccessGreen)
                    HorizontalDivider()
                    SignalRow("Sensitive logging", "Redacted", SuccessGreen)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "AuthePay never stores your customer's full card number, CVV or " +
                    "PIN. Card data is handled only by the certified payment " +
                    "kernel used during authorisation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SignalRow(label: String, value: String, tone: androidx.compose.ui.graphics.Color?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tone ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun scoreTone(score: Int): androidx.compose.ui.graphics.Color =
    when {
        score >= 80 -> SuccessGreen
        score >= 50 -> WarningAmber
        else -> DangerRed
    }

private fun biometricLabel(capability: BiometricGate.Capability): String = when (capability) {
    BiometricGate.Capability.AVAILABLE -> "Available"
    BiometricGate.Capability.NO_HARDWARE -> "No hardware"
    BiometricGate.Capability.NONE_ENROLLED -> "None enrolled"
    BiometricGate.Capability.SECURITY_UPDATE_REQUIRED -> "Update required"
    BiometricGate.Capability.UNAVAILABLE -> "Unavailable"
}

private fun nfcLabel(availability: NfcAvailability): String = when (availability) {
    NfcAvailability.AvailableEnabled -> "Available and enabled"
    NfcAvailability.AvailableDisabled -> "Available but turned off"
    NfcAvailability.NotAvailable -> "Not available"
}
