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
 * Outbound send buffer for one direction of a QUIC stream (or for the
 * per-encryption-level CRYPTO offset stream).
 *
 * Application code [enqueue]s payload bytes; the connection's send loop
 * [takeChunk]s as much as it can fit in the next packet, given the
 * remaining packet budget and stream-level / connection-level flow control
 * credit. Bytes are *retained* in the buffer (RFC 9000 §13.3 — STREAM data
 * is reliable) until [markAcked] removes them, so a [markLost] call can
 * re-queue the same byte range for retransmit.
 *
 * # Three logical regions of the byte sequence
 *
 * The buffer covers the offset range `[flushedFloor, nextOffset)`. Within
 * that range each byte is in exactly one of three states:
 *
 *   1. **In-flight** — sent but not yet ACK'd. Tracked in [inFlight] as
 *      a sorted-by-offset list of [Range]s. ACKs remove from here; loss
 *      moves into the retransmit queue.
 *   2. **Needs retransmit** — declared lost; re-sent before any fresh
 *      bytes. Tracked in [retransmit] as a FIFO of [Range]s.
 *   3. **Unsent** — `[nextSendOffset, nextOffset)`. New bytes the writer
 *      hasn't picked up yet. [takeChunk] drains in priority order:
 *      retransmit first, then fresh.
 *
 * # Compaction
 *
 * When `[flushedFloor, x)` is contiguously ACK'd we advance [flushedFloor]
 * and shift the underlying byte storage. The structure never grows
 * unboundedly under normal traffic — bytes are released as soon as the
 * peer ACKs them.
 *
 * # Concurrency
 *
 * [enqueue] / [finish] run on application coroutines (e.g. WebTransport
 * stream writers in [com.vitorpamplona.quic.webtransport.WtPeerStreamDemux]);
 * [takeChunk] runs on the [com.vitorpamplona.quic.connection.QuicConnectionDriver]
 * send loop under the connection mutex; [markAcked] / [markLost] run on
 * the parser path also under the connection mutex. The two execution
 * paths are NOT serialised by a shared lock, so all internal state is
 * mutated under `synchronized(this)`. Single-field reads
 * ([nextOffset], [sentOffset], [finPending], [finSent], [finAcked])
 * use `@Volatile` backing fields and bypass the monitor — they cannot
 * tear (Boolean is single-byte; Long writes happen inside the
 * synchronized block on JVM/Android, where `@Volatile Long` is
 * atomic). [readableBytes] still synchronizes because its formula
 * combines two fields and would otherwise observe a transient
 * negative value if read mid-`takeChunk`.
 *
 * # FIN
 *
 * The FIN bit is part of the reliable byte sequence per RFC 9000 §3.3.
 * Treated as a "virtual byte" at offset = `nextOffset`: setting
 * [finPending] arms the next [takeChunk] to attach FIN to the final
 * data chunk (or emit an empty FIN-only chunk if the buffer is already
 * drained). [markAcked] / [markLost] respect FIN — a lost range that
 * carried FIN is re-sent with FIN set, and the buffer's "FIN delivered"
 * latch ([finAcked]) only flips when the FIN-carrying range is ACK'd.
 *
 * # Best-effort streams
 *
 * Setting [bestEffort] = true changes [markLost] semantics: lost
 * ranges are dropped without being re-queued for retransmit, the FIN
 * bit (if present in a lost range) is not re-emitted, and the
 * underlying byte storage compacts as if the range had been ACK'd.
 * This is the moq-lite group-stream case — Opus audio frames
 * arriving 200 ms late are worse than useless. The peer will see a
 * truncated stream; that's expected and the moq-lite layer relies on
 * its own per-stream timeouts. Default is false (reliable per RFC
 * 9000 §3.5).
 */
