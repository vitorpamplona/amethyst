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
import com.vitorpamplona.amethyst.service.HttpClientManager
import okhttp3.OkHttpClient

class WssOrHttpFactory(httpClient: OkHttpClient) : MediaSource.Factory {
    @UnstableApi
    val http = DefaultMediaSourceFactory(OkHttpDataSource.Factory(httpClient))

    @UnstableApi
    val wss = DefaultMediaSourceFactory(WssStreamDataSource.Factory(httpClient))

    @OptIn(UnstableApi::class)
    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        http.setDrmSessionManagerProvider(drmSessionManagerProvider)
        wss.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    @OptIn(UnstableApi::class)
    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        http.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        wss.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    @OptIn(UnstableApi::class)
    override fun getSupportedTypes(): IntArray {
        return http.supportedTypes
    }

    @OptIn(UnstableApi::class)
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        return if (mediaItem.mediaId.startsWith("wss")) {
            wss.createMediaSource(mediaItem)
        } else {
            http.createMediaSource(mediaItem)
        }
    }
}

@UnstableApi // Extend MediaSessionService
class PlaybackService : MediaSessionService() {
    private var videoViewedPositionCache = VideoViewedPositionCache()

    private var managerAllInOne: MultiPlayerPlaybackManager? = null

    fun newAllInOneDataSource(): MediaSource.Factory {
        // This might be needed for live kit.
        // return WssOrHttpFactory(HttpClientManager.getHttpClient())
        return DefaultMediaSourceFactory(OkHttpDataSource.Factory(HttpClientManager.getHttpClient()))
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
