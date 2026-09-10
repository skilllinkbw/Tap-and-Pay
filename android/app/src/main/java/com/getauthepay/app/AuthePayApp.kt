package com.getauthepay.app

import android.app.Application
import android.os.StrictMode
import com.getauthepay.app.core.log.SecureLogger
import com.getauthepay.app.di.ServiceLocator

class AuthePayApp : Application() {

    lateinit var locator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        locator = ServiceLocator(this)

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build(),
            )
        }
        SecureLogger.event(
            event = "app.start",
            status = BuildConfig.ENVIRONMENT_NAME,
            extra = mapOf(
                "sandbox" to BuildConfig.SANDBOX_PAYMENTS.toString(),
                "testOtp" to BuildConfig.TEST_OTP_ENABLED.toString(),
                "production" to BuildConfig.PRODUCTION_BUILD.toString(),
            ),
        )
    }

    companion object {
        @Volatile private var instance: AuthePayApp? = null
        fun get(): AuthePayApp = instance ?: error("AuthePayApp not initialised yet")
    }
}