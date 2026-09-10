package com.getauthepay.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getauthepay.app.ui.components.ScreenScaffold
import com.getauthepay.app.ui.theme.BrandBlue
import com.getauthepay.app.ui.theme.SuccessGreen

/**
 * Shown after a merchant submits onboarding, and again whenever the
 * account is not yet able to accept live payments.
 *
 * The status wording is deliberately honest: this screen describes what
 * happens next in the review process, it never implies the merchant is
 * approved, and it never displays a fabricated approval date or a
 * certification badge.
 */
@Composable
fun VerificationPendingScreen(onDone: () -> Unit) {
    ScreenScaffold(
        title = "Verification pending",
        subtitle = "We are reviewing your application",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))

            Icon(
                imageVector = Icons.Outlined.HourglassEmpty,
                contentDescription = null,
                tint = BrandBlue,
                modifier = Modifier.size(72.dp),
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Application submitted",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Your merchant details have been submitted for review. " +
                    "AuthePay will verify your business documents before enabling " +
                    "live payment acceptance on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "What happens next",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(14.dp))
                    VerificationStep(
                        number = "1",
                        title = "Document review",
                        body = "We check your registration documents and owner identity.",
                    )
                    VerificationStep(
                        number = "2",
                        title = "Business verification",
                        body = "Your business is verified against the details you supplied.",
                    )
                    VerificationStep(
                        number = "3",
                        title = "Device enrolment",
                        body = "This device is registered and security-checked.",
                    )
                    VerificationStep(
                        number = "4",
                        title = "Activation",
                        body = "You receive confirmation and can accept live payments.",
                        isLast = true,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "In the meantime",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You can explore the dashboard and run simulated payments in " +
                            "sandbox mode. Sandbox payments are clearly labelled and do " +
                            "not move real money.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("CONTINUE TO DASHBOARD", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Check status again later")
            }
        }
    }
}

@Composable
private fun VerificationStep(
    number: String,
    title: String,
    body: String,
    isLast: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isLast) SuccessGreen else BrandBlue,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
