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
package com.vitorpamplona.androiddevice.emulator

import com.vitorpamplona.androiddevice.grpc.GrpcChannel
import com.vitorpamplona.androiddevice.grpc.ProtoField
import com.vitorpamplona.androiddevice.grpc.ProtoReader
import com.vitorpamplona.androiddevice.grpc.ProtoWriter
import com.vitorpamplona.androiddevice.grpc.bytes
import com.vitorpamplona.androiddevice.grpc.message
import com.vitorpamplona.androiddevice.grpc.messages
import com.vitorpamplona.androiddevice.grpc.string
import com.vitorpamplona.androiddevice.grpc.varint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

/** How the pixels of an [EmulatorImage] are laid out. */
enum class PixelFormat(
    val wire: Int,
) {
    PNG(0),
    RGBA8888(1),
    RGB888(2),
    ;

    companion object {
        fun of(wire: Int) = entries.firstOrNull { it.wire == wire } ?: PNG
    }
}

/**
 * One picture of the emulator's display, as `getScreenshot`/`streamScreenshot` send it. [rotation]
 * is in quarter turns (0 portrait, 1 landscape = 90°, 2 reverse portrait, 3 reverse landscape): the
 * image is already turned that way, while touches are given in the panel's natural (unrotated)
 * frame. An inactive display sends an empty image (0×0).
 */
class EmulatorImage(
    val format: PixelFormat,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val pixels: ByteArray,
    val seq: Long,
) {
    val isEmpty: Boolean get() = width == 0 || height == 0 || pixels.isEmpty()
}

/** What `getStatus` says: the emulator's version, whether Android has finished booting. */
data class EmulatorStatus(
    val version: String,
    val uptimeMs: Long,
    val booted: Boolean,
    val config: Map<String, String>,
)

/** A key as the emulator names it (W3C `KeyboardEvent.key` values; the phone keys are Android's). */
enum class EmulatorKeyEvent(
    val wire: Int,
) {
    DOWN(0),
    UP(1),
    PRESS(2),
}

/**
 * The emulator's `android.emulation.control.EmulatorController` service: a live picture of the
 * display and input, the way Android Studio's Running Devices window drives it. Messages are
 * written by field number from the proto the emulator ships (`emulator/lib/emulator_controller.proto`).
 */
