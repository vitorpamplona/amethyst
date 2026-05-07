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
package com.vitorpamplona.quic.connection

import com.vitorpamplona.quic.transport.UdpSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the UDP socket and runs the read + send loops for a [QuicConnection].
 *
 * Synchronization (post lock-split refactor 2026-05-08): the driver no
 * longer takes a single connection-wide lock around feed/drain. Instead
 * [feedDatagram] and [drainOutbound] internally acquire `streamsLock`
 * for the precise critical sections they touch — leaving app
 * coroutines (`openBidiStream`, etc.) free to run in parallel with the
 * I/O loops. Per-stream and per-level buffers serialize through their
 * leaf `synchronized(this)` blocks.
 *
 * The send loop is woken by a `Channel<Unit>(CONFLATED)` rather than a
 * polling timer — no idle CPU. App writes ([QuicConnection.queueDatagram]
 * and [QuicConnection.openBidiStream]/[com.vitorpamplona.quic.stream.SendBuffer.enqueue])
 * call [wakeup] to nudge the send loop.
 */
class QuicConnectionDriver(
    val connection: QuicConnection,
    private val socket: UdpSocket,
    private val parentScope: CoroutineScope,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job + Dispatchers.IO)
    private val sendWakeup = Channel<Unit>(Channel.CONFLATED)

    // Track the read + send loop jobs so close() can join them cleanly
    // (waiting for the in-flight datagram to finish flushing) instead of
    // racing them with scope.cancel().
    private var readJob: Job? = null
    private var sendJob: Job? = null

    /**
     * Round-5 concurrency #5: close() guard. A second concurrent invocation
     * (e.g. session close + read-loop death close racing) used to launch a
     * parallel teardown that called scope.cancel() and socket.close() while
     * the first close was mid-joinAll. We now memoize the teardown Job so
     * the second caller awaits the first's completion instead.
     */
    @Volatile
    private var closeJob: Job? = null

    fun start() {
        connection.start()
        // Wire the diagnostic UDP-stats supplier so
        // QuicConnection.flowControlSnapshot can surface
        // kernel-delivered datagram counters alongside the QUIC
        // flow-control fields.
        connection.udpStatsSupplier = {
            UdpSocketStats(
                receivedDatagrams = socket.receivedDatagramCount,
                receivedBytes = socket.receivedByteCount,
                receiveBufferSizeBytes = socket.receiveBufferSizeBytes,
            )
        }
        readJob = scope.launch { readLoop() }
        sendJob = scope.launch { sendLoop() }
        // Initial nudge so the ClientHello goes out immediately.
        sendWakeup.trySend(Unit)
    }

    /** Nudge the send loop. Safe to call from any coroutine. */
    fun wakeup() {
        sendWakeup.trySend(Unit)
    }

    private suspend fun readLoop() {
        try {
            while (connection.status != QuicConnection.Status.CLOSED) {
                val datagram = socket.receive() ?: break
                // Phase 1 of the lock-split refactor: parser holds
                // streamsLock for a single datagram-feed pass.
                connection.streamsLock.withLock {
                    feedDatagram(connection, datagram, nowMillis())
                }
                // Inbound data may have produced new outbound (acks, crypto, etc.).
                wakeup()
            }
        } finally {
            // If the read loop exits while the handshake is still pending,
            // unblock anyone awaiting the handshake — otherwise awaitHandshake()
            // suspends forever.
            connection.markClosedExternally("read loop exited (socket closed or peer closed)")
            wakeup() // let the send loop notice CLOSED and exit
        }
    }

    private suspend fun sendLoop() {
        // RFC 9002 §6.2 Probe Timeout. Once handshake is complete and
        // we have an RTT estimate, the PTO duration is
        // `smoothed_rtt + max(4*rttvar, 1ms) + max_ack_delay`,
        // doubled by `1 shl consecutivePtoCount` per §6.2.2. Before
        // the first RTT sample we fall back to a 1 s conservative
        // floor (the same prior-shipping behavior, kept for
        // handshake-timeout safety on lossy paths).
        while (connection.status != QuicConnection.Status.CLOSED) {
            // Phase 1 of the lock-split refactor: the writer holds
            // streamsLock for the build, releases it for the actual
            // socket.send() so a slow socket doesn't stall app
            // coroutines (open/close streams, queue datagrams).
            while (true) {
                val out =
                    connection.streamsLock.withLock {
                        drainOutbound(connection, nowMillis())
                    } ?: break
                socket.send(out)
            }
            // Use the loss-detection's PTO calculation in BOTH the pre- and
            // post-first-RTT-sample regimes. Pre-sample, smoothed_rtt =
            // INITIAL_RTT_MS so ptoBaseMs returns
            // INITIAL_RTT_MS * 3 + max_ack_delay (~300 ms with the 100 ms
            // initial). max_ack_delay only applies to APPLICATION space per
            // RFC 9002 §6.2.1; pre-handshake we pass 0. Earlier shape
            // hardcoded 1000 ms here as a "handshake-timeout safety floor"
            // — the cost was four PTO retransmits in ~30 s of loss
            // recovery instead of the eight that 300 ms initial gives,
            // pinching multiconnect handshake-loss tests at the tail.
            val maxAckDelayMs =
                if (connection.application.sendProtection != null) {
                    connection.peerTransportParameters?.maxAckDelay ?: 0L
                } else {
                    0L
                }
            val ptoBaseMs =
                connection.lossDetection
                    .ptoBaseMs(maxAckDelayMs)
                    .coerceAtLeast(1L)
            val backoff = (1L shl connection.consecutivePtoCount.coerceAtMost(6))
            val ptoMillis = (ptoBaseMs * backoff).coerceAtMost(60_000L)
            // Suspend until either: a wakeup arrives, or the PTO timer expires.
            val woke =
                withTimeoutOrNull(ptoMillis) {
                    sendWakeup.receive()
                    Unit
                }
            if (woke == null) {
                handlePtoFired(connection)
            }
        }
    }

    /**
     * Cleanly tear down the driver. Runs on [parentScope] so the caller (which
     * may itself live inside the driver's [scope]) isn't cancelled before its
     * own teardown completes.
     *
     * Sequence:
     *  1. Mark the connection CLOSING so the writer starts emitting
     *     CONNECTION_CLOSE on the next drain.
     *  2. Wake the send loop and wait up to [CLOSE_FLUSH_TIMEOUT_MILLIS] for it
     *     to flush that packet — yield() alone is not enough because the send
     *     loop may be parked on Channel.receive on a different IO worker
     *     thread; only an explicit join+wakeup sequence guarantees the close
     *     bytes hit the socket.
     *  3. Force the loops out of their `while (status != CLOSED)` guards by
     *     transitioning to CLOSED, then cancel the scope and close the socket.
     *
     * The earlier yield()-based version raced: scope.cancel() could fire
     * while sendLoop was mid-`socket.send()`, occasionally producing partial
     * datagrams or skipping the CONNECTION_CLOSE entirely.
     */
    fun close() {
        // Round-5 #5: idempotent close. Memoize the teardown launch so a
        // second concurrent caller (which is common: session.close() and
        // read-loop death both race to close()) awaits the same Job rather
        // than launching a parallel teardown.
        if (closeJob != null) return
        synchronized(this) {
            if (closeJob != null) return
            closeJob =
                parentScope.launch {
                    connection.close(0L, "")
                    wakeup()
                    val send = sendJob
                    // Bounded wait for the send loop to flush CONNECTION_CLOSE.
                    // We don't want to hang forever if the writer is wedged —
                    // the timeout is the upper bound on how long close() blocks.
                    withTimeoutOrNull(CLOSE_FLUSH_TIMEOUT_MILLIS) {
                        // Spin until the writer has actually drained the queued
                        // close. The CLOSING-status check transitions to CLOSED
                        // once drainOutbound builds the CONNECTION_CLOSE packet.
                        while (connection.status == QuicConnection.Status.CLOSING) {
                            kotlinx.coroutines.delay(1)
                        }
                    }
                    // Now flip to CLOSED so both loops exit their while-guards.
                    connection.markClosedExternally("driver close requested")
                    wakeup()
                    // Wait for both loops to actually exit — joinAll won't
                    // return until the in-flight socket.send() completes.
                    withTimeoutOrNull(CLOSE_FLUSH_TIMEOUT_MILLIS) {
                        listOfNotNull(readJob, send).joinAll()
                    }
                    // Final teardown — cancel guarantees both jobs are done
                    // before we close the socket.
                    scope.cancel()
                    socket.close()
                }
        }
    }

    companion object {
        /** Upper bound on close() flush wait. Each phase (drain + join) gets up to this much. */
        private const val CLOSE_FLUSH_TIMEOUT_MILLIS = 250L
    }
}

