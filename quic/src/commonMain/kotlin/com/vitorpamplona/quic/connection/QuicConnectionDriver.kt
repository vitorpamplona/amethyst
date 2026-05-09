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
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

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
@OptIn(ExperimentalAtomicApi::class)
class QuicConnectionDriver(
    val connection: QuicConnection,
    private val socket: UdpSocket,
    private val parentScope: CoroutineScope,
) {
    /**
     * Single time source: defer to the connection's [QuicConnection.nowMillis]
     * (monotonic by default). Pre-fix the driver had its own
     * `System.currentTimeMillis()` default which silently disagreed with the
     * connection's wallclock-derived clock; under NTP step the two could
     * report different values for the SAME logical "now", leading to RTT
     * samples computed against drifted timestamps.
     */
    private val nowMillis: () -> Long get() = connection.nowMillis
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job + Dispatchers.IO)
    private val sendWakeup = Channel<Unit>(Channel.CONFLATED)

    // Track the read + send loop jobs so close() can join them cleanly
    // (waiting for the in-flight datagram to finish flushing) instead of
    // racing them with scope.cancel().
    private var readJob: Job? = null
    private var sendJob: Job? = null

    /**
     * Test-only handle on the driver's [SupervisorJob]. Used by the
     * session-lifecycle leak test in
     * [com.vitorpamplona.quic.connection.QuicConnectionDriverLifecycleTest]
     * to assert that a closed driver reports `isCompleted = true` once
     * its read/send loops have unwound — the inverse of "the driver
     * leaked a coroutine past close()".
     *
     * Production code MUST NOT touch this — the driver lifecycle is
     * managed end-to-end by [start] / [close].
     */
    internal val driverJob: Job get() = job

    /**
     * Test-only handle on the in-flight teardown coroutine. Returns null
     * before [close] has been called; once close runs, the returned Job
     * lets the test `join()` until teardown is complete (cancel + socket
     * close + read/send join). Pre-existing close() returned immediately
     * and provided no synchronous "teardown is done" signal — tests had
     * to poll `connection.status == CLOSED` and trust that the rest of
     * the cleanup eventually settled.
     */
    internal val closeTeardownJob: Job? get() = closeJob.load()

    /**
     * Round-5 concurrency #5: close() guard. A second concurrent invocation
     * (e.g. session close + read-loop death close racing) used to launch a
     * parallel teardown that called scope.cancel() and socket.close() while
     * the first close was mid-joinAll. We memoize the teardown Job so the
     * second caller awaits the first's completion instead.
     *
     * Lock-free CAS replaces the previous `synchronized(this)` double-checked
     * init: the close path is single-shot ("first writer wins"), which is
     * exactly what `compareAndSet` expresses.
     */
    private val closeJob: AtomicReference<Job?> = AtomicReference(null)

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
        //
        // Mirror the read loop's symmetry: any uncaught throw inside
        // the loop (most common: `socket.send` raising once the OS
        // tears down the UDP socket — typical on Android when the
        // app backgrounds and the kernel reclaims the FD ~30 s
        // later) MUST flip the connection to CLOSED so the
        // higher-level reconnect orchestration in
        // [com.vitorpamplona.nestsclient.connectReconnectingNestsListener]
        // observes a Failed terminal state and fires a fresh
        // handshake. Pre-fix, the throw escaped silently into the
        // SupervisorJob, the read loop kept blocking on
        // `socket.receive()` (which doesn't throw — it returns null
        // on close, but the OS may not surface that for many
        // seconds), and the connection sat in HANDSHAKING / CONNECTED
        // long after the socket was dead — invisibly wedged.
        try {
            sendLoopBody()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Cooperative cancel from close() / scope.cancel(). Don't
            // mark closed here — close() is already driving the
            // teardown and would race with our markClosedExternally.
            throw ce
        } catch (t: Throwable) {
            connection.markClosedExternally("send loop exited: ${t::class.simpleName}: ${t.message}")
        }
    }

    private suspend fun sendLoopBody() {
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
                // RFC 9002 §6.2.4 probe budget: when the PTO timer
                // fired we set `pendingProbePackets = 2`; consume one
                // slot per probe-bearing datagram. If budget remains,
                // re-requeue inflight CRYPTO / STREAM bytes so the
                // next drainOutbound iteration has payload to probe
                // with. The loop's own break-on-null then terminates
                // naturally if there's nothing left to requeue (e.g.
                // a connection with no inflight data — the first
                // probe is a bare PING, the second slot harmlessly
                // expires with no follow-up datagram).
                //
                // Decrementing AFTER socket.send (not before) ensures
                // a non-PTO drain — wakeup-driven, no probe budget
                // outstanding — leaves the field at 0 untouched. The
                // post-decrement check uses `> 0` (not `>= 0`) so
                // we only requeue while another probe is still owed.
                if (connection.pendingProbePackets > 0) {
                    connection.pendingProbePackets--
                    if (connection.pendingProbePackets > 0) {
                        // Two cooperating signals for the next probe:
                        //   (a) requeue inflight CRYPTO / STREAM data so a
                        //       payload-bearing probe can fire (preferred —
                        //       a CRYPTO retransmit also covers the
                        //       ack-eliciting requirement);
                        //   (b) re-arm `pendingPing` for the no-data fallback
                        //       (CRYPTO already ACK'd or never in flight, e.g.
                        //       handshake-confirmed connections idling
                        //       between transfers). Without (b), the writer
                        //       suppresses the second probe entirely because
                        //       `pendingPing` is one-shot — the first probe
                        //       cleared it. The writer's own
                        //       `collectHandshakeLevelFrames` still
                        //       suppresses the PING when a CRYPTO frame
                        //       lands in the same packet, so this never
                        //       wastes a frame on top of a retransmit.
                        requeueInflightForProbe(connection)
                        connection.pendingPing = true
                    }
                }
            }
            // Defence-in-depth: if the inner loop exited with budget
            // still unconsumed (e.g. drainOutbound returned null on
            // the very first iteration because nothing was inflight
            // — only `pendingPing` would have been set, and it can
            // be cleared by the writer without an outbound emission
            // when no level has send keys), zero it out so the next
            // wakeup-driven drain doesn't accidentally re-requeue.
            connection.pendingProbePackets = 0
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
            // RFC 9002 §6.1.2 timer-driven loss detection: take the
            // earliest of (PTO deadline, next-loss-time across levels).
            // Without this, tail loss waits for the PTO instead of the
            // shorter `9/8 * max_rtt` time threshold — visible to the
            // user as a recovery delay equal to the PTO minus that
            // threshold (often ~5x worse than necessary on lossy
            // links). A null nextLossTime contributes nothing.
            val nowForLossTimer = nowMillis()
            val nextLossDelta =
                listOfNotNull(
                    connection.initial.nextLossTimeMs,
                    connection.handshake.nextLossTimeMs,
                    connection.application.nextLossTimeMs,
                ).minOrNull()?.let { (it - nowForLossTimer).coerceAtLeast(0L) }
            // RFC 9000 §10.1 idle-timeout deadline. Null when neither
            // endpoint advertised a non-zero `max_idle_timeout`. We
            // fold the remaining time into the same `withTimeoutOrNull`
            // sleep so an idle connection wakes exactly at expiry
            // instead of polling.
            val idleTimeoutMs = connection.effectiveIdleTimeoutMs()
            val idleDelta =
                idleTimeoutMs?.let {
                    (connection.lastActivityMs + it - nowForLossTimer).coerceAtLeast(0L)
                }
            // RFC 9000 §10.2.2 draining-period deadline. Set when
            // peer's CONNECTION_CLOSE arrives; we hold in DRAINING
            // until 3 * PTO elapses, then transition to CLOSED.
            val drainingDelta =
                connection.drainingDeadlineMs?.let {
                    (it - nowForLossTimer).coerceAtLeast(0L)
                }
            val sleepMillis =
                minOf(
                    ptoMillis,
                    nextLossDelta ?: Long.MAX_VALUE,
                    idleDelta ?: Long.MAX_VALUE,
                    drainingDelta ?: Long.MAX_VALUE,
                )
            // Suspend until either: a wakeup arrives, or the timer expires.
            val woke =
                withTimeoutOrNull(sleepMillis) {
                    sendWakeup.receive()
                    Unit
                }
            if (woke == null) {
                // Distinguish draining-deadline / idle-timeout /
                // loss-timer / PTO expiry. RFC 9000 §10.2.2: the
                // draining period transitions to fully CLOSED once
                // 3 * PTO has elapsed since the peer's
                // CONNECTION_CLOSE — gives the peer's last
                // retransmits a chance to converge before we discard
                // state. Idle-timeout fires per §10.2.1 ("silently
                // closes — discarding the connection state without
                // sending a CONNECTION_CLOSE frame"). A loss-timer
                // wake just runs `detectAndRemoveLost`. A PTO wake
                // additionally bumps the consecutive-PTO counter and
                // arms the probe budget.
                if (connection.isDrainingExpired(nowMillis())) {
                    connection.markClosedExternally("draining period elapsed")
                    continue
                }
                if (idleTimeoutMs != null && connection.isIdleTimedOut(nowMillis())) {
                    connection.markClosedExternally(
                        "idle timeout ($idleTimeoutMs ms with no activity)",
                    )
                    continue
                }
                val pickedLossTimer = nextLossDelta != null && nextLossDelta < ptoMillis
                if (pickedLossTimer) {
                    handleLossTimerFired(connection)
                } else {
                    handlePtoFired(connection)
                }
            }
        }
    }

    /**
     * RFC 9002 §6.1.2 timer-driven loss detection. Re-runs
     * `detectAndRemoveLost` across each encryption level; any newly
     * time-threshold-lost packets dispatch their tokens to the
     * pending retransmit queues, and the next drain emits them.
     * Cheaper than [handlePtoFired] because no probe budget /
     * exponential backoff is needed — the peer ACKs that fix the
     * loss state arrive on their normal cadence.
     */
    private fun handleLossTimerFired(conn: QuicConnection) {
        val nowMs = conn.nowMillis()
        runLossDetectionForLevel(conn, conn.initial, EncryptionLevel.INITIAL, nowMs)
        runLossDetectionForLevel(conn, conn.handshake, EncryptionLevel.HANDSHAKE, nowMs)
        runLossDetectionForLevel(conn, conn.application, EncryptionLevel.APPLICATION, nowMs)
    }

    private fun runLossDetectionForLevel(
        conn: QuicConnection,
        state: LevelState,
        level: EncryptionLevel,
        nowMs: Long,
    ) {
        val largest = state.largestAckedPn ?: return
        val result = conn.lossDetection.detectAndRemoveLost(state.sentPackets, largest, nowMs)
        for (lostPacket in result.lost) {
            conn.onTokensLost(lostPacket.tokens)
        }
        if (result.lost.isNotEmpty()) {
            conn.qlogObserver.onLossDetected(level, result.lost.map { it.packetNumber })
        }
        state.nextLossTimeMs = result.nextLossTimeMs
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
        if (closeJob.load() != null) return
        // Build the teardown coroutine LAZY so we can race-test the CAS
        // without paying for a launched-and-cancelled Job on the loser.
        val teardown =
            parentScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                connection.close(0L, "")
                wakeup()
                val send = sendJob
                // Bounded wait for the send loop to flush CONNECTION_CLOSE.
                // Event-driven via [QuicConnection.closingDrainSignal] —
                // both `drainOutbound` (after building the close datagram)
                // and `markClosedExternally` (forced transition) complete
                // the deferred. Replaces an earlier 1 ms polling loop.
                // The timeout is the upper bound on how long close() blocks
                // if the writer is wedged.
                withTimeoutOrNull(CLOSE_FLUSH_TIMEOUT_MILLIS) {
                    connection.closingDrainSignal.await()
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
        if (closeJob.compareAndSet(null, teardown)) {
            teardown.start()
        } else {
            // A concurrent close() already installed the teardown Job; drop
            // ours without ever starting it. The winner's Job runs, this
            // call is a no-op (matching the original idempotent contract).
            teardown.cancel()
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
    // Increment FIRST so [requeueInflightForProbe]'s threshold check
    // sees the post-increment value. The increment must happen exactly
    // once per PTO event — not per call to [requeueInflightForProbe],
    // because the send loop calls that helper again between the first
    // and second probe (RFC 9002 §6.2.4) and that re-requeue is part
    // of the SAME PTO event.
    conn.consecutivePtoCount = (conn.consecutivePtoCount + 1).coerceAtMost(6)
    conn.pendingPing = true
    requeueInflightForProbe(conn)
    // RFC 9002 §6.2.4: the spec allows up to 2 ack-eliciting packets
    // per PTO. The first probe falls out of the next drain naturally
    // (we just requeued); the send loop watches [pendingProbePackets]
    // and re-requeues for the second probe. Set AFTER the requeue so
    // a concurrent reader of the field doesn't see "budget set,
    // nothing requeued yet" — the requeue is what makes the budget
    // meaningful.
    conn.pendingProbePackets = 2
}

/**
 * Move any sent-but-not-ACK'd CRYPTO / STREAM bytes back onto the
 * retransmit queue at every active encryption level so the next
 * [drainOutbound] re-emits them. Used by both [handlePtoFired] (the
 * initial PTO requeue) and the send loop's RFC 9002 §6.2.4 probe
 * budget (the re-requeue between the first and second probe
 * datagram). Idempotent on levels with nothing inflight, so calling
 * it for already-ACKed or already-discarded levels is harmless.
 *
 * The level walk mirrors [handlePtoFired]'s original inline
 * sequence: Handshake first (most-recent CRYPTO), then Initial
 * (oldest), then Application (1-RTT STREAM data + post-handshake
 * CRYPTO). The application branch holds [QuicConnection.streamsLock]
 * because [QuicConnection.requeueAllInflightStreamData] iterates
 * `streamsList`, which `openBidiStream` mutates under the same lock.
 */
internal suspend fun requeueInflightForProbe(conn: QuicConnection) {
    if (conn.handshake.sendProtection != null && !conn.handshake.keysDiscarded) {
        conn.requeueAllInflightCrypto(EncryptionLevel.HANDSHAKE)
    }
    if (conn.initial.sendProtection != null && !conn.initial.keysDiscarded) {
        conn.requeueAllInflightCrypto(EncryptionLevel.INITIAL)
    }
    // The PTO count is incremented in [handlePtoFired] BEFORE this
    // helper runs, so the threshold check below sees the post-increment
    // value (matching the constant's natural reading: "after N
    // consecutive PTOs with no progress, trigger migration on the Nth
    // PTO firing"). The send loop's between-probe re-requeue calls
    // this helper without bumping the count again — that's the same
    // PTO event, not a new one.
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
            // RFC 9000 §9 client-initiated path validation. After
            // [PATH_PROBE_PTO_THRESHOLD] consecutive PTOs without
            // any inbound ACK we suspect the path is dead (NAT
            // rebind, route flap, dead peer). If the peer has
            // issued spare CIDs via NEW_CONNECTION_ID, rotate to
            // one and emit a PATH_CHALLENGE on the new DCID. The
            // validator only triggers on the FIRST crossing of
            // the threshold per validation cycle — the
            // [PathValidator] internally rejects re-entry while
            // [PathValidationState.Validating] holds.
            //
            // Also check the §8.2.4 budget on any in-flight
            // validation: 3*PTO since the challenge went out
            // without a matching response means the new path is
            // also dead — abandon and let the next PTO try with
            // another CID (or surface the failure to the higher
            // layer).
            //
            // Bug-5 fix: use [conn.nowMillis] so a test-injected
            // virtual clock takes effect. The pre-fix shape called
            // [Clock.System.now()] directly, ignoring the
            // connection's clock supplier and breaking timing
            // assertions in unit tests.
            val nowMillis = conn.nowMillis()
            val maxAckDelayMs = conn.peerTransportParameters?.maxAckDelay ?: 0L
            val ptoBaseMs = conn.lossDetection.ptoBaseMs(maxAckDelayMs).coerceAtLeast(1L)
            conn.checkPathValidationTimeoutLocked(nowMillis)
            if (conn.consecutivePtoCount >= PATH_PROBE_PTO_THRESHOLD) {
                conn.triggerPathMigrationLocked(nowMillis = nowMillis, currentPtoMillis = ptoBaseMs)
            }
        }
    }
}

/**
 * RFC 9000 §9 / §10.1.2 — number of consecutive PTOs we tolerate on
 * the active path before assuming it's dead and probing a new one.
 * The QUIC RFC doesn't pin a specific value (the spec only says "an
 * endpoint that has previously discovered a particular path
 * works"); 2 matches Firefox neqo's `PATH_PROBE_PTO_THRESHOLD`
 * default and Chrome's behavior. Picking 1 is too aggressive
 * (single dropped packet trips a rotation); 4+ is too late (user
 * notices the silence).
 *
 * The check in [handlePtoFired] runs AFTER the consecutive-PTO
 * counter is incremented, so the threshold value is the count of
 * PTOs that fired without an inbound ACK arriving in between.
 * With value 2 migration triggers on the 2nd consecutive PTO.
 *
 * Once the threshold is crossed, [handlePtoFired] calls into
 * [QuicConnection.triggerPathMigrationLocked] which is itself
 * idempotent — a second crossing while validation is already in
 * flight is a no-op.
 */
internal const val PATH_PROBE_PTO_THRESHOLD: Int = 2
