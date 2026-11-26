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

import com.vitorpamplona.androiddevice.emulator.EmulatorController
import com.vitorpamplona.androiddevice.emulator.wire
import com.vitorpamplona.devicecore.grpc.GrpcChannel
import com.vitorpamplona.devicecore.grpc.GrpcException
import com.vitorpamplona.devicecore.grpc.ProtoReader
import com.vitorpamplona.devicecore.grpc.ProtoWriter
import com.vitorpamplona.devicecore.grpc.message
import com.vitorpamplona.devicecore.grpc.varint
import com.vitorpamplona.devicecore.screen.PixelFormat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.Protocol
import okio.Buffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The gRPC framing against a real HTTP/2 (h2c) server, the way the emulator serves it. */
class GrpcChannelTest {
    private lateinit var server: MockWebServer

    @BeforeTest
    fun start() {
        server = MockWebServer()
        server.protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
        server.start()
    }

    @AfterTest
    fun stop() = server.close()

    private fun channel(token: String? = null) = GrpcChannel(server.hostName, server.port, token)

    private fun framed(vararg messages: ByteArray) = Buffer().apply { messages.forEach { write(GrpcChannel.frame(it)) } }

    private fun image(
        width: Int,
        height: Int,
        rotation: Int,
        seq: Int,
    ) = ProtoWriter()
        .message(1) {
            int(1, PixelFormat.RGB888.wire)
            message(2) { int(1, rotation) }
            int(3, width)
            int(4, height)
        }.bytes(4, ByteArray(width * height * 3) { 7 })
        .int(5, seq)
        .bytes()

    @Test
    fun unaryCallSendsFramedMessageAndReadsTheAnswer() =
        runBlocking {
            val status =
                ProtoWriter()
                    .string(1, "36.1.2")
                    .int(2, 1234L)
                    .bool(3, true)
                    .bytes()
            server.enqueue(
                MockResponse
                    .Builder()
                    .addHeader("content-type", "application/grpc")
                    .body(framed(status))
                    .trailers(headersOf("grpc-status", "0"))
                    .build(),
            )

            val result = EmulatorController(channel("secret")).status()

            assertEquals("36.1.2", result.version)
            assertEquals(1234L, result.uptimeMs)
            assertTrue(result.booted)
            val request = server.takeRequest()
            assertEquals("/android.emulation.control.EmulatorController/getStatus", request.url.encodedPath)
            assertEquals("Bearer secret", request.headers["authorization"])
            assertEquals("application/grpc", request.headers["content-type"])
            // An empty message is still one frame: five bytes of header.
            assertContentEquals(GrpcChannel.frame(ByteArray(0)), request.body!!.toByteArray())
        }

    @Test
    fun aSignedTokenReplacesTheConsoleToken() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .addHeader("content-type", "application/grpc")
                    .body(framed(ByteArray(0)))
                    .trailers(headersOf("grpc-status", "0"))
                    .build(),
            )
            var signed = 0
            val channel = GrpcChannel(server.hostName, server.port, authorization = { "Bearer jwt-${++signed}" })

            EmulatorController(channel).status()

            assertEquals("Bearer jwt-1", server.takeRequest().headers["authorization"])
        }

    @Test
    fun aStatusInTheTrailersFailsTheCall() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(Buffer())
                    .trailers(headersOf("grpc-status", "16", "grpc-message", "bad token"))
                    .build(),
            )
            val e = assertFailsWith<GrpcException> { EmulatorController(channel()).status() }
            assertEquals(GrpcException.UNAUTHENTICATED, e.status)
        }

    @Test
    fun aTrailersOnlyAnswerFailsTheCall() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .addHeader("grpc-status", "12")
                    .addHeader("grpc-message", "nope")
                    .body(Buffer())
                    .build(),
            )
            val e = assertFailsWith<GrpcException> { EmulatorController(channel()).status() }
            assertEquals(GrpcException.UNIMPLEMENTED, e.status)
        }

    @Test
    fun serverStreamEmitsEachFrame() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(framed(image(4, 2, 0, 0), image(4, 2, 1, 3)))
                    .trailers(headersOf("grpc-status", "0"))
                    .build(),
            )

            val frames = EmulatorController(channel()).screenshots(PixelFormat.RGB888, 400, 800).toList()

            assertEquals(listOf(0L, 3L), frames.map { it.seq })
            assertEquals(1, frames[1].rotation)
            assertEquals(4, frames[0].width)
            assertEquals(24, frames[0].pixels.size)
            // The format asked for: RGB888 scaled into 400x800.
            val sent = server.takeRequest().body!!.toByteArray()
            val asked = ProtoReader.read(sent.copyOfRange(5, sent.size))
            assertEquals(2L, asked.varint(1))
            assertEquals(400L, asked.varint(3))
            assertEquals(800L, asked.varint(4))
        }

    @Test
    fun touchWritesTheTouchMessage() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(framed(ByteArray(0)))
                    .trailers(headersOf("grpc-status", "0"))
                    .build(),
            )

            EmulatorController(channel()).touch(100, 200, pressed = true)

            val body = server.takeRequest().body!!.toByteArray()
            val fields = ProtoReader.read(body.copyOfRange(5, body.size))
            val touch = fields.message(1)
            assertEquals(100L, touch.varint(1))
            assertEquals(200L, touch.varint(2))
            assertEquals(1024L, touch.varint(4))
        }
}
