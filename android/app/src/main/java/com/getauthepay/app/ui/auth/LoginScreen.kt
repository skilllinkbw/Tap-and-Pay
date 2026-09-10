package com.getauthepay.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getauthepay.app.ui.LocalLocator
import com.getauthepay.app.ui.authePayViewModelFactory
import com.getauthepay.app.ui.components.BrandLogo
import com.getauthepay.app.ui.components.SandboxBanner

/**
 * Step 1 of the merchant login flow: identifier entry.
 *
 *   identifier → [server] one-time code → [OtpScreen] → device security check
 *   → dashboard
 *
 * There is deliberately no password field: AuthePay authenticates merchants
 * with a one-time code delivered to their registered phone or email, which
 * removes the entire class of credential-stuffing and password-reuse risk
 * from the client.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit,
    onRegister: () -> Unit,
) {
    val locator = LocalLocator.current
    val vm: LoginViewModel = viewModel(
        factory = authePayViewModelFactory { LoginViewModel(locator.api) },
    )
    val state by vm.state.collectAsState()

    LaunchedEffect(state.otpRequested) {
        if (state.otpRequested) onLoginSuccess(state.identifier.trim())
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
        Spacer(Modifier.height(24.dp))
        SandboxBanner()
        Spacer(Modifier.height(32.dp))

        BrandLogo(showWordmark = true, sizeDp = 72)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Sign in to AuthePay",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Enter the phone number or email registered to your merchant account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.identifier,
            onValueChange = vm::setIdentifier,
            label = { Text("Phone number or email") },
            placeholder = { Text("+267 7X XXX XXX or you@business.co.bw") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { vm.requestOtp() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Merchant phone number or email" },
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = vm::requestOtp,
            enabled = !state.loading &&
                state.cooldownSeconds == 0 &&
                LoginViewModel.looksLikeIdentifier(state.identifier),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                when {
                    state.loading -> "Sending…"
                    state.cooldownSeconds > 0 -> "Send code"
                    else -> "Send one-time code"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (state.errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.infoMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.infoMessage!!,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                "New to AuthePay?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onRegister) {
                Text("Register your business")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "AuthePay is a service of Braincade Holdings Pty Ltd. " +
                "Your session is protected with device-bound encryption.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}
