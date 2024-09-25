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
package com.vitorpamplona.amethyst.service.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.vitorpamplona.amethyst.Amethyst
import okhttp3.OkHttpClient

/**
 * HLS LiveStreams cannot use cache.
 */
@UnstableApi
class CustomMediaSourceFactory(
    val okHttpClient: OkHttpClient,
) : MediaSource.Factory {
    private var cachingFactory: MediaSource.Factory = DefaultMediaSourceFactory(Amethyst.instance.videoCache.get(okHttpClient))
    private var nonCachingFactory: MediaSource.Factory = DefaultMediaSourceFactory(OkHttpDataSource.Factory(okHttpClient))

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        cachingFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
        nonCachingFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        cachingFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        nonCachingFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = nonCachingFactory.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        if (mediaItem.mediaId.contains(".m3u8", true)) {
            return nonCachingFactory.createMediaSource(mediaItem)
        }
        return cachingFactory.createMediaSource(mediaItem)
    }
}
