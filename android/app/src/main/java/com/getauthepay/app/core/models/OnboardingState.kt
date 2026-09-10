package com.getauthepay.app.core.models

/**
 * Multi-step merchant onboarding wizard.
 *
 *   BUSINESS_INFO  → OWNER_INFO  → DOCUMENTS  → BANK_INFO  → CONSENT  → REVIEW  → DONE
 *
 * Each step has its own screen and persists input locally (encrypted) until
 * the final submission. Server-side review then drives MerchantStatus.
 */
enum class OnboardingStep {
    BUSINESS_INFO,
    OWNER_INFO,
    DOCUMENTS,
    BANK_INFO,
    CONSENT,
    REVIEW,
    DONE;

    fun next(): OnboardingStep? {
        val values = entries
        val idx = values.indexOf(this)
        return if (idx + 1 < values.size) values[idx + 1] else null
    }

    fun previous(): OnboardingStep? {
        val values = entries
        val idx = values.indexOf(this)
        return if (idx > 0) values[idx - 1] else null
    }
}

data class OnboardingDraft(
    val businessName: String? = null,
    val tradingName: String? = null,
    val businessType: BusinessType? = null,
    val country: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val ownerName: String? = null,
    val ownerIdNumber: String? = null,
    val documents: List<OnboardingDocument> = emptyList(),
    val settlementBankCode: String? = null,
    val settlementAccountNumber: String? = null,
    val acceptedTerms: Boolean = false,
    val acceptedAtMs: Long? = null,
    val currentStep: OnboardingStep = OnboardingStep.BUSINESS_INFO,
) {
    fun canAdvance(): Boolean = when (currentStep) {
        OnboardingStep.BUSINESS_INFO -> !businessName.isNullOrBlank() &&
            businessType != null && !country.isNullOrBlank() &&
            !contactEmail.isNullOrBlank() && !contactPhone.isNullOrBlank()
        OnboardingStep.OWNER_INFO -> !ownerName.isNullOrBlank() && !ownerIdNumber.isNullOrBlank()
        OnboardingStep.DOCUMENTS -> documents.isNotEmpty()
        OnboardingStep.BANK_INFO -> !settlementBankCode.isNullOrBlank() &&
            !settlementAccountNumber.isNullOrBlank()
        OnboardingStep.CONSENT -> acceptedTerms && acceptedAtMs != null
        OnboardingStep.REVIEW -> true
        OnboardingStep.DONE -> false
    }
}

data class OnboardingDocument(
    val documentId: String,
    val type: DocumentType,
    val fileUri: String,
    val capturedAtMs: Long,
)

enum class DocumentType {
    CERTIFICATE_OF_INCORPORATION,
    TRADING_LICENSE,
    OWNER_ID,
    OWNER_SELFIE,
    BANK_CONFIRMATION,
    OTHER;

    fun label(): String = when (this) {
        CERTIFICATE_OF_INCORPORATION -> "Certificate of Incorporation"
        TRADING_LICENSE -> "Trading Licence"
        OWNER_ID -> "Owner ID Document"
        OWNER_SELFIE -> "Owner Selfie"
        BANK_CONFIRMATION -> "Bank Confirmation Letter"
        OTHER -> "Other Document"
    }
}