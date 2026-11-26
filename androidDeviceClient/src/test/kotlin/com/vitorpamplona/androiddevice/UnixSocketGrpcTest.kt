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
package com.vitorpamplona.androiddevice

import com.vitorpamplona.androiddevice.grpc.GrpcChannel
import com.vitorpamplona.androiddevice.grpc.ProtoReader
import com.vitorpamplona.androiddevice.grpc.ProtoWriter
import com.vitorpamplona.androiddevice.grpc.UnixSocketFactory
import com.vitorpamplona.androiddevice.grpc.double
import com.vitorpamplona.androiddevice.grpc.string
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketHandler
import okhttp3.Headers.Companion.headersOf
import okhttp3.Protocol
import okio.Buffer
import okio.buffer
import java.net.Socket
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * gRPC over a Unix socket, as idb's companion listens (`--grpc-domain-sock`): the socket file is
 * piped to an h2c server here, so the bytes OkHttp writes through [UnixSocketFactory] are what a
 * TCP connection would carry.
 */
class UnixSocketGrpcTest {
    private lateinit var server: MockWebServer
    private lateinit var dir: Path
    private lateinit var socket: Path
    private lateinit var listener: ServerSocketChannel

    @BeforeTest
    fun start() {
        server = MockWebServer()
        server.protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
        server.start()
        dir = Files.createTempDirectory("grpc-unix")
        socket = dir.resolve("c.sock")
        listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket))
        thread(isDaemon = true) {
            while (listener.isOpen) {
                val client = runCatching { listener.accept() }.getOrNull() ?: break
                val tcp = Socket(server.hostName, server.port)
                val fromClient = Channels.newInputStream(client)
                val toClient = Channels.newOutputStream(client)
                // One thread each way; a unix channel's streams would block each other on one thread.
                thread(isDaemon = true) { runCatching { fromClient.copyTo(tcp.getOutputStream()) } }
                thread(isDaemon = true) { runCatching { tcp.getInputStream().copyTo(toClient) } }
            }
        }
    }

    @AfterTest
    fun stop() {
        listener.close()
        server.close()
        dir.toFile().deleteRecursively()
    }

    private fun channel() = GrpcChannel("idb-companion", 80, socketFactory = UnixSocketFactory(socket))

    private fun framed(vararg messages: ByteArray) = Buffer().apply { messages.forEach { write(GrpcChannel.frame(it)) } }

    private fun answer(vararg messages: ByteArray) =
        MockResponse
            .Builder()
            .addHeader("content-type", "application/grpc")
            .body(framed(*messages))
            .trailers(headersOf("grpc-status", "0"))
            .build()

    @Test
    fun unaryOverTheSocketFile() =
        runBlocking {
            server.enqueue(answer(ProtoWriter().string(1, "ABCD-1234").bytes()))

            val reply = ProtoReader.read(channel().unary("idb.CompanionService/describe", ByteArray(0)))

            assertEquals("ABCD-1234", reply.string(1))
            val request = server.takeRequest()
            assertEquals("/idb.CompanionService/describe", request.url.encodedPath)
            // The host only names the server: it is never looked up.
            assertEquals("idb-companion", request.url.host)
        }

    @Test
    fun aClientStreamSendsEachMessageFramed() =
        runBlocking {
            server.enqueue(answer(ByteArray(0)))
            val down = ProtoWriter().double(1, 12.5).bytes()
            val up = ProtoWriter().double(1, 40.0).bytes()

            channel().clientStream("idb.CompanionService/hid", listOf(down, up))

            val sent = server.takeRequest().body!!.toByteArray()
            assertContentEquals(GrpcChannel.frame(down) + GrpcChannel.frame(up), sent)
            assertEquals(12.5, ProtoReader.read(down).double(1))
        }

    @Test
    fun anOpenStreamKeepsSendingUntilTheCollectorStopsThenSaysGoodbye() =
        runBlocking {
            val received = LinkedBlockingQueue<ByteArray>()
            server.enqueue(
                MockResponse
                    .Builder()
                    .addHeader("content-type", "application/grpc")
                    .socketHandler(
                        object : SocketHandler {
                            override fun handle(socket: okio.Socket) {
                                val source = socket.source.buffer()
                                val sink = socket.sink.buffer()
                                // The client's first message, while its side stays open.
                                GrpcChannel.readMessage(source)?.let(received::add)
                                sink.write(GrpcChannel.frame(byteArrayOf(1)))
                                sink.write(GrpcChannel.frame(byteArrayOf(2)))
                                sink.flush()
                                // Then whatever it sends as it goes: the stop.
                                runCatching { GrpcChannel.readMessage(source)?.let(received::add) }
                                runCatching { sink.close() }
                            }
                        },
                    )
                    // A stream's length isn't known up front: no content-length, as a gRPC server sends it.
                    .removeHeader("Content-Length")
                    .build(),
            )
            val start = ProtoWriter().string(1, "start").bytes()
            val stop = ProtoWriter().string(1, "stop").bytes()

            val frames = withTimeout(10_000) { channel().openStream("idb.CompanionService/video_stream", start, onClose = stop).take(2).toList() }

            assertEquals(listOf(1, 2), frames.map { it.single().toInt() })
            assertEquals("start", ProtoReader.read(assertNotNull(received.poll(5, TimeUnit.SECONDS))).string(1))
            assertEquals("stop", ProtoReader.read(assertNotNull(received.poll(5, TimeUnit.SECONDS))).string(1))
        }
}
