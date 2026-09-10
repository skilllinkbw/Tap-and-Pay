package com.getauthepay.app.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getauthepay.app.ui.LocalLocator
import com.getauthepay.app.ui.authePayViewModelFactory
import com.getauthepay.app.ui.components.OtpInput
import com.getauthepay.app.ui.components.SandboxBanner

/**
 * Step 2 of the login flow: one-time-code verification.
 *
 * Every security property the directive requires is visible in the UI:
 * a countdown (short expiry), an attempt counter (limited attempts), a
 * resend cooldown (rate limiting), and a hard lock-out when attempts are
 * exhausted. The code lives only in transient UI state and is wiped from
 * the field after each failed attempt.
 */
@Composable
fun OtpScreen(
    phone: String,
    onVerified: () -> Unit,
    onBack: () -> Unit,
) {
    val locator = LocalLocator.current
    val vm: OtpViewModel = viewModel(
        factory = authePayViewModelFactory {
            OtpViewModel(locator.api, locator.sessionManager, phone)
        },
    )
    val state by vm.state.collectAsState()

    LaunchedEffect(state.verified) {
        if (state.verified) onVerified()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SandboxBanner()
        Spacer(Modifier.height(8.dp))

        Row {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Enter your one-time code",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "We sent a ${OtpViewModel.OTP_LENGTH}-digit code to $phone",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        OtpInput(
            value = state.code,
            onValueChange = vm::setCode,
            expectedLength = OtpViewModel.OTP_LENGTH,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (state.secondsRemaining > 0) {
                "Code expires in ${state.secondsRemaining}s"
            } else {
                "Code expired"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (state.secondsRemaining > 30) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = vm::verify,
            enabled = !state.loading &&
                !state.lockedOut &&
                !state.verified &&
                state.code.length == OtpViewModel.OTP_LENGTH,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                if (state.loading) "Verifying…" else "Verify code",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(Modifier.height(16.dp))

        TextButton(
            onClick = vm::resend,
            enabled = !state.loading && state.resendCooldownSeconds == 0,
        ) {
            Text(
                if (state.resendCooldownSeconds > 0) {
                    "Resend available in ${state.resendCooldownSeconds}s"
                } else {
                    "Resend code"
                },
            )
        }

        if (state.attemptsUsed > 0 && !state.lockedOut) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${state.attemptsUsed} of ${OtpViewModel.MAX_ATTEMPTS} attempts used",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        if (state.infoMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.infoMessage!!,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Never share this code. AuthePay will not ask you for it by phone, " +
                "SMS, or email.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
