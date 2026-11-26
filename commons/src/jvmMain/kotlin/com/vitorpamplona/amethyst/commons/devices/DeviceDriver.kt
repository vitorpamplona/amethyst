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
package com.vitorpamplona.amethyst.commons.devices

import com.vitorpamplona.devicecore.crash.DeviceCrash
import com.vitorpamplona.devicecore.input.DeviceKey
import com.vitorpamplona.devicecore.screen.DeviceScreen
import com.vitorpamplona.devicecore.screen.ScreenSize
import com.vitorpamplona.devicecore.ui.UiTree
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A device's log as the agent reads it: what it shows, and the lines. */
class DeviceLog(
    val head: String,
    val text: String,
)

/**
 * One device as the panel and the agents drive it, whatever it is: an Android emulator or phone
 * (`AndroidConnection`: adb and the emulator's gRPC), or an iOS simulator (`SimulatorConnection`:
 * simctl and idb). Apps are named by their Android package or iOS bundle id.
 *
 * Every x/y here is in the device's own pixels, turned as its screen is now; [scale] takes them
 * to the agent's (the pixels of [agentShot]).
 */
interface DeviceDriver {
    val serial: String
    val platform: DevicePlatform

    /** Whether [screen] streams as the screen changes (live), rather than being polled. */
    val liveScreen: Boolean

    /** The screen's size as last shown (screenshot or UI tree), for where an agent's touch lands. */
    val lastShownSize: ScreenSize?

    /** From the device's pixels to the agent's ([AgentShot] pixels). */
    suspend fun scale(): Double

    /** The screen in the device's pixels, turned as it is now. */
    suspend fun screenSize(): ScreenSize

    /** Quarter turns from portrait. */
    suspend fun rotation(): Int

    suspend fun rotate(quarterTurns: Int)

    /** The live view. */
    suspend fun screen(): DeviceScreen

    suspend fun tap(
        x: Int,
        y: Int,
    )

    suspend fun longPress(
        x: Int,
        y: Int,
        holdMs: Int,
    )

    suspend fun swipe(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Int,
    )

    suspend fun press(
        key: DeviceKey,
        longPress: Boolean = false,
    )

    suspend fun type(text: String) = screen().type(text)

    /** A picture of the screen at the agent's scale, as PNG. */
    suspend fun agentShot(): AgentShot

    /** The screen at full size, as PNG: for the user to attach to a message. */
    suspend fun screenshotPng(): ByteArray

    suspend fun uiTree(): UiTree

    /** Opens [url] (in [app], when given); returns what the device said. */
    suspend fun openUrl(
        url: String,
        app: String?,
    ): String

    /** Installs an APK or an `.app`; returns what the device said. */
    suspend fun install(file: File): String

    suspend fun launch(app: String)

    suspend fun stopApp(app: String)

    /** Erases [app]'s data; returns what the device said. */
    suspend fun clearData(app: String): String

    /** Whether [app] runs now; null when the device didn't say. */
    suspend fun isRunning(app: String): Boolean?

    /**
     * The device's latest log: [lines] of [app]'s process when given and running, at [level]
     * (V, D, I, W, E, F) and above; with [crashesOnly], the crashes alone.
     */
    suspend fun log(
        lines: Int,
        app: String?,
        level: Char,
        crashesOnly: Boolean,
    ): DeviceLog

    /** The crashes the device has a record of, oldest first: each has an [DeviceCrash.id] that stays. */
    suspend fun crashes(): List<DeviceCrash>
}

/** A screenshot sized for the model, and the scale from the device's pixels to its pixels. */
class AgentShot(
    val png: ByteArray,
    val width: Int,
    val height: Int,
    val scale: Double,
)

/** Screenshots the size a model reads without shrinking them itself: the agent's coordinate space on every platform. */
object AgentImages {
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
