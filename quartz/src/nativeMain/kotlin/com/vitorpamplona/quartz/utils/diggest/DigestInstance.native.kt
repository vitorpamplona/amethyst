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

actual class DigestInstance actual constructor(
    algorithm: String,
) {
    private val sha: ShaDigest? =
        when (algorithm) {
            "SHA-256" -> Sha256Digest()

            "SHA-1" -> Sha1Digest()

            "ripemd160" -> null

            // Adding this below as a reminder.
            "keccak256" -> error("KECCAK-256 is not yet supported.")

            else -> error("This message digest is not supported.")
        }

    private val ripemd = if (sha == null) RIPEMD160Digest() else null

    actual fun update(array: ByteArray) {
        if (sha != null) sha.update(array) else ripemd!!.update(array, 0, array.size)
    }

    actual fun update(byte: Byte) {
        if (sha != null) sha.update(byte) else ripemd!!.update(byte)
    }

    actual fun digest(): ByteArray {
        if (sha != null) return sha.digest()
        val r = ripemd!!
        return ByteArray(r.getDigestSize()).also { r.doFinal(it, 0) }
    }

    actual fun digest(input: ByteArray): ByteArray {
        update(input)
        return digest()
    }
}