/**
 * Spec-correct response to a PTO timer firing (RFC 9002 §6.2.4). The probe
 * packet MUST be ack-eliciting at the encryption level with unacknowledged
 * data, and SHOULD retransmit the lost data rather than emit a bare PING —
 * so we requeue inflight CRYPTO bytes at every active pre-application
 * level (Initial AND Handshake), and the next [drainOutbound] emits a
 * CRYPTO frame at the original offset. `requeueAllInflight` is a no-op
 * when nothing is inflight, so calling this for already-ACKed or
 * already-discarded levels is harmless.
 *
 * `pendingPing` stays set as a fallback. `collectHandshakeLevelFrames`
 * suppresses the PING when CRYPTO is in the same frame list, so we
 * don't waste a frame on top of the retransmit.
 *
 * Why every active pre-application level, not just the highest: there's a
 * window between 1-RTT keys becoming installed (server's Finished arrives,
 * client derives application keys) and the handshake being confirmed
 * (server's HANDSHAKE_DONE arrives). In that window our own Finished is
 * still in flight at Handshake level, and the application-space loss
 * detection that ACK-only PINGs rely on doesn't cover it. If our Finished
 * is dropped, the server keeps retransmitting handshake CRYPTO forever
 * trying to elicit our missing ACK-eliciting handshake-level packet,
 * never confirms the handshake, never sends HANDSHAKE_DONE. Surfaced by
 * `handshakeloss` against aioquic at 30% drop rate (multiconnect iter 12
 * stuck at t=52s with zero handshake_done events; pre-fix
 * `handlePtoFired` gated the requeue on `application.sendProtection ==
 * null` and skipped Handshake CRYPTO retransmit once 1-RTT keys existed).
 *
 * Why aioquic interop demands this: aioquic strictly rejects pre-
 * handshake Initials that contain no CRYPTO frame
 * (`CONNECTION_CLOSE 0x0 "Packet contains no CRYPTO frame"`). A
 * bare-PING probe before the ClientHello is acknowledged is fatal.
 *
 * Extracted from [QuicConnectionDriver.sendLoop]'s PTO branch into a
 * top-level helper so the unit test in
 * [com.vitorpamplona.quic.connection.PtoCryptoRetransmitTest]
 * can invoke the EXACT logic the live driver does, without standing
 * up a UDP socket. Earlier shapes simulated the steps inline in the
 * test, which let the driver-side wiring regress twice
 * (commits c0d7b6031, then again in the lock-split refactor) without
 * any test breaking.
 *
 * Concurrency: `pendingPing` and `consecutivePtoCount` are `@Volatile`.
 * [QuicConnection.requeueAllInflightCrypto] delegates to
 * [com.vitorpamplona.quic.stream.SendBuffer.requeueAllInflight] which
 * is `synchronized(this)` internally, so it's safe to call without
 * an external lock — even concurrent with the writer's `takeChunk`.
 * If the parser concurrently runs `discardKeys` on the same level,
 * `requeueAllInflight` operates on the buffer reference we captured
 * (or the fresh one — both are valid) and is at worst a no-op.
 */
