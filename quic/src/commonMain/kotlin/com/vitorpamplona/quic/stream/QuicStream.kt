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
import kotlinx.coroutines.flow.flow
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * One QUIC stream (bidirectional or unidirectional). Application code
 * accesses it through [enqueue] (write) and the [readFlow] / FIN observation
 * APIs; the [QuicConnection] owns the underlying buffers and drains them
 * into STREAM frames on the wire.
 */
@OptIn(ExperimentalAtomicApi::class)
class QuicStream(
    val streamId: Long,
    val direction: Direction,
    /**
     * If true, lost STREAM bytes are dropped instead of retransmitted
     * (see [SendBuffer.bestEffort]). Used by moq-lite group streams
     * carrying real-time Opus audio: a STREAM frame arriving 200 ms
     * late is worse than useless. Default false (RFC 9000 §3.5
     * reliable byte sequence).
     */
    val bestEffort: Boolean = false,
) {
    enum class Direction { BIDIRECTIONAL, UNIDIRECTIONAL_LOCAL_TO_REMOTE, UNIDIRECTIONAL_REMOTE_TO_LOCAL }

    val send = SendBuffer(bestEffort = bestEffort)
    val receive = ReceiveBuffer()

    /**
     * Send-side scheduling priority. The connection writer's drain loop
     * iterates streams by descending priority; same-priority streams keep
     * their existing round-robin order. Higher value = drains first under
     * congestion. Default 0 matches pre-priority round-robin behaviour for
     * every existing call site.
     *
     * Used by moq-lite group streams: the publisher assigns each new group
     * a priority equal to its sequence number so that newer groups
     * (fresher audio) drain ahead of older ones when retransmits queue up
     * on a lossy link. Mirrors `Publisher::serve_group` in
     * `rs/moq-lite/src/lite/publisher.rs` (`stream.set_priority`).
     *
     * `@Volatile` because callers (e.g. moq-lite's openGroupStream)
     * assign from arbitrary coroutines while the writer reads it under
     * [com.vitorpamplona.quic.connection.QuicConnection.lock] during a
     * drain pass.
     */
    @Volatile
    var priority: Int = 0

    /**
     * Bytes received and confirmed contiguous, exposed as a flow to the consumer.
     *
     * Bounded buffer (64 chunks). The producer (parser) uses [trySend] and
     * surfaces saturation by setting [overflowed]; the parser checks this flag
     * after each delivery and tears the connection down with INTERNAL_ERROR
     * rather than silently dropping bytes. Pre-audit-4 the failed `trySend`
     * was discarded, leaving a hole in the stream that the application could
     * never know about.
     *
     * **Resilience to collector cancellation.** Pre-fix this exposed
     * [incomingChannel] via `consumeAsFlow()`, which cancels the
     * underlying [Channel] when its collector terminates. That coupled
     * "application stopped collecting" to "parser INTERNAL_ERRORs the
     * whole connection on next delivery" — cancelling the collector
     * early (e.g. `withTimeout(...)`) was effectively a request to
     * drop the connection. Rebuilt as `flow { for (c in
     * incomingChannel) emit(c) }`: a fresh emit-only Flow over the
     * channel iterator, with no `consume`-style ownership transfer.
     * Now collector cancellation just exits the flow without touching
     * the channel; the channel stays open, the parser keeps
     * delivering, and the application can re-collect later (a fresh
     * collector picks up at `channel.receive()` — i.e. from the
     * current head, not the start).
     *
     * Producer back-pressure unchanged: the channel buffer is still
     * 64 chunks, [trySend] still sets [overflowed] on saturation,
     * and the parser still closes the connection if the application
     * fails to drain. The looser contract just permits the previously-
     * disallowed pattern of "stop reading temporarily, then resume".
     *
     * Concurrent collectors are still NOT supported — two simultaneous
     * collects would race the channel iterator and each chunk goes to
     * exactly one of them non-deterministically. Sequential collects
     * (one finishes / cancels, then another starts) are the new
     * supported pattern.
     */
    private val incomingChannel = Channel<ByteArray>(capacity = 64)
    val incoming: Flow<ByteArray> = flow { for (chunk in incomingChannel) emit(chunk) }

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
     * RFC 9000 §4.1: highest stream offset (offset + length) ever seen
     * on an inbound STREAM or RESET_STREAM frame. Used by
     * [QuicConnection]'s connection-level flow-control accounting —
     * the spec requires the receiver to compare the SUM across all
     * streams of "largest received offset" against the advertised
     * `initial_max_data` / latest MAX_DATA, NOT the contiguous read
     * frontier. The writer's MAX_DATA threshold logic still uses the
     * cheaper contiguous-end approximation; this field is purely the
     * enforcement signal on receive.
     */
    @Volatile
    internal var receiveHighestOffset: Long = 0L

    /**
     * RFC 9000 §3.2 receive-side state: true once a `RESET_STREAM` for
     * this stream has been delivered by the peer. Subsequent inbound
     * STREAM frames on this id are invalid (the receive side is in the
     * "Reset Recvd" terminal state) and must close the connection with
     * STREAM_STATE_ERROR.
     *
     * Kept distinct from the local-side [resetState] (which tracks OUR
     * RESET_STREAM emission). Both can exist simultaneously: a bidi
     * stream can be reset by the peer's send side (this flag) while we
     * separately reset our own send side.
     */
    @Volatile
    internal var peerResetReceived: Boolean = false

    /**
     * Marker the parser sets whenever [receive.contiguousEnd] advances; the
     * writer's appendFlowControlUpdates consumes it to skip streams that
     * haven't received any new bytes since the last MAX_STREAM_DATA emission.
     *
     * `@Volatile` because the parser writes it from the read loop and the
     * writer reads it from the send loop without holding the same lock.
     * Without volatile the writer could miss the parser's update for an
     * unbounded time on JVM (the field is read in a hot drain loop where
     * the JIT might cache it), suppressing MAX_STREAM_DATA emissions
     * until something else triggered a fresh load.
     *
     * Pre-fix the writer iterated EVERY open stream on every drain
     * (audit-4 perf #9 — O(streams) × ~50 drains/sec; significant for audio
     * rooms with many WT streams).
     */
    @Volatile
    internal var receiveDirtyForFlowControl: Boolean = false

    /** True once we've FIN'd our write side and the peer FIN'd theirs. */
    val isClosed: Boolean
        get() = send.finSent && receive.finReceived

    /**
     * True once both directions are *fully* settled and the stream may be
     * removed from the connection's tracking lists (see
     * `QuicConnection.retireFullyDoneStreamsLocked`). This is strictly
     * stronger than [isClosed]: the latter only requires the FIN bits to
     * have been observed, not that the peer has acknowledged our FIN /
     * RESET (send side) nor that the application has drained the buffered
     * receive bytes (receive side).
     *
     * Lifetime contract:
     *  - SEND side done: peer has ACK'd our FIN ([SendBuffer.finAcked]) OR
     *    we ABORTed the stream and the peer ACK'd our RESET_STREAM
     *    ([resetAcked]). For [Direction.UNIDIRECTIONAL_REMOTE_TO_LOCAL]
     *    there is no send side, so this leg is trivially done.
     *  - RECEIVE side done: peer FIN'd ([ReceiveBuffer.finReceived]) AND
     *    every byte has been delivered from the receive buffer to the
     *    application's incoming Channel ([ReceiveBuffer.isFullyRead]).
     *    Once both hold, the parser has already invoked [closeIncoming]
     *    so any application coroutine still draining the buffered
     *    [incoming] flow will terminate naturally — the QuicStream object
     *    can outlive its membership in the connection's `streamsList`.
     *    For [Direction.UNIDIRECTIONAL_LOCAL_TO_REMOTE] there is no
     *    receive side, so this leg is trivially done.
     *
     * The motivation is the moq-lite audio-rooms path: each Opus frame is
     * forwarded as a fresh peer-uni stream by the relay. A 3-hour session
     * at ~50 frames/sec churns ~540 000 streams. Without retirement,
     * `streamsList` and the `streams` map grow monotonically — the
     * `nestsClient/plans/2026-04-26-moq-lite-gap.md` soak target wants
     * memory flat past handshake-stable. Stream retirement is the only
     * QUIC-level fix that keeps the tracker bounded for that workload.
     *
     * Read this from any thread under the connection's `streamsLock` — the
     * underlying flags are `@Volatile` and the buffers' synchronized
     * blocks publish their state atomically.
     */
    val isFullyRetired: Boolean
        get() {
            val sendSettled =
                when (direction) {
                    Direction.UNIDIRECTIONAL_REMOTE_TO_LOCAL -> true

                    Direction.UNIDIRECTIONAL_LOCAL_TO_REMOTE,
                    Direction.BIDIRECTIONAL,
                    -> send.finAcked || resetAcked
                }
            if (!sendSettled) return false
            val recvSettled =
                when (direction) {
                    Direction.UNIDIRECTIONAL_LOCAL_TO_REMOTE -> true

                    Direction.UNIDIRECTIONAL_REMOTE_TO_LOCAL,
                    Direction.BIDIRECTIONAL,
                    -> receive.finReceived && receive.isFullyRead()
                }
            return recvSettled
        }

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
     * First call wins. A second [resetStream] is a no-op: RFC 9000 §3.5
     * pins `finalSize` at the first emission; replaying retransmits with
     * a larger value (because the app enqueued more bytes between the
     * two calls) would trigger `FINAL_SIZE_ERROR` on the peer. The
     * `errorCode` is likewise frozen.
     *
     * Threading: callable from any coroutine. The boolean flags
     * ([resetEmitPending], [resetAcked]) are `@Volatile` so the writer
     * (which reads them under [com.vitorpamplona.quic.connection.QuicConnection.lock])
     * sees the assignment without acquiring the connection mutex. The
     * "first call wins" gate above closes the only multi-writer race
     * (two app threads racing the writer's clear-after-emit).
     */
    fun resetStream(errorCode: Long) {
        // Lock-free first-call-wins. The previous synchronized block
        // existed because the naive `if (resetState != null) return;
        // resetState = …` had a write-write race: two concurrent
        // callers (e.g. the application aborting a request while
        // STOP_SENDING from the peer triggers our own resetStream
        // from the parser) could both observe null and both write,
        // letting the second clobber the first errorCode while
        // resetEmitPending was already set. The writer would then
        // emit a RESET_STREAM with whichever errorCode landed last.
        //
        // compareAndSet collapses that to a single CAS: only the
        // first writer succeeds, all others observe non-null and
        // bail out. `resetEmitPending = true` happens only on the
        // winning path, so its @Volatile write happens-after the
        // resetState publication — the writer reading the flag sees
        // the populated state.
        val newState =
            ResetState(
                errorCode = errorCode,
                finalSize = send.nextOffset,
            )
        if (resetState.compareAndSet(null, newState)) {
            resetEmitPending = true
        }
    }

    /**
     * RFC 9000 §3.5: ask the peer to stop sending on this stream's
     * RECEIVE side. Application calls this when it's done reading
     * (e.g. an HTTP/3 request handler returned an early response and
     * doesn't care about the rest of the request body). The
     * `STOP_SENDING(streamId, errorCode)` frame is queued for emission
     * on the next writer drain; reliable per §13.3, retransmitted on
     * loss like [resetStream].
     *
     * First call wins (same reasoning as [resetStream]: the peer treats
     * the first STOP_SENDING as authoritative; replaying retransmits
     * with a different `errorCode` would visibly disagree with the
     * original frame already on the wire).
     */
    fun stopSending(errorCode: Long) {
        // Same lock-free first-call-wins rationale as [resetStream].
        if (stopSendingState.compareAndSet(null, StopSendingState(errorCode = errorCode))) {
            stopSendingEmitPending = true
        }
    }

    /**
     * RFC 9000 §3.5 send-side reset state. Set once by [resetStream]
     * (subsequent calls no-op); read by the writer + loss/ACK
     * dispatchers. Once set, contents are immutable.
     *
     * Held in an [AtomicReference] so [resetStream] can use
     * `compareAndSet(null, …)` to win the first-call-wins race
     * without acquiring a lock. Readers use `.load()` — the published
     * state is immutable after the CAS so a single load is enough.
     */
    internal val resetState: AtomicReference<ResetState?> = AtomicReference(null)

    /**
     * True while a RESET_STREAM emit is pending. Cleared after the
     * writer emits; set back to true by the loss dispatcher; cleared
     * again (and not re-set) once [resetAcked] latches.
     *
     * `@Volatile` because [resetStream] writes it from an arbitrary
     * coroutine while the writer / dispatchers read it under
     * [com.vitorpamplona.quic.connection.QuicConnection.lock]. Volatile
     * gives the cross-thread happens-before; the "first call wins"
     * gate in [resetStream] eliminates the write-write race with the
     * writer's clear-after-emit.
     */
    @Volatile
    internal var resetEmitPending: Boolean = false

    /**
     * Latches true when the peer ACKs the RESET_STREAM. After this
     * the writer no longer re-emits even if a stale lost token shows
     * up. Mirrors neqo's `send_stream::ResetAcked` state. Volatile for
     * the same reason as [resetEmitPending].
     */
    @Volatile
    internal var resetAcked: Boolean = false

    /**
     * Receive-side stop-sending state. Set once by [stopSending]
     * (subsequent calls no-op); read by the writer + loss/ACK
     * dispatchers. Atomic for the same reason as [resetState].
     */
    internal val stopSendingState: AtomicReference<StopSendingState?> = AtomicReference(null)

    @Volatile
    internal var stopSendingEmitPending: Boolean = false

    @Volatile
    internal var stopSendingAcked: Boolean = false

    internal data class ResetState(
        val errorCode: Long,
        val finalSize: Long,
    )

    internal data class StopSendingState(
        val errorCode: Long,
    )
}
