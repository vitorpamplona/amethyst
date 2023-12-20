package com.vitorpamplona.quartz.crypto

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Hkdf(val algorithm: String = "HmacSHA256", val hashLen: Int = 32) {
    fun extract(key: ByteArray, salt: ByteArray): ByteArray {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(salt, algorithm))
        return mac.doFinal(key)
    }

    fun expand(key: ByteArray, nonce: ByteArray, outputLength: Int): ByteArray {
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