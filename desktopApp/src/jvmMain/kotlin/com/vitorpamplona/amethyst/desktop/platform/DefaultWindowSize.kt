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
package com.vitorpamplona.amethyst.desktop.platform

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.awt.GraphicsEnvironment
import kotlin.math.min

/** The size the app opens at the first time: 15% past the old 1200x800, roomy enough for the feed beside the notifications. */
private val PREFERRED_WINDOW_SIZE = DpSize(1380.dp, 920.dp)

/** Never more than this share of the screen's usable area, so a small laptop screen still has room around it. */
private const val MAX_SCREEN_SHARE = 0.9f

/**
 * [PREFERRED_WINDOW_SIZE], shrunk to fit the main screen's usable area (without the menu bar, the
 * Dock or the taskbar). AWT reports that area in the same logical units Compose sizes windows in.
 */
fun defaultWindowSize(): DpSize {
    val usable =
        try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        } catch (e: Exception) {
            null
        } ?: return PREFERRED_WINDOW_SIZE
    return fitWindowSize(usable.width.toFloat(), usable.height.toFloat())
}

/** [PREFERRED_WINDOW_SIZE], no larger than [MAX_SCREEN_SHARE] of a usable area of [usableWidth] x [usableHeight]. */
internal fun fitWindowSize(
    usableWidth: Float,
    usableHeight: Float,
): DpSize =
    DpSize(
        width = min(PREFERRED_WINDOW_SIZE.width.value, usableWidth * MAX_SCREEN_SHARE).dp,
        height = min(PREFERRED_WINDOW_SIZE.height.value, usableHeight * MAX_SCREEN_SHARE).dp,
    )
