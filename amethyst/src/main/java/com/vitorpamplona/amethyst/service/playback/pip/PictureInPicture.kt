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
package com.vitorpamplona.amethyst.service.playback.pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.util.Rational

fun makePipParams(
    ratio: Float?,
    bounds: Rect?,
): PictureInPictureParams =
    PictureInPictureParams
        .Builder()
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(true)
            }
            bounds?.let { setSourceRectHint(bounds) }
            ratio?.let { setAspectRatio(Rational((it * 1000).toInt(), 1000)) }
        }.build()

fun Activity.makePipParams(
    isPlaying: Boolean,
    isMuted: Boolean,
    ratio: Float?,
    bounds: Rect?,
): PictureInPictureParams =
    PictureInPictureParams
        .Builder()
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(true)
            }

            setActions(
                listOf(
                    createPlayPauseAction(this@makePipParams, isPlaying),
                    createMuteAction(this@makePipParams, isMuted),
                ),
            )
            bounds?.let { setSourceRectHint(bounds) }
            ratio?.let { setAspectRatio(Rational((it * 1000).toInt(), 1000)) }
        }.build()
