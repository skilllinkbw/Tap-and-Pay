package com.getauthepay.app.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getauthepay.app.core.models.BusinessType
import com.getauthepay.app.core.models.DocumentType
import com.getauthepay.app.core.models.OnboardingDocument
import com.getauthepay.app.core.models.OnboardingDraft
import com.getauthepay.app.core.models.OnboardingStep
import com.getauthepay.app.ui.LocalLocator
import com.getauthepay.app.ui.components.ScreenScaffold
import com.getauthepay.app.ui.components.SectionHeader
import com.getauthepay.app.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * Merchant onboarding wizard.
 *
 * Seven steps, matching directive section 7:
 *
 *   1 Business information → 2 Owner / authorised representative
 *   → 3 Business verification (documents) → 4 Settlement information
 *   → 5 Terms and consent → 6 Review → 7 Submitted
 *
 * Data handling notes:
 *  - The draft is held in memory for the wizard and submitted to the
 *    AuthePay backend over TLS on the final step.
 *  - Settlement bank details are collected strictly for payout purposes and
 *    are not retained on the device after submission.
 *  - The app never asks for a card number, CVV or PIN here.
 *
 * Submission is server-authoritative: this screen cannot mark a merchant
 * ACTIVE by itself. [VerificationPendingScreen] explains what happens next.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val locator = LocalLocator.current
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf(OnboardingDraft()) }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) draft = addDocument(draft, uri)
    }

    val step = draft.currentStep
    val stepCount = OnboardingStep.entries.size
    val stepIndex = OnboardingStep.entries.indexOf(step)

    ScreenScaffold(
        title = stepTitle(step),
        subtitle = "Step ${stepIndex + 1} of $stepCount",
        onBack = {
            val previous = step.previous()
            if (previous != null) draft = draft.copy(currentStep = previous)
            else onBack()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            LinearProgressIndicator(
                progress = { (stepIndex + 1f) / stepCount.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )

            Spacer(Modifier.height(16.dp))

            when (step) {
                OnboardingStep.BUSINESS_INFO -> BusinessInfoStep(draft) { draft = it }
                OnboardingStep.OWNER_INFO -> OwnerInfoStep(draft) { draft = it }
                OnboardingStep.DOCUMENTS -> DocumentsStep(
                    draft = draft,
                    onChanged = { draft = it },
                    onPickDocument = { documentPicker.launch("*/*") },
                )

                OnboardingStep.BANK_INFO -> BankInfoStep(draft) { draft = it }
                OnboardingStep.CONSENT -> ConsentStep(draft) { draft = it }
                OnboardingStep.REVIEW -> ReviewStep(draft)
                OnboardingStep.DONE -> Unit
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            if (submitting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val next = step.next()
                if (next != null) {
                    Button(
                        onClick = {
                            errorMessage = null
                            if (next == OnboardingStep.DONE) {
                                scope.launch {
                                    submitting = true
                                    val ok = runCatching {
                                        locator.api.submitOnboarding(draft.toJson())
                                    }.getOrDefault(false)
                                    submitting = false
                                    if (ok) {
                                        draft = draft.copy(currentStep = OnboardingStep.DONE)
                                        onComplete()
                                    } else {
                                        errorMessage =
                                            "We could not submit your application. " +
                                                "Check your connection and try again."
                                    }
                                }
                            } else {
                                draft = draft.copy(currentStep = next)
                            }
                        },
                        enabled = draft.canAdvance(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text(
                            if (next == OnboardingStep.DONE) "SUBMIT APPLICATION" else "CONTINUE",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (!draft.canAdvance()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Complete the required fields to continue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Steps
// ---------------------------------------------------------------------------

@Composable
private fun BusinessInfoStep(
    draft: OnboardingDraft,
    onChanged: (OnboardingDraft) -> Unit,
) {
    SectionHeader("Business information")
    LabelledField(
        label = "Registered business name",
        value = draft.businessName.orEmpty(),
        onValueChange = { onChanged(draft.copy(businessName = it)) },
    )
    LabelledField(
        label = "Trading name (optional)",
        value = draft.tradingName.orEmpty(),
        onValueChange = { onChanged(draft.copy(tradingName = it)) },
    )

    SectionHeader("Business type")
    BusinessType.entries.forEach { type ->
        SelectableRow(
            label = type.name.replace('_', ' ').lowercase()
                .replaceFirstChar { it.uppercase() },
            selected = draft.businessType == type,
            onClick = { onChanged(draft.copy(businessType = type)) },
        )
    }

    SectionHeader("Contact information")
    LabelledField(
        label = "Business email",
        value = draft.contactEmail.orEmpty(),
        onValueChange = { onChanged(draft.copy(contactEmail = it)) },
        keyboardType = KeyboardType.Email,
    )
    LabelledField(
        label = "Business phone",
        value = draft.contactPhone.orEmpty(),
        onValueChange = { onChanged(draft.copy(contactPhone = it)) },
        keyboardType = KeyboardType.Phone,
    )

    SectionHeader("Country")
    CountryPicker(
        selected = draft.country,
        onSelected = { onChanged(draft.copy(country = it)) },
    )
}

@Composable
private fun OwnerInfoStep(
    draft: OnboardingDraft,
    onChanged: (OnboardingDraft) -> Unit,
) {
    SectionHeader("Owner / authorised representative")
    LabelledField(
        label = "Full name",
        value = draft.ownerName.orEmpty(),
        onValueChange = { onChanged(draft.copy(ownerName = it)) },
    )
    LabelledField(
        label = "Identity / national ID number",
        value = draft.ownerIdNumber.orEmpty(),
        onValueChange = { onChanged(draft.copy(ownerIdNumber = it)) },
    )
    Spacer(Modifier.height(12.dp))
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Identity details are transmitted to the AuthePay verification " +
                "service over an encrypted connection and are used only for " +
                "know-your-business checks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun DocumentsStep(
    draft: OnboardingDraft,
    onChanged: (OnboardingDraft) -> Unit,
    onPickDocument: () -> Unit,
) {
    SectionHeader("Business verification documents")
    Text(
        "Attach the documents we need to verify your business. Files are " +
            "uploaded securely to AuthePay and are used only for verification.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(12.dp))

    if (draft.documents.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No documents attached yet",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "At least one document is required — typically a certificate " +
                        "of incorporation or a trading licence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        draft.documents.forEach { doc ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(doc.type.label(), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Attached",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen,
                        )
                    }
                    IconButton(onClick = {
                        onChanged(draft.copy(documents = draft.documents - doc))
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove document")
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onPickDocument, modifier = Modifier.fillMaxWidth()) {
        Text("Attach a document")
    }
}

@Composable
private fun BankInfoStep(
    draft: OnboardingDraft,
    onChanged: (OnboardingDraft) -> Unit,
) {
    SectionHeader("Settlement information")
    Text(
        "Where should AuthePay send the money from your card sales? Only " +
            "settlement details are needed — never a card number.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    LabelledField(
        label = "Bank / branch code",
        value = draft.settlementBankCode.orEmpty(),
        onValueChange = { onChanged(draft.copy(settlementBankCode = it)) },
    )
    LabelledField(
        label = "Settlement account number",
        value = draft.settlementAccountNumber.orEmpty(),
        onValueChange = { onChanged(draft.copy(settlementAccountNumber = it)) },
        keyboardType = KeyboardType.Number,
    )
    Spacer(Modifier.height(12.dp))
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Settlement details are stored by the AuthePay service, not on this " +
                "device. Changing a settlement account requires re-verification.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun ConsentStep(
    draft: OnboardingDraft,
    onChanged: (OnboardingDraft) -> Unit,
) {
    SectionHeader("Terms and consent")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = draft.acceptedTerms,
            onCheckedChange = { accepted ->
                onChanged(
                    draft.copy(
                        acceptedTerms = accepted,
                        acceptedAtMs = if (accepted) System.currentTimeMillis() else null,
                    ),
                )
            },
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "I confirm that the information provided is accurate, and I accept " +
                "the AuthePay merchant terms and the privacy notice. I consent to " +
                "AuthePay verifying my business and identity details.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewStep(draft: OnboardingDraft) {
    SectionHeader("Review your application")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            ReviewRow("Business name", draft.businessName ?: "—")
            ReviewRow("Trading name", draft.tradingName ?: "—")
            ReviewRow("Business type", draft.businessType?.name ?: "—")
            ReviewRow("Country", draft.country ?: "—")
            ReviewRow("Email", draft.contactEmail ?: "—")
            ReviewRow("Phone", draft.contactPhone ?: "—")
            ReviewRow("Owner", draft.ownerName ?: "—")
            ReviewRow("Owner ID", maskId(draft.ownerIdNumber))
            ReviewRow("Documents", draft.documents.size.toString() + " attached")
            ReviewRow("Settlement account", maskAccount(draft.settlementAccountNumber))
            ReviewRow("Terms accepted", if (draft.acceptedTerms) "Yes" else "No")
        }
    }
}

// ---------------------------------------------------------------------------
// Shared controls
// ---------------------------------------------------------------------------

@Composable
private fun LabelledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun SelectableRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CountryPicker(
    selected: String?,
    onSelected: (String) -> Unit,
) {
    val countries = remember { availableCountries() }
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selected?.let { code ->
        countries.firstOrNull { it.first == code }?.second ?: code
    }

    OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(selectedLabel ?: "Select country")
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        countries.forEach { (code, name) ->
            DropdownMenuItem(
                text = { Text(name) },
                onClick = {
                    onSelected(code)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.55f),
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun stepTitle(step: OnboardingStep): String = when (step) {
    OnboardingStep.BUSINESS_INFO -> "Business information"
    OnboardingStep.OWNER_INFO -> "Owner details"
    OnboardingStep.DOCUMENTS -> "Verification documents"
    OnboardingStep.BANK_INFO -> "Settlement information"
    OnboardingStep.CONSENT -> "Terms and consent"
    OnboardingStep.REVIEW -> "Review application"
    OnboardingStep.DONE -> "Application submitted"
}

/** Attaches a picked file, cycling through the document types the wizard supports. */
internal fun addDocument(draft: OnboardingDraft, uri: Uri): OnboardingDraft {
    val usedTypes = draft.documents.map { it.type }.toSet()
    val type = DocumentType.entries.firstOrNull { it !in usedTypes } ?: DocumentType.OTHER
    val doc = OnboardingDocument(
        documentId = UUID.randomUUID().toString(),
        type = type,
        fileUri = uri.toString(),
        capturedAtMs = System.currentTimeMillis(),
    )
    return draft.copy(documents = draft.documents + doc)
}

/** SADC / African markets first, then the rest of the world. */
internal fun availableCountries(): List<Pair<String, String>> {
    val priority = listOf(
        "BW", "ZA", "ZM", "KE", "NG", "GH", "TZ", "UG",
        "MU", "ZW", "NA", "LS", "SZ", "MW",
    )
    val all = Locale.getISOCountries().map { code ->
        code to Locale("", code).displayCountry
    }.sortedBy { it.second }
    val priorityList = priority.mapNotNull { code -> all.firstOrNull { it.first == code } }
    return priorityList + all.filter { it.first !in priority }
}

/** Shows only the last four characters of an identity number. */
internal fun maskId(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    return "*".repeat((raw.length - 4).coerceAtLeast(0)) + raw.takeLast(4)
}

/** Shows only the last four digits of a settlement account number. */
internal fun maskAccount(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    val digits = raw.filter { it.isDigit() }
    if (digits.length <= 4) return "****"
    return "****" + digits.takeLast(4)
}

/**
 * Serialises the draft for submission.
 *
 * Document file references are sent as opaque URIs; the binary upload is
 * performed by the AuthePay service against those references. No card data
 * is ever part of this payload.
 */
internal fun OnboardingDraft.toJson(): JSONObject = JSONObject()
    .put("businessName", businessName)
    .put("tradingName", tradingName ?: JSONObject.NULL)
    .put("businessType", businessType?.name ?: JSONObject.NULL)
    .put("country", country ?: JSONObject.NULL)
    .put("contactEmail", contactEmail)
    .put("contactPhone", contactPhone)
    .put("ownerName", ownerName ?: JSONObject.NULL)
    .put("ownerIdNumber", ownerIdNumber ?: JSONObject.NULL)
    .put("settlementBankCode", settlementBankCode ?: JSONObject.NULL)
    .put("settlementAccountNumber", settlementAccountNumber ?: JSONObject.NULL)
    .put("acceptedTerms", acceptedTerms)
    .put("acceptedAtMs", acceptedAtMs ?: JSONObject.NULL)
    .put(
        "documents",
        JSONArray().apply {
            documents.forEach { d ->
                put(
                    JSONObject()
                        .put("documentId", d.documentId)
                        .put("type", d.type.name)
                        .put("fileUri", d.fileUri),
                )
            }
        },
    )
