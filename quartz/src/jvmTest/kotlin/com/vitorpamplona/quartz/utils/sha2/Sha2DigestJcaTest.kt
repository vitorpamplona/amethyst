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
package com.vitorpamplona.quartz.utils.sha2

import com.vitorpamplona.quartz.utils.mac.Hmac
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * The pure-Kotlin SHA-2 and HMAC the native targets run, checked against JCA for
 * every message length through three blocks, so each padding boundary is crossed
 * (55/56/64 bytes for SHA-256, 111/112/128 for SHA-512), and for keys around the
 * block size.
 */
class Sha2DigestJcaTest {
    private val random = Random(7)

    @Test
    fun digestsMatchJca() {
        val pairs = listOf(Sha256Digest() to "SHA-256", Sha512Digest() to "SHA-512")
        for ((digest, name) in pairs) {
            val jca = MessageDigest.getInstance(name)
            for (length in 0..3 * digest.blockSize + 1) {
                val message = random.nextBytes(length)
                digest.update(message)
                assertContentEquals(jca.digest(message), digest.digest(), "$name, length $length")
            }
        }
    }

    @Test
    fun hmacsMatchJca() {
        val pairs = listOf({ Sha256Digest() } to "HmacSHA256", { Sha512Digest() } to "HmacSHA512")
        for ((newDigest, name) in pairs) {
            val blockSize = newDigest().blockSize
            for (keyLength in listOf(1, 16, 32, blockSize - 1, blockSize, blockSize + 1, 3 * blockSize)) {
                val key = random.nextBytes(keyLength)
                val jca = Mac.getInstance(name).apply { init(SecretKeySpec(key, name)) }
                val mac = Hmac(newDigest(), key)
                for (length in listOf(0, 1, blockSize - 1, blockSize, 2 * blockSize + 3)) {
                    val message = random.nextBytes(length)
                    mac.update(message)
                    assertContentEquals(jca.doFinal(message), mac.doFinal(), "$name, key $keyLength, message $length")
                }
            }
        }
    }
}
