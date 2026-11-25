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
package com.vitorpamplona.amethyst.commons.model.preferences

import com.vitorpamplona.amethyst.commons.keystorage.Keychain
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.utils.Log

/**
 * iOS [SecretEncryption]: the same AES-256-GCM, in the same layout (12-byte nonce, then ciphertext
 * and tag), as Android and the desktop, with the key held in the Keychain on this device only, so
 * a copy of the app's files is useless without this phone, as on Android. The Notification Service
 * Extension reads it once both share a Keychain access group (plans/2026-10-09-ios-app.md, B6).
 */
actual class SecretEncryption actual constructor() {
    private val keychain = Keychain(SERVICE)
    private val lock = KmpLock()
    private var cachedKey: ByteArray? = null

    private fun key(): ByteArray =
        lock.withLock {
            cachedKey ?: loadOrCreateKey().also { cachedKey = it }
        }

    actual fun encrypt(bytes: ByteArray): ByteArray {
        // A fresh nonce per message: AESGCM refuses a second encrypt with the same one.
        val nonce = RandomInstance.bytes(NONCE_SIZE)
        return nonce + AESGCM(key(), nonce).encrypt(bytes)
    }

    actual fun decrypt(bytes: ByteArray): ByteArray? {
        require(bytes.size > NONCE_SIZE) { "Ciphertext of ${bytes.size} bytes is too short" }
        val nonce = bytes.copyOfRange(0, NONCE_SIZE)
        val data = bytes.copyOfRange(NONCE_SIZE, bytes.size)
        return AESGCM(key(), nonce).decryptOrNull(data)
            ?: throw IllegalStateException("decrypt() failed: the data was not encrypted with this device's key")
    }

    private fun loadOrCreateKey(): ByteArray {
        keychain.read(ACCOUNT)?.let { stored ->
            if (stored.size == KEY_SIZE) return stored
            // A key of the wrong size cannot decrypt what is stored; replacing it would strand that
            // data under a key nobody holds.
            throw IllegalStateException("Keychain key is ${stored.size} bytes, expected $KEY_SIZE")
        }
        Log.d(TAG) { "Creating a new AES key in the Keychain" }
        val key = RandomInstance.bytes(KEY_SIZE)
        keychain.write(ACCOUNT, key)
        return key
    }

    private companion object {
        const val TAG = "SecretEncryption"
        const val SERVICE = "com.vitorpamplona.amethyst.secret-encryption"
        const val ACCOUNT = "aes-256-gcm"
        const val KEY_SIZE = 32
        const val NONCE_SIZE = 12
    }
}
