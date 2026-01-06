/**
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.nip01Core.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation of SecureKeyStorage using EncryptedSharedPreferences
 * backed by Android Keystore (AES-256-GCM, hardware-backed when available).
 */
actual class SecureKeyStorage(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "amethyst_secure_keys"
        private const val KEY_PREFIX = "privkey_"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    actual suspend fun savePrivateKey(npub: String, privKeyHex: String) {
        withContext(Dispatchers.IO) {
            try {
                encryptedPrefs.edit().putString(KEY_PREFIX + npub, privKeyHex).apply()
            } catch (e: Exception) {
                throw SecureStorageException("Failed to save private key", e)
            }
        }
    }

    actual suspend fun getPrivateKey(npub: String): String? =
        withContext(Dispatchers.IO) {
            try {
                encryptedPrefs.getString(KEY_PREFIX + npub, null)
            } catch (e: Exception) {
                throw SecureStorageException("Failed to retrieve private key", e)
            }
        }

    actual suspend fun deletePrivateKey(npub: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val key = KEY_PREFIX + npub
                val existed = encryptedPrefs.contains(key)
                if (existed) {
                    encryptedPrefs.edit().remove(key).apply()
                }
                existed
            } catch (e: Exception) {
                throw SecureStorageException("Failed to delete private key", e)
            }
        }

    actual suspend fun hasPrivateKey(npub: String): Boolean =
        withContext(Dispatchers.IO) {
            encryptedPrefs.contains(KEY_PREFIX + npub)
        }
}
