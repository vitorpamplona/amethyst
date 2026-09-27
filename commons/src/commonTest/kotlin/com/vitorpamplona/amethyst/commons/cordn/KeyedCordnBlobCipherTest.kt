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
package com.vitorpamplona.amethyst.commons.cordn

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The blob cipher every platform without an OS key store uses.
 *
 * What is worth asserting is not that ChaCha20-Poly1305 works — quartz tests
 * that — but the three things this wrapper decides: that the nonce is fresh
 * per blob, that a blob is authenticated rather than merely scrambled, and
 * that the plaintext is nowhere in the output. The first matters most: one key
 * covers every blob, and a repeated nonce leaks the XOR of two `MlsGroupState`
 * serialisations, which is epoch secrets.
 */
class KeyedCordnBlobCipherTest {
    private val key = ByteArray(KeyedCordnBlobCipher.KEY_LENGTH) { it.toByte() }
    private val cipher = KeyedCordnBlobCipher(key)
    private val blob = "ratchet tree and epoch secrets".encodeToByteArray()

    @Test
    fun `a blob round-trips`() {
        assertContentEquals(blob, cipher.decrypt(cipher.encrypt(blob)))
    }

    @Test
    fun `an empty blob round-trips`() {
        // A group with nothing saved yet writes one, and a frame that is all
        // nonce and tag has to survive the trip.
        assertContentEquals(ByteArray(0), cipher.decrypt(cipher.encrypt(ByteArray(0))))
    }

    @Test
    fun `the same blob encrypted twice never repeats a nonce`() {
        val first = cipher.encrypt(blob)
        val second = cipher.encrypt(blob)

        val nonceA = first.copyOfRange(0, KeyedCordnBlobCipher.NONCE_LENGTH)
        val nonceB = second.copyOfRange(0, KeyedCordnBlobCipher.NONCE_LENGTH)

        assertFalse(nonceA.contentEquals(nonceB), "a repeated nonce leaks the XOR of two group states")
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `the plaintext does not appear in the output`() {
        val sealed = cipher.encrypt(blob)

        assertFalse(sealed.decodeToString().contains("ratchet tree"))
        assertNotEquals(blob.size, sealed.size)
    }

    @Test
    fun `a flipped byte is refused rather than decrypted`() {
        val sealed = cipher.encrypt(blob)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()

        // An MlsGroupState cannot be re-derived from anywhere, so a silently
        // corrupted one is worse than a loud failure.
        assertFails { cipher.decrypt(sealed) }
    }

    @Test
    fun `a flipped nonce byte is refused`() {
        val sealed = cipher.encrypt(blob)
        sealed[0] = (sealed[0].toInt() xor 0x01).toByte()

        assertFails { cipher.decrypt(sealed) }
    }

    @Test
    fun `another key cannot open it`() {
        val sealed = cipher.encrypt(blob)
        val other = KeyedCordnBlobCipher(ByteArray(KeyedCordnBlobCipher.KEY_LENGTH) { (it + 1).toByte() })

        assertFails { other.decrypt(sealed) }
    }

    @Test
    fun `a truncated blob is refused with a message that says so`() {
        // A short blob throws either way: the AEAD underneath rejects a
        // ciphertext shorter than its tag, and slicing 12 nonce bytes out of a
        // 0-byte array is an illegal range. So the guard buys no new failure —
        // what it buys is the reason. Asserting the message is therefore the
        // only way to pin it, and the reason is worth pinning: "fromIndex(12)
        // > toIndex(0)" from inside a copyOfRange reads as a bug in Amethyst,
        // while the size of the file that is not a blob points at the file.
        val short = assertFailsWith<IllegalArgumentException> { cipher.decrypt(ByteArray(KeyedCordnBlobCipher.NONCE_LENGTH)) }
        val empty = assertFailsWith<IllegalArgumentException> { cipher.decrypt(ByteArray(0)) }

        assertTrue(short.message!!.contains("not a cordn blob"), "unhelpful: ${short.message}")
        assertTrue(empty.message!!.contains("not a cordn blob"), "unhelpful: ${empty.message}")
    }

    @Test
    fun `a key of the wrong length is refused at construction`() {
        // Failing here rather than at the first encrypt means a misconfigured
        // front end cannot get as far as writing blobs under a 16-byte key.
        assertFails { KeyedCordnBlobCipher(ByteArray(16)) }
        assertFails { KeyedCordnBlobCipher(ByteArray(0)) }
    }

    @Test
    fun `a fresh key is the right length and not a constant`() {
        val a = KeyedCordnBlobCipher.newKey()
        val b = KeyedCordnBlobCipher.newKey()

        assertTrue(a.size == KeyedCordnBlobCipher.KEY_LENGTH)
        assertFalse(a.contentEquals(b))
    }
}
