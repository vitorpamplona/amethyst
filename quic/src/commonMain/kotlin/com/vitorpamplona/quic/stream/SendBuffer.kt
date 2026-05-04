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
 * credit.
 *
 * **Best-effort mode (no STREAM retransmit):** bytes are released from the
 * buffer the moment they're handed off, on the assumption that the
 * underlying network is stable. A real loss event silently truncates the
 * stream. Acceptable for MoQ over QUIC (audio rooms use OBJECT_DATAGRAM,
 * which is loss-tolerant; STREAM is control-plane only). See the deferred
 * items in `quic/plans/2026-04-26-quic-stack-status.md` — adding
 * retain-until-ACK + retransmit is the first thing to add for general
 * STREAM-heavy use.
 *
 * **Concurrency:** [enqueue] / [finish] are invoked by application
 * coroutines (e.g. WebTransport stream writers in [com.vitorpamplona.quic.webtransport.WtPeerStreamDemux]);
 * [takeChunk] runs on the [com.vitorpamplona.quic.connection.QuicConnectionDriver] send loop under the
 * connection mutex. The two paths are NOT serialised by a shared lock,
 * so the buffer's internal state (`chunks`, `pendingBytes`, `headOffset`,
 * FIN flags, offsets) is mutated under `synchronized(this)`. The cheap
 * `readableBytes` / `sentOffset` / `finPending` / `finSent` getters used
 * by the writer's pre-flight checks are also synchronised so they can't
 * read torn state. Without this, an `enqueue` racing a `takeChunk`
 * surfaced as `NoSuchElementException: ArrayDeque is empty` from
 * `chunks.first()` (the writer saw `pendingBytes > 0` after the
 * `addLast` but before the matching deque mutation became visible, so
 * it entered the head-peel branch and tripped on an empty deque).
 */
class SendBuffer {
    /**
     * Pending unsent chunks plus the offset within the head chunk. This avoids
     * the previous O(N) copyOf-per-enqueue: each enqueue is O(1), each
     * takeChunk peels at most one head chunk. Memory bounded by the sum of
     * outstanding writes.
     */
    private val chunks: ArrayDeque<ByteArray> = ArrayDeque()
    private var headOffset: Int = 0
    private var pendingBytes: Int = 0
    private var sentEnd: Long = 0L
    private var _nextOffset: Long = 0L
    private var _finPending: Boolean = false
    private var _finSent: Boolean = false

    val nextOffset: Long get() = synchronized(this) { _nextOffset }
    val finPending: Boolean get() = synchronized(this) { _finPending }
    val finSent: Boolean get() = synchronized(this) { _finSent }

    val readableBytes: Int get() = synchronized(this) { pendingBytes }

    /** Bytes already handed out via [takeChunk]; equal to the next offset to assign. */
    val sentOffset: Long get() = synchronized(this) { sentEnd }

    fun enqueue(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        synchronized(this) {
            chunks.addLast(bytes)
            pendingBytes += bytes.size
            _nextOffset += bytes.size
        }
    }

    /** Mark the write side as closing; the next [takeChunk] will set FIN once empty. */
    fun finish() {
        synchronized(this) { _finPending = true }
    }

    /** Take up to [maxBytes] bytes off the head of the buffer at the current send offset. */
    fun takeChunk(maxBytes: Int): Chunk? =
        synchronized(this) {
            if (pendingBytes == 0 && !(_finPending && !_finSent)) return@synchronized null
            val cap = maxBytes.coerceAtLeast(0)
            if (cap == 0 && pendingBytes > 0) return@synchronized null
            val data: ByteArray
            if (pendingBytes == 0) {
                data = ByteArray(0)
            } else {
                val head = chunks.first()
                val available = head.size - headOffset
                if (available <= cap) {
                    // Hand out the rest of the head chunk. Always copy: the caller's
                    // ByteArray (passed to enqueue) MUST stay opaque to the rest of
                    // the stack, since downstream encoders eventually pass it to
                    // AEAD.seal which assumes immutability for the duration of the
                    // encryption call.
                    data =
                        if (headOffset == 0 && head.size == available) {
                            head.copyOf()
                        } else {
                            head.copyOfRange(headOffset, head.size)
                        }
                    chunks.removeFirst()
                    headOffset = 0
                    pendingBytes -= available
                } else {
                    data = head.copyOfRange(headOffset, headOffset + cap)
                    headOffset += cap
                    pendingBytes -= cap
                }
            }
            val offset = sentEnd
            sentEnd += data.size
            val fin = _finPending && pendingBytes == 0
            if (fin) _finSent = true
            Chunk(offset, data, fin)
        }

    data class Chunk(
        val offset: Long,
        val data: ByteArray,
        val fin: Boolean,
    )
}
