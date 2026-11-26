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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * iOS [SecureKeyStorage]: each account's private key (hex) is a Keychain item named by its npub,
 * kept on this device only (see [Keychain]). Face ID gating comes with `DeviceAuthenticator`
 * (plans/2026-10-09-ios-app.md, B2).
 */
actual class SecureKeyStorage private actual constructor() {
    private val keychain = Keychain(SERVICE)

    actual companion object {
        private const val SERVICE = "com.vitorpamplona.amethyst.private-keys"

        actual fun create(context: Any?): SecureKeyStorage = SecureKeyStorage()
    }

    actual suspend fun savePrivateKey(
        npub: String,
        privKeyHex: String,
    ): Unit = withContext(Dispatchers.IO) { keychain.write(npub, privKeyHex.encodeToByteArray()) }

    actual suspend fun getPrivateKey(npub: String): String? =
        try {
            getPrivateKeyOrThrow(npub)
        } catch (e: SecureStorageException) {
            null
        }

    actual suspend fun getPrivateKeyOrThrow(npub: String): String? = withContext(Dispatchers.IO) { keychain.read(npub)?.decodeToString() }

    actual suspend fun deletePrivateKey(npub: String): Boolean = withContext(Dispatchers.IO) { keychain.delete(npub) }

    actual suspend fun hasPrivateKey(npub: String): Boolean = getPrivateKeyOrThrow(npub) != null
}
