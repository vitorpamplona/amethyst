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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The parameters needed to fetch and decrypt a Marmot group's avatar image.
 *
 * Extracted from the group's [com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData]
 * so front ends can render the icon: the encrypted blob is content-addressed on Blossom by
 * [hash], and decrypted with [key]/[nonce] via
 * [com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupImageEncryption] (MIP-01 v2:
 * [key] is an HKDF seed).
 */
@Immutable
class MarmotGroupImage(
    /** SHA-256 (hex) of the encrypted blob — the Blossom content hash. */
    val hash: HexKey,
    /** 32-byte HKDF seed for the image AEAD key (MIP-01 v2). */
    val key: ByteArray,
    /** 12-byte ChaCha20-Poly1305 nonce. */
    val nonce: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MarmotGroupImage) return false
        return hash == other.hash &&
            key.contentEquals(other.key) &&
            nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int {
        var result = hash.hashCode()
        result = 31 * result + key.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        return result
    }
}
