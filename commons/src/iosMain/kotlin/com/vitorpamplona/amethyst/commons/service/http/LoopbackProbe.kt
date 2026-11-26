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
package com.vitorpamplona.amethyst.commons.service.http

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Probes for the app's own loopback listeners (Tor's SOCKS port, the media relay), which iOS may
 * reclaim while the app is suspended: a reclaimed listener refuses the connection, so a connect is
 * the honest test. Each probe has its own short timeout and never throws.
 */
object LoopbackProbe {
    private const val LOOPBACK = "127.0.0.1"
    const val TIMEOUT_MS = 2_000L

    /**
     * Whether a SOCKS5 server accepts on 127.0.0.1:[port]: it answers a "no authentication" greeting
     * with version 5 and that method. Only the greeting is sent, no CONNECT, so nothing leaves the
     * phone.
     */
    suspend fun isSocks5Listening(
        port: Int,
        timeoutMs: Long = TIMEOUT_MS,
    ): Boolean =
        connect(port, timeoutMs) { socket ->
            val output = socket.openWriteChannel(autoFlush = true)
            val input = socket.openReadChannel()
            output.writeFully(byteArrayOf(SOCKS_VERSION, 1, NO_AUTH))
            input.readByte() == SOCKS_VERSION && input.readByte() == NO_AUTH
        }

    /** Whether anything accepts a TCP connection on 127.0.0.1:[port]. */
    suspend fun isTcpListening(
        port: Int,
        timeoutMs: Long = TIMEOUT_MS,
    ): Boolean = connect(port, timeoutMs) { true }

    private suspend fun connect(
        port: Int,
        timeoutMs: Long,
        check: suspend (Socket) -> Boolean,
    ): Boolean =
        withTimeoutOrNull(timeoutMs) {
            val selector = SelectorManager(Dispatchers.IO)
            try {
                val socket = aSocket(selector).tcp().connect(LOOPBACK, port)
                try {
                    check(socket)
                } finally {
                    socket.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            } finally {
                selector.close()
            }
        } ?: false

    private const val SOCKS_VERSION: Byte = 5
    private const val NO_AUTH: Byte = 0
}
