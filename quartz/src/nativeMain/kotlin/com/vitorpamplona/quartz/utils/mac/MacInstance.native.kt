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

import com.vitorpamplona.quartz.utils.sha2.Sha256Digest
import com.vitorpamplona.quartz.utils.sha2.Sha512Digest

actual class MacInstance actual constructor(
    algorithm: String,
    key: ByteArray,
) {
    private var hmac = Hmac(digestForAlgorithm(algorithm), key)

    actual fun init(
        key: ByteArray,
        algorithm: String,
    ) {
        hmac = Hmac(digestForAlgorithm(algorithm), key)
    }

    actual fun getMacLength(): Int = hmac.macSize

    actual fun update(array: ByteArray) {
        hmac.update(array)
    }

    actual fun update(byte: Byte) {
        hmac.update(byte)
    }

    actual fun doFinal(): ByteArray = hmac.doFinal()

    actual fun doFinal(
        output: ByteArray,
        offset: Int,
    ) {
        hmac.doFinal(output, offset)
    }

    private fun digestForAlgorithm(algorithm: String) =
        when (algorithm) {
            "HmacSHA256" -> Sha256Digest()
            "HmacSHA512" -> Sha512Digest()
            else -> error("Algorithm is not yet supported.")
        }
}
