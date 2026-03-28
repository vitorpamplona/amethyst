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

import io.github.andreypfau.kotlinx.crypto.HMac
import io.github.andreypfau.kotlinx.crypto.Sha256
import io.github.andreypfau.kotlinx.crypto.Sha512

actual class MacInstance actual constructor(
    algorithm: String,
    key: ByteArray,
) {
    private var nativeHmac = HMac(digestForAlgorithm(algorithm), key)

    actual fun init(
        key: ByteArray,
        algorithm: String,
    ) {
        nativeHmac = HMac(digestForAlgorithm(algorithm), key)
    }

    actual fun getMacLength(): Int = nativeHmac.macSize

    actual fun update(array: ByteArray) {
        nativeHmac.update(array)
    }

    actual fun update(byte: Byte) {
        nativeHmac.update(byte)
    }

    actual fun doFinal(): ByteArray = nativeHmac.digest()

    actual fun doFinal(
        output: ByteArray,
        offset: Int,
    ) {
        nativeHmac.digest(output, offset)
    }

    private fun digestForAlgorithm(algorithm: String) =
        when (algorithm) {
            "HmacSHA256" -> Sha256()
            "HmacSHA512" -> Sha512()
            else -> error("Algorithm is not yet supported.")
        }
}
