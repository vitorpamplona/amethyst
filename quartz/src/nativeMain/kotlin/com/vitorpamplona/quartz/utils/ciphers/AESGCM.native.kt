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
package com.vitorpamplona.quartz.utils.ciphers

import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.ciphers.aes.AesGcmKey

/**
 * AES-GCM in Kotlin on every native target: CommonCrypto has no public GCM, and the
 * Apple implementation that has one, CryptoKit, is Swift-only.
 */
actual class AESGCM actual constructor(
    actual val keyBytes: ByteArray,
    actual val nonce: ByteArray,
) : NostrCipher {
    // Expanded once, on first use, and reused by every call on this instance.
    private val key by lazy { AesGcmKey(keyBytes) }

    actual override fun name(): String = AES_GCM_NAME

    actual override fun encrypt(bytesToEncrypt: ByteArray): ByteArray = key.encrypt(nonce, bytesToEncrypt, EMPTY)

    actual override fun decrypt(bytesToDecrypt: ByteArray): ByteArray = key.decrypt(nonce, bytesToDecrypt, EMPTY)

    actual override fun decryptOrNull(bytesToDecrypt: ByteArray): ByteArray? =
        try {
            decrypt(bytesToDecrypt)
        } catch (e: IllegalArgumentException) {
            Log.w("AESGCM", "Failed to decrypt", e)
            null
        }

    actual fun encrypt(
        bytesToEncrypt: ByteArray,
        aad: ByteArray,
    ): ByteArray = key.encrypt(nonce, bytesToEncrypt, aad)

    actual fun decrypt(
        bytesToDecrypt: ByteArray,
        aad: ByteArray,
    ): ByteArray = key.decrypt(nonce, bytesToDecrypt, aad)

    private companion object {
        val EMPTY = ByteArray(0)
    }
}
