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
package com.vitorpamplona.amethyst.desktop.service.media

import androidx.compose.runtime.mutableStateMapOf
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import kotlin.math.abs

/**
 * Non-square pixels of anamorphic videos, by URL. Such a video stores its frames at one shape and
 * is shown at another: 1080x720 with 4:9 pixels is a 480x720 portrait video.
 *
 * The video engine on Linux (GStreamer) reports and draws the stored frame, so that video came out
 * as a squashed landscape box. macOS (AVFoundation) applies the pixel ratio itself. Windows reports
 * the frame size too but is left alone until someone checks how its engine draws such a video.
 *
 * The engine exposes no pixel ratio, so [VideoThumbnailCache] records it here when it reads the
 * container for the poster, with the shape the poster came out at, and [displayRatio] and
 * `VideoSurface` show the video at that shape. The engine's own ratio is not a base to correct:
 * it keeps a 16:9 placeholder when no frame arrived within its open timeout, and then draws the
 * picture at 16:9 too.
 *
 * Snapshot state, so a card that composed before the poster probe finished resizes when it lands.
 */
object VideoPixelAspect {
    private class Shape(
        val pixelWidthHeightRatio: Float,
        val displayRatio: Float,
    )

    private val known = mutableStateMapOf<String, Shape>()

    private val engineIgnoresIt = PlatformInfo.host.isLinux

    /** Records [url]'s [pixelWidthHeightRatio] and the [displayRatio] it shows at, unless its pixels are square. */
    fun record(
        url: String,
        pixelWidthHeightRatio: Float,
        displayRatio: Float,
    ) {
        if (!usable(pixelWidthHeightRatio) || !usable(displayRatio) || abs(pixelWidthHeightRatio - 1f) <= 0.01f) return
        known[url] = Shape(pixelWidthHeightRatio, displayRatio)
    }

    /** The pixel ratio [url] was seen with, or null for square pixels or an unread video. */
    fun get(url: String): Float? = known[url]?.pixelWidthHeightRatio

    /** The shape to show [url] at, given the [engineRatio] its engine reports. */
    fun displayRatio(
        url: String?,
        engineRatio: Float,
    ): Float {
        if (!engineIgnoresIt || url == null) return engineRatio
        return known[url]?.displayRatio ?: engineRatio
    }

    private fun usable(ratio: Float) = ratio > 0f && ratio.isFinite()
}
