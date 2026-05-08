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
 * stores them as **non-overlapping, sorted-by-offset segments** without
 * eager coalescing — the contiguous prefix is collected at [readContiguous]
 * time. Pre-fix the buffer coalesced overlapping segments on EVERY [insert]
 * by allocating a fresh merged `ByteArray((hi - lo).toInt())` and copying
 * each existing segment into it; under a 200-chunk reorder burst that's
 * O(N²) bytes copied. The new layout is O(1) amortized per [insert] for
 * the common cases (adjacent or non-overlapping) and only allocates at
 * read time, when the consumer is ready to pull the bytes.
 *
 * Invariants on [chunks]:
 *  * Sorted strictly ascending by `offset`.
 *  * No two segments overlap, but adjacent segments (one ending at the
 *    next's start) are allowed.
 *  * No segment lives below [readOffset].
 *
 * For a fully streamed CRYPTO transcript the consumer just reads contiguous
 * bytes whenever new data arrives.
 */
class ReceiveBuffer {
    /** Sorted ascending by [Chunk.offset]; non-overlapping. See class kdoc. */
    private val chunks = ArrayDeque<Chunk>()

    /** Cached `chunks.sumOf { data.size }` so [bufferedAhead] is O(1). */
    private var bufferedAheadBytes: Long = 0L

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

    /**
     * Insert a chunk at [offset] of size [data.size]. Idempotent on overlap.
     *
     * Returns [InsertResult.OK] for a well-formed frame, or one of the
     * RFC 9000 §4.5 final-size error variants when the peer's frame is
     * inconsistent with state we've already accepted. The caller (the
     * QUIC parser) is expected to translate these into a connection
     * close with `FINAL_SIZE_ERROR`. Pre-fix the buffer silently
     * dropped conflicting FINs and accepted post-FIN extension data,
     * letting a buggy peer drift state without termination.
     */
    fun insert(
        offset: Long,
        data: ByteArray,
        fin: Boolean = false,
    ): InsertResult {
        if (data.isEmpty() && !fin) return InsertResult.OK
        val frameEnd = offset + data.size
        // RFC 9000 §4.5: once a final size is established, no STREAM
        // frame may extend past it, and a second FIN must agree.
        finOffset?.let { existing ->
            if (fin && frameEnd != existing) {
                return InsertResult.FIN_CONFLICTS_WITH_PRIOR_FIN
            }
            if (frameEnd > existing) {
                return InsertResult.OFFSET_PAST_FIN
            }
        }
        if (fin) {
            finReceived = true
            if (finOffset == null) finOffset = frameEnd
        }
        if (data.isEmpty()) return InsertResult.OK

        // Drop chunk parts already consumed by the reader.
        if (frameEnd <= readOffset) return InsertResult.OK
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

        insertNonOverlapping(effOffset, effData)
        return InsertResult.OK
    }

    /**
     * Insert [data] starting at [start] into [chunks], trimming portions
     * that already exist (retransmit / overlap). The result preserves the
     * non-overlapping, sorted invariants. No allocation when the new
     * range is fully covered (retransmit no-op) or when it doesn't
     * touch any existing segment (single insert).
     */
    private fun insertNonOverlapping(
        start: Long,
        data: ByteArray,
    ) {
        val end = start + data.size
        // Locate the first existing chunk whose `endOffset > start` —
        // anything before it is strictly to the left of [start, end).
        var idx = lowerBoundEnd(start)
        var cursor = start

        while (cursor < end) {
            val existing = if (idx < chunks.size) chunks[idx] else null
            val existingStart = existing?.offset ?: Long.MAX_VALUE
            if (existingStart >= end) {
                // Tail (or whole new range) sits past every existing
                // segment that could overlap. Emit the remainder.
                emitSegment(start, data, cursor, end)
                cursor = end
            } else if (existingStart > cursor) {
                // Gap from [cursor, existingStart) — emit it, then skip
                // over the existing segment (it covers some of [start,end)).
                emitSegment(start, data, cursor, existingStart)
                cursor = minOf(existing!!.endOffset, end)
                idx++
            } else {
                // existingStart <= cursor — the existing segment covers
                // [cursor, existing.endOffset). Skip ahead.
                cursor = minOf(existing!!.endOffset, end)
                idx++
            }
        }
    }

    /**
     * Emit the slice of [src] covering absolute range `[segStart, segEnd)`
     * as a new chunk, inserted in sorted order. [src] starts at absolute
     * offset [srcStart], so the slice index is `(segStart - srcStart)`.
     */
    private fun emitSegment(
        srcStart: Long,
        src: ByteArray,
        segStart: Long,
        segEnd: Long,
    ) {
        if (segStart >= segEnd) return
        val from = (segStart - srcStart).toInt()
        val to = (segEnd - srcStart).toInt()
        val piece =
            if (from == 0 && to == src.size) {
                // Whole src is the segment; avoid the copyOfRange.
                src
            } else {
                src.copyOfRange(from, to)
            }
        // Insert in sorted position. The index from [lowerBoundOffset]
        // is monotonically non-decreasing across the walk in
        // [insertNonOverlapping] but we recompute defensively to keep
        // this helper self-contained.
        val insertAt = lowerBoundOffset(segStart)
        chunks.add(insertAt, Chunk(segStart, piece))
        bufferedAheadBytes += piece.size
    }

    /** Index of first chunk whose `endOffset > target`, or `chunks.size`. */
    private fun lowerBoundEnd(target: Long): Int {
        var lo = 0
        var hi = chunks.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (chunks[mid].endOffset > target) hi = mid else lo = mid + 1
        }
        return lo
    }

    /** Index of first chunk whose `offset >= target`, or `chunks.size`. */
    private fun lowerBoundOffset(target: Long): Int {
        var lo = 0
        var hi = chunks.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (chunks[mid].offset >= target) hi = mid else lo = mid + 1
        }
        return lo
    }

    /** Highest `offset + data.size` ever seen on this stream (whether or not contiguous). */
    fun highestObservedOffset(): Long {
        val finEnd = finOffset
        val bufEnd = if (chunks.isEmpty()) readOffset else chunks.last().endOffset
        return if (finEnd != null) maxOf(finEnd, bufEnd) else bufEnd
    }

    /** Result of an [insert] call — see RFC 9000 §4.5. */
    enum class InsertResult {
        /** Frame accepted (or harmlessly redundant). */
        OK,

        /**
         * The frame's `offset + data.size` exceeds the previously-established
         * final size. Caller MUST close with FINAL_SIZE_ERROR.
         */
        OFFSET_PAST_FIN,

        /**
         * A second FIN-bearing frame disagreed with the established final
         * size. Caller MUST close with FINAL_SIZE_ERROR.
         */
        FIN_CONFLICTS_WITH_PRIOR_FIN,
    }

    /**
     * Returns and consumes the contiguous bytes available starting from
     * [readOffset]. Walks every consecutive segment whose left edge meets
     * the current read frontier and returns them as a single concatenated
     * [ByteArray]. Returns an empty array if the next pending segment is
     * beyond the current frontier (gap) or the buffer is empty.
     *
     * Allocation: zero allocations when only one segment is consecutive
     * (the most common case — one inserted segment per call); a single
     * concat allocation when multiple segments stack up after a gap fill.
     */
    fun readContiguous(): ByteArray {
        if (chunks.isEmpty() || chunks.first().offset != readOffset) return EMPTY
        val first = chunks.removeFirst()
        bufferedAheadBytes -= first.data.size
        var cursor = first.endOffset
        // Fast-path: only one consecutive segment.
        if (chunks.isEmpty() || chunks.first().offset != cursor) {
            readOffset = cursor
            return first.data
        }
        // Multiple consecutive segments — collect, then concat once.
        val collected = mutableListOf(first)
        var total = first.data.size.toLong()
        while (chunks.isNotEmpty() && chunks.first().offset == cursor) {
            val next = chunks.removeFirst()
            bufferedAheadBytes -= next.data.size
            cursor = next.endOffset
            collected += next
            total += next.data.size
        }
        val out = ByteArray(total.toInt())
        var pos = 0
        for (c in collected) {
            c.data.copyInto(out, pos)
            pos += c.data.size
        }
        readOffset = cursor
        return out
    }

    /** Bytes already buffered and held back due to gaps. */
    fun bufferedAhead(): Long = bufferedAheadBytes

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
        val endOffset: Long get() = offset + data.size
    }

    private companion object {
        private val EMPTY = ByteArray(0)
    }
}
