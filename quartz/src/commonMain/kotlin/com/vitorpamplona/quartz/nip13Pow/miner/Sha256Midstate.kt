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
package com.vitorpamplona.quartz.nip13Pow.miner

import com.vitorpamplona.quartz.utils.sha2.Sha256Compression

/**
 * SHA-256 split at a block boundary, so a miner hashes only the part of the
 * message it changes.
 *
 * SHA-256 eats the message in 64-byte blocks, and the state after a block depends
 * only on the blocks before it. When the nonce starts at [changesFrom], every
 * block before the one holding it is the same for every candidate: they are
 * compressed once, here, into [midstate]. A candidate then costs only the blocks
 * from the nonce's block to the end, padding included, instead of the whole
 * serialized event.
 *
 * The miner writes candidates straight into [tail], which holds the message from
 * [prefixLength] on with the SHA-256 padding already appended: byte `i` of the
 * message is `tail[i - prefixLength]`.
 *
 * Pure Kotlin on purpose: MessageDigest has no API to resume from a saved state
 * (cloning one allocates per candidate).
 */
class Sha256Midstate(
    message: ByteArray,
    changesFrom: Int,
) {
    /** Bytes of the message folded into [midstate]: whole blocks before [changesFrom]. */
    val prefixLength = (changesFrom / BLOCK) * BLOCK

    /** The message from [prefixLength] to its end, plus the SHA-256 padding. */
    val tail: ByteArray

    private val midstate = IntArray(8).also { Sha256Compression.initState(it) }
    private val state = IntArray(8)
    private val w = IntArray(64)

    init {
        require(changesFrom in 0..message.size) { "changesFrom must be in 0..${message.size}, was $changesFrom" }

        var offset = 0
        while (offset < prefixLength) {
            Sha256Compression.compress(midstate, message, offset, w)
            offset += BLOCK
        }

        val remaining = message.size - prefixLength
        // 0x80 marker + 8-byte length, rounded up to whole blocks
        val tailLength = ((remaining + 9 + BLOCK - 1) / BLOCK) * BLOCK
        tail = ByteArray(tailLength)
        message.copyInto(tail, 0, prefixLength, message.size)
        tail[remaining] = 0x80.toByte()
        val bitLength = message.size.toLong() * 8
        for (i in 0 until 8) {
            tail[tailLength - 1 - i] = (bitLength ushr (8 * i)).toByte()
        }
    }

    /** Hashes the current [tail] and leaves the digest words in [state]. */
    private fun hashTail() {
        midstate.copyInto(state)
        val bytes = tail
        var offset = 0
        while (offset < bytes.size) {
            Sha256Compression.compress(state, bytes, offset, w)
            offset += BLOCK
        }
    }

    /**
     * True when the digest of the current [tail] starts with at least [minBits]
     * zero bits. Reads the state words directly: the digest is those words in
     * big-endian order, so no 32-byte digest is ever materialized.
     */
    fun hasLeadingZeroBits(minBits: Int): Boolean {
        hashTail()
        val s = state
        var needed = minBits
        var i = 0
        while (needed > 0) {
            val word = s[i]
            if (needed <= 32) return word.countLeadingZeroBits() >= needed
            if (word != 0) return false
            needed -= 32
            i++
        }
        return true
    }

    /** Writes the 32-byte digest of the current [tail] into [out]. */
    fun digestInto(out: ByteArray): ByteArray {
        hashTail()
        for (i in 0 until 8) {
            val word = state[i]
            out[4 * i] = (word ushr 24).toByte()
            out[4 * i + 1] = (word ushr 16).toByte()
            out[4 * i + 2] = (word ushr 8).toByte()
            out[4 * i + 3] = word.toByte()
        }
        return out
    }

    companion object {
        const val BLOCK = Sha256Compression.BLOCK
    }
}
