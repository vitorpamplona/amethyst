/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service.playback.playerPool

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.vitorpamplona.amethyst.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.service.playback.playerPool.aspectRatio.AspectRatioCacher
import com.vitorpamplona.amethyst.service.playback.playerPool.positions.CurrentPlayPositionCacher
import com.vitorpamplona.amethyst.service.playback.playerPool.positions.VideoViewedPositionCache
import com.vitorpamplona.amethyst.service.playback.playerPool.wake.KeepVideosPlaying
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
class ExoPlayerBuilder(
    val okHttp: OkHttpClient,
) {
    fun build(context: Context) =
        ExoPlayer
            .Builder(context)
            .apply {
                setMediaSourceFactory(CustomMediaSourceFactory(okHttp))
            }.build()
            .apply {
                addListener(AspectRatioCacher(MediaAspectRatioCache))
                addListener(KeepVideosPlaying(this))
                addListener(CurrentPlayPositionCacher(this, VideoViewedPositionCache))
            }
}
