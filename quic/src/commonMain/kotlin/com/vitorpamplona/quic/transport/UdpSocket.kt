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
package com.vitorpamplona.quic.transport

/**
 * Connected UDP socket abstraction used by the QUIC connection.
 *
 * The socket is bound to an ephemeral local port and connected to one remote
 * peer. We do not support unconnected sockets, multipath, or address migration
 * — the QUIC connection uses exactly one 4-tuple for its lifetime.
 *
 * Implementations:
 *  - jvmAndroid: NIO `DatagramChannel` wrapped in suspend functions.
 *  - native (future): platform `socket()` / `recv()` / `send()` syscalls.
 */
expect class UdpSocket {
    /** Send one datagram to the connected peer. Returns the number of bytes written. */
    suspend fun send(payload: ByteArray): Int

    /**
     * Receive one datagram from the connected peer. Suspends until either a
     * packet arrives or the socket is closed. Returns null on close.
     *
     * The returned ByteArray is freshly allocated for each call.
     */
    suspend fun receive(): ByteArray?

    /** Close the socket. After close, [receive] returns null and [send] throws. */
    fun close()

    /** Local port the OS assigned to the socket. */
    val localPort: Int

    /**
     * Lifetime count of datagrams successfully returned by [receive].
     * Diagnostic-only — surfaces in
     * [com.vitorpamplona.quic.connection.QuicFlowControlSnapshot.udp]
     * so a test can correlate apparent stream loss against the
     * datagrams the kernel actually delivered to the application.
     */
    val receivedDatagramCount: Long

    /** Sum of payload bytes returned by [receive]. */
    val receivedByteCount: Long

    /**
     * Effective `SO_RCVBUF` value the kernel reports. On Linux the
     * application-requested value is doubled and then capped at
     * `rmem_max`, so this is what the kernel actually allocates.
     */
    val receiveBufferSizeBytes: Int

    companion object {
        /** Open a UDP socket connected to [host]:[port]. Throws on resolution / bind / connect failure. */
        suspend fun connect(
            host: String,
            port: Int,
        ): UdpSocket
    }
}
