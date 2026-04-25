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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the UDP socket and runs the read + send loops for a [QuicConnection].
 *
 * Lifecycle:
 *   - [open] connects the UDP socket, calls [QuicConnection.start], spawns
 *     read + send loops, and suspends until handshake completes.
 *   - [close] cancels the loops and closes the socket.
 */
class QuicConnectionDriver(
    val connection: QuicConnection,
    private val socket: UdpSocket,
    parentScope: CoroutineScope,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job + Dispatchers.IO)
    private val sendLock = Mutex()

    fun start() {
        connection.start()
        scope.launch { readLoop() }
        scope.launch { sendLoop() }
    }

    private suspend fun readLoop() {
        while (true) {
            val datagram = socket.receive() ?: break
            sendLock.withLock {
                feedDatagram(connection, datagram, nowMillis())
            }
        }
    }

    private suspend fun sendLoop() {
        while (connection.status != QuicConnection.Status.CLOSED) {
            sendLock.withLock {
                while (true) {
                    val out = drainOutbound(connection, nowMillis()) ?: break
                    socket.send(out)
                }
            }
            // Tiny sleep to avoid tight-spin between drains; in practice, application
            // writes (via stream.send.enqueue + queueDatagram) wake the loop next tick.
            delay(2)
        }
    }

    fun close() {
        connection.close(0L, "")
        scope.cancel()
        socket.close()
    }
}
