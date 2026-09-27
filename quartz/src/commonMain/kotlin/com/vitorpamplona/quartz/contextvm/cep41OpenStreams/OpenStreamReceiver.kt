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
package com.vitorpamplona.quartz.contextvm.cep41OpenStreams

import com.vitorpamplona.quartz.contextvm.transfer.ProgressToken

/** Local resource and keepalive policy for one stream. */
data class OpenStreamPolicy(
    /**
     * Idle time before the peer must be probed with a `ping`.
     *
     * 30s matches what deployed clients use. A shorter window (the 20s some
     * SDKs default to) turns a single lost relay round-trip into a stream abort.
     */
    val idleTimeoutMs: Long = 30_000,
    /** How long a `ping` may go unanswered before the stream is failed. */
    val probeTimeoutMs: Long = 30_000,
    /** Bounded buffering for chunks that arrive before their predecessors. */
    val maxPendingChunks: Int = 256,
)

/** Outcome of feeding one inbound frame to [OpenStreamReceiver]. */
sealed interface OpenStreamEvent {
    /** Frame accepted; nothing to deliver or answer. */
    data object Continue : OpenStreamEvent

    /** The receiver must send an `accept` before the peer may send chunks. */
    data object SendAccept : OpenStreamEvent

    /** Payload fragments that became contiguous, in `chunkIndex` order. */
    data class Delivered(
        val fragments: List<String>,
    ) : OpenStreamEvent

    /**
     * The peer probed us and MUST receive a `pong` carrying the same nonce.
     *
     * The response rides our own outbound progress sequence, which is why the
     * caller supplies the progress value rather than this class.
     */
    data class Pong(
        val nonce: String,
    ) : OpenStreamEvent

    /**
     * The peer closed the stream.
     *
     * This does **not** complete the originating JSON-RPC request: CEP-41
     * requires a final response as well, and a client must never synthesize
     * success from `close` alone.
     */
    data class Closed(
        val lastChunkIndex: Long?,
    ) : OpenStreamEvent

    data class Aborted(
        val reason: String?,
    ) : OpenStreamEvent
}

/** Thrown when a stream violates CEP-41. The stream is terminal once this is raised. */
class OpenStreamException(
    message: String,
) : IllegalStateException(message)

/**
 * Receives one CEP-41 open-ended stream, keyed by its `progressToken`.
 *
 * Rules are §6.5 `CVM-41-*` in `quartz/plans/2026-09-17-cordn-interop.md`.
 *
 * @param requireAccept true in stateless bootstrap, where the peer must wait for
 *   our `accept` before its first chunk.
 * @param now injectable clock (epoch millis) so keepalive is testable without
 *   real time passing.
 */
