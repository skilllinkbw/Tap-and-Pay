package com.getauthepay.app.di

import android.content.Context
import com.getauthepay.app.core.PaymentAcceptanceEngine
import com.getauthepay.app.core.ledger.TransactionLedger
import com.getauthepay.app.core.nfc.ContactlessPaymentProvider
import com.getauthepay.app.core.nfc.NfcAvailability
import com.getauthepay.app.core.nfc.NfcReaderProvider
import com.getauthepay.app.core.nfc.SandboxContactlessProvider
import com.getauthepay.app.core.payment.PaymentProcessor
import com.getauthepay.app.core.payment.SandboxPaymentProcessor
import com.getauthepay.app.core.risk.RiskService
import com.getauthepay.app.core.risk.SandboxRiskService
import com.getauthepay.app.data.MerchantRepository
import com.getauthepay.app.data.SecureStorage
import com.getauthepay.app.data.SessionManager
import com.getauthepay.app.network.AuthePayApiClient
import com.getauthepay.app.network.HttpClient
import com.getauthepay.app.security.SecurityManager
import com.getauthepay.app.BuildConfig

/**
 * Minimal manual dependency container. The merchant app is small
 * enough that a full DI framework is unnecessary; the locator exposes
 * one of each service so screens can call `locator.api.verifyOtp(...)`
 * without coupling to a framework.
 *
 * The locator chooses implementations based on [BuildConfig.SANDBOX_PAYMENTS]:
 *   - SANDBOX_PAYMENTS = true  → SandboxContactlessProvider +
 *                                 SandboxPaymentProcessor + SandboxRiskService
 *   - SANDBOX_PAYMENTS = false → NfcReaderProvider + a future real processor
 *                                 (the production wiring is documented in
 *                                  docs/DEPLOYMENT_GUIDE.md and must be
 *                                 supplied by the acquirer integration)
 */
class ServiceLocator(private val appContext: Context) {

    val securityManager: SecurityManager by lazy { SecurityManager(appContext) }
    val secureStorage: SecureStorage by lazy { SecureStorage(appContext) }
    val sessionManager: SessionManager by lazy { SessionManager(secureStorage) }
    val transactionLedger: TransactionLedger by lazy { TransactionLedger() }

    private val httpClient: HttpClient by lazy {
        HttpClient(
            baseUrl = BuildConfig.API_BASE_URL,
            sessionTokenProvider = { sessionManager.current().token.takeIf { it.isNotBlank() } },
        )
    }
    val api: AuthePayApiClient by lazy { AuthePayApiClient(httpClient) }

    /**
     * Read models for the dashboard / history / settlements screens.
     * Server-authoritative, with an honest local-ledger fallback.
     */
    val merchantRepository: MerchantRepository by lazy {
        MerchantRepository(api, transactionLedger)
    }

    val riskService: RiskService by lazy { SandboxRiskService() }

    private val processor: PaymentProcessor by lazy {
        if (BuildConfig.SANDBOX_PAYMENTS) SandboxPaymentProcessor()
        else error(
            "Production PaymentProcessor is supplied by the acquiring partner " +
                "and is not bundled with this build. Wire the acquirer SDK into " +
                "ServiceLocator.kt following docs/DEPLOYMENT_GUIDE.md.",
        )
    }

    private val contactlessProvider: ContactlessPaymentProvider by lazy {
        if (BuildConfig.SANDBOX_PAYMENTS) SandboxContactlessProvider()
        else NfcReaderProvider(
            activityProvider = { currentActivity },
            contextProvider = { appContext },
        )
    }

    /** Updated by [MainActivity] when the activity becomes available. */
    @Volatile
    var currentActivity: android.app.Activity? = null

    /**
     * Current NFC capability. The UI reads this before showing a
     * "ready to tap" surface so a merchant is never asked to present a card
     * to a device that cannot read one.
     */
    fun nfcAvailability(): NfcAvailability = contactlessProvider.availability

    fun buildPaymentEngine(): PaymentAcceptanceEngine {
        val session = sessionManager.current()
        val sessionForEngine = PaymentAcceptanceEngine.EngineSession(
            merchantId = session.merchant.merchantId.ifBlank { "ANONYMOUS" },
            terminalId = session.terminalId.ifBlank { "ANONYMOUS-T1" },
        )
        return PaymentAcceptanceEngine(
            contactlessProvider = contactlessProvider,
            processor = processor,
            riskService = riskService,
            ledger = transactionLedger,
            session = sessionForEngine,
        )
    }
}