class SendBuffer(
    val bestEffort: Boolean = false,
) {
    /**
     * Contiguous byte storage covering `[flushedFloor, nextOffset)`.
     * Indexing: byte at logical offset `o` lives at `data[(o - flushedFloor).toInt()]`.
     * Capacity grows on enqueue; the front end shifts on
     * [advanceFlushedFloorIfPossible] to release ACK'd-from-the-bottom
     * bytes.
     */
    private var data: ByteArray = ByteArray(64)

    /** `nextOffset - flushedFloor` — bytes currently held in [data]. */
    private var dataLen: Int = 0

    /** Logical offset of the byte at `data[0]`. Advances on contiguous ACK. */
    private var flushedFloor: Long = 0L

    /** Logical offset just past the last byte. Advances on [enqueue]. */
    @Volatile
    private var _nextOffset: Long = 0L

    /**
     * Logical offset of the next FRESH byte the writer would send if
     * the [retransmit] queue is empty. Bytes `[nextSendOffset, nextOffset)`
     * are unsent; bytes below that are either in-flight, in retransmit,
     * or already ACK'd (released).
     *
     * Invariant: `flushedFloor <= nextSendOffset <= nextOffset`.
     */
    @Volatile
    private var nextSendOffset: Long = 0L

    /**
     * Sent-but-not-yet-ACK'd ranges, sorted by offset ascending. Mutated
     * by [takeChunk] (append), [markAcked] (remove / split), [markLost]
     * (remove and move to [retransmit]).
     *
     * Overlap lookup uses [firstOverlapIndex] (binary search, O(log N));
     * the early-exit forward walk visits only the k entries that actually
     * overlap. A single ACK or loss notification therefore costs
     * O(log N + k), with k typically 1-2 for a well-aligned ACK range.
     */
    private val inFlight: ArrayDeque<Range> = ArrayDeque()

    /**
     * FIFO queue of ranges declared lost and awaiting re-emission.
     * [takeChunk] drains from the front before touching fresh bytes.
     * Same byte data lives in [data] — only the metadata is duplicated.
     */
    private val retransmit: ArrayDeque<Range> = ArrayDeque()

    /**
     * Cached `sum(retransmit[].length)` so [readableBytes] can return
     * in O(1) instead of walking the deque on every call. The writer's
     * pre-flight "anything to send?" check runs per-stream per-drain
     * (~50 drains/sec × N streams), so an O(R) per-call cost on lossy
     * paths with deep retransmit queues was meaningful CPU.
     *
     * Updated in lockstep with every [retransmit] add/remove path:
     * retransmit.addLast / addFirst / removeFirst, and
     * [removeOverlap]'s mid-walk add/remove during ACK processing.
     * The invariant `retransmitTotalBytes == retransmit.sumOf { it.length }`
     * holds whenever no [SendBuffer] method is mid-mutation.
     */
    private var retransmitTotalBytes: Long = 0L

    @Volatile
    private var _finPending: Boolean = false

    @Volatile
    private var _finSent: Boolean = false

    @Volatile
    private var _finAcked: Boolean = false

    val nextOffset: Long get() = _nextOffset
    val finPending: Boolean get() = _finPending
    val finSent: Boolean get() = _finSent
    val finAcked: Boolean get() = _finAcked

    /**
     * Bytes the writer would emit on the next [takeChunk] before any
     * flow-control limits. Includes both retransmit-queued bytes (which
     * have priority) and unsent-fresh bytes. Used by the writer's
     * pre-flight skip check ("nothing to send → continue").
     */
    val readableBytes: Int
        get() =
            synchronized(this) {
                val sum = retransmitTotalBytes + (_nextOffset - nextSendOffset)
                sum.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }

    /**
     * High-water mark of bytes ever handed to [takeChunk]. Equal to
     * [_nextOffset] only when all bytes have been at least sent once.
     * Used by the writer's connection-level flow-control accounting.
     *
     * Note: under retain-until-ACK semantics, the same byte may be
     * "sent" multiple times across retransmits. [sentOffset] reports
     * the high-water mark of *fresh* sends only, not the cumulative
     * retransmit volume.
     */
    val sentOffset: Long get() = nextSendOffset

    fun enqueue(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        synchronized(this) {
            ensureCapacity(dataLen + bytes.size)
            bytes.copyInto(data, dataLen)
            dataLen += bytes.size
            _nextOffset += bytes.size
        }
    }

    /** Mark the write side as closing; the next [takeChunk] sets FIN once empty. */
    fun finish() {
        synchronized(this) { _finPending = true }
    }

    /**
     * Take up to [maxBytes] bytes off the head of the next available
     * range. Priority order:
     *
     *   1. [retransmit] queue — retain offset semantics; the chunk's
     *      offset is the original lost range's offset.
     *   2. Fresh unsent bytes from `[nextSendOffset, nextOffset)`.
     *   3. FIN-only zero-byte chunk if [finPending] and everything
     *      else is drained.
     *
     * Returns null if there's nothing to send.
     */
    fun takeChunk(maxBytes: Int): Chunk? =
        synchronized(this) {
            val cap = maxBytes.coerceAtLeast(0)

            // 1. Retransmit queue first.
            val retransmitHead = retransmit.firstOrNull()
            if (retransmitHead != null) {
                if (cap == 0 && retransmitHead.length > 0L) return@synchronized null
                val take = minOf(retransmitHead.length, cap.toLong())
                val payload = sliceAt(retransmitHead.offset, take.toInt())
                retransmit.removeFirst()
                retransmitTotalBytes -= retransmitHead.length
                val fin = retransmitHead.fin && take == retransmitHead.length
                if (take < retransmitHead.length) {
                    // Push remainder back at the head, preserving offset.
                    val remainder =
                        Range(
                            offset = retransmitHead.offset + take,
                            length = retransmitHead.length - take,
                            fin = retransmitHead.fin,
                        )
                    retransmit.addFirst(remainder)
                    retransmitTotalBytes += remainder.length
                }
                addToInFlight(Range(retransmitHead.offset, take, fin))
                if (fin) _finSent = true
                return@synchronized Chunk(retransmitHead.offset, payload, fin, isRetransmit = true)
            }

            // 2. Fresh bytes.
            val freshAvailable = _nextOffset - nextSendOffset
            if (freshAvailable > 0L) {
                if (cap == 0) return@synchronized null
                val take = minOf(freshAvailable, cap.toLong())
                val offset = nextSendOffset
                val payload = sliceAt(offset, take.toInt())
                nextSendOffset += take
                val finForThis = _finPending && !_finSent && nextSendOffset == _nextOffset
                addToInFlight(Range(offset, take, finForThis))
                if (finForThis) _finSent = true
                return@synchronized Chunk(offset, payload, finForThis, isRetransmit = false)
            }

            // 3. FIN-only.
            if (_finPending && !_finSent) {
                _finSent = true
                addToInFlight(Range(nextSendOffset, 0L, true))
                return@synchronized Chunk(nextSendOffset, ByteArray(0), true, isRetransmit = false)
            }
            null
        }

    /**
     * Mark the byte range `[offset, offset + length)` as ACK'd by the
     * peer. The range may cover one or several entries in [inFlight];
     * each is split where needed and the covered portion removed. If
     * the contiguous low end of the buffer becomes fully ACK'd, the
     * underlying byte storage is shifted forward (releasing memory).
     *
     * `length == 0` is interpreted as a FIN-only ACK at [offset]. The
     * matching FIN-bearing in-flight range is removed and [finAcked]
     * latches true.
     */
    fun markAcked(
        offset: Long,
        length: Long,
    ) {
        synchronized(this) {
            removeOverlap(inFlight, offset, length, OverlapAction.ACK)
            advanceFlushedFloorIfPossible()
        }
    }

    /**
     * Mark the byte range `[offset, offset + length)` as lost — re-queue
     * it for retransmit. The range may cover one or several entries in
     * [inFlight]; each is split where needed and the covered portion
     * appended to [retransmit]. If [fin] is true, the FIN bit is forced
     * to re-send (clears [finSent] so [takeChunk] will re-emit the FIN
     * on the retransmit chunk).
     *
     * Idempotent: calling with a range already in retransmit (or
     * already ACK'd) is a no-op. The dispatcher in
     * [com.vitorpamplona.quic.connection.QuicConnection.onTokensLost] may
     * call this with stale offsets after compaction; we silently absorb.
     */
    fun markLost(
        offset: Long,
        length: Long,
        fin: Boolean,
    ) {
        synchronized(this) {
            // Bytes already released (below flushedFloor) are gone for
            // good — by definition they were ACK'd, so there's nothing
            // to retransmit. Clamp the requested range to the retained
            // window to keep the operation idempotent.
            if (offset + length <= flushedFloor) {
                if (fin && !_finAcked && !bestEffort) _finSent = false
                return
            }
            val clampedOffset = maxOf(offset, flushedFloor)
            val clampedLength = (offset + length) - clampedOffset
            if (bestEffort) {
                // Drop without retransmit (see class kdoc). Bytes vanish
                // from inFlight and the data buffer can compact, the
                // same way an ACK would. The FIN flag intentionally
                // stays as `_finSent = true` — best-effort means we
                // don't try to re-emit FIN either; the peer either saw
                // it on the original wire or it's lost forever.
                removeOverlap(inFlight, clampedOffset, clampedLength, OverlapAction.DROP)
                advanceFlushedFloorIfPossible()
            } else {
                removeOverlap(inFlight, clampedOffset, clampedLength, OverlapAction.RETRANSMIT)
                if (fin && !_finAcked) _finSent = false
            }
        }
    }

    /**
     * Re-queue every byte range currently sent-but-not-yet-ACK'd for
     * retransmission. The writer's next [takeChunk] drains the
     * retransmit queue before any fresh bytes, at the original
     * offsets — so the peer sees the same CRYPTO/STREAM bytes again
     * with the same offset, only at a new packet number.
     *
     * Used by the RFC 9002 §6.2.4 PTO probe path: when the timer
     * fires before the handshake completes, the client MUST
     * retransmit the unacknowledged ClientHello (the CRYPTO bytes
     * sitting in [inFlight] for the Initial level). A PING alone
     * isn't enough — the peer may never have seen the original
     * datagram and therefore has no state to correlate the PING
     * against.
     *
     * Idempotent: ranges already in [retransmit] are not affected
     * (only [inFlight] is walked); calling twice in a row is a no-op
     * the second time because the first call moved everything out of
     * [inFlight]. FIN bit is preserved per range — a lost FIN-bearing
     * range will re-emit FIN.
     *
     * Best-effort buffers ([bestEffort] = true) drop the inflight
     * ranges instead of retransmitting them, matching [markLost]'s
     * semantics. The data buffer can compact as if those ranges had
     * been ACK'd.
     */
    fun requeueAllInflight() {
        synchronized(this) {
            if (inFlight.isEmpty()) return
            if (bestEffort) {
                inFlight.clear()
                advanceFlushedFloorIfPossible()
                return
            }
            // Move every inflight range to the retransmit queue,
            // preserving offset order (inFlight is sorted by offset
            // ascending). Coalesce adjacent ranges on insert: if the
            // previous tail entry's `offset + length` equals the
            // current's `offset`, merge them into a single range. The
            // PTO probe path otherwise emits one tiny STREAM frame per
            // original-packet boundary instead of replaying the
            // contiguous bytes as a single chunk, fragmenting the
            // probe across N small frames + N AEAD seals + N writes
            // when one frame would do. Coalescing across the FIN bit
            // is gated — only merge when the previous tail had no
            // FIN, otherwise the FIN's implicit final-size invariant
            // could shift.
            for (r in inFlight) {
                if (r.fin && !_finAcked) _finSent = false
                val tail = retransmit.lastOrNull()
                if (tail != null && !tail.fin && tail.offset + tail.length == r.offset) {
                    // Merge: extend the tail to cover [tail.offset, r.endOffset).
                    val merged = Range(tail.offset, tail.length + r.length, r.fin)
                    retransmit.removeLast()
                    retransmit.addLast(merged)
                } else {
                    retransmit.addLast(r)
                }
                retransmitTotalBytes += r.length
            }
            inFlight.clear()
        }
    }

    /**
     * Disposition for a range that overlapped a [markAcked] / [markLost]
     * range. ACK drops the range and latches `_finAcked` if the FIN
     * was covered. RETRANSMIT moves the range to the retransmit queue
     * for re-emission. DROP just removes the range, used by best-effort
     * streams where retransmit would deliver stale data.
     */
    private enum class OverlapAction { ACK, RETRANSMIT, DROP }

    /**
     * Walk [list] for any range overlapping `[offset, offset + length)`,
     * remove the overlapping portion, and dispose of it per [action]:
     * ACK latches `_finAcked` if FIN was covered; RETRANSMIT moves the
     * covered range to the retransmit queue; DROP just removes it.
     * Splits ranges where the overlap is partial.
     */
    private fun removeOverlap(
        list: ArrayDeque<Range>,
        offset: Long,
        length: Long,
        action: OverlapAction,
    ) {
        // length == 0 only meaningful for FIN-only ranges; handle by
        // matching the exact-offset zero-length range.
        if (length == 0L) {
            // Binary-search for offset; the FIN-only range, if present,
            // sits at the position whose offset equals the requested
            // offset. Multiple zero-length ranges at the same offset are
            // disallowed by [addToInFlight]'s sort invariant — at most
            // one FIN-only range exists per buffer.
            val startIndex = firstOverlapIndex(list, offset)
            if (startIndex < list.size) {
                val r = list[startIndex]
                if (r.offset == offset && r.length == 0L) {
                    list.removeAt(startIndex)
                    when (action) {
                        OverlapAction.ACK -> {
                            if (r.fin) _finAcked = true
                        }

                        OverlapAction.RETRANSMIT -> {
                            retransmit.addLast(r)
                            retransmitTotalBytes += r.length
                        }

                        OverlapAction.DROP -> {} // discard
                    }
                }
            }
            return
        }
        val rangeEnd = offset + length
        // Find the first list entry whose end-offset is > the requested
        // start (i.e. the leftmost potential overlap). All earlier
        // entries fall entirely below the request and are untouched —
        // we skip them in O(log N) instead of the prior O(N) full scan.
        var startIndex = firstOverlapIndex(list, offset)
        if (startIndex >= list.size) return

        // Walk forward. Two outputs from this loop:
        //   - dropCount: number of consecutive entries (starting at
        //     startIndex) we need to remove via a single subList clear,
        //     instead of O(N²) per-element removeAt.
        //   - replacements: kept-pieces (left and right of an overlap
        //     that doesn't fully cover an entry) re-inserted at the
        //     end. Sort-stable by offset since we process entries in
        //     ascending order.
        val replacements = ArrayList<Range>(2)
        var endIndex = startIndex
        while (endIndex < list.size) {
            val r = list[endIndex]
            // Sorted invariant: once r.offset >= rangeEnd, no later
            // entry overlaps. Early-exit before the prior O(N) scan
            // would have continued.
            if (r.offset >= rangeEnd) break
            val rEnd = r.offset + r.length
            // r.offset < rangeEnd guarantees overlap candidacy; rule
            // out the case where r ends exactly at offset.
            if (rEnd <= offset) {
                // Should not happen given firstOverlapIndex semantics,
                // but defensively skip.
                endIndex += 1
                continue
            }
            val coveredStart = if (r.offset > offset) r.offset else offset
            val coveredEnd = if (rEnd < rangeEnd) rEnd else rangeEnd
            val coveredLen = coveredEnd - coveredStart
            if (coveredStart > r.offset) {
                replacements +=
                    Range(
                        offset = r.offset,
                        length = coveredStart - r.offset,
                        // FIN belongs to the rightmost covered piece;
                        // a left-kept piece never carries FIN.
                        fin = false,
                    )
            }
            if (coveredEnd < rEnd) {
                replacements +=
                    Range(
                        offset = coveredEnd,
                        length = rEnd - coveredEnd,
                        fin = r.fin,
                    )
            }
            if (coveredLen > 0L || r.length == 0L) {
                val coveredFin = r.fin && coveredEnd == rEnd
                when (action) {
                    OverlapAction.ACK -> {
                        if (coveredFin) _finAcked = true
                    }

                    OverlapAction.RETRANSMIT -> {
                        retransmit.addLast(
                            Range(
                                offset = coveredStart,
                                length = coveredLen,
                                fin = coveredFin,
                            ),
                        )
                        retransmitTotalBytes += coveredLen
                    }

                    OverlapAction.DROP -> {} // discard the covered piece
                }
            }
            endIndex += 1
        }
        // Remove the contiguous overlap span [startIndex, endIndex).
        // ArrayDeque has no bulk-clear-range; each removeAt(i) shifts
        // (size - i) elements, so total cost is O(k * (size - end + k))
        // worst case. Backward iteration shrinks the per-step shift —
        // negligible for our workload (k is 1 in steady state, occasionally
        // 2 across a partial-overlap split).
        if (endIndex > startIndex) {
            var i = endIndex - 1
            while (i >= startIndex) {
                list.removeAt(i)
                i -= 1
            }
        }
        // Re-insert kept pieces. Both pieces (left + right) for a single
        // overlap are themselves in offset-ascending order because
        // they're emitted left-then-right per loop iteration. Across
        // multiple processed overlaps they remain sorted (each overlap's
        // left-piece sits below the next overlap's left-piece). So no
        // explicit sort needed.
        for (r in replacements) addToInFlight(r)
    }

    /**
     * Binary-search [list] for the index of the first entry whose
     * end-offset (`offset + length`) is strictly greater than
     * [targetOffset]. All entries strictly before that index end
     * at-or-before [targetOffset] and therefore cannot overlap any
     * range starting at [targetOffset]. The binary search runs in
     * O(log N).
     *
     * Returns `list.size` when every entry ends at-or-before
     * [targetOffset] — meaning no overlap exists.
     */
    private fun firstOverlapIndex(
        list: ArrayDeque<Range>,
        targetOffset: Long,
    ): Int {
        // Standard lower-bound-style binary search adapted to "first
        // entry whose end > targetOffset". Entries are kept sorted by
        // start offset (sort invariant of [addToInFlight]); since
        // ranges within the list don't overlap each other, "sorted by
        // start" implies "sorted by end" too.
        var lo = 0
        var hi = list.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            val midEnd = list[mid].offset + list[mid].length
            if (midEnd > targetOffset) hi = mid else lo = mid + 1
        }
        return lo
    }

    /**
     * Insert [r] into [inFlight] preserving offset-ascending order.
     * Append is the common case (writer drains fresh bytes in offset
     * order); when an in-the-middle insert is needed (after a partial
     * overlap split), uses binary search to find the position in
     * O(log N) instead of the prior linear walk.
     */
    private fun addToInFlight(r: Range) {
        // Append fast-path: empty list, or new range starts at-or-after
        // the current tail's start offset (i.e. it's the new max).
        if (inFlight.isEmpty() || inFlight.last().offset <= r.offset) {
            inFlight.addLast(r)
            return
        }
        // Otherwise binary-search for the first index where the existing
        // entry's offset >= r.offset, and insert before it.
        var lo = 0
        var hi = inFlight.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (inFlight[mid].offset < r.offset) lo = mid + 1 else hi = mid
        }
        inFlight.add(lo, r)
    }

    /**
     * If `[flushedFloor, x)` is contiguously ACK'd (no entry in
     * [inFlight] or [retransmit] starts at or below `flushedFloor`),
     * advance [flushedFloor] up to the lowest in-flight or
     * retransmit-queued offset, and shift [data] forward by the same
     * amount.
     */
    private fun advanceFlushedFloorIfPossible() {
        val lowestInFlight = inFlight.firstOrNull()?.offset ?: _nextOffset
        val lowestRetransmit = retransmit.minOfOrNull { it.offset } ?: _nextOffset
        val lowest = minOf(lowestInFlight, lowestRetransmit, nextSendOffset)
        val advance = lowest - flushedFloor
        if (advance <= 0L) return
        val advanceInt = advance.toInt()
        // Shift [data] forward.
        if (advanceInt < dataLen) {
            data.copyInto(
                destination = data,
                destinationOffset = 0,
                startIndex = advanceInt,
                endIndex = dataLen,
            )
        }
        dataLen -= advanceInt
        flushedFloor += advance
        // Shrink the backing buffer when a transient burst has been fully
        // drained. Without this, a stream that ever held N bytes pins
        // `data.size = N` for the rest of the connection — long-tail
        // memory retention. Trigger when:
        //   * the buffer is large enough to bother (above the doubling
        //     floor of 64 bytes) AND
        //   * live data fits in 1/4 of the allocation (capacity is
        //     ≥ 4× live size).
        // Shrink to twice the live size (or [SHRINK_FLOOR_BYTES],
        // whichever is larger) to leave headroom for the next push
        // without re-doubling immediately.
        if (data.size > SHRINK_FLOOR_BYTES && dataLen * 4 < data.size) {
            val newCap = maxOf(SHRINK_FLOOR_BYTES, dataLen * 2)
            if (newCap < data.size) {
                data = data.copyOf(newCap)
            }
        }
    }

    /**
     * Slice [length] bytes starting at logical offset [offset].
     * [offset] must lie in `[flushedFloor, nextOffset)`.
     */
    private fun sliceAt(
        offset: Long,
        length: Int,
    ): ByteArray {
        if (length == 0) return ByteArray(0)
        val pos = (offset - flushedFloor).toInt()
        return data.copyOfRange(pos, pos + length)
    }

    private fun ensureCapacity(needed: Int) {
        if (data.size < needed) {
            var newCap = if (data.size == 0) 64 else data.size
            while (newCap < needed) newCap *= 2
            data = data.copyOf(newCap)
        }
    }

    private companion object {
        /**
         * Backing-buffer floor below which [advanceFlushedFloorIfPossible]
         * does NOT shrink. Below this size the doubling-on-grow is so
         * cheap that shrinking would be a wash; above it, transient
         * bursts that bloat the buffer to MiBs leave a long-tail
         * memory footprint we want to release.
         */
        const val SHRINK_FLOOR_BYTES: Int = 4096
    }

    /**
     * One contiguous offset range tracked by the buffer's bookkeeping.
     * [length] is `Long` to match QUIC's offset arithmetic — practical
     * range sizes fit in Int, but the offset field naturally is.
     */
    private data class Range(
        val offset: Long,
        val length: Long,
        val fin: Boolean,
    )

    /**
     * One emit-able STREAM chunk. [isRetransmit] distinguishes a chunk
     * pulled off the retransmit queue (path 1 of [takeChunk]) from a
     * fresh-bytes emission (path 2/3). Connection-level flow control
     * (RFC 9000 §4.1) caps cumulative *new* bytes — retransmits MUST
     * NOT consume additional credit, so the writer reads this flag and
     * skips `sendConnectionFlowConsumed += data.size` when it's true.
     * Without the distinction a single round of loss recovery on a
     * long stream eventually exhausts credit and stalls the connection.
     */
    data class Chunk(
        val offset: Long,
        val data: ByteArray,
        val fin: Boolean,
        val isRetransmit: Boolean = false,
    ) {
        // ByteArray needs explicit equality.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Chunk) return false
            return offset == other.offset &&
                fin == other.fin &&
                isRetransmit == other.isRetransmit &&
                data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = offset.hashCode()
            result = 31 * result + fin.hashCode()
            result = 31 * result + isRetransmit.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
}