class OpenStreamReceiver(
    val token: ProgressToken,
    private val policy: OpenStreamPolicy = OpenStreamPolicy(),
    private val requireAccept: Boolean = true,
    private val now: () -> Long = { 0L },
) {
    private var started = false
    private var startProgress: Double? = null
    private var accepted = false
    private var terminal = false

    /** Every inbound `progress` seen, to reject replays without assuming arrival order. */
    private val seenProgress = mutableSetOf<Double>()

    private val pending = mutableMapOf<Long, String>()
    private var nextIndex = 0L
    private var highestIndex: Long? = null

    /** Nonces of pings we sent that are still awaiting a pong, with their send time. */
    private val outstandingPings = mutableMapOf<String, Long>()

    // Starts now, not at zero. Against a real clock `now() - 0` is thirty-odd
    // years, so a receiver that had not yet seen a single frame reported
    // itself idle and due a probe the instant it was built.
    private var lastActivityMs = now()

    val isTerminal get() = terminal

    fun accept(frame: OpenStreamFrame): OpenStreamEvent {
        if (terminal) throw OpenStreamException("frame received after the stream terminated")
        if (frame.token != token) throw OpenStreamException("frame belongs to another stream")

        lastActivityMs = now()

        if (frame is OpenStreamFrame.Abort) {
            terminal = true
            return OpenStreamEvent.Aborted(frame.reason)
        }

        // A repeated progress value from the same peer is a replay or a bug.
        // Arrival *order* is deliberately not checked -- relays reorder, and the
        // CEP makes chunkIndex, not progress, the completeness test.
        if (!seenProgress.add(frame.progress)) {
            fail("duplicate progress ${frame.progress} from the peer")
        }

        return when (frame) {
            is OpenStreamFrame.Start -> onStart(frame)
            is OpenStreamFrame.Accept -> OpenStreamEvent.Continue
            is OpenStreamFrame.Chunk -> onChunk(frame)
            is OpenStreamFrame.Ping -> OpenStreamEvent.Pong(frame.nonce)
            is OpenStreamFrame.Pong -> onPong(frame)
            is OpenStreamFrame.Close -> onClose(frame)
            is OpenStreamFrame.Abort -> error("handled above")
        }
    }

    private fun onStart(frame: OpenStreamFrame.Start): OpenStreamEvent {
        // A second start for a live token MUST fail the stream.
        if (started) fail("a second start arrived for a live stream")
        started = true
        startProgress = frame.progress
        return if (requireAccept) OpenStreamEvent.SendAccept else OpenStreamEvent.Continue
    }

    private fun onChunk(frame: OpenStreamFrame.Chunk): OpenStreamEvent {
        if (!started) fail("chunk arrived before start")
        if (requireAccept && !accepted) fail("chunk arrived before accept in a bootstrap stream")

        val start = startProgress
        if (start != null && frame.progress <= start) {
            fail("chunk progress ${frame.progress} does not follow start at $start")
        }
        if (frame.chunkIndex < nextIndex) {
            fail("chunkIndex ${frame.chunkIndex} was already delivered")
        }
        if (pending.containsKey(frame.chunkIndex)) {
            fail("duplicate chunkIndex ${frame.chunkIndex}")
        }
        if (pending.size >= policy.maxPendingChunks) {
            fail("buffered chunk count exceeds the limit ${policy.maxPendingChunks}")
        }

        pending[frame.chunkIndex] = frame.data
        highestIndex = maxOf(highestIndex ?: frame.chunkIndex, frame.chunkIndex)

        // Release the contiguous run. A gap is not an error while the stream is
        // live -- it is a provisional gap the peer may still fill.
        val ready = mutableListOf<String>()
        while (true) {
            val next = pending.remove(nextIndex) ?: break
            ready += next
            nextIndex += 1
        }

        return if (ready.isEmpty()) OpenStreamEvent.Continue else OpenStreamEvent.Delivered(ready)
    }

    private fun onPong(frame: OpenStreamFrame.Pong): OpenStreamEvent {
        // A pong for a nonce we never sent, or already retired, is not evidence
        // of liveness. It is ignored rather than fatal: the CEP allows local
        // anti-abuse policy, and failing the stream would let a third party
        // disrupt it by replaying a stale pong.
        outstandingPings.remove(frame.nonce)
        return OpenStreamEvent.Continue
    }

    private fun onClose(frame: OpenStreamFrame.Close): OpenStreamEvent {
        if (!started) fail("close arrived before start")

        val bound = frame.lastChunkIndex
        if (bound != null) {
            if (highestIndex == null) {
                // "If the stream included no chunk frames, close.lastChunkIndex
                // MUST be omitted."
                fail("close declared lastChunkIndex $bound but the stream carried no chunks")
            }
            if (bound != (nextIndex - 1)) {
                fail("close declared lastChunkIndex $bound but ${nextIndex - 1} is the last contiguous index")
            }
            if (pending.isNotEmpty()) {
                fail("close declared a completeness bound while ${pending.size} chunks remain unresolved")
            }
        }

        terminal = true
        return OpenStreamEvent.Closed(bound)
    }

    // --- keepalive ---

    /** True when the idle timeout has elapsed and the peer must be probed. */
    fun needsProbe(atMs: Long = now()) = !terminal && outstandingPings.isEmpty() && atMs - lastActivityMs >= policy.idleTimeoutMs

    /** Records that we sent a `ping` with [nonce], starting its probe window. */
    fun markProbeSent(
        nonce: String,
        atMs: Long = now(),
    ) {
        require(nonce.encodeToByteArray().size <= OpenStreamFrame.MAX_NONCE_BYTES) {
            "nonce exceeds ${OpenStreamFrame.MAX_NONCE_BYTES} bytes"
        }
        outstandingPings[nonce] = atMs
    }

    /**
     * True when a probe went unanswered past the probe timeout.
     *
     * The caller MUST then fail the stream, and SHOULD send `abort` if it still
     * can.
     */
    fun probeExpired(atMs: Long = now()) = outstandingPings.values.any { atMs - it >= policy.probeTimeoutMs }

    /** Records that we sent `accept`, unblocking the peer's chunk frames. */
    fun markAccepted() {
        accepted = true
    }

    /** Marks the stream terminal after a local policy failure. */
    fun failLocally(reason: String): Nothing = fail(reason)

    private fun fail(reason: String): Nothing {
        terminal = true
        throw OpenStreamException(reason)
    }
}
