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
package com.vitorpamplona.androiddevice.device

import com.vitorpamplona.androiddevice.adb.Adb
import com.vitorpamplona.androiddevice.adb.DeviceKey
import com.vitorpamplona.androiddevice.adb.ScreenSize
import com.vitorpamplona.androiddevice.emulator.EmulatorController
import com.vitorpamplona.androiddevice.emulator.EmulatorImage
import com.vitorpamplona.androiddevice.emulator.PixelFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A picture of the screen for the live view, in the orientation it is shown in. */
class ScreenFrame(
    val width: Int,
    val height: Int,
    val format: PixelFormat,
    val pixels: ByteArray,
    /** Quarter turns from the panel's natural orientation; the picture is already turned. */
    val rotation: Int,
)

enum class PointerAction { DOWN, MOVE, UP }

/**
 * A device's screen as a live view drives it: frames to draw, a pointer (mouse) to touch with, keys
 * and text. Where the pointer lands is given as a fraction of the frame shown ([fx], [fy] in
 * `0..1`), so the view needn't know the device's pixels.
 */
interface DeviceScreen {
    /** Whether frames are pushed as the screen changes (the emulator) rather than polled (adb). */
    val streams: Boolean

    fun frames(
        maxWidth: Int,
        maxHeight: Int,
    ): Flow<ScreenFrame>

    suspend fun pointer(
        action: PointerAction,
        fx: Float,
        fy: Float,
        frame: ScreenFrame,
    )

    suspend fun key(key: DeviceKey)

    suspend fun type(text: String)
}

/**
 * The emulator's screen through its gRPC endpoint: frames pushed as they change (RGBA, scaled by
 * the emulator to the view, ready to draw without decoding), touches as they move, keys typed on
 * its virtual keyboard.
 */
class EmulatorScreen(
    private val controller: EmulatorController,
    private val adb: Adb,
    private val serial: String,
    /** The display's natural size in physical pixels: what touches are measured in. */
    private val natural: ScreenSize,
) : DeviceScreen {
    override val streams = true

    override fun frames(
        maxWidth: Int,
        maxHeight: Int,
    ): Flow<ScreenFrame> =
        controller
            .screenshots(PixelFormat.RGBA8888, maxWidth, maxHeight)
            .filter { !it.isEmpty }
            .map { it.toFrame() }

    override suspend fun pointer(
        action: PointerAction,
        fx: Float,
        fy: Float,
        frame: ScreenFrame,
    ) {
        val (x, y) = toNatural(fx, fy, frame.rotation, natural)
        controller.touch(x, y, pressed = action != PointerAction.UP)
    }

    override suspend fun key(key: DeviceKey) {
        // The emulator's keyboard has no paste or wake key: those go through adb.
        if (key == DeviceKey.PASTE || key == DeviceKey.WAKE) adb.key(serial, key) else controller.key(key.w3c)
    }

    override suspend fun type(text: String) {
        if (text.all { it.code in 0x20..0x7e || it == '\n' || it == '\t' }) {
            controller.type(text)
        } else {
            // Its keyboard types ASCII only: anything else goes in by the clipboard.
            controller.setClipboard(text)
            adb.key(serial, DeviceKey.PASTE)
        }
    }

    companion object {
        /**
         * A point given as a fraction of a frame turned [rotation] quarter turns, in the display's
         * natural pixels: a landscape frame's left edge is the panel's top.
         */
        fun toNatural(
            fx: Float,
            fy: Float,
            rotation: Int,
            natural: ScreenSize,
        ): Pair<Int, Int> {
            val u = fx.coerceIn(0f, 1f)
            val v = fy.coerceIn(0f, 1f)
            val (nx, ny) =
                when (rotation and 3) {
                    1 -> (1 - v) to u
                    2 -> (1 - u) to (1 - v)
                    3 -> v to (1 - u)
                    else -> u to v
                }
            val x = (nx * natural.width).roundToInt().coerceIn(0, natural.width - 1)
            val y = (ny * natural.height).roundToInt().coerceIn(0, natural.height - 1)
            return x to y
        }

        fun EmulatorImage.toFrame() = ScreenFrame(width, height, format, pixels, rotation)
    }
}

/**
 * Any device's screen through adb alone: a screenshot as soon as the last one arrives (a few a
 * second), and a pointer that becomes a tap, a long press or a swipe when the button goes up, since
 * adb can't hold a finger down.
 */
