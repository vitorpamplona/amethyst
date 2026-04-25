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

    fun start() {
        connection.start()
        scope.launch { readLoop() }
        scope.launch { sendLoop() }
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
     * Cleanly tear down the driver. Safe to call from inside the driver scope —
     * the actual cancel-and-close runs on [parentScope] so the caller's coroutine
     * (which may itself be in [scope]) doesn't get cancelled before the close
     * completes.
     */
    fun close() {
        // Drive the close on the parent scope so we don't cancel our own caller.
        parentScope.launch {
            try {
                connection.close(0L, "")
                wakeup() // let the send loop emit CONNECTION_CLOSE
                // Give the send loop one tick to flush the close packet, then tear down.
                kotlinx.coroutines.yield()
            } finally {
                scope.cancel()
                socket.close()
            }
        }
    }
}
