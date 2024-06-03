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

import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.service.HttpClientManager

/**
 * HLS LiveStreams cannot use cache.
 */
@UnstableApi
class CustomMediaSourceFactory() : MediaSource.Factory {
    private var cachingFactory: MediaSource.Factory =
        DefaultMediaSourceFactory(Amethyst.instance.videoCache.get(HttpClientManager.getHttpClient()))
    private var nonCachingFactory: MediaSource.Factory =
        DefaultMediaSourceFactory(OkHttpDataSource.Factory(HttpClientManager.getHttpClient()))

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

    override fun getSupportedTypes(): IntArray {
        return nonCachingFactory.supportedTypes
    }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        if (mediaItem.mediaId.contains(".m3u8", true)) {
            return nonCachingFactory.createMediaSource(mediaItem)
        }
        return cachingFactory.createMediaSource(mediaItem)
    }
}

class PlaybackService : MediaSessionService() {
    private var videoViewedPositionCache = VideoViewedPositionCache()

    private var managerAllInOne: MultiPlayerPlaybackManager? = null

    @OptIn(UnstableApi::class)
    fun newAllInOneDataSource(): MediaSource.Factory {
        // This might be needed for live kit.
        // return WssOrHttpFactory(HttpClientManager.getHttpClient())
        return CustomMediaSourceFactory()
    }

    fun lazyDS(): MultiPlayerPlaybackManager {
        managerAllInOne?.let {
            return it
        }

        val newInstance = MultiPlayerPlaybackManager(newAllInOneDataSource(), videoViewedPositionCache)
        managerAllInOne = newInstance
        return newInstance
    }

    // Create your Player and MediaSession in the onCreate lifecycle event
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        Log.d("Lifetime Event", "PlaybackService.onCreate")

        // Stop all videos and recreates all managers when the proxy changes.
        HttpClientManager.proxyChangeListeners.add(this@PlaybackService::onProxyUpdated)
    }

    private fun onProxyUpdated() {
        val toDestroyAllInOne = managerAllInOne

        managerAllInOne = MultiPlayerPlaybackManager(newAllInOneDataSource(), videoViewedPositionCache)

        toDestroyAllInOne?.releaseAppPlayers()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        Log.d("Lifetime Event", "onTaskRemoved")
    }

    override fun onDestroy() {
        Log.d("Lifetime Event", "PlaybackService.onDestroy")

        HttpClientManager.proxyChangeListeners.remove(this@PlaybackService::onProxyUpdated)

        managerAllInOne?.releaseAppPlayers()

        super.onDestroy()
    }

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        // Updates any new player ready
        super.onUpdateNotification(session, startInForegroundRequired)

        // Overrides the notification with any player actually playing
        managerAllInOne?.playingContent()?.forEach {
            if (it.player.isPlaying) {
                super.onUpdateNotification(it, startInForegroundRequired)
            }
        }

        // Overrides again with playing with audio
        managerAllInOne?.playingContent()?.forEach {
            if (it.player.isPlaying && it.player.volume > 0) {
                super.onUpdateNotification(it, startInForegroundRequired)
            }
        }
    }

    // Return a MediaSession to link with the MediaController that is making
    // this request.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val id = controllerInfo.connectionHints.getString("id") ?: return null
        val uri = controllerInfo.connectionHints.getString("uri") ?: return null
        val callbackUri = controllerInfo.connectionHints.getString("callbackUri")

        val manager = lazyDS()

        return manager.getMediaSession(
            id,
            uri,
            callbackUri,
            context = this,
            applicationContext = applicationContext,
        )
    }
}
