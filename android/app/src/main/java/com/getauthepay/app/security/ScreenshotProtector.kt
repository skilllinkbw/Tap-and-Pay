package com.getauthepay.app.security

import android.app.Activity
import android.view.WindowManager

/**
 * Toggle FLAG_SECURE on sensitive activities (payment acceptance, OTP
 * entry, refund confirmation). Prevents screenshots and excludes the
 * window from the system screen recorder.
 */
object ScreenshotProtector {

    fun protect(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }

    fun unprotect(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}