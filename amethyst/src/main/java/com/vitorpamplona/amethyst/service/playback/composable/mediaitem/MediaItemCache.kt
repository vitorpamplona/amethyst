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
package com.vitorpamplona.amethyst.service.playback.composable.mediaitem

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.vitorpamplona.amethyst.commons.compose.GenericBaseCache
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class MediaItemCache : GenericBaseCache<MediaItemData, LoadedMediaItem>(20) {
    companion object {
        const val EXTRA_CALLBACK_URI = "callbackUri"
        const val EXTRA_IS_LIVE_STREAM = "isLiveStream"

        // ExoPlayer's URI-based content-type inference only fires for http(s)
        // schemes + known extensions — BUD-10 blossom URIs have neither, so
        // without an explicit mimeType HLS playlists get routed to
        // ProgressiveMediaSource and fail.
        internal fun toExoPlayerMimeType(
            mimeType: String?,
            videoUri: String? = null,
        ): String? {
            if (!mimeType.isNullOrBlank()) {
                return when (mimeType.lowercase()) {
                    "application/vnd.apple.mpegurl",
                    "application/x-mpegurl",
                    "audio/x-mpegurl",
                    "audio/mpegurl",
                    -> MimeTypes.APPLICATION_M3U8

                    else -> mimeType
                }
            }
            if (videoUri != null && hasM3u8PathExtension(videoUri)) {
                return MimeTypes.APPLICATION_M3U8
            }
            return null
        }

        // Restrict `.m3u8` matching to the path component so a query param or
        // fragment that happens to mention .m3u8 (e.g. `?ref=a.m3u8`) on an
        // MP4 URI doesn't misroute to HlsMediaSource.
        private fun hasM3u8PathExtension(uri: String): Boolean {
            val path = uri.substringBefore('?').substringBefore('#')
            return path.endsWith(".m3u8", ignoreCase = true)
        }
    }

    override suspend fun compute(key: MediaItemData): LoadedMediaItem =
        withContext(Dispatchers.IO) {
            val normalizedMime = toExoPlayerMimeType(key.mimeType, key.videoUri)
            Log.d("MediaItemCache") {
                "compute: videoUri=${key.videoUri} imetaMime=${key.mimeType} -> exoMime=$normalizedMime isLive=${key.isLiveStream}"
            }
            LoadedMediaItem(
                key,
                MediaItem
                    .Builder()
                    .setMediaId(key.videoUri)
                    .setUri(key.videoUri)
                    .apply { normalizedMime?.let { setMimeType(it) } }
                    .setMediaMetadata(
                        MediaMetadata
                            .Builder()
                            .setArtist(key.authorName?.ifBlank { null })
                            .setTitle(key.title?.ifBlank { null } ?: key.videoUri)
                            .setExtras(
                                Bundle().apply {
                                    putString(EXTRA_CALLBACK_URI, key.callbackUri)
                                    putBoolean(EXTRA_IS_LIVE_STREAM, key.isLiveStream)
                                },
                            ).setArtworkUri(
                                try {
                                    key.artworkUri?.toUri()
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    null
                                },
                            ).build(),
                    ).build(),
            )
        }
}
