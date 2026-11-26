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
package com.vitorpamplona.iossimulator.idb

import com.vitorpamplona.devicecore.grpc.GrpcChannel
import com.vitorpamplona.devicecore.grpc.GrpcException
import com.vitorpamplona.devicecore.grpc.ProtoReader
import com.vitorpamplona.devicecore.grpc.ProtoWriter
import com.vitorpamplona.devicecore.grpc.bytes
import com.vitorpamplona.devicecore.grpc.double
import com.vitorpamplona.devicecore.grpc.message
import com.vitorpamplona.devicecore.grpc.messages
import com.vitorpamplona.devicecore.grpc.string
import com.vitorpamplona.devicecore.grpc.varint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** The simulator's screen as idb describes it: pixels, and the points touches are given in. */
data class IdbScreen(
    val widthPixels: Int,
    val heightPixels: Int,
    /** Pixels per point: 2 or 3 on an iPhone. */
    val density: Double,
    val widthPoints: Int,
    val heightPoints: Int,
)

data class IdbTarget(
    val udid: String,
    val name: String,
    val state: String,
    val osVersion: String,
    /** The screen in its natural (portrait) orientation. */
    val screen: IdbScreen?,
    /** Whether the HID stream types text itself (`HIDEvent.text`); else text goes as key presses. */
    val typesText: Boolean,
)

/** A crash report idb found for the simulator's apps, newest last when listed. */
data class IdbCrash(
    /** The report's file name: what [IdbClient.crashShow] takes. */
    val name: String,
    val bundleId: String,
    val processName: String,
    val pid: Long,
    /** Seconds since 1970. */
    val timestamp: Long,
)

data class IdbApp(
    val bundleId: String,
    val name: String,
    val installType: String,
    val running: Boolean,
    val pid: Long,
)

/** The hardware buttons idb presses (`HIDButtonType`). */
enum class IdbButton(
    val wire: Int,
) {
    APPLE_PAY(0),
    HOME(1),
    LOCK(2),
    SIDE_BUTTON(3),
    SIRI(4),
    VOLUME_UP(6),
    VOLUME_DOWN(7),
}

/** One step of a gesture or key sequence, sent in one HID stream ([IdbClient.hid]). */
sealed class HidStep {
    /** A finger down (or still down, moved) at x/y in points, or lifted there. */
    data class Touch(
        val x: Double,
        val y: Double,
        val down: Boolean,
    ) : HidStep()

    data class Button(
        val button: IdbButton,
        val down: Boolean,
    ) : HidStep()

    /** A key by its USB HID usage code (page 7: `a` is 4, Return 40). */
    data class Key(
        val code: Int,
        val down: Boolean,
    ) : HidStep()

    /** A pause between the steps around it, applied by the companion so the pace holds. */
    data class Delay(
        val seconds: Double,
    ) : HidStep()

    /** Text the companion types as a hardware keyboard would ([IdbTarget.typesText]). */
    data class Text(
        val text: String,
    ) : HidStep()
}

/**
 * idb's companion over gRPC (`idb.CompanionService`, `proto/idb.proto` in facebook/idb), each
 * message encoded by hand like the emulator's. One companion serves one simulator.
 */