class AdbScreen(
    private val adb: Adb,
    private val serial: String,
    private val minFrameIntervalMs: Long = 150,
    /** The slowest it looks at a screen that stopped changing. */
    private val idleFrameIntervalMs: Long = 1_000,
) : DeviceScreen {
    override val streams = false

    private var downAt: Pair<Float, Float>? = null
    private var downTime = 0L

    /** When the user last touched or typed: the screen is about to change, so look at full speed. */
    @Volatile
    private var lastInput = 0L

    /**
     * The wait before the next screenshot: [minFrameIntervalMs] right after input or a change, then
     * doubling up to [idleFrameIntervalMs] while the screen stays the same. Each screenshot costs the
     * phone a PNG encode and a megabyte or more over USB; an idle screen needn't be read 6 times a second.
     */
    internal fun nextInterval(unchangedFrames: Int): Long {
        if (System.currentTimeMillis() - lastInput < INPUT_BURST_MS) return minFrameIntervalMs
        return (minFrameIntervalMs shl unchangedFrames.coerceAtMost(4)).coerceAtMost(idleFrameIntervalMs)
    }

    override fun frames(
        maxWidth: Int,
        maxHeight: Int,
    ): Flow<ScreenFrame> =
        flow {
            var last: ByteArray? = null
            var failures = 0
            var unchanged = 0
            while (true) {
                val started = System.currentTimeMillis()
                try {
                    val frame = capture()
                    failures = 0
                    // An unchanged screen isn't drawn again, and is looked at less often.
                    if (last == null || !frame.pixels.contentEquals(last)) {
                        last = frame.pixels
                        unchanged = 0
                        emit(frame)
                    } else {
                        unchanged++
                    }
                } catch (e: IOException) {
                    if (++failures >= MAX_FAILURES) throw e
                    delay(RETRY_MS)
                }
                // Waits in short steps, so input arriving during an idle wait cuts it short.
                while (System.currentTimeMillis() - started < nextInterval(unchanged)) delay(INPUT_POLL_MS)
            }
        }

    /** Raw pixels while the device sends them (an emulator), else PNG; a device that can't sends PNG from then on. */
    @Volatile
    private var raw = serial.startsWith("emulator-")

    private suspend fun capture(): ScreenFrame {
        if (raw) {
            try {
                val screen = adb.screencapRaw(serial)
                return ScreenFrame(screen.width, screen.height, PixelFormat.RGBA8888, screen.rgba, 0)
            } catch (_: IOException) {
                raw = false
            }
        }
        val png = adb.screencapPng(serial)
        val size = PngSize.of(png) ?: throw IOException("Not a PNG")
        return ScreenFrame(size.width, size.height, PixelFormat.PNG, png, 0)
    }

    override suspend fun pointer(
        action: PointerAction,
        fx: Float,
        fy: Float,
        frame: ScreenFrame,
    ) {
        lastInput = System.currentTimeMillis()
        when (action) {
            PointerAction.DOWN -> {
                downAt = fx to fy
                downTime = System.currentTimeMillis()
            }
            PointerAction.MOVE -> Unit
            PointerAction.UP -> {
                val (sx, sy) = downAt ?: (fx to fy)
                downAt = null
                val held = System.currentTimeMillis() - downTime
                // The screenshot is full size and turned as the screen is: its pixels are the ones input takes.
                val x1 = (sx * frame.width).roundToInt()
                val y1 = (sy * frame.height).roundToInt()
                val x2 = (fx * frame.width).roundToInt()
                val y2 = (fy * frame.height).roundToInt()
                val moved = hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()) > TAP_SLOP_PX
                when {
                    moved -> adb.swipe(serial, x1, y1, x2, y2, held.toInt().coerceIn(MIN_SWIPE_MS, MAX_SWIPE_MS))
                    held >= LONG_PRESS_MS -> adb.swipe(serial, x1, y1, x1, y1, held.toInt().coerceAtMost(MAX_SWIPE_MS))
                    else -> adb.tap(serial, x1, y1)
                }
            }
        }
    }

    override suspend fun key(key: DeviceKey) {
        lastInput = System.currentTimeMillis()
        adb.key(serial, key)
    }

    override suspend fun type(text: String) {
        lastInput = System.currentTimeMillis()
        adb.type(serial, text)
    }

    companion object {
        private const val MAX_FAILURES = 5
        private const val INPUT_BURST_MS = 2_000L
        private const val INPUT_POLL_MS = 50L
        private const val RETRY_MS = 1_000L
        private const val TAP_SLOP_PX = 12.0
        private const val LONG_PRESS_MS = 500L
        private const val MIN_SWIPE_MS = 80
        private const val MAX_SWIPE_MS = 3_000
    }
}

/** A PNG's size, from its IHDR chunk. */
object PngSize {
    fun of(png: ByteArray): ScreenSize? {
        if (png.size < 24 || png[12] != 'I'.code.toByte() || png[13] != 'H'.code.toByte()) return null

        fun int(at: Int) = ((png[at].toInt() and 0xff) shl 24) or ((png[at + 1].toInt() and 0xff) shl 16) or ((png[at + 2].toInt() and 0xff) shl 8) or (png[at + 3].toInt() and 0xff)
        return ScreenSize(int(16), int(20))
    }
}
