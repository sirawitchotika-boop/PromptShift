package com.promptshift.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ApiKeyStore {
    fun saveKey(profileId: String, apiKey: String)
    fun getKey(profileId: String): String?
    fun deleteKey(profileId: String)
    fun hasKey(profileId: String): Boolean
}

/**
 * Encrypts API keys locally using Android Keystore (AES-256-GCM) with a fresh 12-byte IV
 * per secret. Keys never leave app-private storage and are never included in profile exports.
 */
class AndroidKeystoreApiKeyStore(context: Context) : ApiKeyStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun saveKey(profileId: String, apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            deleteKey(profileId)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(1 + iv.size + ciphertext.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(ciphertext, 0, combined, 1 + iv.size, ciphertext.size)

        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
        prefs.edit().putString(keyFor(profileId), encoded).apply()
    }

    override fun getKey(profileId: String): String? {
        val encoded = prefs.getString(keyFor(profileId), null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < 2) return null
            val ivLen = combined[0].toInt() and 0xFF
            if (combined.size <= 1 + ivLen) return null
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val ciphertext = combined.copyOfRange(1 + ivLen, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    override fun deleteKey(profileId: String) {
        prefs.edit().remove(keyFor(profileId)).apply()
    }

    override fun hasKey(profileId: String): Boolean =
        prefs.contains(keyFor(profileId))

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun keyFor(profileId: String): String = "enc_api_key_$profileId"

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "promptshift_api_key_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PREFS_NAME = "promptshift_secure_keys"
    }
}
