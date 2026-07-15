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
import com.vitorpamplona.amethyst.service.playback.PLAYBACK_DIAG_TAG
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemCache
import com.vitorpamplona.amethyst.service.playback.diskCache.HlsLivenessCache
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache
import com.vitorpamplona.amethyst.service.playback.diskCache.isLiveStreaming
import com.vitorpamplona.quartz.utils.Log

/**
 * Whether an HLS item should bypass the disk cache. Pure so the routing is unit-testable.
 *
 * - **Flagged live** (kind:30311 live activity) → always bypass.
 * - **Progressive** (mp4, …) → never bypass; cache normally.
 * - **HLS learned on-demand** → cache. A VOD playlist has `#EXT-X-ENDLIST`, so it is static and its
 *   segments are immutable — safe and worthwhile to cache.
 * - **HLS live or not-yet-classified** → bypass. Caching a live playlist makes ExoPlayer reload a
 *   stale, non-advancing playlist and throw `PlaylistStuckException`, after which playback loops on
 *   the frozen window. Until we have positively learned a URL is on-demand (see [HlsLivenessCache]),
 *   the safe default is to bypass.
 */
internal fun shouldBypassCache(
    isFlaggedLive: Boolean,
    isHls: Boolean,
    isKnownOnDemand: Boolean,
): Boolean =
    when {
        isFlaggedLive -> true
        !isHls -> false
        isKnownOnDemand -> false
        else -> true
    }

/**
 * Decides whether a [MediaItem] plays through the caching data source or bypasses it.
 *
 * The hard constraint is that a **live** HLS stream must never be cached — caching its mutating
 * playlist makes ExoPlayer reload a stale, non-advancing manifest and throw `PlaylistStuckException`
 * (surfaced as `ERROR_CODE_IO_UNSPECIFIED`), after which playback loops replaying the frozen window.
 * But we also *want* to cache immutable on-demand HLS (multi-rendition NIP-71 VOD).
 *
 * A `.m3u8` URL cannot distinguish the two, and the `isLiveStream` metadata flag only marks
 * kind:30311 live activities, so a live `.m3u8` shared in a plain kind:1 note arrives flagged
 * non-live. We therefore learn liveness from ExoPlayer itself: [HlsLivenessRecorder] records the
 * playlist's live/on-demand verdict into [HlsLivenessCache] once loaded, and the *next* play of that
 * URL routes by it. The first (unclassified) play bypasses — the safe default — so a live stream is
 * never cached even once; only a proven on-demand URL is cached, from its second view onward.
 *
 * The `isLiveStream` flag is carried on [MediaItem.mediaMetadata] extras and set by
 * [MediaItemCache] from [com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData].
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
        val id = mediaItem.mediaId
        val flaggedLive = isFlaggedLive(mediaItem)
        val hls = isLiveStreaming(id)
        val knownOnDemand = HlsLivenessCache.isKnownOnDemand(id)
        val bypassCache = shouldBypassCache(flaggedLive, hls, knownOnDemand)

        val source =
            if (bypassCache) {
                nonCachingFactory.createMediaSource(mediaItem)
            } else {
                cachingFactory.createMediaSource(mediaItem)
            }
        // Logs the three routing inputs directly rather than a re-derived label, so it can't drift
        // from shouldBypassCache.
        Log.d(PLAYBACK_DIAG_TAG) {
            "SOURCE ${if (bypassCache) "BYPASS" else "CACHE"} flaggedLive=$flaggedLive hls=$hls knownOnDemand=$knownOnDemand " +
                "mime=${mediaItem.localConfiguration?.mimeType} -> ${source::class.java.simpleName} id=$id"
        }
        return source
    }

    // Only the explicit event-kind flag (kind:30311 live activities). Returns false when the flag
    // is absent or false; the HLS URL check in createMediaSource covers everything else.
    private fun isFlaggedLive(mediaItem: MediaItem): Boolean {
        val extras = mediaItem.mediaMetadata.extras
        return extras != null &&
            extras.containsKey(MediaItemCache.EXTRA_IS_LIVE_STREAM) &&
            extras.getBoolean(MediaItemCache.EXTRA_IS_LIVE_STREAM, false)
    }
}
