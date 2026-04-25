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
 * credit. Sent bytes stay in the buffer until [acknowledge] (Phase F adds
 * retransmission of lost bytes; for v1 we just trust the receiver and
 * release on send).
 *
 * For Phase D-K we run a "best effort" mode: bytes are released from the
 * buffer the moment they're handed off, on the assumption that the
 * underlying network is stable. Phase L adds retransmit-on-loss (using the
 * same send buffer that retains until ACK).
 */
class SendBuffer {
    private var pending: ByteArray = ByteArray(0)
    private var sentEnd: Long = 0L
    var nextOffset: Long = 0L
        private set
    var finPending: Boolean = false
        private set
    var finSent: Boolean = false
        private set

    val readableBytes: Int get() = pending.size

    fun enqueue(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val combined = ByteArray(pending.size + bytes.size)
        pending.copyInto(combined, 0)
        bytes.copyInto(combined, pending.size)
        pending = combined
        nextOffset += bytes.size
    }

    /** Mark the write side as closing; the next [takeChunk] will set FIN once empty. */
    fun finish() {
        finPending = true
    }

    /** Take up to [maxBytes] bytes off the head of the buffer at the current send offset. */
    fun takeChunk(maxBytes: Int): Chunk? {
        if (pending.isEmpty() && !(finPending && !finSent)) return null
        val take = minOf(pending.size, maxBytes.coerceAtLeast(0))
        val data = pending.copyOfRange(0, take)
        pending = pending.copyOfRange(take, pending.size)
        val offset = sentEnd
        sentEnd += take
        val fin = finPending && pending.isEmpty()
        if (fin) finSent = true
        return Chunk(offset, data, fin)
    }

    data class Chunk(
        val offset: Long,
        val data: ByteArray,
        val fin: Boolean,
    )
}
