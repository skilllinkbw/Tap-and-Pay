package com.getauthepay.app.security

import android.content.Context
import android.util.Base64
import com.getauthepay.app.core.log.SecureLogger
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Bank-grade security primitives. Backed by the Android Keystore so the
 * AES key material is non-exportable.
 *
 * Responsibilities:
 *  - AES-GCM encrypt / decrypt for at-rest data
 *  - Coarse device integrity signals (root, debug, emulator) used to
 *    compute the device trust score surfaced to the risk engine
 *  - Lifecycle hooks for screenshots / clipboard protection
 *
 * The class deliberately has no third-party dependencies.
 */
class SecurityManager(private val context: Context) {

    private val keyAlias = "AuthePaySecureKey"
    private val algorithm = "AES/GCM/NoPadding"
    private val tagLengthBit = 128
    private val ivLengthByte = 12

    fun isDeviceSecure(): Boolean = !DeviceSecurityChecker.hasObviousRootIndicators(context)

    fun deviceTrustScore(): Int = DeviceSecurityChecker.trustScore(context)

    /** Encrypts plaintext with the Keystore-backed AES-GCM key. */
    fun encryptData(plaintext: String): String {
        val cipher = Cipher.getInstance(algorithm)
        val key = obtainSecretKey()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        require(iv.size == ivLengthByte) { "unexpected IV size: ${iv.size}" }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** Decrypts a payload produced by {@link #encryptData(String)}. */
    fun decryptData(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > ivLengthByte) { "ciphertext too short" }
        val iv = combined.copyOfRange(0, ivLengthByte)
        val cipherText = combined.copyOfRange(ivLengthByte, combined.size)
        val cipher = Cipher.getInstance(algorithm)
        val key = obtainSecretKey()
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(tagLengthBit, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    /** Wipes the Keystore key. Used on logout / device revoke. */
    fun wipe() {
        runCatching {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
        }.onFailure {
            SecureLogger.event(
                event = "security.wipe_failed",
                extra = mapOf("error" to (it.javaClass.simpleName)),
            )
        }
    }

    private fun obtainSecretKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (!keyStore.containsAlias(keyAlias)) {
            val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
            val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                keyAlias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(spec)
            return generator.generateKey()
        }
        val entry = keyStore.getEntry(keyAlias, null) as java.security.KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    companion object {
        private const val TAG = "AuthePaySecurity"
    }

    /** Visible for tests. */
    internal fun filesystemRootHints(): List<String> {
        val hints = mutableListOf<String>()
        val paths = listOf("/system/xbin/su", "/system/bin/su", "/sbin/su",
            "/system/app/Superuser.apk", "/data/local/xbin/su")
        for (p in paths) {
            if (File(p).exists()) hints += p
        }
        return hints
    }
}