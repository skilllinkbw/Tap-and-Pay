package com.getauthepay.app.ui.help

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getauthepay.app.ui.components.ScreenScaffold
import com.getauthepay.app.ui.components.SectionHeader

/**
 * Merchant self-service help.
 *
 * The guidance here is written to match how the app actually behaves —
 * in particular the payment-failure entries describe the real error codes
 * emitted by [com.getauthepay.app.core.PaymentAcceptanceEngine] rather
 * than invented messages.
 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    ScreenScaffold(
        title = "Help",
        subtitle = "Answers for common counter problems",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SectionHeader("Accepting a payment")
            HelpCard(
                question = "The card is not being read",
                answer = "Check that NFC is switched on in Android Settings. Hold the " +
                    "card flat against the back of the phone, near the top, and keep " +
                    "it there until the screen changes. Some phone cases and metal " +
                    "surfaces interfere with the NFC antenna.",
            )
            Spacer(Modifier.height(10.dp))
            HelpCard(
                question = "Can I take a payment with no internet?",
                answer = "No. This app does not approve payments offline. If there is " +
                    "no connection the attempt ends as 'Payment unavailable' and no " +
                    "money is taken. Wait for a connection and try again.",
            )
            Spacer(Modifier.height(10.dp))
            HelpCard(
                question = "The customer tapped twice",
                answer = "Each payment attempt carries an idempotency key, so a " +
                    "repeated request returns the original result instead of creating " +
                    "a second charge. Always check the transaction list if you are " +
                    "unsure whether a payment went through.",
            )

            SectionHeader("Declines and errors")
            HelpCard(
                question = "Payment declined",
                answer = "The issuer refused the transaction. Ask the customer to use " +
                    "another card or payment method. No money was taken.",
            )
            Spacer(Modifier.height(10.dp))
            HelpCard(
                question = "Payment timed out",
                answer = "The card was removed too early, or the authorisation took " +
                    "too long. This is retryable, but first confirm with the customer " +
                    "that their account was not debited.",
            )
            Spacer(Modifier.height(10.dp))
            HelpCard(
                question = "Blocked by the risk engine",
                answer = "The transaction was stopped by fraud-risk rules before it " +
                    "reached the issuer. Contact AuthePay support with the " +
                    "correlation ID shown on the result screen.",
            )

            SectionHeader("Security")
            HelpCard(
                question = "Why does the app ask for my fingerprint?",
                answer = "Refunds, security settings and opening payment acceptance " +
                    "are sensitive actions. Biometrics confirm that the person at the " +
                    "counter is an authorised member of your team.",
            )
            Spacer(Modifier.height(10.dp))
            HelpCard(
                question = "Does this app store card numbers?",
                answer = "No. The app never receives a full card number, PIN or CVV. " +
                    "Receipts show only a masked card reference such as " +
                    "412345******1234.",
            )

            SectionHeader("Lost or stolen device")
            HelpCard(
                question = "A device is missing",
                answer = "Sign in from another enrolled device or contact support and " +
                    "revoke the missing device immediately. Revocation takes effect " +
                    "server-side, so the device can no longer accept payments.",
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HelpCard(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = question,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
