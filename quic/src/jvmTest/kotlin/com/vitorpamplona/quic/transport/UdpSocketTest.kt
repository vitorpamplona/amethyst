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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UdpSocketTest {
    // A plain UDP peer on loopback that echoes one datagram back.
    private val peer = DatagramSocket(InetSocketAddress("127.0.0.1", 0))

    @AfterTest
    fun tearDown() {
        runCatching { peer.close() }
    }

    private fun threadNames(): List<String> = Thread.getAllStackTraces().keys.map { it.name }

    private fun hasThreadPrefixed(prefix: String) = threadNames().any { it.startsWith(prefix) }

    @Test
    fun `round trips a datagram over loopback`() {
        runBlocking {
            val socket = UdpSocket.connect("127.0.0.1", peer.localPort)
            try {
                // Peer thread: receive one packet and echo it back to the sender.
                val echo =
                    Thread {
                        val buf = ByteArray(2048)
                        val incoming = DatagramPacket(buf, buf.size)
                        peer.receive(incoming)
                        peer.send(DatagramPacket(incoming.data, incoming.length, incoming.socketAddress))
                    }.apply {
                        isDaemon = true
                        start()
                    }

                socket.send(byteArrayOf(1, 2, 3, 4))
                val reply = withTimeoutOrNull(3_000) { socket.receive() }
                echo.join(1_000)

                assertTrue(reply != null && reply.contentEquals(byteArrayOf(1, 2, 3, 4)), "should echo the datagram back")
                assertEquals(1, socket.receivedDatagramCount)
            } finally {
                socket.close()
            }
        }
    }

    @Test
    fun `blocking receive runs on a dedicated thread, not Dispatchers-IO`() {
        runBlocking {
            val socket = UdpSocket.connect("127.0.0.1", peer.localPort)
            try {
                // Park a receive with no incoming datagram — it blocks in recvfrom
                // on the socket's dedicated recv thread, not a Dispatchers.IO worker.
                val pending = async(Dispatchers.IO) { socket.receive() }
                // Give the receive time to reach the blocking call on its own thread.
                delay(200)

                assertTrue(hasThreadPrefixed("quic-udp-recv"), "a dedicated recv thread must carry the blocking receive")

                // Closing unblocks the parked receive (returns null), proving the
                // blocking call was on the dedicated thread and is released on close.
                socket.close()
                val result = withTimeoutOrNull(2_000) { pending.await() }
                assertNull(result, "receive() must return null once the socket closes")
            } finally {
                socket.close()
            }
        }
    }

    @Test
    fun `close shuts down the dedicated threads`() {
        runBlocking {
            val socket = UdpSocket.connect("127.0.0.1", peer.localPort)
            try {
                // The single-thread executors spawn their thread lazily on first
                // use, so touch BOTH directions to bring both threads up.
                socket.send(byteArrayOf(0))
                val recv = launch(Dispatchers.IO) { socket.receive() }
                delay(200)
                assertTrue(
                    hasThreadPrefixed("quic-udp-recv") && hasThreadPrefixed("quic-udp-send"),
                    "both dedicated threads must be up while open",
                )

                socket.close()
                recv.join()

                // The executors shut down on close; their threads must exit promptly.
                val gone =
                    withTimeoutOrNull(2_000) {
                        while (hasThreadPrefixed("quic-udp-recv") || hasThreadPrefixed("quic-udp-send")) delay(25)
                        true
                    }
                assertTrue(gone == true, "dedicated recv/send threads must be gone after close() — else they leak per connection")
            } finally {
                socket.close()
            }
        }
    }

    @Test
    fun `after close receive returns null and send throws`() {
        runBlocking {
            val socket = UdpSocket.connect("127.0.0.1", peer.localPort)
            socket.close()
            assertNull(socket.receive(), "receive() returns null after close")
            assertFailsWith<ClosedChannelException> { socket.send(byteArrayOf(9)) }
        }
    }
}
