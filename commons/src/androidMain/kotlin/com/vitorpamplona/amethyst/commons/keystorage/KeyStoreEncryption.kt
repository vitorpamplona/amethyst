/*
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
package com.vitorpamplona.amethyst.commons.keystorage

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

internal class KeyStoreEncryption {
    companion object {
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
        private const val PURPOSE = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        private const val KEY_ALIAS = "AMETHYST_AES_KEY"
    }

    // One Cipher per thread rather than one shared instance: a Cipher holds the
    // state of the operation in progress, so two callers on different threads
    // through the same object would corrupt each other's output.
    private val ciphers = ThreadLocal.withInitial { Cipher.getInstance(TRANSFORMATION) }

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    // The handle never changes for the life of the alias, but fetching it is a
    // round trip to the keystore daemon — not something to pay per operation.
    @Volatile
    private var cachedKey: SecretKey? = null

    private fun getKey(): SecretKey =
        cachedKey ?: synchronized(this) {
            cachedKey ?: loadOrCreateKey().also { cachedKey = it }
        }

    private fun loadOrCreateKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createKey()
    }

    private fun createKeyStrongBoxIfAvailable(): SecretKey? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val keyParams =
                    KeyGenParameterSpec
                        .Builder(KEY_ALIAS, PURPOSE)
                        .setBlockModes(BLOCK_MODE)
                        .setEncryptionPaddings(PADDING)
                        .setIsStrongBoxBacked(true)
                        .build()

                val generator = KeyGenerator.getInstance(ALGORITHM, "AndroidKeyStore")
                generator.init(keyParams)
                generator.generateKey()
            } catch (_: StrongBoxUnavailableException) {
                null
            }
        } else {
            null
        }

    private fun createKeyRegular(): SecretKey {
        val keyParams =
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, PURPOSE)
                .setBlockModes(BLOCK_MODE)
                .setEncryptionPaddings(PADDING)
                .build()

        val generator = KeyGenerator.getInstance(ALGORITHM, "AndroidKeyStore")
        generator.init(keyParams)
        return generator.generateKey()
    }

    private fun createKey(): SecretKey = createKeyStrongBoxIfAvailable() ?: createKeyRegular()

    fun encrypt(bytes: ByteArray): ByteArray {
        try {
            // Initializes the cipher in encrypt mode and encrypts data
            val cipher = ciphers.get()
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(bytes)
            return iv + encrypted
        } catch (e: Exception) {
            // A key the system has retired (a wipe, a credential reset) keeps
            // failing until it is re-read, so the cached handle goes with it.
            cachedKey = null
            throw e
        }
    }

    fun decrypt(bytes: ByteArray): ByteArray {
        try {
            // Extracts IV and decrypts the data
            val iv = bytes.copyOfRange(0, 12) // GCM mode uses 12-byte IV
            val data = bytes.copyOfRange(12, bytes.size)
            val cipher = ciphers.get()
            cipher.init(Cipher.DECRYPT_MODE, getKey(), IvParameterSpec(iv))
            return cipher.doFinal(data)
        } catch (e: Exception) {
            cachedKey = null
            throw e
        }
    }
}
