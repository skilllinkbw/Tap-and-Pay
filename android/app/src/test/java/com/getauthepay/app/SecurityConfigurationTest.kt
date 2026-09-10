package com.getauthepay.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Security configuration acceptance tests (directive sections 33, 36, 53, 55).
 *
 * These are the checks a bank submission reviewer performs by hand. Pinning
 * them as tests means a future commit cannot quietly ship a build that
 * contacts a real card network from a debug APK, leaks cleartext traffic, or
 * leaves a test OTP shortcut enabled in production.
 *
 * The release build type cannot be inspected through BuildConfig at unit-test
 * time (only the variant under test is generated), so the release gates are
 * asserted against the Gradle source of truth, while the runtime gates are
 * asserted against the generated BuildConfig for the variant under test.
 */
class SecurityConfigurationTest {

    private val appModuleDir: File by lazy { locateAppModule() }

    private fun locateAppModule(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            val candidate = File(dir, "build.gradle")
            if (candidate.isFile && candidate.readText().contains("com.android.application")) {
                return dir
            }
            // Also handle a run from the project root.
            val nested = File(dir, "app/build.gradle")
            if (nested.isFile && nested.readText().contains("com.android.application")) {
                return File(dir, "app")
            }
            dir = dir.parentFile ?: error("Could not locate the Android app module from ${System.getProperty("user.dir")}")
        }
        error("Could not locate the Android app module")
    }

    private val buildGradle: String by lazy { File(appModuleDir, "build.gradle").readText() }

    private val mainDir: File by lazy {
        File(appModuleDir, "src/main").also {
            assertTrue("src/main must exist", it.isDirectory)
        }
    }

    /** Extracts the named build-type block from the `buildTypes { ... }` block. */
    private fun buildTypeBlock(name: String): String {
        // Scope the search to the buildTypes block so a `release {` declared
        // elsewhere (e.g. signingConfigs.release) does not shadow the build type.
        val btStart = buildGradle.indexOf("buildTypes")
        assertTrue("build.gradle must declare buildTypes", btStart >= 0)
        val btEnd = run {
            var depth = 0
            for (i in btStart until buildGradle.length) {
                when (buildGradle[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return@run i + 1
                    }
                }
            }
            buildGradle.length
        }
        val btBlock = buildGradle.substring(btStart, btEnd)
        val start = btBlock.indexOf("$name {")
        assertTrue("build.gradle must declare a '$name' build type", start >= 0)
        var depth = 0
        for (i in start until btBlock.length) {
            when (btBlock[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return btBlock.substring(start, i + 1)
                }
            }
        }
        error("Unbalanced braces for build type '$name'")
    }

    private fun fieldValue(block: String, field: String): String {
        val pattern = Regex("""buildConfigField\s+"boolean",\s+"$field",\s+"(\w+)"""")
        return pattern.find(block)?.groupValues?.get(1)
            ?: error("build.gradle is missing buildConfigField '$field'")
    }

    // ---- release gates ---------------------------------------------------

    @Test
    fun `release disables sandbox payments`() {
        assertEquals("false", fieldValue(buildTypeBlock("release"), "SANDBOX_PAYMENTS"))
    }

    @Test
    fun `release disables the test OTP shortcut`() {
        assertEquals("false", fieldValue(buildTypeBlock("release"), "TEST_OTP_ENABLED"))
    }

    @Test
    fun `release blocks cleartext traffic`() {
        assertEquals("false", fieldValue(buildTypeBlock("release"), "ALLOW_CLEARTEXT"))
    }

    @Test
    fun `release is marked as the production build`() {
        assertEquals("true", fieldValue(buildTypeBlock("release"), "PRODUCTION_BUILD"))
    }

    @Test
    fun `release is not debuggable and is minified`() {
        val block = buildTypeBlock("release")
        assertTrue("release must not be debuggable", block.contains("debuggable false"))
        assertTrue("release must be minified", block.contains("minifyEnabled true"))
    }

    @Test
    fun `release uses the production api endpoint`() {
        val block = buildTypeBlock("release")
        assertTrue(
            "release must target the production API",
            block.contains("https://api.authepay.co.bw"),
        )
        assertFalse(
            "release must not point at a sandbox host",
            block.contains("sandbox-api"),
        )
    }

    @Test
    fun `staging disables the test OTP shortcut`() {
        assertEquals("false", fieldValue(buildTypeBlock("staging"), "TEST_OTP_ENABLED"))
    }

    @Test
    fun `debug is the only build that enables the test OTP shortcut`() {
        assertEquals("true", fieldValue(buildTypeBlock("debug"), "TEST_OTP_ENABLED"))
    }

    // ---- network security -------------------------------------------------

    @Test
    fun `network security config blocks cleartext`() {
        val file = File(mainDir, "res/xml/network_security_config.xml")
        assertTrue("network_security_config.xml must exist", file.isFile)
        val content = file.readText()
        assertTrue(
            "base config must forbid cleartext",
            content.contains("cleartextTrafficPermitted=\"false\""),
        )
        assertFalse(
            "no debug override may permit cleartext",
            content.contains("cleartextTrafficPermitted=\"true\""),
        )
    }

    @Test
    fun `manifest forbids cleartext and backup`() {
        val manifest = File(mainDir, "AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }

    @Test
    fun `manifest declares only the permissions the app needs`() {
        val manifest = File(mainDir, "AndroidManifest.xml")
            .readLines()
            .filter { it.contains("<uses-permission") }
            .map { it.trim() }
        val names = manifest.map {
            Regex("""android:name="([^"]+)"""").find(it)!!.groupValues[1]
        }.toSet()

        assertTrue(names.contains("android.permission.NFC"))
        assertTrue(names.contains("android.permission.INTERNET"))
        assertTrue(names.contains("android.permission.CAMERA"))
        // The app must not request anything it cannot justify to a reviewer.
        assertFalse(
            "must not request READ_SMS",
            names.contains("android.permission.READ_SMS"),
        )
        assertFalse(
            "must not request READ_CONTACTS",
            names.contains("android.permission.READ_CONTACTS"),
        )
        assertFalse(
            "must not request location",
            names.any { it.contains("LOCATION") },
        )
    }

    // ---- secrets ----------------------------------------------------------

    @Test
    fun `no service-role or signing keys are committed in source`() {
        val sources = mainDir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml" || it.extension == "gradle") }
            .toList()
        assertTrue("source files must be found", sources.isNotEmpty())

        val banned = listOf(
            "service_role", "serviceRole", "SERVICE_ROLE",
            "sk_live", "pk_live", "sk_test_",
            "BEGIN RSA PRIVATE KEY", "BEGIN PRIVATE KEY",
            "SUPABASE_SERVICE", "supabaseServiceKey",
        )
        sources.forEach { file ->
            val text = file.readText()
            banned.forEach { token ->
                assertFalse(
                    "${file.name} must not contain '$token'",
                    text.contains(token),
                )
            }
        }
    }

    @Test
    fun `no hardcoded bearer or api key literals in kotlin sources`() {
        val sources = mainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val pattern = Regex(
            """(apiKey|apiSecret|secretKey|privateKey|password)\s*=\s*"[^"]{12,}"""",
        )
        sources.forEach { file ->
            assertFalse(
                "${file.name} must not hardcode a credential literal",
                pattern.containsMatchIn(file.readText()),
            )
        }
    }

    // ---- runtime gates for the variant under test ---------------------------

    @Test
    fun `build config exposes the security gate fields`() {
        val fields = BuildConfig::class.java.declaredFields.map { it.name }.toSet()
        listOf(
            "SANDBOX_PAYMENTS", "TEST_OTP_ENABLED",
            "ALLOW_CLEARTEXT", "PRODUCTION_BUILD",
            "API_BASE_URL", "ENVIRONMENT_NAME",
        ).forEach {
            assertTrue("BuildConfig must expose $it", it in fields)
        }
    }

    @Test
    fun `api base url is always https`() {
        assertTrue(
            "API_BASE_URL must be https (was ${BuildConfig.API_BASE_URL})",
            BuildConfig.API_BASE_URL.startsWith("https://"),
        )
    }

    @Test
    fun `debug and staging never report as production`() {
        if (BuildConfig.DEBUG) {
            assertFalse(
                "a debuggable build must not be marked production",
                BuildConfig.PRODUCTION_BUILD,
            )
        }
    }

    @Test
    fun `environment name is one of the three known values`() {
        assertTrue(
            "unexpected environment: ${BuildConfig.ENVIRONMENT_NAME}",
            BuildConfig.ENVIRONMENT_NAME in setOf("SANDBOX", "STAGING", "PRODUCTION"),
        )
    }

    @Test
    fun `sandbox payments imply a non-production environment`() {
        if (BuildConfig.SANDBOX_PAYMENTS) {
            assertFalse(
                "a sandbox build must not be marked production",
                BuildConfig.PRODUCTION_BUILD,
            )
        }
    }

    @Test
    fun `release variant exists alongside debug and staging`() {
        val block = buildTypeBlock("release")
        assertNotNull(block)
        assertTrue(block.contains("release"))
    }
}
