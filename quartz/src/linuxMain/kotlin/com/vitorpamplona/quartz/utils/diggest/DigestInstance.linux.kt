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
package com.vitorpamplona.quartz.utils.diggest

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.RIPEMD160
import dev.whyoleg.cryptography.algorithms.SHA1
import dev.whyoleg.cryptography.algorithms.SHA256

actual class DigestInstance actual constructor(
    algorithm: String,
) {
    private val cryptoProvider = CryptographyProvider.Default

    private val hasher = cryptoProvider.get(digestForAlgorithm(algorithm)).hasher()
    private val hashFunction = hasher.createHashFunction()

    actual fun update(array: ByteArray) {
        hashFunction.update(array)
    }

    actual fun update(byte: Byte) {
        hashFunction.update(byteArrayOf(byte))
    }

    actual fun digest(): ByteArray = hashFunction.hashToByteArray()

    actual fun digest(input: ByteArray): ByteArray = hasher.hashBlocking(input)

    @OptIn(DelicateCryptographyApi::class)
    private fun digestForAlgorithm(algorithmName: String) =
        when (algorithmName) {
            "SHA-256" -> SHA256
            "SHA-1" -> SHA1
            "ripemd160" -> RIPEMD160
            "keccak256" -> error("KECCAK-256 is not yet supported.")
            else -> error("This message digest is not supported.")
        }
}