internal suspend fun handlePtoFired(conn: QuicConnection) {
    conn.pendingPing = true
    if (conn.handshake.sendProtection != null && !conn.handshake.keysDiscarded) {
        conn.requeueAllInflightCrypto(EncryptionLevel.HANDSHAKE)
    }
    if (conn.initial.sendProtection != null && !conn.initial.keysDiscarded) {
        conn.requeueAllInflightCrypto(EncryptionLevel.INITIAL)
    }
    // Once 1-RTT keys are installed, PTO must also retransmit application
    // data — STREAM bytes that were sent but never ACK'd. Without this,
    // a single corrupted/lost 1-RTT packet (especially the first one
    // carrying our HTTP/3 init streams + the GET request) is unrecoverable
    // because loss detection only runs after the peer ACKs something
    // and we have nothing else for the peer to ACK. Iterating streamsList
    // requires streamsLock — `openBidiStream` and friends mutate it under
    // the same lock, so unlocked iteration races with stream creation.
    if (conn.application.sendProtection != null) {
        conn.streamsLock.withLock {
            conn.requeueAllInflightStreamData()
            conn.requeueAllInflightCrypto(EncryptionLevel.APPLICATION)
        }
    }
    conn.consecutivePtoCount = (conn.consecutivePtoCount + 1).coerceAtMost(6)
}