class EmulatorController(
    private val channel: GrpcChannel,
) {
    private suspend fun call(
        method: String,
        message: ByteArray = ByteArray(0),
    ): List<ProtoField> = ProtoReader.read(channel.unary("$SERVICE/$method", message))

    suspend fun status(): EmulatorStatus {
        val fields = call("getStatus")
        val config =
            (fields.messages(8) + fields.messages(7)).associate { it.string(1) to it.string(2) }
        return EmulatorStatus(
            version = fields.string(1),
            uptimeMs = fields.varint(2),
            booted = fields.varint(3) != 0L,
            config = config,
        )
    }

    /**
     * One picture, scaled by the emulator to fit [maxWidth]×[maxHeight] (0 for the display's own
     * size), keeping its aspect ratio.
     */
    suspend fun screenshot(
        format: PixelFormat = PixelFormat.PNG,
        maxWidth: Int = 0,
        maxHeight: Int = 0,
        display: Int = 0,
    ): EmulatorImage = image(call("getScreenshot", imageFormat(format, maxWidth, maxHeight, display)))

    /** A new picture whenever the display changes. See [screenshot] for the sizes. */
    fun screenshots(
        format: PixelFormat = PixelFormat.RGB888,
        maxWidth: Int = 0,
        maxHeight: Int = 0,
        display: Int = 0,
    ): Flow<EmulatorImage> =
        channel
            .serverStream("$SERVICE/streamScreenshot", imageFormat(format, maxWidth, maxHeight, display))
            .map { image(ProtoReader.read(it)) }

    /**
     * One finger at ([x], [y]) in the display's natural (unrotated) pixels: down or moving while
     * [pressed], lifted when not. [finger] is its slot (0 to 9), kept across one gesture.
     */
    suspend fun touch(
        x: Int,
        y: Int,
        pressed: Boolean,
        finger: Int = 0,
        display: Int = 0,
    ) {
        call(
            "sendTouch",
            ProtoWriter()
                .message(1) {
                    int(1, x)
                    int(2, y)
                    int(3, finger)
                    int(4, if (pressed) PRESSURE else 0)
                }.int(2, display)
                .bytes(),
        )
    }

    /**
     * How the display is turned now, in quarter turns: from a one-pixel screenshot, which carries
     * the rotation and costs next to nothing.
     */
    suspend fun rotation(): Int = screenshot(PixelFormat.RGB888, 1, 1).rotation

    /**
     * Turns the emulated device itself to [quarterTurns] (0 portrait, 1 landscape, 2 upside down, 3
     * the other landscape), by its rotation sensor (`setPhysicalModel`, ROTATION): the angle on z the
     * proto gives each skin rotation. Android turns its screen to follow (with auto-rotate on), and
     * the frames come turned, with [EmulatorImage.rotation] saying so, as the emulator's own rotate
     * buttons do it.
     */
    suspend fun setRotation(quarterTurns: Int) {
        val z =
            when (quarterTurns and 3) {
                1 -> 90f
                2 -> -180f
                3 -> -90f
                else -> 0f
            }
        call(
            "setPhysicalModel",
            ProtoWriter()
                .int(1, PHYSICAL_ROTATION)
                .message(3) { packedFloats(1, floatArrayOf(0f, 0f, z)) }
                .bytes(),
        )
    }

    /** A W3C key name ("GoBack", "GoHome", "AppSwitch", "Power", "Enter", "Backspace", "a"…). */
    suspend fun key(
        key: String,
        event: EmulatorKeyEvent = EmulatorKeyEvent.PRESS,
    ) {
        call(
            "sendKey",
            ProtoWriter()
                .int(2, event.wire)
                .string(4, key)
                .bytes(),
        )
    }

    /** Types [text]. The emulator types printable ASCII, newlines and tabs only, and drops the rest. */
    suspend fun type(text: String) {
        call("sendKey", ProtoWriter().int(2, EmulatorKeyEvent.PRESS.wire).string(5, text).bytes())
    }

    /** Puts [text] on the guest's clipboard: a way in for text the keyboard can't type. */
    suspend fun setClipboard(text: String) {
        call("setClipboard", ProtoWriter().string(1, text).bytes())
    }

    /**
     * Loads the snapshot [id] (`default_boot` is the quick-boot state the AVD saved when it last
     * shut down): the device goes back to how it was then. Throws [IOException] when the emulator
     * says it failed.
     */
    suspend fun loadSnapshot(id: String = DEFAULT_BOOT) {
        val answer = ProtoReader.read(channel.unary("$SNAPSHOTS/LoadSnapshot", ProtoWriter().string(1, id).bytes()))
        if (answer.varint(3) == 0L) throw IOException("The emulator couldn't load snapshot $id: ${answer.bytes(4).toString(Charsets.UTF_8).ifBlank { "no reason given" }}")
    }

    private fun imageFormat(
        format: PixelFormat,
        maxWidth: Int,
        maxHeight: Int,
        display: Int,
    ): ByteArray =
        ProtoWriter()
            .int(1, format.wire)
            .int(3, maxWidth)
            .int(4, maxHeight)
            .int(5, display)
            .bytes()

    companion object {
        const val SERVICE = "android.emulation.control.EmulatorController"
        const val SNAPSHOTS = "android.emulation.control.SnapshotService"

        /** `PhysicalModelValue.PhysicalType.ROTATION`. */
        private const val PHYSICAL_ROTATION = 1L

        /** The quick-boot snapshot the emulator saves on a normal exit and boots from. */
        const val DEFAULT_BOOT = "default_boot"

        /** The nominal full pressure, `[0, 1024]` in the proto. */
        private const val PRESSURE = 1024

        internal fun image(fields: List<ProtoField>): EmulatorImage {
            val format = fields.message(1)
            // Width and height moved into the format; older emulators still fill the top-level ones.
            val width = format.varint(3).takeIf { it > 0 } ?: fields.varint(2)
            val height = format.varint(4).takeIf { it > 0 } ?: fields.varint(3)
            return EmulatorImage(
                format = PixelFormat.of(format.varint(1).toInt()),
                width = width.toInt(),
                height = height.toInt(),
                rotation = format.message(2).varint(1).toInt() and 3,
                pixels = fields.bytes(4),
                seq = fields.varint(5),
            )
        }
    }
}
