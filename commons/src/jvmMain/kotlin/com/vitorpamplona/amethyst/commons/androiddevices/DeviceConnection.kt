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
package com.vitorpamplona.amethyst.commons.androiddevices

import com.vitorpamplona.androiddevice.adb.Adb
import com.vitorpamplona.androiddevice.adb.ScreenSize
import com.vitorpamplona.androiddevice.adb.WmSize
import com.vitorpamplona.androiddevice.device.AdbScreen
import com.vitorpamplona.androiddevice.device.DeviceScreen
import com.vitorpamplona.androiddevice.device.EmulatorScreen
import com.vitorpamplona.androiddevice.device.PngSize
import com.vitorpamplona.androiddevice.emulator.EmulatorController
import com.vitorpamplona.androiddevice.emulator.PixelFormat
import com.vitorpamplona.androiddevice.grpc.GrpcException
import com.vitorpamplona.androiddevice.ui.UiTree
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A screenshot sized for the model, and the scale from the device's pixels to its pixels. */
class AgentShot(
    val png: ByteArray,
    val width: Int,
    val height: Int,
    val scale: Double,
)

/**
 * One device as the app drives it: adb always, and the emulator's gRPC endpoint when this app can
 * open it (an emulator it started, or one started with `-grpc-use-token`).
 *
 * The agent works in one coordinate space for every tool: the pixels of the screenshots it gets,
 * which are the device's own pixels shrunk ([scale]) to what the model sees without resizing them
 * itself. The UI tree's bounds and the taps it asks for are in that space too.
 */
