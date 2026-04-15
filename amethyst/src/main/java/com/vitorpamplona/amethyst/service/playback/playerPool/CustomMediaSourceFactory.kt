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
package com.vitorpamplona.amethyst.service.playback.playerPool

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemCache
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache
import com.vitorpamplona.amethyst.service.playback.diskCache.isLiveStreaming

/**
 * True live streams (kind 30311) must not be cached. On-demand HLS
 * (e.g. multi-rendition NIP-71 videos) is cached like any other video,
 * because its segments are immutable.
 *
 * The `isLiveStream` flag is carried on [MediaItem.mediaMetadata] extras
 * and set by [MediaItemCache] from [com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData].
 * If the flag is absent (e.g. a [MediaItem] built outside the cache path),
 * we fall back to the URL-based heuristic.
 */
@UnstableApi
class CustomMediaSourceFactory(
    videoCache: VideoCache,
    dataSourceFactory: DataSource.Factory,
) : MediaSource.Factory {
    private var cachingFactory: MediaSource.Factory =
        DefaultMediaSourceFactory(videoCache.get(dataSourceFactory))
    private var nonCachingFactory: MediaSource.Factory =
        DefaultMediaSourceFactory(dataSourceFactory)

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
        if (isLiveStream(mediaItem)) {
            return nonCachingFactory.createMediaSource(mediaItem)
        }
        return cachingFactory.createMediaSource(mediaItem)
    }

    private fun isLiveStream(mediaItem: MediaItem): Boolean {
        val extras = mediaItem.mediaMetadata.extras
        return if (extras != null && extras.containsKey(MediaItemCache.EXTRA_IS_LIVE_STREAM)) {
            extras.getBoolean(MediaItemCache.EXTRA_IS_LIVE_STREAM, false)
        } else {
            // Fallback for MediaItems that weren't built via MediaItemCache.
            isLiveStreaming(mediaItem.mediaId)
        }
    }
}
