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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The native [DigestInstance] for every algorithm OpenTimestamps asks of it. Native
 * only: on the JVM "ripemd160" needs a provider the default JDK does not have.
 */
class DigestInstanceNativeTest {
    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun hash(
        algorithm: String,
        input: String,
    ) = DigestInstance(algorithm).digest(input.encodeToByteArray()).hex()

    @Test
    fun knownAnswers() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash("SHA-256", "abc"))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", hash("SHA-1", "abc"))
        assertEquals("9c1185a5c5e9fc54612808977ee8f548b2258d31", hash("ripemd160", ""))
        assertEquals("8eb208f7e05d987a9b044a8e98c6b087f15a0bfc", hash("ripemd160", "abc"))
        assertEquals("5d0689ef49d2fae572b881b123a85ffa21595f36", hash("ripemd160", "message digest"))
    }

    @Test
    fun streamsInPieces() {
        val digest = DigestInstance("ripemd160")
        "message ".encodeToByteArray().forEach { digest.update(it) }
        digest.update("digest".encodeToByteArray())
        assertEquals("5d0689ef49d2fae572b881b123a85ffa21595f36", digest.digest().hex())
    }

    @Test
    fun resetsAfterEachDigest() {
        for ((algorithm, abc) in listOf(
            "SHA-256" to "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "SHA-1" to "a9993e364706816aba3e25717850c26c9cd0d89d",
            "ripemd160" to "8eb208f7e05d987a9b044a8e98c6b087f15a0bfc",
        )) {
            val digest = DigestInstance(algorithm)
            repeat(3) {
                digest.update("abc".encodeToByteArray())
                assertEquals(abc, digest.digest().hex(), algorithm)
            }
            assertEquals(abc, digest.digest("abc".encodeToByteArray()).hex(), algorithm)
        }
    }
}
