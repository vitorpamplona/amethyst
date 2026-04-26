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
package com.vitorpamplona.quic.stream

/**
 * Out-of-order chunk reassembly for one direction of one QUIC stream
 * (or for the per-encryption-level CRYPTO offset stream).
 *
 * Chunks may arrive in any order with possibly overlapping ranges. The buffer
 * coalesces them into a single contiguous prefix that's available to the
 * consumer via [readContiguous]. We never expose bytes past the contiguous
 * frontier — gaps stall further reads until the missing offsets arrive.
 *
 * For a fully streamed CRYPTO transcript, the consumer just reads contiguous
 * bytes whenever new data arrives; the lookup is O(log N) on the gap tree.
 *
 * Implementation: we store raw chunks sorted by offset. Calls to [insert]
 * coalesce adjacent and overlapping ranges. [readContiguous] returns the
 * range from the current cursor up to the first gap.
 */
class ReceiveBuffer {
    private val chunks = mutableListOf<Chunk>() // sorted by offset, non-overlapping after insert
    var readOffset: Long = 0L
        private set

    /** True once a STREAM frame carrying FIN has been observed. */
    var finReceived: Boolean = false
        private set

    /**
     * Total stream length, set the moment any frame carrying FIN arrives.
     * Equals `offset + data.size` of the FIN-bearing frame; null until then.
     * Used by [isFullyRead] to distinguish "FIN seen but holes remain" from
     * "FIN seen and contiguous read frontier reached the end".
     */
    var finOffset: Long? = null
        private set

    /** Insert a chunk at [offset] of size [data.size]. Idempotent on overlap. */
    fun insert(
        offset: Long,
        data: ByteArray,
        fin: Boolean = false,
    ) {
        if (data.isEmpty() && !fin) return
        if (fin) {
            finReceived = true
            // The FIN flag carries an implicit final offset = offset + data.size.
            // RFC 9000 §4.5: once set, this MUST NOT change; ignore subsequent
            // FIN frames whose final size disagrees (they should already have
            // been rejected at the stream-state level, but be defensive here).
            val finalSize = offset + data.size
            if (finOffset == null) finOffset = finalSize
        }
        if (data.isEmpty()) return

        val end = offset + data.size
        // Drop chunk parts already consumed.
        if (end <= readOffset) return
        val effOffset: Long
        val effData: ByteArray
        if (offset < readOffset) {
            val dropFront = (readOffset - offset).toInt()
            effOffset = readOffset
            effData = data.copyOfRange(dropFront, data.size)
        } else {
            effOffset = offset
            effData = data
        }

        // Find the first chunk that's not strictly before the new range. The
        // boundary is `<=` so a perfectly adjacent prior chunk (its endOffset
        // equals our offset) is included in the merge — otherwise it would
        // stay as a separate adjacent chunk and bufferedAhead() would
        // overcount on perfectly-sequential receives starting at offset > 0.
        var startIdx = 0
        while (startIdx < chunks.size && chunks[startIdx].endOffset() < effOffset) startIdx++
        var endIdx = startIdx
        while (endIdx < chunks.size && chunks[endIdx].offset <= effOffset + effData.size) endIdx++
        // Also pull in the prior chunk if it's exactly adjacent on the lower end.
        if (startIdx > 0 && chunks[startIdx - 1].endOffset() == effOffset) startIdx -= 1

        if (startIdx == endIdx) {
            // No overlap — just insert.
            chunks.add(startIdx, Chunk(effOffset, effData))
            return
        }

        // Coalesce [startIdx, endIdx) plus the new chunk.
        var lo = effOffset
        var hi = effOffset + effData.size
        for (i in startIdx until endIdx) {
            lo = minOf(lo, chunks[i].offset)
            hi = maxOf(hi, chunks[i].endOffset())
        }
        val merged = ByteArray((hi - lo).toInt())
        for (i in startIdx until endIdx) {
            chunks[i].data.copyInto(merged, (chunks[i].offset - lo).toInt())
        }
        effData.copyInto(merged, (effOffset - lo).toInt())
        // Replace
        for (i in 1..(endIdx - startIdx)) chunks.removeAt(startIdx)
        chunks.add(startIdx, Chunk(lo, merged))
    }

    /** Returns and consumes the contiguous bytes available starting from [readOffset]. */
    fun readContiguous(): ByteArray {
        if (chunks.isEmpty()) return ByteArray(0)
        val first = chunks[0]
        if (first.offset != readOffset) return ByteArray(0)
        val data = first.data
        readOffset += data.size
        chunks.removeAt(0)
        return data
    }

    /** Bytes already buffered and held back due to gaps. */
    fun bufferedAhead(): Long = chunks.sumOf { it.data.size.toLong() }

    /** Highest contiguous offset received so far. */
    fun contiguousEnd(): Long = readOffset

    /**
     * True once the contiguous read frontier has reached the FIN offset, i.e.
     * the application has received every byte the sender ever sent. Closing
     * the consumer-facing channel before this point would silently drop any
     * later-arriving fill chunks — that's the audit-4 #4 bug.
     */
    fun isFullyRead(): Boolean = finReceived && chunks.isEmpty() && finOffset == readOffset

    private class Chunk(
        val offset: Long,
        val data: ByteArray,
    ) {
        fun endOffset() = offset + data.size
    }
}
