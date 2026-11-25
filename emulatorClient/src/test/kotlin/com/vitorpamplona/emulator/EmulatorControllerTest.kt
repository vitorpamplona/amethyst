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
package com.vitorpamplona.emulator

import com.vitorpamplona.emulator.grpc.GrpcFraming
import com.vitorpamplona.emulator.grpc.GrpcStatusException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okio.Buffer
import okio.buffer
import okio.source
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/** End to end over a real cleartext HTTP/2 (prior knowledge) server. */
class EmulatorControllerTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun controller(token: String? = "tok") = EmulatorController(server.port, token, client)

    private fun grpcOk(vararg messages: ByteArray): MockResponse =
        MockResponse
            .Builder()
            .code(200)
            .addHeader("content-type", "application/grpc")
            .body(Buffer().write(GrpcFraming.frameAll(messages.toList())))
            .trailers(headersOf("grpc-status", "0"))
            .build()

    private fun takeRequest(): RecordedRequest = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "no request" }

    /** The messages in a recorded gRPC request body. */
    private fun messagesOf(request: RecordedRequest): List<ByteArray> {
        val source =
            request.body!!
                .toByteArray()
                .inputStream()
                .source()
                .buffer()
        return generateSequence { GrpcFraming.readMessage(source) }.toList()
    }

    private fun frame(seq: Long) =
        EmulatorFrame(
            width = 2,
            height = 1,
            encoding = ImageEncoding.RGB888,
            pixels = byteArrayOf(1, 2, 3, 4, 5, seq.toByte()),
            seq = seq,
            timestampUs = 1000 + seq,
            rotation = SkinRotation.LANDSCAPE,
            display = 0,
        )

    @Test
    fun getStatusSendsAGrpcRequestAndReadsTheTrailers() =
        runBlocking {
            val status = EmulatorStatus(version = "35.1", uptimeMs = 5000, booted = true, hardwareConfig = mapOf("hw.lcd.width" to "1080"))
            server.enqueue(grpcOk(EmulatorProto.encodeStatus(status)))

            val got = withTimeout(10.seconds) { controller().getStatus() }
            assertEquals(status.copy(vmConfig = VmConfiguration()), got)

            val request = takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/android.emulation.control.EmulatorController/getStatus", request.url.encodedPath)
            assertEquals("HTTP/2", request.version.uppercase())
            assertEquals("application/grpc", request.headers["content-type"])
            assertEquals("trailers", request.headers["te"])
            assertEquals("Bearer tok", request.headers["authorization"])
            // google.protobuf.Empty: one zero-length message.
            assertContentEquals(byteArrayOf(0, 0, 0, 0, 0), request.body!!.toByteArray())
        }

    @Test
    fun noTokenSendsNoAuthorization() =
        runBlocking {
            server.enqueue(grpcOk(ByteArray(0)))
            withTimeout(10.seconds) { controller(token = null).sendMouse(1, 2, 1) }
            val request = takeRequest()
            assertNull(request.headers["authorization"])
            assertEquals("/android.emulation.control.EmulatorController/sendMouse", request.url.encodedPath)
            assertContentEquals(EmulatorProto.mouseEvent(1, 2, 1, 0), messagesOf(request).single())
        }

    @Test
    fun trailersOnlyErrorBecomesAnException() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .addHeader("content-type", "application/grpc")
                    .addHeader("grpc-status", "16")
                    .addHeader("grpc-message", "bad%20token")
                    .build(),
            )
            val e = assertFailsWith<GrpcStatusException> { withTimeout(10.seconds) { controller().getStatus() } }
            assertEquals(GrpcStatusException.UNAUTHENTICATED, e.status)
            assertEquals("bad token", e.grpcMessage)
            assertEquals("getStatus", e.method)
        }

    @Test
    fun errorInTrailersBecomesAnException() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .addHeader("content-type", "application/grpc")
                    .body(Buffer().write(GrpcFraming.frame(ByteArray(0))))
                    .trailers(headersOf("grpc-status", "12", "grpc-message", "no such method"))
                    .build(),
            )
            val e = assertFailsWith<GrpcStatusException> { withTimeout(10.seconds) { controller().pressKey(KeyboardEvent.GO_HOME) } }
            assertEquals(GrpcStatusException.UNIMPLEMENTED, e.status)
            assertEquals("no such method", e.grpcMessage)
        }

    @Test
    fun nonGrpcHttpAnswerBecomesAnException() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(404)
                    .body("nope")
                    .build(),
            )
            val e = assertFailsWith<GrpcStatusException> { withTimeout(10.seconds) { controller().getVmState() } }
            assertEquals(404, e.httpCode)
            assertEquals(GrpcStatusException.UNKNOWN, e.status)
        }

    @Test
    fun streamScreenshotDeliversEveryFrameThenEnds() =
        runBlocking {
            val frames = (0L until 5L).map { frame(it * 2) }
            server.enqueue(grpcOk(*frames.map { EmulatorProto.encodeImage(it) }.toTypedArray()))

            val controller = controller()
            // Every message, before conflation.
            val raw = withTimeout(10.seconds) { controller.rawScreenshots(ImageEncoding.RGB888, 640, 0, 0).toList() }
            assertEquals(frames.map { it.seq }, raw.map { EmulatorProto.decodeImage(it).seq })

            val request = takeRequest()
            assertEquals("/android.emulation.control.EmulatorController/streamScreenshot", request.url.encodedPath)
            assertContentEquals(EmulatorProto.imageFormat(ImageEncoding.RGB888, 640, 0, 0), messagesOf(request).single())
        }

    @Test
    fun screenshotsDecodesFramesAndEndsAtTheTrailers() =
        runBlocking {
            val frames = (0L until 3L).map { frame(it) }
            server.enqueue(grpcOk(*frames.map { EmulatorProto.encodeImage(it) }.toTypedArray()))
            val got = withTimeout(10.seconds) { controller().screenshots(ImageEncoding.RGB888).toList() }
            // Conflation may skip frames, never reorder them, and the last one always arrives.
            assertEquals(2L, got.last().seq)
            assertEquals(got.map { it.seq }.sorted(), got.map { it.seq })
            val last = got.last()
            assertEquals(2, last.width)
            assertEquals(1, last.height)
            assertEquals(SkinRotation.LANDSCAPE, last.rotation)
            assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 2), last.pixels)
        }

    @Test
    fun streamErrorAfterFramesIsThrown() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .addHeader("content-type", "application/grpc")
                    .body(Buffer().write(GrpcFraming.frame(EmulatorProto.encodeImage(frame(0)))))
                    .trailers(headersOf("grpc-status", "14", "grpc-message", "shutting down"))
                    .build(),
            )
            val e = assertFailsWith<GrpcStatusException> { withTimeout(10.seconds) { controller().screenshots().toList() } }
            assertEquals(GrpcStatusException.UNAVAILABLE, e.status)
        }

    @Test
    fun cancellingAStreamLeavesTheClientUsable() =
        runBlocking {
            // ~30 MB: more than HTTP/2 flow control lets the server send before the client
            // reads, so the stream is still open on the server when the collector cancels it.
            val big = ByteArray(100_000)
            val frames = (0L until 300L).map { EmulatorProto.encodeImage(frame(it).let { f -> EmulatorFrame(2, 1, f.encoding, big, f.seq, 0, f.rotation, 0) }) }
            server.enqueue(grpcOk(*frames.toTypedArray()))
            server.enqueue(grpcOk(EmulatorProto.vmRunState(VmRunState.RUNNING)))

            val controller = controller()
            val first = withTimeout(10.seconds) { controller.screenshots().first() }
            assertEquals(2, first.width)
            assertEquals(VmRunState.RUNNING, withTimeout(10.seconds) { controller.getVmState() })
        }

    @Test
    fun touchKeyWheelAndRotationGoToTheirMethods() =
        runBlocking {
            repeat(5) { server.enqueue(grpcOk(ByteArray(0))) }
            val controller = controller()
            withTimeout(10.seconds) {
                controller.sendTouch(listOf(Touch(10, 20, identifier = 1, pressure = 5)))
                controller.sendKey(KeyboardEvent.press(KeyboardEvent.GO_BACK))
                controller.sendWheel(0, -120)
                controller.rotateTo(SkinRotation.LANDSCAPE)
                controller.setVmState(VmRunState.PAUSED)
            }
            val calls = (0 until 5).map { takeRequest() }
            assertEquals(
                listOf("sendTouch", "sendKey", "injectWheel", "setPhysicalModel", "setVmState"),
                calls.map { it.url.encodedPath.substringAfterLast('/') },
            )
            assertContentEquals(EmulatorProto.touchEvent(listOf(Touch(10, 20, identifier = 1, pressure = 5)), 0), messagesOf(calls[0]).single())
            assertContentEquals(EmulatorProto.keyboardEvent(KeyboardEvent.press("GoBack")), messagesOf(calls[1]).single())
            assertContentEquals(EmulatorProto.wheelEvent(0, -120, 0), messagesOf(calls[2]).single())
            assertEquals(listOf(0f, 0f, 90f), EmulatorProto.decodePhysicalModelValues(messagesOf(calls[3]).single()))
            assertEquals(VmRunState.PAUSED, EmulatorProto.decodeVmRunState(messagesOf(calls[4]).single()))
        }
}
