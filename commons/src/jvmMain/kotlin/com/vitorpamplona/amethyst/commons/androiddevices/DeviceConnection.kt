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

    /** The live view: the emulator's stream when it can be opened, else adb screenshots. */
    suspend fun screen(): DeviceScreen = emulator?.let { EmulatorScreen(it, adb, serial, size().physical) } ?: AdbScreen(adb, serial)

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