class IdbClient(
    private val channel: GrpcChannel,
) {
    /** HID streams one at a time: a touch's down must reach the simulator before its move. */
    private val hidLock = Mutex()

    private fun rpc(name: String) = "$SERVICE/$name"

    suspend fun describe(): IdbTarget {
        val answer = ProtoReader.read(channel.unary(rpc("describe"), ByteArray(0)))
        val target = answer.message(1)
        val companion = answer.message(2)
        val dims = target.message(3)
        val screen =
            if (dims.isEmpty()) {
                null
            } else {
                IdbScreen(
                    widthPixels = dims.varint(1).toInt(),
                    heightPixels = dims.varint(2).toInt(),
                    density = dims.double(3),
                    widthPoints = dims.varint(4).toInt(),
                    heightPoints = dims.varint(5).toInt(),
                )
            }
        return IdbTarget(
            udid = target.string(1),
            name = target.string(2),
            state = target.string(4),
            osVersion = target.string(6),
            screen = screen,
            typesText = companion.varint(13) != 0L,
        )
    }

    /** The screen as a PNG, at full size. */
    suspend fun screenshot(): ByteArray {
        // ScreenshotRequest{format=PNG(0)}: the default, so an empty message.
        val answer = ProtoReader.read(channel.unary(rpc("screenshot"), ByteArray(0)))
        val png = answer.bytes(1)
        if (png.isEmpty()) throw IOException("The companion's screenshot was empty")
        return png
    }

    /**
     * The screen as it changes, one JPEG per frame, at most [fps] a second and scaled by [scale]
     * (0..1]. The stream runs until the collector stops.
     */
    fun screenStream(
        fps: Int,
        quality: Double,
        scale: Double,
    ): Flow<ByteArray> =
        flow {
            val start =
                ProtoWriter()
                    .message(1) {
                        int(2, fps.toLong())
                        int(3, FORMAT_MINICAP)
                        double(4, quality)
                        if (scale < 1.0) double(5, scale)
                    }.bytes()
            val stop = ProtoWriter().message(2) {}.bytes()
            val frames = MinicapReader()
            channel.openStream(rpc("video_stream"), start, onClose = stop).collect { message ->
                // VideoStreamResponse{payload=2: Payload{data=2}}: read in place, the bytes copied only into the reader.
                val payload = ProtoReader.lengthField(message, 2) ?: return@collect
                val data = ProtoReader.lengthField(message, 2, payload.first, payload.last + 1) ?: return@collect
                if (!data.isEmpty()) frames.feed(message, data.first, data.last - data.first + 1).forEach { emit(it) }
            }
        }

    /** Sends [steps] in one HID stream; the companion applies each as it reads it, delays included. */
    suspend fun hid(steps: List<HidStep>) {
        if (steps.isEmpty()) return
        hidLock.withLock { channel.clientStream(rpc("hid"), steps.map(::encode)) }
    }

    suspend fun touch(
        x: Double,
        y: Double,
        down: Boolean,
    ) = hid(listOf(HidStep.Touch(x, y, down)))

    /** The screen's accessibility tree, as idb's nested JSON: an array of elements with `children`. */
    suspend fun accessibilityTree(): String {
        // AccessibilityInfoRequest{format=NESTED(1)}.
        val answer = ProtoReader.read(channel.unary(rpc("accessibility_info"), ProtoWriter().int(3, 1).bytes()))
        return answer.string(1)
    }

    /** Quarter turns from portrait the simulator is held in, or null when it can't say (face up, unknown). */
    suspend fun orientation(): Int? {
        val answer = ProtoReader.read(channel.unary(rpc("get_orientation"), ByteArray(0)))
        return when (answer.varint(1).toInt()) {
            1 -> 0
            3 -> 1
            2 -> 2
            4 -> 3
            else -> null
        }
    }

    /** Turns the simulator to [quarterTurns] from portrait: landscape left is one. */
    suspend fun setOrientation(quarterTurns: Int) {
        // HIDOrientationType: PORTRAIT 0, PORTRAIT_UPSIDE_DOWN 1, LANDSCAPE_LEFT 2, LANDSCAPE_RIGHT 3.
        val wire =
            when (Math.floorMod(quarterTurns, 4)) {
                0 -> 0
                1 -> 2
                2 -> 1
                else -> 3
            }
        try {
            channel.unary(rpc("set_orientation"), ProtoWriter().int(1, wire).bytes())
        } catch (e: GrpcException) {
            if (e.status != GrpcException.UNIMPLEMENTED) throw e
            // A companion before set_orientation took it as a HID event.
            hidLock.withLock { channel.clientStream(rpc("hid"), listOf(ProtoWriter().message(5) { int(1, wire) }.bytes())) }
        }
    }

    /** The crash reports of the simulator's apps since [sinceEpochSeconds] (0: all of them). */
    suspend fun crashes(sinceEpochSeconds: Long = 0): List<IdbCrash> {
        val answer = ProtoReader.read(channel.unary(rpc("crash_list"), ProtoWriter().int(1, sinceEpochSeconds).bytes()))
        return answer.messages(1).map { c ->
            IdbCrash(
                name = c.string(1),
                bundleId = c.string(2),
                processName = c.string(3),
                pid = c.varint(5),
                timestamp = c.varint(7),
            )
        }
    }

    /** A crash report's text (`.ips`): the exception, and each thread's frames. */
    suspend fun crashShow(name: String): String {
        val answer = ProtoReader.read(channel.unary(rpc("crash_show"), ProtoWriter().string(1, name).bytes()))
        return answer.string(2)
    }

    suspend fun apps(): List<IdbApp> {
        val answer = ProtoReader.read(channel.unary(rpc("list_apps"), ByteArray(0)))
        return answer.messages(1).map { a ->
            IdbApp(
                bundleId = a.string(1),
                name = a.string(2),
                installType = a.string(4),
                running = a.varint(5) == 2L,
                pid = a.varint(7),
            )
        }
    }

    companion object {
        const val SERVICE = "idb.CompanionService"

        /** `VideoStreamRequest.Format.MINICAP`: each JPEG frame after a little-endian length. */
        private const val FORMAT_MINICAP = 3

        fun encode(step: HidStep): ByteArray =
            when (step) {
                is HidStep.Touch -> press(step.down) { message(1) { message(1) { double(1, step.x).double(2, step.y) } } }
                is HidStep.Button -> press(step.down) { message(2) { int(1, step.button.wire) } }
                is HidStep.Key -> press(step.down) { message(3) { int(1, step.code) } }
                is HidStep.Delay -> ProtoWriter().message(3) { double(1, step.seconds) }.bytes()
                is HidStep.Text -> ProtoWriter().message(9) { string(1, step.text) }.bytes()
            }

        /** HIDEvent{press=1: HIDPress{action=1: HIDPressAction{…}, direction=2 (DOWN 0, UP 1)}}. */
        private fun press(
            down: Boolean,
            action: ProtoWriter.() -> Unit,
        ): ByteArray =
            ProtoWriter()
                .message(1) {
                    message(1, action)
                    int(2, if (down) 0 else 1)
                }.bytes()
    }
}

