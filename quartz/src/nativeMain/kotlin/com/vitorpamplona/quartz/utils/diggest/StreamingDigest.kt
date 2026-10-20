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

import com.vitorpamplona.quartz.nip03Timestamp.ots.crypto.RIPEMD160Digest
import com.vitorpamplona.quartz.utils.sha2.Sha1Digest
import com.vitorpamplona.quartz.utils.sha2.Sha256Digest
import com.vitorpamplona.quartz.utils.sha2.ShaDigest

/** What a native [DigestInstance] drives, so each algorithm is picked once, at construction. */
internal interface StreamingDigest {
    fun update(array: ByteArray)

    fun update(byte: Byte)

    /** Returns the digest and resets for the next message, like JCA's `MessageDigest.digest()`. */
    fun digest(): ByteArray
}

/** The pure-Kotlin digests every native target can fall back on. */
internal fun kotlinStreamingDigest(algorithm: String): StreamingDigest =
    when (algorithm) {
        "SHA-256" -> ShaStreamingDigest(Sha256Digest())

        "SHA-1" -> ShaStreamingDigest(Sha1Digest())

        "ripemd160" -> Ripemd160StreamingDigest()

        // Adding this below as a reminder.
        "keccak256" -> error("KECCAK-256 is not yet supported.")

        else -> error("This message digest is not supported.")
    }

private class ShaStreamingDigest(
    private val sha: ShaDigest,
) : StreamingDigest {
    override fun update(array: ByteArray) = sha.update(array)

    override fun update(byte: Byte) = sha.update(byte)

    override fun digest(): ByteArray = sha.digest()
}

private class Ripemd160StreamingDigest : StreamingDigest {
    private val ripemd = RIPEMD160Digest()

    override fun update(array: ByteArray) = ripemd.update(array, 0, array.size)

    override fun update(byte: Byte) = ripemd.update(byte)

    override fun digest(): ByteArray = ByteArray(ripemd.getDigestSize()).also { ripemd.doFinal(it, 0) }
}
