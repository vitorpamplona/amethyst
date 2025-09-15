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

import com.vitorpamplona.quartz.nip44Encryption.Nip44v2.MessageKey
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.SecretKey
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
        mac.init(HMacKey(salt))
        return mac.doFinal(key)
    }

    fun extract(
        key1: ByteArray,
        key2: ByteArray,
        salt: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance(algorithm)
        mac.init(HMacKey(salt))
        mac.update(key1)
        mac.update(key2)
        return mac.doFinal()
    }

    fun expand(
        key: ByteArray,
        nonce: ByteArray,
        outputLength: Int,
    ): MessageKey {
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

        return MessageKey(
            chachaKey = result.copyOfRange(0, 32),
            chachaNonce = result.copyOfRange(32, 44),
            hmacKey = result.copyOfRange(44, 76),
        )
    }

    /**
     * Expands with outputLength == 76 while using the least amount of memory allocation
     */
    fun fastExpand(
        key: ByteArray,
        nonce: ByteArray,
    ): MessageKey {
        check(key.size == hashLen)
        check(nonce.size == hashLen)

        val mac = Mac.getInstance(algorithm)
        mac.init(HMacKey(key))

        // First round: T(1) = HMAC-SHA256(key, nonce || 0x01)
        mac.update(nonce)
        mac.update(1)
        val round1 = mac.doFinal()

        // Second round: T(2) = HMAC-SHA256(key, T(1) || nonce || 0x02)
        mac.update(round1)
        mac.update(nonce)
        mac.update(2)
        val round2 = mac.doFinal()

        // Third round: T(3) = HMAC-SHA256(key, T(2) || nonce || 0x03)
        mac.update(round2)
        mac.update(nonce)
        mac.update(3)
        val round3 = mac.doFinal()

        val hmacKey = ByteArray(32)
        System.arraycopy(round2, 12, hmacKey, 0, 20)
        System.arraycopy(round3, 0, hmacKey, 20, 12)

        return MessageKey(
            chachaKey = round1,
            chachaNonce = round2.copyOfRange(0, 12),
            hmacKey = hmacKey,
        )
    }
}

class HMacKey(
    val key: ByteArray,
) : SecretKey {
    override fun getAlgorithm() = "HmacSHA256"

    override fun getEncoded() = key

    override fun getFormat() = "RAW"

    override fun hashCode() = key.contentHashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HMacKey) return false
        return key.contentEquals(other.key)
    }

    override fun destroy() = key.fill(0)

    override fun isDestroyed() = key.all { it.toInt() == 0 }
}
