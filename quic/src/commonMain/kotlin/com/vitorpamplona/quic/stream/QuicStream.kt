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
     * Bounded buffer (64 chunks). The producer (parser) uses [trySend] and
     * surfaces saturation by setting [overflowed]; the parser checks this flag
     * after each delivery and tears the connection down with INTERNAL_ERROR
     * rather than silently dropping bytes. Pre-audit-4 the failed `trySend`
     * was discarded, leaving a hole in the stream that the application could
     * never know about.
     */
    private val incomingChannel = Channel<ByteArray>(capacity = 64)
    val incoming: Flow<ByteArray> get() = incomingChannel.consumeAsFlow()

    /**
     * True once a [deliverIncoming] call failed because the channel was
     * saturated (slow consumer). The parser observes this and closes the
     * connection rather than letting bytes silently disappear.
     */
    @Volatile
    var overflowed: Boolean = false
        private set

    /** Per-stream send credit (peer's MAX_STREAM_DATA value). */
    var sendCredit: Long = 0L
        internal set

    /** Per-stream receive credit (the value we advertised). */
    var receiveLimit: Long = 0L
        internal set

    /**
     * Marker the parser sets whenever [receive.contiguousEnd] advances; the
     * writer's appendFlowControlUpdates consumes it to skip streams that
     * haven't received any new bytes since the last MAX_STREAM_DATA emission.
     *
     * Pre-fix the writer iterated EVERY open stream on every drain
     * (audit-4 perf #9 — O(streams) × ~50 drains/sec; significant for audio
     * rooms with many WT streams).
     */
    internal var receiveDirtyForFlowControl: Boolean = false

    /** True once we've FIN'd our write side and the peer FIN'd theirs. */
    val isClosed: Boolean
        get() = send.finSent && receive.finReceived

    /**
     * Pushes [data] toward the consumer. Returns false if the bounded channel
     * was full; the caller (parser) is expected to escalate to a connection-
     * level error in that case (audit-4 #3 — silent data loss is unacceptable
     * because the peer believes the bytes were delivered).
     */
    internal fun deliverIncoming(data: ByteArray): Boolean {
        if (data.isEmpty()) return true
        val ok = incomingChannel.trySend(data).isSuccess
        if (!ok) overflowed = true
        return ok
    }

    internal fun closeIncoming() {
        incomingChannel.close()
    }

    /**
     * RFC 9000 §3.5: abruptly terminate the SEND side. Application code
     * calls this when it wants to cancel a partially-written stream
     * (e.g. user cancelled a request mid-upload). After [resetStream]:
     *
     *   - A `RESET_STREAM(streamId, errorCode, finalSize)` frame is
     *     queued for emission on the next writer drain. `finalSize` is
     *     the largest stream offset the application has appended, i.e.
     *     [SendBuffer.nextOffset] at reset time (RFC 9000 §3.5 — the
     *     peer uses this to satisfy its receive-side flow-control
     *     accounting even though it discards the bytes).
     *   - The frame is reliable per §13.3 — if its carrying packet is
     *     declared lost, the dispatcher in
     *     [com.vitorpamplona.quic.connection.QuicConnection.onTokensLost]
     *     re-flags [resetEmitPending] for re-emit on next drain.
     *   - Once the peer ACKs the RESET_STREAM, [resetAcked] latches
     *     true and the writer stops re-emitting.
     *
     * Idempotent: a second [resetStream] call overwrites the queued
     * entry with the newer error code (in practice apps shouldn't
     * reset twice — the second call is benign).
     */
    fun resetStream(errorCode: Long) {
        resetState =
            ResetState(
                errorCode = errorCode,
                finalSize = send.nextOffset,
            )
        resetEmitPending = true
    }

    /**
     * RFC 9000 §3.5: ask the peer to stop sending on this stream's
     * RECEIVE side. Application calls this when it's done reading
     * (e.g. an HTTP/3 request handler returned an early response and
     * doesn't care about the rest of the request body). The
     * `STOP_SENDING(streamId, errorCode)` frame is queued for emission
     * on the next writer drain; reliable per §13.3, retransmitted on
     * loss like [resetStream].
     */
    fun stopSending(errorCode: Long) {
        stopSendingState = StopSendingState(errorCode = errorCode)
        stopSendingEmitPending = true
    }

    /** RFC 9000 §3.5 send-side reset state. Set by [resetStream]. */
    internal var resetState: ResetState? = null

    /**
     * True while a RESET_STREAM emit is pending. Cleared after the
     * writer emits; set back to true by the loss dispatcher; cleared
     * again (and not re-set) once [resetAcked] latches.
     */
    internal var resetEmitPending: Boolean = false

    /**
     * Latches true when the peer ACKs the RESET_STREAM. After this
     * the writer no longer re-emits even if a stale lost token shows
     * up. Mirrors neqo's `send_stream::ResetAcked` state.
     */
    internal var resetAcked: Boolean = false

    /** Receive-side stop-sending state. Set by [stopSending]. */
    internal var stopSendingState: StopSendingState? = null

    internal var stopSendingEmitPending: Boolean = false
    internal var stopSendingAcked: Boolean = false

    internal data class ResetState(
        val errorCode: Long,
        val finalSize: Long,
    )

    internal data class StopSendingState(
        val errorCode: Long,
    )
}
