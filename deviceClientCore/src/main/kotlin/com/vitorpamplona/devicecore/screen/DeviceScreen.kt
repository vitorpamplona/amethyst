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
package com.vitorpamplona.devicecore.screen

import com.vitorpamplona.devicecore.input.DeviceKey
import kotlinx.coroutines.flow.Flow

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
