package com.getauthepay.app;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Smoke test: the app under test is the Tap & Pay merchant application.
 *
 * Replaces the Capacitor template test, which asserted a package name from
 * the old web scaffolding ("com.getcapacitor.app") and could never pass
 * against this native app.
 */
@RunWith(AndroidJUnit4.class)
public class SmokeInstrumentedTest {
    @Test
    public void appContextHasTapAndPayPackage() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Debug/staging/demo build types append an applicationId suffix.
        assertTrue(
                "unexpected applicationId: " + appContext.getPackageName(),
                appContext.getPackageName().startsWith("com.getauthepay.app"));
    }
}
