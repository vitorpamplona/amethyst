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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JVM/Android UDP socket using blocking [DatagramChannel] dispatched onto
 * [Dispatchers.IO]. We don't use NIO selectors because each QUIC connection
 * has exactly one socket and one receive loop — Selector doesn't pay for
 * itself at this scale.
 *
 * The receive buffer is sized to 64 KiB (max IPv4/IPv6 datagram); QUIC packets
 * cap at MTU (~1500 in practice).
 */
actual class UdpSocket private constructor(
    private val channel: DatagramChannel,
    private val remote: InetSocketAddress,
) {
    private val closed = AtomicBoolean(false)

    // Sized to typical Ethernet MTU + a bit; QUIC tops out at ~1500 in practice
    // and any larger inbound frame is dropped as malformed anyway. The previous
    // 64 KiB buffer was wasteful per connection.
    private val readBuf = ByteBuffer.allocate(2048)

    actual val localPort: Int
        get() = (channel.localAddress as InetSocketAddress).port

    actual suspend fun send(payload: ByteArray): Int =
        withContext(Dispatchers.IO) {
            if (closed.get()) throw ClosedChannelException()
            val buf = ByteBuffer.wrap(payload)
            channel.send(buf, remote)
        }

    actual suspend fun receive(): ByteArray? =
        withContext(Dispatchers.IO) {
            if (closed.get()) return@withContext null
            try {
                // No synchronized — only the read loop touches readBuf, by
                // contract. The previous synchronized block was pointless.
                readBuf.clear()
                channel.receive(readBuf) ?: return@withContext null
                readBuf.flip()
                val out = ByteArray(readBuf.remaining())
                readBuf.get(out)
                out
            } catch (_: ClosedChannelException) {
                null
            }
        }

    actual fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                channel.close()
            } catch (_: Throwable) {
                // already closed
            }
        }
    }

    actual companion object {
        actual suspend fun connect(
            host: String,
            port: Int,
        ): UdpSocket =
            withContext(Dispatchers.IO) {
                val address = InetAddress.getByName(host)
                val remote = InetSocketAddress(address, port)
                val channel = DatagramChannel.open()
                channel.configureBlocking(true)
                channel.bind(InetSocketAddress(0)) // ephemeral
                // We use receive()/send(addr) instead of channel.connect() so that
                // sendDatagram-style flows can still be implemented on the same
                // socket if we ever need them. For the pure client use-case this
                // is identical in latency.
                UdpSocket(channel, remote)
            }
    }
}
