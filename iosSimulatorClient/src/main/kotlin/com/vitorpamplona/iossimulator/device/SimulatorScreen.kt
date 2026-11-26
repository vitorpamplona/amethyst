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
package com.vitorpamplona.iossimulator.device

import com.vitorpamplona.devicecore.grpc.GrpcException
import com.vitorpamplona.devicecore.input.DeviceKey
import com.vitorpamplona.devicecore.screen.DeviceScreen
import com.vitorpamplona.devicecore.screen.JpegSize
import com.vitorpamplona.devicecore.screen.PixelFormat
import com.vitorpamplona.devicecore.screen.PngSize
import com.vitorpamplona.devicecore.screen.PointerAction
import com.vitorpamplona.devicecore.screen.ScreenFrame
import com.vitorpamplona.devicecore.screen.ScreenSize
import com.vitorpamplona.iossimulator.idb.HidStep
import com.vitorpamplona.iossimulator.idb.IdbClient
import com.vitorpamplona.iossimulator.idb.IdbScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/**
 * An iOS simulator's screen for the live view, through idb: the companion streams JPEG frames
 * (minicap), scaled on the Mac to the view, and touches go in as the simulator's own digitizer
 * events, in points. A companion that can't stream is asked for screenshots instead.
 */
class SimulatorScreen(
    private val idb: IdbClient,
    /** The screen in its natural (portrait) orientation. */
    private val natural: IdbScreen,
    /** Whether the companion types text itself; else text is US key presses. */
    private val typesText: Boolean,
    private val fps: Int = 30,
    private val quality: Double = 0.75,
    /** Quarter turns from portrait the simulator is held in now: where the back swipe starts. */
    private val rotation: suspend () -> Int = { 0 },
) : DeviceScreen {
    override val streams = true

    @Volatile
    private var polling = false

    override fun frames(
        maxWidth: Int,
        maxHeight: Int,
    ): Flow<ScreenFrame> =
        flow {
            if (!polling) {
                var any = false
                try {
                    idb.screenStream(fps, quality, streamScale(maxWidth, maxHeight)).collect { jpeg ->
                        val size = JpegSize.of(jpeg) ?: return@collect
                        any = true
                        emit(ScreenFrame(size.width, size.height, PixelFormat.JPEG, jpeg, rotationOf(size)))
                    }
                    if (any) return@flow
                } catch (e: IOException) {
                    // A companion that streams nothing (an old one, or no frames from this runtime) is
                    // looked at by screenshots; one that streamed and stopped has stopped.
                    if (any) throw e
                    if (e is GrpcException && e.status == GrpcException.CANCELLED) throw e
                }
                polling = true
            }
            var last: ByteArray? = null
            while (true) {
                val png = idb.screenshot()
                if (last == null || !png.contentEquals(last)) {
                    last = png
                    val size = PngSize.of(png) ?: throw IOException("The simulator's screenshot isn't a PNG")
                    emit(ScreenFrame(size.width, size.height, PixelFormat.PNG, png, rotationOf(size)))
                }
                delay(POLL_MS)
            }
        }

    /** The scale that fits the screen in [maxWidth]x[maxHeight] whichever way it is turned. */
    internal fun streamScale(
        maxWidth: Int,
        maxHeight: Int,
    ): Double {
        val long = max(natural.widthPixels, natural.heightPixels).toDouble()
        val short = min(natural.widthPixels, natural.heightPixels).toDouble()
        if (long <= 0 || maxWidth <= 0 || maxHeight <= 0) return 1.0
        val portrait = min(maxWidth / short, maxHeight / long)
        val landscape = min(maxWidth / long, maxHeight / short)
        return min(1.0, max(portrait, landscape)).coerceAtLeast(MIN_SCALE)
    }

    /** A frame wider than it is tall, on a panel that isn't, is the screen turned a quarter. */
    private fun rotationOf(size: ScreenSize): Int = if ((size.width > size.height) != (natural.widthPixels > natural.heightPixels)) 1 else 0

    /** The screen's size in points as [frame] shows it, turned or not. */
    private fun shownPoints(frame: ScreenFrame): Pair<Double, Double> = if (frame.rotation % 2 == 1) natural.heightPoints.toDouble() to natural.widthPoints.toDouble() else natural.widthPoints.toDouble() to natural.heightPoints.toDouble()

    override suspend fun pointer(
        action: PointerAction,
        fx: Float,
        fy: Float,
        frame: ScreenFrame,
    ) {
        val (w, h) = shownPoints(frame)
        val x = (fx.coerceIn(0f, 1f) * w)
        val y = (fy.coerceIn(0f, 1f) * h)
        // A finger still down at a new point is a move: the simulator's digitizer has no other.
        idb.touch(x, y, down = action != PointerAction.UP)
    }

    override suspend fun key(key: DeviceKey) {
        val steps = HidKeys.steps(key) ?: return backSwipe(rotation())
        idb.hid(steps)
    }

    /** iOS has no back key: a swipe in from the left edge goes back, as navigation stacks take it. */
    suspend fun backSwipe(rotation: Int = 0) {
        val (w, h) = if (rotation % 2 == 1) natural.heightPoints.toDouble() to natural.widthPoints.toDouble() else natural.widthPoints.toDouble() to natural.heightPoints.toDouble()
        val y = h / 2
        val steps = 12
        val path = (0..steps).map { i -> 1.0 + (w * 0.7 - 1.0) * i / steps }
        idb.hid(
            buildList {
                path.forEachIndexed { i, x ->
                    if (i > 0) add(HidStep.Delay(0.016))
                    add(HidStep.Touch(x, y, down = true))
                }
                add(HidStep.Touch(path.last(), y, down = false))
            },
        )
    }

    override suspend fun type(text: String) {
        if (typesText) {
            idb.hid(listOf(HidStep.Text(text)))
        } else {
            if (!HidKeys.typeable(text)) throw IOException("This idb_companion types only what a US keyboard has keys for: update it (brew upgrade meta/fb/idb) for any text")
            idb.hid(HidKeys.typing(text))
        }
    }

    companion object {
        private const val POLL_MS = 250L
        private const val MIN_SCALE = 0.1
    }
}
