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
package com.vitorpamplona.quartz.concord.crypto

import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Edition-hash chain for Control Plane editions (CORD-04 §1).
 *
 * Every authority edition (metadata, role, channel, grant, banlist, …) has an
 * identity computed by [hash]. The next edition cites this value in its `ep` tag,
 * forming an unforgeable chain: clients refuse downgrades and fold to the highest
 * version with an intact chain.
 *
 * The preimage is fully domain-separated and every field is fixed-width or
 * length-prefixed, so distinct inputs can never collide:
 *
 * ```
 * len64(label) ‖ label ‖ eid[32] ‖ ver_be64 ‖ hasPrev(1) ‖ prev[32] ‖ len64(content) ‖ content
 * ```
 *
 * where `label` is the frozen [DOMAIN] string, all `len*`/`ver` fields are
 * big-endian unsigned 64-bit integers, `hasPrev` is `0x01`/`0x00`, `prev` is the
 * previous edition hash (32 zero bytes for the first edition), and `content` is
 * the **exact wire bytes** of the rumor content — never re-serialized.
 */
object EditionHash {
    /** Frozen domain-separation label, pinned to the Concord v2 reference client. */
    const val DOMAIN = "vector-community/v1/edition"

    private val ZERO_32 = ByteArray(32)

    fun hash(
        entityId: ByteArray,
        version: Long,
        prevHash: ByteArray?,
        content: ByteArray,
    ): ByteArray {
        val label = DOMAIN.encodeToByteArray()
        val prev = prevHash ?: ZERO_32
        require(entityId.size == 32) { "entityId must be 32 bytes, was ${entityId.size}" }
        require(prev.size == 32) { "prevHash must be 32 bytes, was ${prev.size}" }

        // 8 + label + 32 + 8 + 1 + 32 + 8 + content
        val preimage = ByteArray(8 + label.size + 32 + 8 + 1 + 32 + 8 + content.size)
        var pos = 0
        pos = writeBe64(preimage, pos, label.size.toLong())
        label.copyInto(preimage, pos)
        pos += label.size
        entityId.copyInto(preimage, pos)
        pos += 32
        pos = writeBe64(preimage, pos, version)
        preimage[pos] = if (prevHash != null) 0x01 else 0x00
        pos += 1
        prev.copyInto(preimage, pos)
        pos += 32
        pos = writeBe64(preimage, pos, content.size.toLong())
        content.copyInto(preimage, pos)

        return sha256(preimage)
    }

    /** Convenience overload that hashes the UTF-8 bytes of a [content] string. */
    fun hash(
        entityId: ByteArray,
        version: Long,
        prevHash: ByteArray?,
        content: String,
    ): ByteArray = hash(entityId, version, prevHash, content.encodeToByteArray())

    private fun writeBe64(
        out: ByteArray,
        offset: Int,
        value: Long,
    ): Int {
        ConcordKeyDerivation.writeBe64(out, offset, value)
        return offset + 8
    }
}
