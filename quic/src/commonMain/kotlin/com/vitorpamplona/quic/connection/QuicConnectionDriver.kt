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
 * Synchronization: every public mutator on [QuicConnection] takes
 * `connection.lock`; the driver acquires the same lock around feed + drain.
 * That guarantees the read loop, send loop, and app coroutines never see a
 * mid-mutation state of the streams map / datagram queues / counters.
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

    fun start() {
        connection.start()
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
                connection.lock.withLock {
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
        // PTO budget: how long the loop will sleep before waking itself to
        // check for retransmission opportunities. RFC 9002 §6.2 — initial
        // PTO is roughly 3 × (smoothed RTT + max_ack_delay). We don't track
        // RTT yet, so use a conservative fixed value that doubles on each
        // consecutive timeout (Exponential backoff caps after ~6 timeouts).
        var ptoMillis = 1_000L
        while (connection.status != QuicConnection.Status.CLOSED) {
            connection.lock.withLock {
                while (true) {
                    val out = drainOutbound(connection, nowMillis()) ?: break
                    socket.send(out)
                }
            }
            // Suspend until either: a wakeup arrives, or the PTO timer expires.
            // The PTO wake ensures a single lost ClientHello doesn't wedge
            // the connection forever — eventually the loop wakes, the writer
            // re-emits Initial CRYPTO that's still in the send buffer (since
            // we don't free it until ACK), and the handshake retries.
            val woke =
                withTimeoutOrNull(ptoMillis) {
                    sendWakeup.receive()
                    Unit
                }
            ptoMillis =
                if (woke == null) {
                    (ptoMillis * 2).coerceAtMost(60_000L)
                } else {
                    1_000L
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
        parentScope.launch {
            connection.close(0L, "")
            wakeup()
            val send = sendJob
            // Bounded wait for the send loop to flush CONNECTION_CLOSE. We
            // don't want to hang forever if the writer is wedged — the timeout
            // is the upper bound on how long close() can block.
            withTimeoutOrNull(CLOSE_FLUSH_TIMEOUT_MILLIS) {
                // Spin until the writer has actually drained the queued close
                // (queues are empty AND the send loop has cycled at least
                // once). Easiest proxy: write was attempted and there's
                // nothing more to send. We approximate by giving the loop a
                // chance to drain by sleeping briefly. This is the one place
                // a short sleep is acceptable because we're racing a flush.
                while (true) {
                    val drained =
                        connection.lock.withLock {
                            // No more pending datagrams or stream bytes? Then
                            // CONNECTION_CLOSE has either been sent or there
                            // was nothing to send.
                            connection.pendingDatagramsLocked().isEmpty()
                        }
                    if (drained) break
                    kotlinx.coroutines.delay(1)
                }
            }
            // Now flip to CLOSED so both loops exit their while-guards.
            connection.markClosedExternally("driver close requested")
            wakeup()
            // Wait for both loops to actually exit — joinAll won't return
            // until the in-flight socket.send() (if any) completes.
            withTimeoutOrNull(CLOSE_FLUSH_TIMEOUT_MILLIS) {
                listOfNotNull(readJob, send).joinAll()
            }
            // Final teardown. By now both jobs have either exited cleanly or
            // exceeded the timeout — cancel guarantees they're done before we
            // close the socket.
            scope.cancel()
            socket.close()
        }
    }

    companion object {
        /** Upper bound on close() flush wait. Each phase (drain + join) gets up to this much. */
        private const val CLOSE_FLUSH_TIMEOUT_MILLIS = 250L
    }
}
