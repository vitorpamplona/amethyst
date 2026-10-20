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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * FIPS 180-4 known answers for the pure-Kotlin SHA-1 and SHA-2 that the native targets use.
 * jvmTest also compares it with JCA on every length across the padding boundaries.
 */
class ShaDigestTest {
    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private val twoBlock256 = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()
    private val twoBlock512 =
        (
            "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmno" +
                "ijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu"
        ).encodeToByteArray()

    private fun ShaDigest.hash(input: ByteArray): String {
        update(input)
        return digest().hex()
    }

    @Test
    fun sha256KnownAnswers() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256Digest().hash(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256Digest().hash("abc".encodeToByteArray()))
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1", Sha256Digest().hash(twoBlock256))
    }

    @Test
    fun sha1KnownAnswers() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", Sha1Digest().hash(ByteArray(0)))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Sha1Digest().hash("abc".encodeToByteArray()))
        assertEquals("84983e441c3bd26ebaae4aa1f95129e5e54670f1", Sha1Digest().hash(twoBlock256))
    }

    @Test
    fun sha512KnownAnswers() {
        assertEquals(
            "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce" +
                "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
            Sha512Digest().hash(ByteArray(0)),
        )
        assertEquals(
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
                "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
            Sha512Digest().hash("abc".encodeToByteArray()),
        )
        assertEquals(
            "8e959b75dae313da8cf4f72814fc143f8f7779c6eb9f7fa17299aeadb6889018" +
                "501d289e4900f7e4331b99dec4b5433ac7d329eeb6dd26545e96e55b874be909",
            Sha512Digest().hash(twoBlock512),
        )
    }

    @Test
    fun millionAs() {
        val chunk = ByteArray(1000) { 'a'.code.toByte() }
        val sha256 = Sha256Digest()
        val sha512 = Sha512Digest()
        repeat(1000) {
            sha256.update(chunk)
            sha512.update(chunk)
        }
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", sha256.digest().hex())
        assertEquals(
            "e718483d0ce769644e2e42c7bc15b4638e1f98b13b2044285632a803afa973eb" +
                "de0ff244877ea60a4cb0432ce577c31beb009c5c2c49aa2e4eadb217ad8cc09b",
            sha512.digest().hex(),
        )
    }

    @Test
    fun splitUpdatesMatchOneShot() {
        val random = Random(42)
        for (digest in listOf(Sha1Digest(), Sha256Digest(), Sha512Digest())) {
            for (length in 0..300) {
                val message = random.nextBytes(length)
                digest.update(message)
                val oneShot = digest.digest()

                var pos = 0
                while (pos < length) {
                    val take = random.nextInt(1, 70).coerceAtMost(length - pos)
                    if (take == 1) digest.update(message[pos]) else digest.update(message, pos, take)
                    pos += take
                }
                assertContentEquals(oneShot, digest.digest(), "length $length")
            }
        }
    }

    @Test
    fun oneShotSha256MatchesTheDigest() {
        val random = Random(9)
        val digest = Sha256Digest()
        for (length in 0..200) {
            val message = random.nextBytes(length + 7)
            digest.update(message, 3, length)
            val expected = digest.digest()
            val out = ByteArray(40)
            Sha256Compression.hash(message, 3, length, out, 5)
            assertContentEquals(expected, out.copyOfRange(5, 37), "length $length")
        }
    }

    @Test
    fun digestIntoWritesAtOffsetAndResets() {
        val digest = Sha256Digest()
        val out = ByteArray(40)
        digest.update("ignored".encodeToByteArray())
        digest.reset()
        digest.update("abc".encodeToByteArray())
        digest.digestInto(out, 8)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", out.copyOfRange(8, 40).hex())
        assertContentEquals(ByteArray(8), out.copyOfRange(0, 8))
        // The digest reset itself, so the next message starts clean.
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", digest.digest().hex())
    }
}