class DeviceConnection(
    val serial: String,
    val adb: Adb,
    val emulator: EmulatorController?,
) {
    @Volatile
    private var size: Pair<WmSize, Long>? = null

    /**
     * The size of the screen as it was last shown (screenshot or UI tree), turned as the screen
     * was: where an agent's tap lands as a fraction of the picture, for the panel to show it.
     */
    @Volatile
    var lastShownSize: ScreenSize? = null
        private set

    suspend fun size(): WmSize {
        size?.let { (value, at) -> if (System.currentTimeMillis() - at < SIZE_TTL_MS) return value }
        return adb.screenSize(serial).also { size = it to System.currentTimeMillis() }
    }

    /** From the device's pixels to the agent's ([AgentShot] pixels). Turning the screen keeps it. */
    suspend fun scale(): Double = agentScale(size().logical)

    @Volatile
    private var cachedScreen: DeviceScreen? = null

    /**
     * The live view: the emulator's stream when it can be opened, else adb screenshots. One per
     * connection, so a key press from the panel doesn't build a new one (and ask adb its size).
     */
    suspend fun screen(): DeviceScreen = cachedScreen ?: (emulator?.let { EmulatorScreen(it, adb, serial, size().physical) } ?: AdbScreen(adb, serial)).also { cachedScreen = it }

    /**
     * Agent input, in the device's own (logical, turned) pixels. On an emulator whose gRPC endpoint
     * answers, a touch is a millisecond-scale call; `adb shell input` starts a JVM on the device for
     * each one (half a second or more), so it is only the fallback.
     */
    suspend fun tap(
        x: Int,
        y: Int,
    ) = gesture(listOf(x to y), holdMs = TAP_HOLD_MS) { adb.tap(serial, x, y) }

    suspend fun longPress(
        x: Int,
        y: Int,
        holdMs: Int,
    ) = gesture(listOf(x to y), holdMs = holdMs.toLong()) { adb.swipe(serial, x, y, x, y, holdMs) }

    suspend fun swipe(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Int,
    ) {
        val steps = (durationMs / SWIPE_STEP_MS).toInt().coerceIn(2, MAX_SWIPE_STEPS)
        val path = (0..steps).map { i -> (x1 + (x2 - x1) * i / steps) to (y1 + (y2 - y1) * i / steps) }
        gesture(path, holdMs = 0, stepMs = durationMs.toLong() / steps) { adb.swipe(serial, x1, y1, x2, y2, durationMs) }
    }

    /**
     * One finger along [path] over the emulator's gRPC endpoint: down at the first point, held
     * [holdMs], moved [stepMs] apart, lifted at the last. Falls back to [viaAdb] without an
     * endpoint, or when it fails.
     */
    private suspend fun gesture(
        path: List<Pair<Int, Int>>,
        holdMs: Long,
        stepMs: Long = 0,
        viaAdb: suspend () -> Unit,
    ) {
        val controller = emulator ?: return viaAdb()
        try {
            val rotation = controller.rotation()
            val turned = size().let { if (rotation % 2 == 1) it.logical.swapped() else it.logical }
            val natural = size().physical

            fun at(p: Pair<Int, Int>) = EmulatorScreen.toNatural(p.first.toFloat() / turned.width, p.second.toFloat() / turned.height, rotation, natural)
            val (sx, sy) = at(path.first())
            controller.touch(sx, sy, pressed = true)
            if (holdMs > 0) delay(holdMs)
            path.drop(1).forEach { p ->
                if (stepMs > 0) delay(stepMs)
                val (x, y) = at(p)
                controller.touch(x, y, pressed = true)
            }
            val (ex, ey) = at(path.last())
            controller.touch(ex, ey, pressed = false)
        } catch (e: IOException) {
            Log.w(TAG, "The emulator's touch failed; asking adb", e)
            viaAdb()
        }
    }

    /** A picture of the screen at the agent's scale, as PNG. */
    suspend fun agentShot(): AgentShot {
        val scale = scale()
        val logical = size().logical
        val longEdge = (max(logical.width, logical.height) * scale).roundToInt()
        emulator?.let { controller ->
            try {
                // The emulator scales it itself; a box of the long edge on both sides fits either orientation.
                val image = controller.screenshot(PixelFormat.PNG, longEdge, longEdge)
                if (!image.isEmpty) {
                    lastShownSize = ScreenSize((image.width / scale).roundToInt(), (image.height / scale).roundToInt())
                    return AgentShot(image.pixels, image.width, image.height, scale)
                }
            } catch (e: GrpcException) {
                Log.w(TAG, "The emulator's screenshot failed; asking adb", e)
            } catch (e: IOException) {
                Log.w(TAG, "The emulator's screenshot failed; asking adb", e)
            }
        }
        val png = adb.screencapPng(serial)
        PngSize.of(png)?.let { lastShownSize = it }
        return withContext(Dispatchers.Default) { scaledPng(png, scale) }
    }

    suspend fun uiTree(): UiTree {
        val tree = withContext(Dispatchers.Default) { UiTree.parse(adb.uiDump(serial)) }
        // The first node spans the screen, turned as it is.
        tree.nodes
            .firstOrNull()
            ?.bounds
            ?.let { if (!it.isEmpty) lastShownSize = ScreenSize(it.right, it.bottom) }
        return tree
    }

    companion object {
        private const val TAG = "DeviceConnection"
        private const val SIZE_TTL_MS = 30_000L
        private const val TAP_HOLD_MS = 40L
        private const val SWIPE_STEP_MS = 16L
        private const val MAX_SWIPE_STEPS = 60

        /** The model sees an image up to this long edge and this many pixels without shrinking it. */
        const val MAX_EDGE = 1568
        const val MAX_PIXELS = 1_150_000

        fun agentScale(size: ScreenSize): Double {
            if (size.width <= 0 || size.height <= 0) return 1.0
            val byEdge = MAX_EDGE.toDouble() / max(size.width, size.height)
            val byArea = sqrt(MAX_PIXELS.toDouble() / (size.width.toLong() * size.height))
            return min(1.0, min(byEdge, byArea))
        }

        /** [png] shrunk by [scale] (when it's below 1), as PNG. */
        fun scaledPng(
            png: ByteArray,
            scale: Double,
        ): AgentShot {
            val source = ImageIO.read(png.inputStream()) ?: throw IOException("The device's screenshot isn't an image")
            if (scale >= 1.0) return AgentShot(png, source.width, source.height, 1.0)
            val width = max(1, (source.width * scale).roundToInt())
            val height = max(1, (source.height * scale).roundToInt())
            val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g = scaled.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g.drawImage(source, 0, 0, width, height, null)
            } finally {
                g.dispose()
            }
            val out = ByteArrayOutputStream()
            if (!ImageIO.write(scaled, "png", out)) throw IOException("Could not encode the screenshot")
            return AgentShot(out.toByteArray(), width, height, scale)
        }
    }
}
