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
    parentScope: CoroutineScope,
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
        while (connection.status != QuicConnection.Status.CLOSED) {
            val datagram = socket.receive() ?: break
            connection.lock.withLock {
                feedDatagram(connection, datagram, nowMillis())
            }
            // Inbound data may have produced new outbound (acks, crypto, etc.).
            wakeup()
        }
    }

    private suspend fun sendLoop() {
        while (connection.status != QuicConnection.Status.CLOSED) {
            connection.lock.withLock {
                while (true) {
                    val out = drainOutbound(connection, nowMillis()) ?: break
                    socket.send(out)
                }
            }
            // Suspend until the next wakeup — no busy polling.
            sendWakeup.receive()
        }
    }

    suspend fun close() {
        connection.close(0L, "")
        wakeup() // let the send loop emit CONNECTION_CLOSE
        scope.cancel()
        socket.close()
    }
}
