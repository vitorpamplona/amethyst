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
package com.vitorpamplona.quartz.utils.mac

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA512
import dev.whyoleg.cryptography.providers.apple.Apple

actual class MacInstance actual constructor(
    algorithm: String,
    key: ByteArray,
) {
    private val cryptoProvider = CryptographyProvider.Apple

    private var internalMacInstance: HMAC.Key =
        cryptoProvider
            .get(HMAC)
            .keyDecoder(digestForAlgorithm(algorithm))
            .decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, key)

    private val hmacSignFunction = internalMacInstance.signatureGenerator().createSignFunction()

    actual fun init(
        key: ByteArray,
        algorithm: String,
    ) {
        internalMacInstance =
            cryptoProvider
                .get(HMAC)
                .keyDecoder(digestForAlgorithm(algorithm))
                .decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, key)
    }

    actual fun getMacLength(): Int = hmacSignFunction.signIntoByteArray(internalMacInstance.encodeToByteArrayBlocking(HMAC.Key.Format.RAW))

    actual fun update(array: ByteArray) {
        hmacSignFunction.update(array)
    }

    actual fun update(byte: Byte) {
        hmacSignFunction.update(byteArrayOf(byte))
    }

    actual fun doFinal(): ByteArray = hmacSignFunction.signToByteArray()

    actual fun doFinal(
        output: ByteArray,
        offset: Int,
    ) {
        hmacSignFunction.signIntoByteArray(output, offset)
    }

    private fun digestForAlgorithm(algorithm: String) =
        when (algorithm) {
            "HmacSHA256" -> SHA256
            "HmacSHA512" -> SHA512
            else -> error("Algorithm is not yet supported.")
        }
}
