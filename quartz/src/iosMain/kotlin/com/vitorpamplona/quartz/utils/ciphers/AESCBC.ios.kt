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
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.providers.apple.Apple

actual class AESCBC actual constructor(
    actual val keyBytes: ByteArray,
    actual val iv: ByteArray,
) : NostrCipher {
    private val cryptoProvider = CryptographyProvider.Apple
    private val aesCbc = cryptoProvider.get(AES.CBC)

    private val keyDecoder =
        aesCbc
            .keyDecoder()
            .decodeFromByteArrayBlocking(format = AES.Key.Format.RAW, keyBytes)

    private fun cipher() = keyDecoder.cipher()

    actual override fun name(): String = "aes-cbc"

    @OptIn(DelicateCryptographyApi::class)
    actual override fun encrypt(bytesToEncrypt: ByteArray): ByteArray =
        with(cipher()) {
            encryptWithIvBlocking(iv, bytesToEncrypt)
        }

    @OptIn(DelicateCryptographyApi::class)
    actual override fun decrypt(bytesToDecrypt: ByteArray): ByteArray =
        with(cipher()) {
            decryptWithIvBlocking(iv, bytesToDecrypt)
        }

    @OptIn(DelicateCryptographyApi::class)
    actual override fun decryptOrNull(bytesToDecrypt: ByteArray): ByteArray? =
        try {
            with(cipher()) {
                decryptWithIvBlocking(iv, bytesToDecrypt)
            }
        } catch (e: Exception) {
            Log.w("AESCBC", "Failed to decrypt", e)
            null
        }
}
