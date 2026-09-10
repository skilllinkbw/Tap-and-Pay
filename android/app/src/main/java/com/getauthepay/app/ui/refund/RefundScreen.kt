package com.getauthepay.app.ui.refund

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getauthepay.app.core.models.RefundStatus
import com.getauthepay.app.security.BiometricGate
import com.getauthepay.app.ui.LocalLocator
import com.getauthepay.app.ui.authePayViewModelFactory
import com.getauthepay.app.ui.components.DetailRow
import com.getauthepay.app.ui.components.EmptyState
import com.getauthepay.app.ui.components.LoadingState
import com.getauthepay.app.ui.components.ScreenScaffold
import com.getauthepay.app.ui.components.SectionHeader
import com.getauthepay.app.ui.formatMoney
import com.getauthepay.app.ui.theme.DangerRed
import com.getauthepay.app.ui.theme.SuccessGreen
import com.getauthepay.app.ui.theme.WarningAmber
import kotlinx.coroutines.launch

/**
 * Refund a previously approved payment.
 *
 * Gate order: role permission → amount/reason entry → **biometric prompt** →
 * server submission → confirmed result. No step is skipped, and the refund is
 * only reported as done once the server says so.
 */
@Composable
fun RefundScreen(
    transactionId: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val locator = LocalLocator.current
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    val vm: RefundViewModel = viewModel(
        factory = authePayViewModelFactory {
            RefundViewModel(
                transactionId,
                locator.merchantRepository,
                locator.sessionManager,
                locator.api,
            )
        },
    )
    val state by vm.state.collectAsState()

    ScreenScaffold(
        title = "Refund",
        subtitle = transactionId,
        onBack = onBack,
    ) {
        when {
            state.loading -> LoadingState("Loading transaction…")

            state.notFound -> EmptyState("We could not find that transaction.")

            state.permissionDenied -> EmptyState(
                message = "You do not have permission to issue refunds. " +
                    "Ask an owner or administrator to perform this refund.",
                icon = Icons.Filled.Lock,
            )

            state.errorMessage != null && state.stage == RefundViewModel.Stage.Form ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = state.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Back")
                    }
                }

            state.stage == RefundViewModel.Stage.Result ->
                RefundResult(state = state, onDone = onDone, onBack = onBack)

            else -> RefundForm(
                state = state,
                vm = vm,
                submitting = state.stage == RefundViewModel.Stage.Submitting,
                onSubmit = {
                    if (activity == null) {
                        Toast.makeText(
                            context,
                            "Biometric confirmation is unavailable on this device.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@RefundForm
                    }
                    if (vm.validate() == null) return@RefundForm
                    scope.launch {
                        val capability = BiometricGate.capability(context)
                        if (capability != BiometricGate.Capability.AVAILABLE) {
                            Toast.makeText(
                                context,
                                "Biometric confirmation is not set up on this device. " +
                                    "Refunds require it.",
                                Toast.LENGTH_LONG,
                            ).show()
                            return@launch
                        }
                        val ok = BiometricGate.prompt(
                            activity = activity,
                            title = "Confirm refund",
                            subtitle = transactionId,
                            description = "Verify your identity to refund this payment.",
                        )
                        if (ok) vm.submit()
                        else Toast.makeText(context, "Refund cancelled.", Toast.LENGTH_SHORT).show()
                    }
                },
                onCancel = onBack,
            )
        }
    }
}

@Composable
private fun RefundForm(
    state: RefundViewModel.UiState,
    vm: RefundViewModel,
    submitting: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DetailRow("Original amount", state.originalAmount?.let(::formatMoney) ?: "—")
                HorizontalDivider()
                DetailRow("Already refunded", state.alreadyRefunded?.let(::formatMoney) ?: "—")
                HorizontalDivider()
                DetailRow("Available to refund", state.remaining?.let(::formatMoney) ?: "—",
                    emphasise = true)
            }
        }

        SectionHeader("Refund amount")
        OutlinedTextField(
            value = state.amountInput,
            onValueChange = vm::setAmount,
            label = { Text("Amount") },
            singleLine = true,
            isError = state.amountError != null,
            supportingText = state.amountError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader("Reason")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                RefundViewModel.REASONS.forEach { (code, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        RadioButton(
                            selected = state.reasonCode == code,
                            onClick = { vm.setReason(code) },
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.reasonNote,
            onValueChange = vm::setNote,
            label = { Text("Note (optional)") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(
                    "You will be asked to confirm with your fingerprint or face " +
                        "before this refund is submitted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        if (submitting) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("CONFIRM REFUND", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Cancel") }
        }
    }
}

@Composable
private fun RefundResult(
    state: RefundViewModel.UiState,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val (icon, tint, headline, body) = when (state.resultStatus) {
        RefundStatus.REFUNDED -> Quad(
            Icons.Filled.CheckCircle, SuccessGreen, "REFUND COMPLETE",
            "The refund has been processed in full.",
        )
        RefundStatus.PARTIAL_REFUND -> Quad(
            Icons.Filled.CheckCircle, SuccessGreen, "REFUND COMPLETE",
            "The partial refund has been processed.",
        )
        RefundStatus.REFUND_PENDING -> Quad(
            Icons.Filled.CheckCircle, WarningAmber, "REFUND PENDING",
            "The refund has been submitted and is awaiting confirmation.",
        )
        RefundStatus.REFUND_FAILED -> Quad(
            Icons.Filled.ErrorOutline, DangerRed, "REFUND FAILED",
            state.errorMessage ?: "The refund could not be processed.",
        )
        null -> Quad(
            Icons.Filled.ErrorOutline, DangerRed, "REFUND FAILED",
            state.errorMessage ?: "The refund could not be processed.",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.height(80.dp).fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text(
            headline,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = tint,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        state.refundId?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                "Refund $it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("DONE", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
