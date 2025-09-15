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
package com.vitorpamplona.quartz.nip44Encryption.crypto

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Hkdf(
    val algorithm: String = "HmacSHA256",
    val hashLen: Int = 32,
) {
    fun extract(
        key: ByteArray,
        salt: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(salt, algorithm))
        return mac.doFinal(key)
    }

    fun extract(
        key1: ByteArray,
        key2: ByteArray,
        salt: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(salt, algorithm))
        mac.update(key1)
        mac.update(key2)
        return mac.doFinal()
    }

    fun expand(
        key: ByteArray,
        nonce: ByteArray,
        outputLength: Int,
    ): ByteArray {
        check(key.size == hashLen)
        check(nonce.size == hashLen)

        val n = if (outputLength % hashLen == 0) outputLength / hashLen else outputLength / hashLen + 1
        var hashRound = ByteArray(0)
        val generatedBytes = ByteBuffer.allocate(Math.multiplyExact(n, hashLen))
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(key, algorithm))
        for (roundNum in 1..n) {
            mac.reset()
            val t = ByteBuffer.allocate(hashRound.size + nonce.size + 1)
            t.put(hashRound)
            t.put(nonce)
            t.put(roundNum.toByte())
            hashRound = mac.doFinal(t.array())
            generatedBytes.put(hashRound)
        }
        val result = ByteArray(outputLength)
        generatedBytes.rewind()
        generatedBytes[result, 0, outputLength]
        return result
    }
}
