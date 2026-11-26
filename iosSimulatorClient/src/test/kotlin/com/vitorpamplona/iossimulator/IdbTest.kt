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
package com.vitorpamplona.iossimulator

import com.vitorpamplona.devicecore.grpc.GrpcChannel
import com.vitorpamplona.devicecore.grpc.ProtoReader
import com.vitorpamplona.devicecore.grpc.ProtoWriter
import com.vitorpamplona.devicecore.grpc.double
import com.vitorpamplona.devicecore.grpc.message
import com.vitorpamplona.devicecore.grpc.string
import com.vitorpamplona.devicecore.grpc.varint
import com.vitorpamplona.devicecore.input.DeviceKey
import com.vitorpamplona.devicecore.screen.JpegSize
import com.vitorpamplona.iossimulator.device.HidKeys
import com.vitorpamplona.iossimulator.device.SimulatorScreen
import com.vitorpamplona.iossimulator.idb.HidStep
import com.vitorpamplona.iossimulator.idb.IdbButton
import com.vitorpamplona.iossimulator.idb.IdbClient
import com.vitorpamplona.iossimulator.idb.IdbCompanion
import com.vitorpamplona.iossimulator.idb.IdbScreen
import com.vitorpamplona.iossimulator.idb.MinicapReader
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.Protocol
import okio.Buffer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdbTest {
    private fun jpeg(
        width: Int,
        height: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", out)
        return out.toByteArray()
    }

    private fun le(n: Int) = byteArrayOf(n.toByte(), (n ushr 8).toByte(), (n ushr 16).toByte(), (n ushr 24).toByte())

    @Test
    fun minicapFramesComeWholeHoweverTheBytesArrive() {
        val header =
            ByteArray(24).also {
                it[0] = 1
                it[1] = 24
            }
        val one = jpeg(30, 60)
        val two = jpeg(60, 30)
        val stream = header + le(one.size) + one + le(two.size) + two
        val reader = MinicapReader()
        // Cut at every awkward place: inside the header, the length, a frame.
        val pieces = listOf(stream.copyOfRange(0, 1), stream.copyOfRange(1, 26), stream.copyOfRange(26, 40), stream.copyOfRange(40, stream.size))
        val frames = pieces.flatMap { reader.feed(it) }
        assertEquals(2, frames.size)
        assertContentEquals(one, frames[0])
        assertContentEquals(two, frames[1])
    }

    @Test
    fun aJpegsSizeComesFromItsFrameHeader() {
        val size = JpegSize.of(jpeg(390, 844))
        assertEquals(390, size?.width)
        assertEquals(844, size?.height)
        assertNull(JpegSize.of(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun hidEventsAreEncodedAsIdbsProtoHasThem() {
        // HIDEvent{press{action{touch{point{x, y}}}, direction DOWN(0)}}
        val down = ProtoReader.read(IdbClient.encode(HidStep.Touch(100.5, 200.0, down = true))).message(1)
        val point = down.message(1).message(1).message(1)
        assertEquals(100.5, point.double(1))
        assertEquals(200.0, point.double(2))
        assertEquals(0L, down.varint(2))
        // Lifted: direction UP(1).
        assertEquals(1L, ProtoReader.read(IdbClient.encode(HidStep.Touch(1.0, 1.0, down = false))).message(1).varint(2))
        // A button: action field 2, HIDButton{button}.
        assertEquals(
            1L,
            ProtoReader
                .read(IdbClient.encode(HidStep.Button(IdbButton.HOME, true)))
                .message(1)
                .message(1)
                .message(2)
                .varint(1),
        )
        // A key: action field 3, HIDKey{keycode}.
        assertEquals(
            40L,
            ProtoReader
                .read(IdbClient.encode(HidStep.Key(40, true)))
                .message(1)
                .message(1)
                .message(3)
                .varint(1),
        )
        assertEquals(0.25, ProtoReader.read(IdbClient.encode(HidStep.Delay(0.25))).message(3).double(1))
        assertEquals("héllo", ProtoReader.read(IdbClient.encode(HidStep.Text("héllo"))).message(9).string(1))
    }

    @Test
    fun describeReadsTheScreenAndWhatTheCompanionCanDo() =
        runBlocking {
            MockWebServer().use { server ->
                server.protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
                server.start()
                val answer =
                    ProtoWriter()
                        .message(1) {
                            string(1, "9F3B")
                            string(2, "iPhone 17 Pro")
                            message(3) {
                                int(1, 1206)
                                int(2, 2622)
                                double(3, 3.0)
                                int(4, 402)
                                int(5, 874)
                            }
                            string(4, "Booted")
                            string(6, "iOS 26.0")
                        }.message(2) { bool(13, true) }
                        .bytes()
                server.enqueue(
                    MockResponse
                        .Builder()
                        .addHeader("content-type", "application/grpc")
                        .body(Buffer().write(GrpcChannel.frame(answer)))
                        .trailers(headersOf("grpc-status", "0"))
                        .build(),
                )
                val target = IdbClient(GrpcChannel(server.hostName, server.port)).describe()
                assertEquals("iPhone 17 Pro", target.name)
                assertEquals(IdbScreen(1206, 2622, 3.0, 402, 874), target.screen)
                assertTrue(target.typesText)
                assertEquals("/idb.CompanionService/describe", server.takeRequest().url.encodedPath)
            }
        }

    @Test
    fun theCompanionSaysWhereItListens() {
        assertEquals(IdbCompanion.Listening.Socket("/tmp/amy-idb1/9F3B5D2A.sock"), IdbCompanion.parseListening("""{"grpc_path":"/tmp/amy-idb1/9F3B5D2A.sock"}"""))
        assertEquals(IdbCompanion.Listening.Port(10882), IdbCompanion.parseListening("""{"grpc_swift_port":10882,"grpc_port":10882}"""))
        assertNull(IdbCompanion.parseListening("Companion started"))
        assertNull(IdbCompanion.parseListening("""{"udid":"9F3B"}"""))
    }

    @Test
    fun textBecomesUsKeyPresses() {
        val steps = HidKeys.typing("aB!")
        assertEquals(
            listOf(
                HidStep.Key(4, true),
                HidStep.Key(4, false),
                HidStep.Key(HidKeys.SHIFT, true),
                HidStep.Key(5, true),
                HidStep.Key(5, false),
                HidStep.Key(HidKeys.SHIFT, false),
                HidStep.Key(HidKeys.SHIFT, true),
                HidStep.Key(30, true),
                HidStep.Key(30, false),
                HidStep.Key(HidKeys.SHIFT, false),
            ),
            steps,
        )
        assertTrue(HidKeys.typeable("user@example.com\n"))
        assertFalse(HidKeys.typeable("olá"))
    }

    @Test
    fun keysMapToButtonsOrKeyboardUsages() {
        assertEquals(listOf(HidStep.Button(IdbButton.HOME, true), HidStep.Button(IdbButton.HOME, false)), HidKeys.steps(DeviceKey.HOME))
        assertEquals(listOf(HidStep.Key(42, true), HidStep.Key(42, false)), HidKeys.steps(DeviceKey.BACKSPACE))
        // Back is a gesture on iOS, not a key.
        assertNull(HidKeys.steps(DeviceKey.BACK))
        assertEquals(5, HidKeys.steps(DeviceKey.RECENTS)?.size)
    }

    @Test
    fun theStreamIsScaledToFitTheViewEitherWayItIsTurned() {
        val phone = IdbScreen(1206, 2622, 3.0, 402, 874)
        val screen = SimulatorScreen(IdbClient(GrpcChannel("unused", 1)), phone, typesText = true)
        // A tall view: fits the portrait height.
        assertEquals(1311.0 / 2622, screen.streamScale(800, 1311), 1e-9)
        // Bigger than the screen: never scaled up.
        assertEquals(1.0, screen.streamScale(4000, 4000))
        // A wide view: the landscape fit is the larger, and it wins.
        assertEquals(1000.0 / 2622, screen.streamScale(1000, 603), 1e-9)
    }

    @Test
    fun bigFramesInSmallMessagesComeWholeFromInsideTheirMessages() {
        val header = ByteArray(24).also { it[1] = 24 }
        // Bigger than the reader's first buffer, so it grows while the frame arrives.
        val big = ByteArray(700_000) { (it % 251).toByte() }
        val small = jpeg(10, 10)
        val stream = header + le(big.size) + big + le(small.size) + small
        val reader = MinicapReader()
        val frames = ArrayList<ByteArray>()
        var at = 0
        while (at < stream.size) {
            val n = minOf(16_384, stream.size - at)
            // Each piece sits inside a bigger array, as the frame's bytes sit inside the gRPC message.
            val message = ByteArray(n + 7).also { stream.copyInto(it, 3, at, at + n) }
            frames += reader.feed(message, 3, n)
            at += n
        }
        assertEquals(2, frames.size)
        assertContentEquals(big, frames[0])
        assertContentEquals(small, frames[1])
    }
}
