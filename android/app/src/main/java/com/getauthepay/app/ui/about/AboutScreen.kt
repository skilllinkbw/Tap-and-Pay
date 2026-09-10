package com.getauthepay.app.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getauthepay.app.BuildConfig
import com.getauthepay.app.ui.components.BrandLogo
import com.getauthepay.app.ui.components.DetailRow
import com.getauthepay.app.ui.components.ScreenScaffold
import com.getauthepay.app.ui.components.SectionHeader
import com.getauthepay.app.ui.theme.DangerRed

/**
 * Company, version and compliance-status information.
 *
 * COMPLIANCE RULE (directive section 2): this screen must not claim any
 * certification that has not actually been obtained. It states plainly
 * which standards the application is *architected toward* and that the
 * external certification processes remain outstanding. No PCI, EMV, Visa
 * or Mastercard badge is rendered anywhere in the app.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    ScreenScaffold(
        title = "About",
        subtitle = "AuthePay Tap & Pay",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            BrandLogo(sizeDp = 80)
            Spacer(Modifier.height(12.dp))
            Text(
                "Secure payments. Built for merchants.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            SectionHeader("Application")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    DetailRow("Version", BuildConfig.VERSION_NAME)
                    HorizontalDivider()
                    DetailRow("Build type", BuildConfig.BUILD_TYPE)
                    HorizontalDivider()
                    DetailRow("Environment", BuildConfig.ENVIRONMENT_NAME)
                    HorizontalDivider()
                    DetailRow("Sandbox payments", if (BuildConfig.SANDBOX_PAYMENTS) "Enabled" else "Disabled")
                    HorizontalDivider()
                    DetailRow("Test OTP", if (BuildConfig.TEST_OTP_ENABLED) "Enabled" else "Disabled")
                }
            }

            SectionHeader("Company")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    DetailRow("Legal entity", "Braincade Holdings Pty Ltd")
                    HorizontalDivider()
                    DetailRow("Product", "AuthePay Tap & Pay")
                    HorizontalDivider()
                    DetailRow("Launch market", "Botswana")
                }
            }

            SectionHeader("Compliance status")
            ComplianceCard()

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ComplianceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Not yet certified",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = DangerRed,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This application is architected toward PCI DSS v4.0.1, PCI MPoC, " +
                    "EMV contactless requirements and acquiring-bank requirements. " +
                    "Those certifications are external processes conducted by " +
                    "accredited laboratories and payment networks. They have NOT " +
                    "been completed for this build, and no certification is claimed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Outstanding external items",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            listOf(
                "PCI MPoC solution evaluation by an accredited laboratory",
                "EMV contactless Level 1 / Level 2 (or MPoC equivalent) approval",
                "Acquirer / processor onboarding and certification",
                "Payment-network registration where applicable",
            ).forEach { item ->
                Text(
                    "• $item",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