/**
 * Splits idb's minicap stream into its JPEG frames: a header (version, its own length, the pid, the
 * screen's sizes), then each frame as a 32-bit little-endian length and that many bytes. The
 * companion's messages don't line up with frames, so bytes are kept until a frame is whole.
 *
 * One growable buffer with read and write offsets: each byte is copied in once and each frame out
 * once, however many messages a frame arrives in.
 */
class MinicapReader {
    private var buffer = ByteArray(INITIAL)
    private var start = 0
    private var end = 0
    private var headerLeft = -1

    fun feed(data: ByteArray) = feed(data, 0, data.size)

    fun feed(
        data: ByteArray,
        offset: Int,
        length: Int,
    ): List<ByteArray> {
        append(data, offset, length)
        if (headerLeft < 0) {
            if (end - start < 2) return emptyList()
            headerLeft = buffer[start + 1].toInt() and 0xff
        }
        if (headerLeft > 0) {
            val skip = minOf(headerLeft, end - start)
            headerLeft -= skip
            start += skip
        }
        var frames: ArrayList<ByteArray>? = null
        while (headerLeft == 0 && end - start >= 4) {
            val b = buffer
            val at = start
            val size = (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8) or ((b[at + 2].toInt() and 0xff) shl 16) or ((b[at + 3].toInt() and 0xff) shl 24)
            if (size < 0 || size > MAX_FRAME) throw IOException("Bad minicap frame length $size")
            if (end - at - 4 < size) break
            (frames ?: ArrayList<ByteArray>(1).also { frames = it }).add(b.copyOfRange(at + 4, at + 4 + size))
            start = at + 4 + size
        }
        if (start == end) {
            start = 0
            end = 0
        }
        return frames ?: emptyList()
    }

    private fun append(
        data: ByteArray,
        offset: Int,
        length: Int,
    ) {
        if (end + length > buffer.size) {
            val kept = end - start
            // Room by moving what is kept to the front, else a bigger buffer (doubling, so a big
            // frame costs a few grows, not one per message).
            if (kept + length <= buffer.size && start > 0) {
                buffer.copyInto(buffer, 0, start, end)
            } else {
                buffer = buffer.copyInto(ByteArray(maxOf(buffer.size * 2, kept + length)), 0, start, end)
            }
            start = 0
            end = kept
        }
        data.copyInto(buffer, end, offset, offset + length)
        end += length
    }

    companion object {
        private const val INITIAL = 256 * 1024
        private const val MAX_FRAME = 64 * 1024 * 1024
    }
}
