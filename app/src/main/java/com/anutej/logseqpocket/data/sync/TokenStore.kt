package com.anutej.logseqpocket.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Holds the user's read-only GitHub token. The token is never logged or shown again once saved. */
interface TokenStore {
    val hasToken: Flow<Boolean>
    suspend fun load(): String?
    suspend fun save(token: String)
    suspend fun clear()
}

private val Context.secretStore: DataStore<Preferences> by preferencesDataStore(name = "secrets")

/**
 * Encrypts the token with an AES-256-GCM key that lives in the Android Keystore (non-exportable),
 * and stores only ciphertext + IV in DataStore. App data is also excluded from cloud backup.
 */
class KeystoreTokenStore(context: Context) : TokenStore {
    private val store = context.applicationContext.secretStore

    override val hasToken: Flow<Boolean> = store.data.map { it[CIPHERTEXT] != null }

    override suspend fun load(): String? {
        val prefs = store.data.first()
        val ct = prefs[CIPHERTEXT] ?: return null
        val iv = prefs[IV] ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull() // A lost/invalidated key just means the user pastes the token again.
    }

    override suspend fun save(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        store.edit {
            it[CIPHERTEXT] = Base64.encodeToString(ct, Base64.NO_WRAP)
            it[IV] = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        }
    }

    override suspend fun clear() {
        store.edit { it.remove(CIPHERTEXT); it.remove(IV) }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "logseq_pocket_github_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val CIPHERTEXT = stringPreferencesKey("github_token_ct")
        val IV = stringPreferencesKey("github_token_iv")
    }
}
