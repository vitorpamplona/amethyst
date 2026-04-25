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

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

/**
 * One QUIC stream (bidirectional or unidirectional). Application code
 * accesses it through [enqueue] (write) and the [readFlow] / FIN observation
 * APIs; the [QuicConnection] owns the underlying buffers and drains them
 * into STREAM frames on the wire.
 */
class QuicStream(
    val streamId: Long,
    val direction: Direction,
) {
    enum class Direction { BIDIRECTIONAL, UNIDIRECTIONAL_LOCAL_TO_REMOTE, UNIDIRECTIONAL_REMOTE_TO_LOCAL }

    val send = SendBuffer()
    val receive = ReceiveBuffer()

    /**
     * Bytes received and confirmed contiguous, exposed as a flow to the consumer.
     *
     * Bounded buffer (64 chunks) — combined with the per-stream receive-limit
     * enforced in the parser, this caps unbounded memory growth from a slow
     * consumer. Producer (the parser) uses [trySend], dropping bytes if the
     * channel is full; the receive-limit enforcement makes "channel full" a
     * connection-level error long before it becomes a memory problem.
     */
    private val incomingChannel = Channel<ByteArray>(capacity = 64)
    val incoming: Flow<ByteArray> get() = incomingChannel.consumeAsFlow()

    /** Per-stream send credit (peer's MAX_STREAM_DATA value). */
    var sendCredit: Long = 0L
        internal set

    /** Per-stream receive credit (the value we advertised). */
    var receiveLimit: Long = 0L
        internal set

    /** True once we've FIN'd our write side and the peer FIN'd theirs. */
    val isClosed: Boolean
        get() = send.finSent && receive.finReceived

    internal fun deliverIncoming(data: ByteArray) {
        if (data.isNotEmpty()) {
            incomingChannel.trySend(data)
        }
    }

    internal fun closeIncoming() {
        incomingChannel.close()
    }
}
